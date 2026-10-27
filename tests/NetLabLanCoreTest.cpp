// SPDX-License-Identifier: Apache-2.0
#include "lan_core.h"
#include <algorithm>
#include <iostream>
#include <stdexcept>
#include <functional>
using namespace hecker::lan;
static int checks = 0;
static void check(bool result) { ++checks; if (!result) throw std::runtime_error("check " + std::to_string(checks)); }
static void rejects(const std::function<void()>& action) {
    try { action(); } catch (const std::exception&) { check(true); return; } check(false);
}
static Link link() { return Link{"wlan0", ip("192.168.10.2"), ip("192.168.10.1"), 24, mac("02:00:00:00:00:02"), mac("02:00:00:00:00:01")}; }
static Mac peerMac(unsigned i = 3) { Mac m{2,0,0,0,0,static_cast<uint8_t>(i)}; return m; }
static uint32_t peerIp(unsigned i = 3) { return ip("192.168.10." + std::to_string(i)); }
static uint32_t readIp(const Bytes& f, size_t i) { return uint32_t(f[i]) << 24 | uint32_t(f[i + 1]) << 16 | uint32_t(f[i + 2]) << 8 | f[i + 3]; }
static Mac readMac(const Bytes& f, size_t i) { Mac m{}; std::copy_n(f.begin() + i, 6, m.begin()); return m; }
static Bytes packet(bool upload, unsigned peer = 3, size_t size = 1014) {
    Link l = link(); Bytes f(size, 0x5a);
    std::copy(l.ownMac.begin(), l.ownMac.end(), f.begin()); Mac source = upload ? peerMac(peer) : l.gatewayMac;
    std::copy(source.begin(), source.end(), f.begin() + 6); f[12] = 8; f[13] = 0;
    f[14] = 0x45; f[16] = (size - 14) >> 8; f[17] = (size - 14) & 255;
    uint32_t a = upload ? peerIp(peer) : ip("8.8.8.8"), b = upload ? ip("8.8.8.8") : peerIp(peer);
    for (int i = 0; i < 4; i++) { f[26 + i] = (a >> (24 - 8 * i)) & 255; f[30 + i] = (b >> (24 - 8 * i)) & 255; }
    return f;
}
int main() {
    // Reproduce slow root/firewall preparation: neither duration nor heartbeat starts there.
    for (uint32_t duration : {1U, 5U, 300U}) {
        Session prepared(link(), 1000, duration); prepared.add(peerIp(), peerMac(), Mode::Block, 0, 0, 1000);
        check(prepared.expired(50000)); prepared.beginActive(50000); check(!prepared.expired(50000));
        check(!prepared.expired(50999)); rejects([&] { prepared.beginActive(51000); });
        prepared.heartbeat(50000 + duration * 1000ULL - 1);
        check(prepared.expired(50000 + duration * 1000ULL));
    }
    Session live(link(), 0, 300);
    live.add(peerIp(), peerMac(), Mode::Limit, 128, 256, 0);live.add(peerIp(4), peerMac(4), Mode::Block, 0, 0, 0);
    live.beginActive(100000);
    check(live.configure("SET 192.168.10.3 BLOCK 0 0", 100001) == peerIp());
    live.receive(packet(true), 100001);live.receive(packet(false), 100001);
    check(live.drain(100001).empty());check(live.peers().at(peerIp()).upload.dropped > 0 && live.peers().at(peerIp()).download.dropped > 0);
    check(live.configure("SET 192.168.10.3 LIMIT 128 512", 100002) == peerIp());
    live.receive(packet(true), 100002);live.receive(packet(false), 100002);check(live.drain(100002).size() == 2);
    check(live.peers().at(peerIp(4)).mode == Mode::Block);
    for (const auto& bad : {"SET 192.168.10.5 BLOCK 0 0", "SET 192.168.10.1 BLOCK 0 0", "SET 192.168.10.3 PASS 0 0",
            "SET 192.168.10.3 LIMIT 0 0", "SET 192.168.10.3 BLOCK 1 0", "SET 192.168.10.3 LIMIT 1024001 1",
            "SET 192.168.10.3 BLOCK 0 0 extra", "SET 0192.168.10.3 BLOCK 0 0", "SET 192.168.10.3 BLOCK 0 0\nSTOP"})
        rejects([&] { live.configure(bad, 100003); });
    live.restore(peerIp(), 100004);check(live.active());check(live.announcements(true,peerIp()).size()==2);
    live.configure("SET 192.168.10.3 BLOCK 0 0", 100005);check(live.announcements(true,peerIp()).empty());
    check(live.peers().at(peerIp()).mode == Mode::Block);live.heartbeat(399999);live.configure("SET 192.168.10.3 LIMIT 1 2", 399999);
    check(live.expired(400000)); // Editing a policy never extends the session.
    for (const auto& value : {"0.0.0.0", "10.0.0.1", "172.31.255.254", "192.168.10.2", "255.255.255.255"}) check(ipText(ip(value)) == value);
    for (const auto& value : {"", "127.1", "01.2.3.4", "1.2.3.4.5", "1.2.3.-1", "1.2.3.256", "1.2.3.4\n", "1.2..4", "0x01.2.3.4"}) rejects([&] { ip(value); });
    for (const auto& value : {"00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff", "01:22:33:44:55:66", "02-00-00-00-00-01", "02:0:00:00:00:01", "02:gg:00:00:00:01"}) rejects([&] { mac(value); });
    check(macText(mac("A2:BB:CC:DD:EE:FF")) == "a2:bb:cc:dd:ee:ff");
    check(privateIp(ip("172.16.0.1")) && privateIp(ip("172.31.255.254")) && !privateIp(ip("172.32.0.1")) && !privateIp(ip("8.8.8.8")));
    Link l = link(); l.validate(); check(l.peer(peerIp()) && !l.peer(l.own) && !l.peer(l.gateway) && !l.peer(ip("192.168.10.255")) && !l.peer(ip("192.168.11.3")));
    for (const auto& value : {"eth0", "wlan0;id", "../wlan0", "wlan100", "wlan", "wlan-1"}) rejects([&] { Link bad = l; bad.iface = value; bad.validate(); });
    for (unsigned prefix : {0U, 15U, 31U, 32U}) rejects([&] { Link bad = l; bad.prefix = prefix; bad.validate(); });
    rejects([&] { Link bad = l; bad.gateway = ip("10.0.0.1"); bad.validate(); });
    rejects([&] { Link bad = l; bad.own = ip("192.168.10.0"); bad.validate(); });
    check(scanTargets(l).size() == 252);
    Link large = l; large.prefix = 16; large.gateway = ip("192.168.1.1"); auto scope = scanTargets(large);
    check(scope.size() == 255 && scope.front() == ip("192.168.10.0") && scope.back() == ip("192.168.10.255"));
    Link small = l; small.prefix = 30; check(scanTargets(small).empty());
    auto request = arp(l.ownMac, l.own, {}, l.gateway, true);
    check(request.size() == 42 && request[21] == 1 && request[0] == 255 && readMac(request, 32) == Mac{});
    uint32_t address; Mac hardware;
    check(arpSender(request, address, hardware) && address == l.own && hardware == l.ownMac);
    auto forged = request; forged[6] ^= 2; check(!arpSender(forged, address, hardware));
    auto malformed = request; malformed[18] = 8; check(!arpSender(malformed, address, hardware));
    for (size_t i = 0; i < 42; i++) check(!arpSender(Bytes(request.begin(), request.begin() + i), address, hardware));
    Session session(l, 1000, 300); session.add(peerIp(), peerMac(), Mode::Block, 0, 0, 1000);
    auto poison = session.announcements(false), repairs = session.announcements(true);
    check(poison.size() == 2 && repairs.size() == 2);
    check(readMac(poison[0], 0) == peerMac() && readMac(poison[0], 22) == l.ownMac && readIp(poison[0], 28) == l.gateway && readIp(poison[0], 38) == peerIp());
    check(readMac(poison[1], 0) == l.gatewayMac && readMac(poison[1], 22) == l.ownMac && readIp(poison[1], 28) == peerIp());
    check(readMac(repairs[0], 22) == l.gatewayMac && readMac(repairs[1], 22) == peerMac());
    rejects([&] { session.add(l.gateway, peerMac(8), Mode::Block, 0, 0, 1000); });
    rejects([&] { session.add(peerIp(4), peerMac(), Mode::Block, 0, 0, 1000); });
    rejects([&] { session.update(peerIp(), Mode::Limit, 0, 0, 1000); });
    rejects([&] { session.update(peerIp(), Mode::Limit, kMaxRate + 1, 128, 1000); });
    auto upload = packet(true), download = packet(false);
    session.receive(upload, 1000); session.receive(download, 1000);
    check(session.drain(1000).empty() && session.peers().at(peerIp()).upload.dropped == upload.size() && session.peers().at(peerIp()).download.dropped == download.size());
    session.restore(peerIp(), 1001); check(!session.active() && session.announcements(false).empty());
    session.receive(upload, 1001); session.receive(download, 1001); auto forwarded = session.drain(1001);
    check(forwarded.size() == 2 && forwarded[0].upload && !forwarded[1].upload);
    check(readMac(forwarded[0].frame, 0) == l.gatewayMac && readMac(forwarded[1].frame, 0) == peerMac());
    check(readMac(forwarded[0].frame, 6) == l.ownMac && readMac(forwarded[1].frame, 6) == l.ownMac);
    check(std::equal(upload.begin() + 14, upload.end(), forwarded[0].frame.begin() + 14));
    check(std::equal(download.begin() + 14, download.end(), forwarded[1].frame.begin() + 14));
    session.sent(forwarded[0], true); session.sent(forwarded[1], false);
    check(session.peers().at(peerIp()).upload.forwarded == upload.size() && session.peers().at(peerIp()).download.dropped == 2 * download.size());
    for (size_t i = 0; i < 34; i++) session.receive(Bytes(upload.begin(), upload.begin() + i), 1002);
    for (size_t offset : {size_t(0), size_t(6), size_t(12), size_t(14), size_t(16), size_t(26)}) { auto f = upload; f[offset] ^= 0xff; session.receive(f, 1002); }
    check(session.drain(1002).empty());
    auto third = packet(true, 4); session.receive(third, 1002); check(session.drain(1002).empty());
    auto local = upload; for (unsigned i = 0; i < 4; i++) local[30 + i] = (l.own >> (24 - 8 * i)) & 255;
    session.receive(local, 1002); check(session.drain(1002).empty());
    local = download; for (unsigned i = 0; i < 4; i++) local[26 + i] = (l.own >> (24 - 8 * i)) & 255;
    session.receive(local, 1002); check(session.drain(1002).empty());
    session.update(peerIp(), Mode::Limit, 128, 256, 2000);
    // Keep both queues busy and measure the independently bounded L2 byte budgets.
    uint64_t up = 0, down = 0;
    for (uint64_t now = 2000; now <= 4000; now += 10) {
        for (int i = 0; i < 5; i++) { session.receive(upload, now); session.receive(download, now); }
        for (const auto& p : session.drain(now)) (p.upload ? up : down) += p.frame.size();
    }
    check(up <= 128 * 2000 / 8 + 1600 && up >= 30'000);
    check(down <= 256 * 2000 / 8 + 3200 && down >= 60'000);
    check(down > up && session.peers().at(peerIp()).upload.dropped > 0);
    check(session.peers().at(peerIp()).upload.queue.size() <= 128 && session.peers().at(peerIp()).upload.queued <= 256 * 1024);
    session.stop(4001); check(session.stopping() && !session.active() && session.announcements(false).empty());
    check(!session.drain(4001).empty()); session.stop(4002);
    rejects([&] { session.add(peerIp(4), peerMac(4), Mode::Block, 0, 0, 4002); });
    Session timer(l, 1000, 10); check(!timer.expired(9000) && timer.expired(9001));
    timer.heartbeat(9000); check(!timer.expired(10999) && timer.expired(11000));
    timer.heartbeat(12000); check(timer.expired(11999));
    rejects([&] { Session bad(l, 0, 0); }); rejects([&] { Session bad(l, 0, 3601); });
    Bucket low; low.configure(1, 0); check(low.take(9000, 0) && !low.take(100, 0));
    for (uint64_t t = 1000; t <= 60'000; t += 1000) check(!low.take(100, t));
    check(low.take(100, 61'000));
    Bucket unlimited; unlimited.configure(0, 0); check(unlimited.take(1'000'000, 0));
    Session many(l, 0, 3600);
    for (unsigned i = 3; i < 3 + kMaxPeers; i++) many.add(peerIp(i), peerMac(i), Mode::Limit, 1000, 1000, 0);
    rejects([&] { many.add(peerIp(50), peerMac(50), Mode::Block, 0, 0, 0); });
    for (unsigned i = 3; i < 3 + kMaxPeers; i++) { for (int n = 0; n < 8; n++) many.receive(packet(true, i), 0); many.receive(packet(false, i), 0); }
    auto fair = many.drain(0); check(fair.size() == 128);
    for (unsigned i = 3; i < 3 + kMaxPeers; i++) check(std::any_of(fair.begin(), fair.end(), [=](const Outgoing& p) { return p.peer == peerIp(i) && !p.upload; }));
    for (const auto& text : {"-1", "4294967296", "1a", "", " 1"}) rejects([&] { number(text, UINT32_MAX); });
    rejects([&] { words(std::string(1025, 'x')); }); rejects([&] { words(std::string("A\0B", 3)); });
    check(words("RESTORE 192.168.10.3").size() == 2);
    std::cout << "NetLab packet integrity, pinned peers, ARP repair, independent rates, fairness and watchdog: " << checks << " checks PASS\n";
}
