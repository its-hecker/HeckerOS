// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <array>
#include <cstdint>
#include <deque>
#include <map>
#include <string>
#include <vector>

namespace hecker::lan {
using Bytes = std::vector<uint8_t>;
using Mac = std::array<uint8_t, 6>;
constexpr size_t kMaxPeers = 32;
constexpr uint32_t kMaxRate = 1024000;
uint32_t ip(const std::string& text);
std::string ipText(uint32_t address);
Mac mac(const std::string& text);
std::string macText(const Mac& address);
bool unicast(const Mac& address);
bool privateIp(uint32_t address);
uint32_t number(const std::string& text, uint32_t maximum);
std::vector<std::string> words(const std::string& text);

struct Link {
    std::string iface;
    uint32_t own = 0, gateway = 0;
    unsigned prefix = 0;
    Mac ownMac{}, gatewayMac{};
    void validate() const;
    bool peer(uint32_t address) const;
};
enum class Mode { Block, Limit, Pass };
Mode mode(const std::string& value);
std::string modeText(Mode value);
Bytes arp(const Mac& sender, uint32_t senderIp, const Mac& target, uint32_t targetIp, bool request);
bool arpSender(const Bytes& frame, uint32_t& address, Mac& hardware);
std::vector<uint32_t> scanTargets(const Link& link);

class Bucket {
    uint32_t rate_ = 0;
    double credit_ = 0;
    uint64_t last_ = 0;
public:
    void configure(uint32_t kbps, uint64_t now);
    bool take(size_t bytes, uint64_t now);
};
struct Direction {
    Bucket bucket;
    std::deque<Bytes> queue;
    size_t queued = 0;
    uint64_t forwarded = 0, dropped = 0;
};
struct Peer {
    uint32_t address;
    Mac hardware;
    Mode mode;
    uint32_t up = 0, down = 0;
    Direction upload, download;
};
struct Outgoing { Bytes frame; uint32_t peer; bool upload; };

/** Only selected, pinned IPv4/MAC pairs enter the relay; payloads are never persisted. */
class Session {
    Link link_;
    std::map<uint32_t, Peer> peers_;
    uint64_t deadline_, heartbeat_;
    bool stopping_ = false;
public:
    Session(Link link, uint64_t now, uint32_t seconds);
    void add(uint32_t address, const Mac& hardware, Mode policy, uint32_t up, uint32_t down, uint64_t now);
    void update(uint32_t address, Mode policy, uint32_t up, uint32_t down, uint64_t now);
    std::vector<Bytes> announcements(bool restore, uint32_t only = 0) const;
    void heartbeat(uint64_t now) { heartbeat_ = now; }
    bool expired(uint64_t now) const;
    void restore(uint32_t address, uint64_t now);
    void stop(uint64_t now);
    bool stopping() const { return stopping_; }
    bool active() const;
    void receive(const Bytes& frame, uint64_t now);
    std::vector<Outgoing> drain(uint64_t now);
    void sent(const Outgoing& packet, bool success);
    const std::map<uint32_t, Peer>& peers() const { return peers_; }
};
}
