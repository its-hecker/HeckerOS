// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <string>
namespace hecker::vpn {
struct ProxyConfig {
    unsigned int netid = 0, port = 0;
    bool tls = false;
    std::string server, sni, username, password, mode, applications;
};
bool parse_proxy(const std::string& input, ProxyConfig& result);
std::string proxy_json(const ProxyConfig& config);
std::string proxy_routes(const ProxyConfig& config);
std::string proxy_core_config(const ProxyConfig& config);
}
