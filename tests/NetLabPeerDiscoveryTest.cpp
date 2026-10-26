// SPDX-License-Identifier: Apache-2.0
// Exercise production peer verification with every network/clock call simulated.
#define main hecker_netlab_entry_for_test
#include "../netlab/native/lan_main.cpp"
#undef main
#include <cstdlib>
#include <functional>

namespace {
constexpr int kWireFd = 72;
uint64_t fakeTime, inputAt;
bool eof, failSend;
unsigned checks, opened, closed;
std::vector<Bytes> requests;
std::vector<std::string> records;
std::map<uint32_t, unsigned> probes;
struct Reply { uint64_t due; Bytes frame; unsigned type; };
std::deque<Reply> replies;
std::function<void(uint32_t, unsigned)> respond;

void check(bool ok, const char* message) {
    ++checks;
    if (!ok) { std::cerr << message << '\n'; std::abort(); }
}
Link fixture() {
    return {"wlan0", ip("192.168.1.2"), ip("192.168.1.1"), 24,
        mac("02:00:00:00:00:02"), mac("02:00:00:00:00:01")};
}
const uint32_t a = ip("192.168.1.30"), b = ip("192.168.1.8");
const Mac am = mac("36:5b:39:87:cd:56"), bm = mac("3e:6e:74:7b:7e:5c");
const Mac broadcast{255, 255, 255, 255, 255, 255};

void reset() {
    check(opened == closed, "packet descriptor leaked");
    fakeTime = 1000; inputAt = 0; eof = failSend = false; interrupted = 0;
    requests.clear(); records.clear(); probes.clear(); replies.clear(); respond = {};
}
void reply(uint32_t address, const Mac& hardware, uint64_t delay = 20,
        unsigned type = PACKET_HOST) {
    auto link = fixture();
    replies.push_back({fakeTime + delay, arp(hardware, address, link.ownMac, link.own, false), type});
    std::sort(replies.begin(), replies.end(), [](const Reply& x, const Reply& y) { return x.due < y.due; });
}
void verify(const std::vector<std::pair<uint32_t, Mac>>& selected, const std::string& error = "") {
    auto link = fixture(); Wire wire(link); Input input;
    try { verifyPeers(wire, link, selected, input); check(error.empty(), "verification unexpectedly succeeded"); }
    catch (const std::runtime_error& actual) { check(actual.what() == error, "unexpected verification failure"); }
    check(fakeTime - 1000 < 15000, "verification exceeded startup budget");
}
bool destination(const Bytes& frame, const Mac& hardware) {
    return std::equal(hardware.begin(), hardware.end(), frame.begin());
}
}

