// SPDX-License-Identifier: Apache-2.0
#include "lan_core.h"
#include <algorithm>
#include <charconv>
#include <cstdio>
#include <limits>
#include <sstream>
#include <stdexcept>

namespace hecker::lan {
static void require(bool ok, const char* error) { if (!ok) throw std::invalid_argument(error); }
uint32_t number(const std::string& text, uint32_t maximum) {
    uint32_t value = 0;
    auto result = std::from_chars(text.data(), text.data() + text.size(), value);
    require(!text.empty() && result.ec == std::errc() && result.ptr == text.data() + text.size()
            && value <= maximum, "invalid_number");
    return value;
}
std::vector<std::string> words(const std::string& text) {
    require(text.size() <= 1024 && text.find('\0') == std::string::npos, "invalid_command");
    std::istringstream in(text); std::vector<std::string> result; std::string part;
    while (in >> part) { require(result.size() < 16, "invalid_command"); result.push_back(part); }
    return result;
}
uint32_t ip(const std::string& text) {
    uint32_t value = 0; size_t start = 0;
    for (unsigned i = 0; i < 4; ++i) {
        size_t end = text.find('.', start);
        require((i == 3) == (end == std::string::npos), "invalid_ip");
        std::string part = text.substr(start, end - start);
        require(part.size() == 1 || (!part.empty() && part[0] != '0'), "invalid_ip");
        value = (value << 8) | number(part, 255); start = end + 1;
    }
    return value;
}
std::string ipText(uint32_t v) {
    return std::to_string(v >> 24) + "." + std::to_string((v >> 16) & 255)
            + "." + std::to_string((v >> 8) & 255) + "." + std::to_string(v & 255);
}
bool privateIp(uint32_t v) {
    return (v >> 24) == 10 || (v >> 20) == 0xac1 || (v >> 16) == 0xc0a8;
}
Mac mac(const std::string& text) {
    require(text.size() == 17, "invalid_mac"); Mac out{};
    for (size_t i = 0; i < out.size(); ++i) {
        if (i) require(text[i * 3 - 1] == ':', "invalid_mac");
        unsigned value = 0;
        auto parsed = std::from_chars(text.data() + i * 3, text.data() + i * 3 + 2, value, 16);
        require(parsed.ec == std::errc() && parsed.ptr == text.data() + i * 3 + 2 && value <= 255, "invalid_mac");
        out[i] = value;
    }
    require(unicast(out), "invalid_mac"); return out;
}
bool unicast(const Mac& v) { return !(v[0] & 1) && std::any_of(v.begin(), v.end(), [](uint8_t x) { return x != 0; }); }
std::string macText(const Mac& v) {
    char text[18]; std::snprintf(text, sizeof(text), "%02x:%02x:%02x:%02x:%02x:%02x", v[0], v[1], v[2], v[3], v[4], v[5]); return text;
}
void Link::validate() const {
    bool validName = false;
    for (const std::string prefixName : {"wlan", "wifi"}) {
        if (iface.compare(0, prefixName.size(), prefixName) == 0) {
            std::string suffix = iface.substr(prefixName.size());
            validName |= !suffix.empty() && suffix.size() <= 2
                    && std::all_of(suffix.begin(), suffix.end(), [](char x) { return x >= '0' && x <= '9'; });
        }
    }
    require(validName && prefix >= 16 && prefix <= 30 && privateIp(own) && privateIp(gateway), "invalid_link");
    uint32_t mask = 0xffffffffU << (32 - prefix);
    require(own != gateway && (own & mask) == (gateway & mask), "invalid_gateway");
    require((own & ~mask) != 0 && (own & ~mask) != ~mask
            && (gateway & ~mask) != 0 && (gateway & ~mask) != ~mask, "invalid_link");
}
bool Link::peer(uint32_t v) const {
    if (prefix < 16 || prefix > 30) return false;
    uint32_t mask = 0xffffffffU << (32 - prefix);
    return privateIp(v) && v != own && v != gateway && (v & mask) == (own & mask)
            && (v & ~mask) != 0 && (v & ~mask) != ~mask;
}
Mode mode(const std::string& v) {
    if (v == "BLOCK") return Mode::Block;
    if (v == "LIMIT") return Mode::Limit;
    if (v == "PASS") return Mode::Pass;
    throw std::invalid_argument("invalid_mode");
}
std::string modeText(Mode v) { return v == Mode::Block ? "BLOCK" : v == Mode::Limit ? "LIMIT" : "PASS"; }
static void put16(Bytes& v, size_t at, uint16_t x) { v[at] = x >> 8; v[at + 1] = x & 255; }
static void put32(Bytes& v, size_t at, uint32_t x) { for (unsigned i = 0; i < 4; ++i) v[at + i] = (x >> (24 - 8 * i)) & 255; }
static uint32_t get32(const Bytes& v, size_t at) { return uint32_t(v[at]) << 24 | uint32_t(v[at + 1]) << 16 | uint32_t(v[at + 2]) << 8 | v[at + 3]; }
static Mac getMac(const Bytes& v, size_t at) { Mac m{}; std::copy_n(v.begin() + at, 6, m.begin()); return m; }
Bytes arp(const Mac& source, uint32_t sourceIp, const Mac& target, uint32_t targetIp, bool request) {
    Bytes frame(42, 0); const Mac broadcast{255,255,255,255,255,255};
    std::copy((request ? broadcast : target).begin(), (request ? broadcast : target).end(), frame.begin());
    std::copy(source.begin(), source.end(), frame.begin() + 6);
    put16(frame, 12, 0x0806); put16(frame, 14, 1); put16(frame, 16, 0x0800);
    frame[18] = 6; frame[19] = 4; put16(frame, 20, request ? 1 : 2);
    std::copy(source.begin(), source.end(), frame.begin() + 22); put32(frame, 28, sourceIp);
    if (!request) std::copy(target.begin(), target.end(), frame.begin() + 32);
    put32(frame, 38, targetIp); return frame;
}
bool arpSender(const Bytes& f, uint32_t& address, Mac& hardware) {
    if (f.size() < 42 || f[12] != 8 || f[13] != 6 || f[14] || f[15] != 1
            || f[16] != 8 || f[17] || f[18] != 6 || f[19] != 4 || f[20]
            || (f[21] != 1 && f[21] != 2)) return false;
    hardware = getMac(f, 22); address = get32(f, 28);
    return unicast(hardware) && hardware == getMac(f, 6) && privateIp(address);
}
std::vector<uint32_t> scanTargets(const Link& link) {
    link.validate();
    uint32_t mask = 0xffffffffU << (32 - link.prefix);
    uint32_t first = std::max((link.own & mask) + 1, link.own & 0xffffff00U);
    uint32_t last = std::min((link.own | ~mask) - 1, (link.own & 0xffffff00U) + 255);
    std::vector<uint32_t> targets;
    for (uint32_t v = first; v <= last; ++v) if (link.peer(v)) targets.push_back(v);
    return targets;
}
void Bucket::configure(uint32_t rate, uint64_t now) {
    require(rate <= kMaxRate, "invalid_rate"); rate_ = rate;
    credit_ = std::max(1536.0, rate_ * 12.5); last_ = now;
}
bool Bucket::take(size_t bytes, uint64_t now) {
    if (!rate_) return true;
    double capacity = std::max(1536.0, rate_ * 12.5);
    credit_ = std::min(capacity, credit_ + (now >= last_ ? std::min(now - last_, uint64_t(1000)) : 0) * rate_ / 8.0);
    last_ = now;
    // A frame larger than the bucket may borrow one frame's worth of tokens.
    // Its debt must be repaid before the next frame; low rates cannot deadlock.
    if (credit_ < std::min(double(bytes), capacity)) return false;
    credit_ -= bytes; return true;
}
Session::Session(Link link, uint64_t now, uint32_t seconds) : link_(std::move(link)), deadline_(now + seconds * 1000ULL), heartbeat_(now) {
    link_.validate(); require(seconds >= 1 && seconds <= 3600, "invalid_duration");
    require(unicast(link_.ownMac) && unicast(link_.gatewayMac) && link_.ownMac != link_.gatewayMac, "invalid_mac");
}
void Session::add(uint32_t address, const Mac& hardware, Mode policy, uint32_t up, uint32_t down, uint64_t now) {
    require(!stopping_ && peers_.size() < kMaxPeers && !peers_.count(address) && link_.peer(address)
            && unicast(hardware) && hardware != link_.ownMac && hardware != link_.gatewayMac, "invalid_peer");
    for (const auto& entry : peers_) require(entry.second.hardware != hardware, "duplicate_peer_mac");
    require(up <= kMaxRate && down <= kMaxRate && (policy != Mode::Limit || up || down), "invalid_rate");
    peers_.emplace(address, Peer{address, hardware, policy, 0, 0, {}, {}}); update(address, policy, up, down, now);
}
void Session::update(uint32_t address, Mode policy, uint32_t up, uint32_t down, uint64_t now) {
    require(!stopping_ && peers_.count(address), "not_owned");
    require(up <= kMaxRate && down <= kMaxRate, "invalid_rate");
    require(policy != Mode::Limit || up || down, "invalid_rate");
    Peer& p = peers_.at(address); p.mode = policy; p.up = up; p.down = down;
    p.upload.bucket.configure(up, now); p.download.bucket.configure(down, now);
}
std::vector<Bytes> Session::announcements(bool restore, uint32_t only) const {
    std::vector<Bytes> result;
    for (const auto& entry : peers_) {
        const Peer& p = entry.second;
        if ((only && only != p.address) || (!restore && p.mode == Mode::Pass)) continue;
        result.push_back(arp(restore ? link_.gatewayMac : link_.ownMac, link_.gateway, p.hardware, p.address, false));
        result.push_back(arp(restore ? p.hardware : link_.ownMac, p.address, link_.gatewayMac, link_.gateway, false));
    }
    return result;
}
bool Session::expired(uint64_t now) const { return now >= deadline_ || now < heartbeat_ || now - heartbeat_ > 8000; }
void Session::restore(uint32_t address, uint64_t now) { update(address, Mode::Pass, 0, 0, now); }
void Session::stop(uint64_t now) { if (stopping_) return; for (auto& entry : peers_) restore(entry.first, now); stopping_ = true; }
bool Session::active() const { for (const auto& entry : peers_) if (entry.second.mode != Mode::Pass) return true; return false; }
void Session::receive(const Bytes& f, uint64_t now) {
    (void)now;
    if (f.size() < 34 || f[12] != 8 || f[13] || f[14] >> 4 != 4 || getMac(f, 0) != link_.ownMac) return;
    size_t header = (f[14] & 15) * 4, total = size_t(f[16]) * 256 + f[17];
    if (header < 20 || total < header || total + 14 > f.size()) return;
    uint32_t source = get32(f, 26), destination = get32(f, 30); bool upload = peers_.count(source);
    // Local peer/phone communication belongs to Android, not the gateway relay.
    if (source == link_.own || destination == link_.own) return;
    if ((destination >> 28) >= 14 || destination == 0xffffffffU || !source) return;
    auto found = peers_.find(upload ? source : destination); if (found == peers_.end()) return;
    Peer& p = found->second;
    if (getMac(f, 6) != (upload ? p.hardware : link_.gatewayMac)) return;
    Direction& flow = upload ? p.upload : p.download;
    if (p.mode == Mode::Block || flow.queue.size() >= 128 || flow.queued + total + 14 > 256 * 1024) { flow.dropped += total + 14; return; }
    Bytes packet(f.begin(), f.begin() + total + 14);
    const Mac& target = upload ? link_.gatewayMac : p.hardware;
    std::copy(target.begin(), target.end(), packet.begin());
    std::copy(link_.ownMac.begin(), link_.ownMac.end(), packet.begin() + 6);
    flow.queued += packet.size(); flow.queue.push_back(std::move(packet));
}
std::vector<Outgoing> Session::drain(uint64_t now) {
    std::vector<Outgoing> packets;
    bool progressed = true;
    while (progressed && packets.size() < 128) {
    progressed = false;
    for (auto& entry : peers_) for (bool upload : {true, false}) {
        Peer& p = entry.second; Direction& flow = upload ? p.upload : p.download;
        if (!flow.queue.empty() && packets.size() < 128) {
            if (p.mode == Mode::Block) { flow.dropped += flow.queue.front().size(); }
            else {
                if (p.mode == Mode::Limit && !flow.bucket.take(flow.queue.front().size(), now)) continue;
                flow.queued -= flow.queue.front().size();
                packets.push_back({std::move(flow.queue.front()), p.address, upload});
            }
            if (p.mode == Mode::Block) flow.queued -= flow.queue.front().size();
            flow.queue.pop_front();
            progressed = true;
        }
    }
    }
    return packets;
}
void Session::sent(const Outgoing& packet, bool success) {
    Peer& p = peers_.at(packet.peer); Direction& flow = packet.upload ? p.upload : p.download;
    (success ? flow.forwarded : flow.dropped) += packet.frame.size();
}
}
