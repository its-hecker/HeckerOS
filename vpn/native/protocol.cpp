// SPDX-License-Identifier: Apache-2.0
#include "protocol.h"
#include <algorithm>
#include <cctype>
#include <set>
#include <sstream>

namespace hecker::vpn {
bool authorized_peer(unsigned int uid, const std::string& context) {
    return uid >= 10000 && uid < 100000 && context.rfind("u:r:hecker_vpn_app:", 0) == 0;
}
bool valid_config(const std::string& config) {
    if (config.empty() || config.size() > kMaxConfig || config.find('\0') != std::string::npos) return false;
    std::istringstream input(config); std::string line;
    bool network = false, found = false, private_key = false, address = false;
    unsigned peers = 0; std::set<std::string> keys;
    const std::set<std::string> interface_keys = {"PrivateKey", "Address", "DNS", "MTU", "ListenPort", "IncludedApplications", "ExcludedApplications"};
    const std::set<std::string> peer_keys = {"PublicKey", "PresharedKey", "Endpoint", "AllowedIPs", "PersistentKeepalive"};
    bool include = false, exclude = false;
    auto complete = [&]() { return !peers || (keys.count("PublicKey") && keys.count("Endpoint") && keys.count("AllowedIPs")); };
    while (std::getline(input, line)) {
        if (line == "[Interface]") {
            if (found || peers) return false;
            found = network = true; keys.clear(); continue;
        }
        if (line == "[Peer]") {
            if (!found || !complete() || ++peers > 32) return false;
            network = false; keys.clear(); continue;
        }
        if (line.empty()) continue;
        auto split = line.find('=');
        if (!found || split == std::string::npos || split + 1 >= line.size()) return false;
        std::string key = line.substr(0, split), value = line.substr(split + 1);
        if (!(network ? interface_keys : peer_keys).count(key) || !keys.insert(key).second) return false;
        // Upstream wg-quick constructs shell commands. Only canonical tokens may cross this boundary.
        for (unsigned char ch : value) if (!(std::isalnum(ch) || std::string("+/=:.,-[]_").find(ch) != std::string::npos)) return false;
        if (key == "IncludedApplications" || key == "ExcludedApplications") {
            if (value.front() == ',' || value.back() == ',' || value.find(",,") != std::string::npos) return false;
            for (unsigned char ch : value) if (!(std::isalnum(ch) || ch == '.' || ch == '_' || ch == ',')) return false;
            include |= key == "IncludedApplications"; exclude |= key == "ExcludedApplications";
            if (include && exclude) return false;
        }
        if (network) { private_key |= key == "PrivateKey"; address |= key == "Address"; }
    }
    return private_key && address && peers && complete();
}
}
