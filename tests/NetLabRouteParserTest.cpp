// SPDX-License-Identifier: Apache-2.0
// Compile the production route reader; every OS interaction below is wrapped.
#define main hecker_netlab_entry_for_test
#include "../netlab/native/lan_main.cpp"
#undef main
#include <cstdlib>
#include <deque>

namespace {
constexpr int kRouteFd = 71;
uint64_t fakeTime;
unsigned opened, closed, sent, checks;
std::deque<Bytes> replies;

void check(bool ok, const char* message) {
    ++checks;
    if (!ok) { std::cerr << message << '\n'; std::abort(); }
}

Bytes route(unsigned iface, uint32_t gateway) {
    Bytes message(NLMSG_LENGTH(sizeof(rtmsg)), 0);
    auto attribute = [&](unsigned type, const void* value) {
        size_t offset = NLMSG_ALIGN(message.size());
        message.resize(offset + RTA_SPACE(sizeof(uint32_t)), 0);
        auto* a = reinterpret_cast<rtattr*>(message.data() + offset);
        a->rta_type = type;
        a->rta_len = RTA_LENGTH(sizeof(uint32_t));
        std::memcpy(RTA_DATA(a), value, sizeof(uint32_t));
    };
    uint32_t networkGateway = htonl(gateway);
    attribute(RTA_OIF, &iface);
    attribute(RTA_GATEWAY, &networkGateway);
    auto* h = reinterpret_cast<nlmsghdr*>(message.data());
    h->nlmsg_len = message.size();
    h->nlmsg_type = RTM_NEWROUTE;
    h->nlmsg_seq = 731;
    auto* r = reinterpret_cast<rtmsg*>(NLMSG_DATA(h));
    r->rtm_family = AF_INET;
    r->rtm_type = RTN_UNICAST;
    r->rtm_table = 117;  // Android policy routes need not use the main table.
    return message;
}

nlmsghdr& header(Bytes& bytes) { return *reinterpret_cast<nlmsghdr*>(bytes.data()); }
rtmsg& payload(Bytes& bytes) { return *reinterpret_cast<rtmsg*>(NLMSG_DATA(&header(bytes))); }

void scenario(std::initializer_list<Bytes> packets, bool expected) {
    static unsigned number = 0;
    ++number;
    replies = packets;
    fakeTime = 1000;
    opened = closed = sent = 0;
    bool actual = routeMatches(3, 0xc0a80a01);
    if (actual != expected) std::cerr << "Route scenario " << number
        << ": expected " << expected << ", got " << actual
        << ", requests " << sent << ", unread replies " << replies.size()
        << ", elapsed " << fakeTime - 1000 << " ms\n";
    check(actual == expected, "unexpected route result");
    check(opened == 1 && closed == 1 && sent == 1, "route socket cleanup/request changed");
}
}