extern "C" int __wrap_socket(int domain, int type, int protocol) {
    check(domain == AF_PACKET && (type & SOCK_RAW) && protocol == htons(ETH_P_ALL),
        "test must not open a live socket");
    ++opened; return kWireFd;
}
extern "C" unsigned __wrap_if_nametoindex(const char* name) {
    check(std::string(name) == "wlan0", "unexpected interface"); return 3;
}
extern "C" int __wrap_bind(int fd, const sockaddr* address, socklen_t) {
    const auto* link = reinterpret_cast<const sockaddr_ll*>(address);
    check(fd == kWireFd && link->sll_family == AF_PACKET && link->sll_ifindex == 3,
        "unexpected packet bind"); return 0;
}
extern "C" int __wrap_setsockopt(int fd, int level, int option, const void*, socklen_t) {
    check(fd == kWireFd && level == SOL_SOCKET
        && (option == SO_ATTACH_FILTER || option == SO_RCVBUF), "unexpected socket option"); return 0;
}
extern "C" int __wrap_close(int fd) {
    check(fd == kWireFd, "unexpected close"); ++closed; return 0;
}
extern "C" ssize_t __wrap_sendto(int fd, const void* data, size_t length, int,
        const sockaddr* address, socklen_t) {
    check(fd == kWireFd && address->sa_family == AF_PACKET && length == 42,
        "unexpected packet transmission");
    if (failSend) { errno = EIO; return -1; }
    const auto* bytes = static_cast<const uint8_t*>(data); Bytes frame(bytes, bytes + length);
    auto link = fixture(); uint32_t sender; Mac hardware;
    check(arpSender(frame, sender, hardware) && sender == link.own && hardware == link.ownMac
        && frame[21] == 1, "verification must send genuine ARP requests only");
    uint32_t target = hostIp(bytes + 38);
    const auto* destinationLink = reinterpret_cast<const sockaddr_ll*>(address);
    check(std::equal(frame.begin(), frame.begin() + 6, destinationLink->sll_addr), "L2 destination mismatch");
    requests.push_back(frame); unsigned count = ++probes[target];
    if (respond) respond(target, count);
    return length;
}
extern "C" ssize_t __wrap_recvfrom(int fd, void* data, size_t capacity, int,
        sockaddr* address, socklen_t*) {
    check(fd == kWireFd, "unexpected receive");
    if (replies.empty() || replies.front().due > fakeTime) { errno = EAGAIN; return -1; }
    Reply packet = std::move(replies.front()); replies.pop_front();
    check(packet.frame.size() <= capacity, "fixture too large");
    std::memcpy(data, packet.frame.data(), packet.frame.size());
    auto* from = reinterpret_cast<sockaddr_ll*>(address);
    from->sll_ifindex = 3; from->sll_pkttype = packet.type;
    return packet.frame.size();
}
extern "C" int __wrap_poll(pollfd* descriptors, nfds_t count, int timeout) {
    check(count == 1 || count == 2, "unexpected poll");
    fakeTime += timeout;
    int ready = 0;
    for (nfds_t i = 0; i < count; ++i) {
        check(descriptors[i].fd == kWireFd || descriptors[i].fd == STDIN_FILENO, "unexpected polling target");
        bool available = descriptors[i].fd == STDIN_FILENO ? inputAt && fakeTime >= inputAt
            : !replies.empty() && replies.front().due <= fakeTime;
        descriptors[i].revents = available ? POLLIN : 0; ready += available;
    }
    return ready;
}
extern "C" ssize_t __wrap_read(int fd, void* buffer, size_t capacity) {
    check(fd == STDIN_FILENO && inputAt && fakeTime >= inputAt, "unexpected controller read");
    inputAt = 0;
    if (eof) return 0;
    check(capacity >= 5, "short controller buffer"); std::memcpy(buffer, "STOP\n", 5); return 5;
}
extern "C" ssize_t __wrap_write(int fd, const void* bytes, size_t length) {
    check(fd == STDOUT_FILENO, "unexpected output descriptor");
    records.emplace_back(static_cast<const char*>(bytes), length); return length;
}
extern "C" int __wrap_clock_gettime(clockid_t id, timespec* time) {
    check(id == CLOCK_BOOTTIME, "unexpected clock");
    time->tv_sec = fakeTime / 1000; time->tv_nsec = (fakeTime % 1000) * 1000000; return 0;
}
// Fortified builds can use these aliases instead of the regular symbols.
extern "C" int __wrap___poll_chk(pollfd* p, nfds_t n, int timeout, size_t capacity) {
    check(n <= capacity / sizeof(pollfd), "invalid poll buffer"); return __wrap_poll(p, n, timeout);
}
extern "C" ssize_t __wrap___read_chk(int fd, void* buffer, size_t n, size_t capacity) {
    check(n <= capacity, "invalid read buffer"); return __wrap_read(fd, buffer, n);
}
extern "C" ssize_t __wrap___recvfrom_chk(int fd, void* buffer, size_t n, size_t capacity,
        int flags, sockaddr* address, socklen_t* length) {
    check(n <= capacity, "invalid receive buffer"); return __wrap_recvfrom(fd, buffer, n, flags, address, length);
}

