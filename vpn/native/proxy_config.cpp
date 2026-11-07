// SPDX-License-Identifier: Apache-2.0
// Only a validated HTTP outbound crosses the privileged boundary. Never execute a subscription.
#include "proxy_config.h"
#include "../../third_party/picojson/picojson.h"
#include <arpa/inet.h>
#include <algorithm>
#include <cmath>
#include <set>
#include <sstream>
#include <vector>
namespace hecker::vpn {
namespace {
using Object = picojson::object;
using Array = picojson::array;
using Value = picojson::value;
bool text(const Object& o, const char* key, std::string& out, size_t limit) {
    auto it = o.find(key);
    if (it == o.end() || !it->second.is<std::string>()) return false;
    out = it->second.get<std::string>();
    return out.size() <= limit && std::none_of(out.begin(), out.end(), [](unsigned char c) { return c < 32 || c == 127; });
}
bool number(const Object& o, const char* key, unsigned int& out, unsigned int low, unsigned int high) {
    auto it = o.find(key);
    if (it == o.end() || !it->second.is<double>()) return false;
    double n = it->second.get<double>();
    if (!std::isfinite(n) || n < low || n > high || std::floor(n) != n) return false;
    out = static_cast<unsigned int>(n); return true;
}
bool host(const std::string& s) {
    return !s.empty() && s.size() <= 253 && s.front() != '.' && s.back() != '.' &&
        s.find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.-:") == std::string::npos;
}
bool package(const std::string& s) {
    if (s.empty() || s.size() > 255) return false;
    bool first = true;
    for (char ch : s) {
        bool letter = (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
        if (first && !letter) return false;
        if (ch == '.') first = true;
        else if (letter || (ch >= '0' && ch <= '9') || ch == '_') first = false;
        else return false;
    }
    return !first;
}
Object outbound(const ProxyConfig& c) {
    return {{"name", Value("Hecker TCP")}, {"type", Value("http")}, {"server", Value(c.server)},
        {"port", Value(double(c.port))}, {"tls", Value(c.tls)}, {"sni", Value(c.sni)},
        {"skip-cert-verify", Value(false)}, {"username", Value(c.username)}, {"password", Value(c.password)}};
}
}
bool parse_proxy(const std::string& input, ProxyConfig& result) {
    if (input.empty() || input.size() > 16384) return false;
    // Bound nesting before calling a recursive JSON parser, including malformed input.
    unsigned depth = 0; bool quoted = false, escaped = false;
    for (char c : input) {
        if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; }
        else if (c == '"') quoted = true;
        else if (c == '{' || c == '[') { if (++depth > 4) return false; }
        else if (c == '}' || c == ']') { if (!depth) return false; --depth; }
    }
    Value root;
    std::string error;
    auto end = picojson::parse(root, input.begin(), input.end(), &error);
    while (end != input.end() && (*end == ' ' || *end == '\t' || *end == '\r' || *end == '\n')) ++end;
    if (!error.empty() || end != input.end() || !root.is<Object>()) return false;
    const auto& o = root.get<Object>();
    const std::set<std::string> allowed = {"netid", "server", "port", "tls", "sni", "username", "password", "mode", "apps"};
    if (o.size() != allowed.size()) return false;
    for (const auto& item : o) if (!allowed.count(item.first)) return false;
    ProxyConfig c;
    if (!number(o, "netid", c.netid, 100, 65535) || !number(o, "port", c.port, 1, 65535) ||
        !text(o, "server", c.server, 64) || !text(o, "sni", c.sni, 253) ||
        !text(o, "username", c.username, 512) || !text(o, "password", c.password, 512) ||
        !text(o, "mode", c.mode, 8) || !text(o, "apps", c.applications, 12000)) return false;
    auto tls = o.find("tls"); if (!tls->second.is<bool>()) return false; c.tls = tls->second.get<bool>();
    in_addr v4{}; in6_addr v6{};
    if (inet_pton(AF_INET, c.server.c_str(), &v4) == 1) {
        uint32_t address = ntohl(v4.s_addr);
        if (!address || address == 0xffffffffU || (address >> 24) == 127 || (address >> 28) == 14 || (address >> 16) == 0xa9fe) return false;
    } else if (inet_pton(AF_INET6, c.server.c_str(), &v6) != 1 || IN6_IS_ADDR_UNSPECIFIED(&v6) ||
        IN6_IS_ADDR_LOOPBACK(&v6) || IN6_IS_ADDR_LINKLOCAL(&v6) || IN6_IS_ADDR_MULTICAST(&v6)) return false;
    if (!host(c.sni) || c.username.find(':') != std::string::npos) return false;
    if (c.mode != "all" && c.mode != "include" && c.mode != "exclude") return false;
    if ((c.mode == "all" && !c.applications.empty()) || (c.mode == "include" && c.applications.empty())) return false;
    if (!c.applications.empty()) {
        std::istringstream list(c.applications); std::string app; unsigned count = 0;
        if (c.applications.back() == ',') return false;
        while (std::getline(list, app, ',')) if (!package(app) || ++count > 256) return false;
    }
    result = c; return true;
}
std::string proxy_json(const ProxyConfig& c) {
    return Value(Object{{"netid", Value(double(c.netid))}, {"server", Value(c.server)}, {"port", Value(double(c.port))},
        {"tls", Value(c.tls)}, {"sni", Value(c.sni)}, {"username", Value(c.username)}, {"password", Value(c.password)},
        {"mode", Value(c.mode)}, {"apps", Value(c.applications)}}).serialize();
}
std::string proxy_routes(const ProxyConfig& c) {
    std::string s = "[Interface]\nAddress=172.19.0.1/30,fdfe:dcba:9876::1/126\nDNS=172.19.0.2\nMTU=1280\n";
    if (c.mode != "all" && !c.applications.empty()) s += (c.mode == "include" ? "IncludedApplications=" : "ExcludedApplications=") + c.applications + "\n";
    return s;
}
std::string proxy_core_config(const ProxyConfig& c) {
    // No remote listeners, rules, scripts, geodata, providers or DIRECT fallback.
    Object dns{{"enable", Value(true)}, {"listen", Value("")}, {"ipv6", Value(false)}, {"enhanced-mode", Value("redir-host")},
        {"default-nameserver", Value(Array{Value("https://1.1.1.1/dns-query")})},
        {"nameserver", Value(Array{Value("https://1.1.1.1/dns-query#Hecker TCP")})},
        {"proxy-server-nameserver", Value(Array{Value("https://1.1.1.1/dns-query")})}};
    Object root{{"mode", Value("rule")}, {"ipv6", Value(true)}, {"allow-lan", Value(false)}, {"bind-address", Value("127.0.0.1")},
        {"port", Value(0.0)}, {"socks-port", Value(0.0)}, {"mixed-port", Value(0.0)},
        {"redir-port", Value(0.0)}, {"tproxy-port", Value(0.0)}, {"external-controller", Value("")},
        {"log-level", Value("silent")}, {"find-process-mode", Value("off")},
        {"geo-auto-update", Value(false)}, {"routing-mark", Value(double(0xf0000U | c.netid))},
        {"dns", Value(dns)}, {"proxies", Value(Array{Value(outbound(c))})},
        {"proxy-groups", Value(Array{})}, {"rules", Value(Array{Value("NETWORK,UDP,REJECT"), Value("MATCH,Hecker TCP")})},
        {"profile", Value(Object{{"store-selected", Value(false)}, {"store-fake-ip", Value(false)}})}};
    std::string json = Value(root).serialize();
    // PicoJSON escapes solidus as \/. The core's YAML reader rejects that JSON
    // escape; an unescaped slash is equivalent JSON and accepted by both readers.
    for (size_t at = 0; (at = json.find("\\/", at)) != std::string::npos;) json.erase(at, 1);
    return json;
}
}