extern "C" int __wrap_socket(int domain, int type, int protocol) {
    check(domain == AF_NETLINK && protocol == NETLINK_ROUTE && (type & SOCK_RAW),
        "test must not open a live or packet socket");
    ++opened;
    return kRouteFd;
}
extern "C" int __wrap_close(int fd) {
    check(fd == kRouteFd, "unexpected descriptor");
    ++closed;
    return 0;
}
extern "C" ssize_t __wrap_sendto(int fd, const void* data, size_t length, int,
        const sockaddr* address, socklen_t) {
    check(fd == kRouteFd && address->sa_family == AF_NETLINK, "unexpected route request");
    const auto* h = static_cast<const nlmsghdr*>(data);
    check(h->nlmsg_type == RTM_GETROUTE && h->nlmsg_seq == 731
        && (h->nlmsg_flags & NLM_F_DUMP) == NLM_F_DUMP, "route dump changed");
    ++sent;
    return length;
}
extern "C" ssize_t __wrap_recv(int fd, void* data, size_t capacity, int) {
    check(fd == kRouteFd && !replies.empty(), "unexpected route receive");
    Bytes bytes = std::move(replies.front());
    replies.pop_front();
    check(bytes.size() <= capacity, "fixture too large");
    if (!bytes.empty()) std::memcpy(data, bytes.data(), bytes.size());
    return bytes.size();
}
extern "C" int __wrap_poll(pollfd* descriptors, nfds_t count, int) {
    check(count == 1 && descriptors[0].fd == kRouteFd, "unexpected polling target");
    descriptors[0].revents = replies.empty() ? 0 : POLLIN;
    return replies.empty() ? 0 : 1;
}
// Fortified host headers can emit these aliases in sanitizer builds.
extern "C" ssize_t __wrap___recv_chk(int fd, void* data, size_t length,
        size_t capacity, int flags) {
    check(length <= capacity, "invalid fortified receive size");
    return __wrap_recv(fd, data, length, flags);
}
extern "C" int __wrap___poll_chk(pollfd* descriptors, nfds_t count,
        int timeout, size_t capacity) {
    check(count <= capacity / sizeof(pollfd), "invalid fortified poll size");
    return __wrap_poll(descriptors, count, timeout);
}
extern "C" int __wrap_clock_gettime(clockid_t id, timespec* time) {
    check(id == CLOCK_BOOTTIME, "route deadline clock changed");
    fakeTime += 25;
    time->tv_sec = fakeTime / 1000;
    time->tv_nsec = (fakeTime % 1000) * 1000000;
    return 0;
}

int main() {
    auto valid = route(3, 0xc0a80a01);
    scenario({valid}, true);
    scenario({route(4, 0xc0a80a01)}, false);
    scenario({route(3, 0xc0a80a02)}, false);
    scenario({}, false);
    scenario({Bytes{}}, false);
    scenario({Bytes(sizeof(nlmsghdr) - 1, 0)}, false);

    Bytes invalid = valid;
    header(invalid).nlmsg_len = sizeof(nlmsghdr) - 1;
    scenario({invalid}, false);
    invalid = valid;
    header(invalid).nlmsg_len = valid.size() + 1;
    scenario({invalid}, false);
    header(invalid).nlmsg_len = 0xffffffffU;
    scenario({invalid}, false);

    // NLMSG_NEXT subtracts the aligned size. An unpadded final record can make
    // its signed remaining count negative; it must stop before the next read.
    invalid = Bytes(sizeof(nlmsghdr) + 1, 0);
    header(invalid).nlmsg_len = invalid.size();
    header(invalid).nlmsg_type = NLMSG_NOOP;
    header(invalid).nlmsg_seq = 731;
    scenario({invalid}, false);

    invalid = valid;
    invalid.resize(NLMSG_LENGTH(sizeof(rtmsg)) - 1);
    header(invalid).nlmsg_len = invalid.size();
    scenario({invalid}, false);
    invalid = valid;
    reinterpret_cast<rtattr*>(RTM_RTA(&payload(invalid)))->rta_len = 1;
    scenario({invalid}, false);
    reinterpret_cast<rtattr*>(RTM_RTA(&payload(invalid)))->rta_len = 65535;
    scenario({invalid}, false);

    invalid = valid;
    payload(invalid).rtm_family = AF_INET6;
    scenario({invalid}, false);
    invalid = valid;
    payload(invalid).rtm_dst_len = 24;
    scenario({invalid}, false);
    invalid = valid;
    payload(invalid).rtm_type = RTN_UNREACHABLE;
    scenario({invalid}, false);
    invalid = valid;
    header(invalid).nlmsg_seq = 732;
    scenario({invalid}, false);
    scenario({invalid, valid}, true);
    invalid.insert(invalid.end(), valid.begin(), valid.end());
    scenario({invalid}, true);
    invalid = valid;
    header(invalid).nlmsg_type = NLMSG_DONE;
    scenario({invalid, valid}, false);
    header(invalid).nlmsg_type = NLMSG_ERROR;
    scenario({invalid, valid}, false);

    std::cout << "NetLab production route reader: " << checks
        << " checks PASS (Bionic macro, malformed/truncated replies, no network I/O)\n";
}
