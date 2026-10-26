// SPDX-License-Identifier: Apache-2.0
// Fixed stdin protocol. Per-session firewall guard; no shell or global forwarding changes.
#include "lan_core.h"
#include "forwarding_guard.h"
#include <algorithm>
#include <cerrno>
#include <csignal>
#include <cstring>
#include <fcntl.h>
#include <fstream>
#include <iostream>
#include <linux/filter.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <memory>
#include <net/if.h>
#include <netinet/in.h>
#include <poll.h>
#include <stdexcept>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

using namespace hecker::lan;
namespace {
volatile sig_atomic_t interrupted = 0;
void onSignal(int) { interrupted = 1; }
uint64_t clockMs() { timespec t{}; clock_gettime(CLOCK_BOOTTIME, &t); return uint64_t(t.tv_sec) * 1000 + t.tv_nsec / 1000000; }
void need(bool ok, const char* code) { if (!ok) throw std::runtime_error(code); }
struct Fd {
    int value;
    explicit Fd(int v) : value(v) {}
    ~Fd() { if (value >= 0) close(value); }
    Fd(const Fd&) = delete;
    Fd& operator=(const Fd&) = delete;
};
void output(const std::string& line) {
    std::string data = line + "\n";
    // A dead/full controller pipe triggers cleanup instead of hanging the relay.
    ssize_t n = write(STDOUT_FILENO, data.data(), data.size());
    if (n != ssize_t(data.size())) interrupted = 1;
}
class Input {
    std::string pending;
public:
    bool eof = false;
    std::vector<std::string> read() {
        char buffer[1024];
        ssize_t n = ::read(STDIN_FILENO, buffer, sizeof(buffer));
        if (n == 0) eof = true;
        if (n < 0) { if (errno != EAGAIN && errno != EINTR) eof = true; return {}; }
        pending.append(buffer, n);
        std::vector<std::string> lines;
        size_t end;
        while ((end = pending.find('\n')) != std::string::npos) {
            need(end <= 1024 && lines.size() < 40, "invalid_command");
            lines.push_back(pending.substr(0, end)); pending.erase(0, end + 1);
        }
        need(pending.size() <= 1024, "invalid_command"); return lines;
    }
    std::vector<std::string> initial(size_t count, uint64_t deadline) {
        std::vector<std::string> lines;
        while (lines.size() < count) {
            need(!eof && !interrupted && clockMs() < deadline, "controller_lost");
            pollfd p{STDIN_FILENO, POLLIN, 0};
            if (poll(&p, 1, 100) > 0) {
                auto got = read(); lines.insert(lines.end(), got.begin(), got.end());
            }
        }
        return lines;
    }
};
uint32_t hostIp(const void* bytes) {
    const auto* b = static_cast<const uint8_t*>(bytes);
    return uint32_t(b[0]) << 24 | uint32_t(b[1]) << 16 | uint32_t(b[2]) << 8 | b[3];
}
// Android Wi-Fi routes can live outside the main table. Dump all IPv4 tables.
bool routeMatches(unsigned index, uint32_t gateway) {
    Fd fd(socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC | SOCK_NONBLOCK, NETLINK_ROUTE));
    if (fd.value < 0) return false;
    struct { nlmsghdr header; rtmsg route; } request{};
    request.header.nlmsg_len = NLMSG_LENGTH(sizeof(rtmsg)); request.header.nlmsg_type = RTM_GETROUTE;
    request.header.nlmsg_flags = NLM_F_REQUEST | NLM_F_DUMP; request.header.nlmsg_seq = 731;
    request.route.rtm_family = AF_INET;
    sockaddr_nl kernel{}; kernel.nl_family = AF_NETLINK;
    if (sendto(fd.value, &request, request.header.nlmsg_len, 0, reinterpret_cast<sockaddr*>(&kernel), sizeof(kernel)) < 0) return false;
    uint64_t deadline = clockMs() + 500;
    while (clockMs() < deadline) {
        pollfd p{fd.value, POLLIN, 0}; if (poll(&p, 1, 50) <= 0) continue;
        alignas(nlmsghdr) char buffer[32768];
        int length = recv(fd.value, buffer, sizeof(buffer), 0); if (length <= 0) continue;
        // NLMSG_NEXT can leave a negative count for an unpadded final record.
        // Guard that before the unsigned comparison required by Bionic's macro.
        for (auto* h = reinterpret_cast<nlmsghdr*>(buffer);
                length >= 0 && NLMSG_OK(h, static_cast<unsigned>(length));
                h = NLMSG_NEXT(h, length)) {
            if (h->nlmsg_seq != 731) continue;
            if (h->nlmsg_type == NLMSG_DONE || h->nlmsg_type == NLMSG_ERROR) return false;
            if (h->nlmsg_type != RTM_NEWROUTE || h->nlmsg_len < NLMSG_LENGTH(sizeof(rtmsg))) continue;
            auto* route = reinterpret_cast<rtmsg*>(NLMSG_DATA(h));
            if (route->rtm_family != AF_INET || route->rtm_dst_len || route->rtm_type != RTN_UNICAST) continue;
            unsigned iface = 0; uint32_t gw = 0; int remaining = RTM_PAYLOAD(h);
            for (auto* a = RTM_RTA(route); RTA_OK(a, remaining); a = RTA_NEXT(a, remaining)) {
                if (a->rta_type == RTA_OIF && RTA_PAYLOAD(a) == 4) std::memcpy(&iface, RTA_DATA(a), 4);
                if (a->rta_type == RTA_GATEWAY && RTA_PAYLOAD(a) == 4) gw = hostIp(RTA_DATA(a));
            }
            if (iface == index && gw == gateway) return true;
        }
    }
    return false;
}
Mac interfaceMac(const Link& link, bool /*relay*/) {
    Fd fd(socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0)); need(fd.value >= 0, "interface_unavailable");
    ifreq info{}; std::memcpy(info.ifr_name, link.iface.c_str(), link.iface.size());
    need(ioctl(fd.value, SIOCGIFFLAGS, &info) == 0 && (info.ifr_flags & IFF_UP)
            && !(info.ifr_flags & IFF_LOOPBACK), "link_changed");
    need(ioctl(fd.value, SIOCGIFADDR, &info) == 0
            && hostIp(&reinterpret_cast<sockaddr_in*>(&info.ifr_addr)->sin_addr) == link.own, "link_changed");
    need(ioctl(fd.value, SIOCGIFNETMASK, &info) == 0
            && hostIp(&reinterpret_cast<sockaddr_in*>(&info.ifr_netmask)->sin_addr) == (0xffffffffU << (32 - link.prefix)), "link_changed");
    need(ioctl(fd.value, SIOCGIFHWADDR, &info) == 0, "interface_unavailable");
    Mac hardware{}; std::copy_n(reinterpret_cast<uint8_t*>(info.ifr_hwaddr.sa_data), 6, hardware.begin());
    need(unicast(hardware), "invalid_mac");
    need(routeMatches(if_nametoindex(link.iface.c_str()), link.gateway), "route_changed");
    return hardware;
}
class Wire {
    Fd fd;
    unsigned index;
public:
    explicit Wire(const Link& link) : fd(socket(AF_PACKET, SOCK_RAW | SOCK_CLOEXEC | SOCK_NONBLOCK, htons(ETH_P_ALL))), index(if_nametoindex(link.iface.c_str())) {
        need(fd.value >= 0, "raw_socket_denied"); need(index != 0, "interface_unavailable");
        sockaddr_ll address{}; address.sll_family = AF_PACKET; address.sll_ifindex = index; address.sll_protocol = htons(ETH_P_ALL);
        need(bind(fd.value, reinterpret_cast<sockaddr*>(&address), sizeof(address)) == 0, "raw_socket_denied");
        uint32_t first = uint32_t(link.ownMac[0]) << 24 | uint32_t(link.ownMac[1]) << 16 | uint32_t(link.ownMac[2]) << 8 | link.ownMac[3];
        uint32_t last = uint32_t(link.ownMac[4]) << 8 | link.ownMac[5];
        sock_filter filter[] = {
            BPF_STMT(BPF_LD | BPF_H | BPF_ABS, 12),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, ETH_P_ARP, 5, 0),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, ETH_P_IP, 0, 5),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, 0),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, first, 0, 3),
            BPF_STMT(BPF_LD | BPF_H | BPF_ABS, 4),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, last, 0, 1),
            BPF_STMT(BPF_RET | BPF_K, 65550), BPF_STMT(BPF_RET | BPF_K, 0)
        };
        sock_fprog program{static_cast<unsigned short>(sizeof(filter) / sizeof(filter[0])), filter};
        need(setsockopt(fd.value, SOL_SOCKET, SO_ATTACH_FILTER, &program, sizeof(program)) == 0, "packet_filter_denied");
        int buffer = 1024 * 1024; setsockopt(fd.value, SOL_SOCKET, SO_RCVBUF, &buffer, sizeof(buffer));
    }
    int descriptor() const { return fd.value; }
    bool send(const Bytes& frame) {
        sockaddr_ll target{}; target.sll_family = AF_PACKET; target.sll_ifindex = index; target.sll_halen = 6;
        target.sll_protocol = htons(uint16_t(frame[12]) << 8 | frame[13]);
        std::copy_n(frame.begin(), 6, target.sll_addr);
        return sendto(fd.value, frame.data(), frame.size(), MSG_DONTWAIT, reinterpret_cast<sockaddr*>(&target), sizeof(target)) == ssize_t(frame.size());
    }
    Bytes receive() {
        Bytes frame(65550); sockaddr_ll from{}; socklen_t size = sizeof(from);
        ssize_t n = recvfrom(fd.value, frame.data(), frame.size(), 0, reinterpret_cast<sockaddr*>(&from), &size);
        if (n <= 0 || from.sll_pkttype == PACKET_OUTGOING || from.sll_ifindex != int(index)) return {};
        frame.resize(n); return frame;
    }
    void announce(const std::vector<Bytes>& frames) { for (const auto& frame : frames) send(frame); }
};
std::map<uint32_t, Mac> discover(Wire& wire, const Link& link, const std::vector<uint32_t>& targets, Input& input, bool print) {
    std::map<uint32_t, Mac> found; size_t cursor = 0; uint64_t next = 0, finish = 0;
    while (!interrupted) {
        uint64_t now = clockMs();
        if (input.eof) throw std::runtime_error("controller_lost");
        if (now >= next && cursor < targets.size()) {
            need(wire.send(arp(link.ownMac, link.own, {}, targets[cursor++], true)), "send_failed");
            next = now + 40; if (cursor == targets.size()) finish = now + 1200;
        }
        if (targets.empty() || (finish && now >= finish)) break;
        pollfd descriptors[]{{STDIN_FILENO, POLLIN, 0}, {wire.descriptor(), POLLIN, 0}};
        poll(descriptors, 2, 20);
        if (descriptors[0].revents) { auto commands = input.read(); if (!commands.empty()) throw std::runtime_error("cancelled"); }
        for (int i = 0; i < 128; ++i) {
            auto frame = wire.receive(); if (frame.empty()) break;
            uint32_t address; Mac hardware;
            if (!arpSender(frame, address, hardware) || hardware == link.ownMac
                    || std::find(targets.begin(), targets.end(), address) == targets.end()) continue;
            if (found.count(address) && found.at(address) != hardware) throw std::runtime_error("neighbor_changed");
            if (!found.count(address) && print) output("PEER " + ipText(address) + " " + macText(hardware));
            found[address] = hardware;
        }
    }
    need(!interrupted, "cancelled"); return found;
}
void peerStats(const Peer& p) {
        output("STAT " + ipText(p.address) + " " + modeText(p.mode) + " " + std::to_string(p.up) + " " + std::to_string(p.down)
                + " " + std::to_string(p.upload.forwarded) + " " + std::to_string(p.download.forwarded)
                + " " + std::to_string(p.upload.dropped) + " " + std::to_string(p.download.dropped));
}
void stats(const Session& session) { for (const auto& entry : session.peers()) peerStats(entry.second); }
bool gatewayStillMatches(Wire& wire, const Link& link) {
    if (!wire.send(arp(link.ownMac, link.own, {}, link.gateway, true))) return false;
    uint64_t end = clockMs() + 400;
    while (clockMs() < end) {
        pollfd descriptor{wire.descriptor(), POLLIN, 0}; poll(&descriptor, 1, 10);
        for (int i = 0; i < 128; ++i) {
            auto frame = wire.receive(); if (frame.empty()) break;
            uint32_t address; Mac hardware;
            if (arpSender(frame, address, hardware) && address == link.gateway) return hardware == link.gatewayMac;
        }
    }
    return false;
}
void run(Session& session, Link& link, Wire& wire, Input& input, ForwardingGuard& guard) {
    std::string reason = "stopped"; uint64_t announceAt = 0, checkAt = 0, statsAt = 0, repairAt = 0;
    std::map<uint32_t, uint64_t> repairs;
    bool sameLink = true;
    // Even the initial integrity check can block on xtables; finish it before arming.
    need(interfaceMac(link, true) == link.ownMac, "link_changed");
    need(guard.intact(), "forwarding_guard_lost");
    checkAt = clockMs() + 2000;
    session.beginActive(clockMs());
    output("STARTED");
    stats(session); // Establish per-policy baselines before the first intercepted packet.
    try {
        while (!interrupted && !input.eof && session.active() && !session.expired(clockMs())) {
            uint64_t now = clockMs();
            if (now >= checkAt) {
                need(interfaceMac(link, true) == link.ownMac, "link_changed");
                need(guard.intact(), "forwarding_guard_lost");
                checkAt = clockMs() + 2000;
            }
            now = clockMs();
            if (session.expired(now)) break;
            if (now >= announceAt) {
                for (const auto& frame : session.announcements(false)) need(wire.send(frame), "send_failed");
                wire.send(arp(link.ownMac, link.own, {}, link.gateway, true)); announceAt = now + 1500;
            }
            if (now >= repairAt) {
                for (auto it = repairs.begin(); it != repairs.end();) {
                    if (it->second < now) it = repairs.erase(it);
                    else { wire.announce(session.announcements(true, it->first)); ++it; }
                }
                repairAt = now + 400;
            }
            pollfd descriptors[]{{STDIN_FILENO, POLLIN, 0}, {wire.descriptor(), POLLIN, 0}};
            poll(descriptors, 2, 5);
            if (descriptors[0].revents) for (const auto& command : input.read()) {
                auto w = words(command);
                if (w.size() == 1 && w[0] == "PING") session.heartbeat(now);
                else if (w.size() == 1 && w[0] == "STOP") { reason = "stopped"; session.stop(now); }
                else if (w.size() == 1 && w[0] == "ABORT") { reason = "link_changed"; sameLink = false; session.stop(now); }
                else if (!w.empty() && w[0] == "SET") {
                    uint32_t address = session.configure(command, now);
                    repairs.erase(address);
                    peerStats(session.peers().at(address));
                    for (const auto& frame : session.announcements(false, address)) need(wire.send(frame), "send_failed");
                    statsAt = 0;
                }
                else if (w.size() == 2 && w[0] == "RESTORE") {
                    uint32_t address = ip(w[1]); session.restore(address, now); repairs[address] = now + 2000;
                    wire.announce(session.announcements(true, address)); output("RESTORED " + ipText(address));
                } else throw std::runtime_error("invalid_command");
            }
            if (!sameLink) break;
            for (int i = 0; i < 128; ++i) {
                auto frame = wire.receive(); if (frame.empty()) break;
                uint32_t address; Mac hardware;
                if (arpSender(frame, address, hardware)) {
                    if (address == link.gateway) need(hardware == link.gatewayMac, "neighbor_changed");
                    auto peer = session.peers().find(address);
                    if (peer != session.peers().end()) need(peer->second.hardware == hardware, "neighbor_changed");
                }
                session.receive(frame, now);
            }
            for (const auto& packet : session.drain(now)) {
                bool sent = wire.send(packet.frame); session.sent(packet, sent); need(sent, "send_failed");
            }
            if (now >= statsAt) { stats(session); statsAt = now + 1000; }
        }
        if (session.expired(clockMs())) reason = "expired_or_watchdog";
        else if (interrupted || input.eof) reason = "controller_lost";
    } catch (const std::exception& error) { reason = error.what(); }
    output("STOPPING");
    // Restore only on the original interface/address/route. A new LAN must never inherit repairs.
    if (!session.stopping()) session.stop(clockMs());
    try { sameLink = sameLink && reason != "neighbor_changed" && interfaceMac(link, false) == link.ownMac && gatewayStillMatches(wire, link); }
    catch (...) { sameLink = false; }
    uint64_t end = clockMs() + 2000, next = 0;
    while (sameLink && clockMs() < end) {
        uint64_t now = clockMs();
        if (now >= next) { wire.announce(session.announcements(true)); next = now + 400; }
        for (int i = 0; i < 128; ++i) { auto frame = wire.receive(); if (frame.empty()) break; session.receive(frame, now); }
        for (const auto& packet : session.drain(now)) session.sent(packet, wire.send(packet.frame));
        pollfd descriptor{wire.descriptor(), POLLIN, 0}; poll(&descriptor, 1, 5);
    }
    if (!guard.clear()) reason = "forwarding_guard_cleanup_failed";
    stats(session); output("END " + reason + (sameLink ? "" : "_repair_unavailable"));
}
}
int main() {
    signal(SIGTERM, onSignal); signal(SIGINT, onSignal); signal(SIGHUP, onSignal); signal(SIGPIPE, SIG_IGN);
    fcntl(STDIN_FILENO, F_SETFL, fcntl(STDIN_FILENO, F_GETFL) | O_NONBLOCK);
    fcntl(STDOUT_FILENO, F_SETFL, fcntl(STDOUT_FILENO, F_GETFL) | O_NONBLOCK);
    try {
        need(geteuid() == 0, "root_required");
        Input input; uint64_t deadline = clockMs() + 20000;
        auto lines = input.initial(1, deadline); auto w = words(lines.front());
        if (w.size() == 1 && w[0] == "INFO") { need(lines.size() == 1, "invalid_command"); output("READY 1"); return 0; }
        need(w.size() >= 5 && (w[0] == "SCAN" || w[0] == "START"), "invalid_command");
        bool start = w[0] == "START";
        need(w.size() == (start ? 10U : 5U), "invalid_command");
        Link link; link.iface = w[1]; link.own = ip(w[2]); link.prefix = number(w[3], 30); link.gateway = ip(w[4]); link.validate();
        link.ownMac = interfaceMac(link, start); Wire wire(link);
        std::vector<std::pair<uint32_t, Mac>> selected;
        if (start) {
            size_t count = number(w[9], kMaxPeers); need(count != 0, "invalid_peer");
            if (lines.size() < count + 2) {
                auto more = input.initial(count + 2 - lines.size(), deadline); lines.insert(lines.end(), more.begin(), more.end());
            }
            need(lines.size() == count + 2 && lines.back() == "COMMIT", "invalid_command");
            for (size_t i = 0; i < count; ++i) {
                auto p = words(lines[i + 1]); need(p.size() == 3 && p[0] == "PEER", "invalid_peer");
                uint32_t address = ip(p[1]); need(link.peer(address), "invalid_peer");
                selected.emplace_back(address, mac(p[2]));
            }
        } else need(lines.size() == 1, "invalid_command");
        auto gateway = discover(wire, link, {link.gateway}, input, false);
        need(gateway.count(link.gateway), "gateway_unreachable"); link.gatewayMac = gateway.at(link.gateway);
        output("LINK " + macText(link.ownMac) + " " + macText(link.gatewayMac));
        if (!start) {
            discover(wire, link, scanTargets(link), input, true); output("END scanned"); return 0;
        }
        std::vector<uint32_t> addresses; for (const auto& p : selected) addresses.push_back(p.first);
        auto found = discover(wire, link, addresses, input, false);
        need(interfaceMac(link, true) == link.ownMac, "link_changed");
        need(gatewayStillMatches(wire, link), "neighbor_changed");
        Session session(link, clockMs(), number(w[5], 3600));
        for (const auto& p : selected) {
            need(found.count(p.first) && found.at(p.first) == p.second, "peer_unreachable_or_changed");
            session.add(p.first, p.second, mode(w[6]), number(w[7], kMaxRate), number(w[8], kMaxRate), clockMs());
        }
        ForwardingGuard guard(link.iface, "HKNETLAB_" + std::to_string(getpid()));
        std::vector<std::string> guarded; for (const auto& p : selected) guarded.push_back(ipText(p.first));
        guard.install(guarded); guard.supervise(number(w[5], 3600));
        run(session, link, wire, input, guard); return 0;
    } catch (const std::exception& error) { output(std::string("ERROR ") + error.what()); return 1; }
}