int main() {
    // Private/randomized unicast MACs are valid identities; the first broadcast
    // can be lost while a directed retry reaches the same, sleeping Wi-Fi peer.
    reset(); respond = [](uint32_t address, unsigned count) { if (count == 2) reply(address, am); };
    verify({{a, am}});
    check(probes[a] == 2 && records.empty(), "lost first probe was not recovered");
    check(destination(requests[0], broadcast) && destination(requests[1], am)
        && std::equal(am.begin(), am.end(), requests[1].begin() + 32), "directed retry did not use scanned MAC");

    reset(); respond = [](uint32_t address, unsigned count) { if (count == 1) reply(address, am, 1800); };
    verify({{a, am}}); check(probes[a] == 2, "late first response was not accepted");

    reset(); respond = [](uint32_t address, unsigned count) { if (count == 3) reply(address, am); };
    verify({{a, am}}); check(probes[a] == 3 && destination(requests[2], broadcast), "broadcast retry was not bounded/repeated");

    reset(); respond = [](uint32_t address, unsigned count) {
        if ((address == a && count == 1) || (address == b && count == 2)) reply(address, address == a ? am : bm);
    };
    verify({{a, am}, {b, bm}});
    check(probes[a] == 1 && probes[b] == 2, "resolved peers were reprobed unnecessarily");

    reset(); verify({{a, am}, {b, bm}}, "peer_unreachable");
    check(probes[a] == 4 && probes[b] == 4 && records.size() == 2, "missing peers were not individually reported after retries");
    check(records[0] == "VERIFY 192.168.1.30 36:5b:39:87:cd:56 missing\n", "missing peer details lost");

    reset(); respond = [](uint32_t address, unsigned) { reply(address, bm); };
    verify({{a, am}}, "peer_mac_changed");
    check(probes[a] == 1 && records.size() == 1
        && records[0] == "VERIFY 192.168.1.30 36:5b:39:87:cd:56 3e:6e:74:7b:7e:5c\n", "changed MAC was accepted or misreported");

    reset(); respond = [](uint32_t address, unsigned count) {
        if (count == 1) { reply(b, bm); reply(address, fixture().ownMac); reply(address, am, 20, PACKET_OUTGOING); }
        if (count == 4) reply(address, am);
    };
    verify({{a, am}}); check(probes[a] == 4, "unrelated/self/outgoing frames verified a peer");

    reset(); inputAt = 1100; verify({{a, am}}, "cancelled");
    check(probes[a] == 1 && records.empty(), "stop did not cancel preflight promptly");
    reset(); inputAt = 1100; eof = true; verify({{a, am}}, "controller_lost");
    check(probes[a] == 1, "controller EOF did not stop probing");
    reset(); failSend = true; verify({{a, am}}, "send_failed");

    // Full-subnet discovery retains its one-pass limit and ordinary broadcasts.
    reset(); respond = [](uint32_t address, unsigned) { reply(address, am); };
    { auto link = fixture(); Wire wire(link); Input input;
      auto found = discover(wire, link, {a}, input, true);
      check(found.at(a) == am && probes[a] == 1 && records.size() == 1, "scan semantics changed"); }

    reset(); std::vector<std::pair<uint32_t, Mac>> selected;
    for (unsigned i = 3; i < 35; ++i) { Mac hardware = am; hardware[5] = i; selected.emplace_back(ip("192.168.1." + std::to_string(i)), hardware); }
    verify(selected, "peer_unreachable");
    check(requests.size() == 128 && records.size() == 32, "maximum-size verification was not bounded");

    // Gateway loss also tolerates dropped probes, including during SIGTERM repair.
    for (bool stopping : {false, true}) {
        reset(); interrupted = stopping;
        respond = [](uint32_t address, unsigned count) { if (count == 3) reply(address, fixture().gatewayMac); };
        auto link = fixture(); Wire wire(link);
        check(gatewayStillMatches(wire, link) && probes[link.gateway] == 3, "gateway retry/stop restoration failed");
    }
    reset(); respond = [](uint32_t address, unsigned) { reply(address, bm); };
    { auto link = fixture(); Wire wire(link); check(!gatewayStillMatches(wire, link), "changed gateway accepted"); }
    reset();
    std::cout << "NetLab production peer verification: " << checks
        << " checks PASS (loss/delay, direct retry, MAC changes, cancellation; no network I/O)\n";
}
