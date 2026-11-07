// SPDX-License-Identifier: Apache-2.0
#include "proxy_config.h"
#include "../third_party/picojson/picojson.h"
#include <cassert>
#include <iostream>
#include <fstream>
int main(int argc, char** argv) {
    using namespace hecker::vpn;
    ProxyConfig c;
    const std::string valid = R"({"netid":137,"server":"203.0.113.12","port":443,"tls":true,"sni":"proxy.example","username":"user","password":"p: a$`\"\\雪","mode":"include","apps":"org.telegram.plus,com.whatsapp"})";
    assert(parse_proxy(valid, c));
    if (argc == 2) { std::ofstream file(argv[1]); file << proxy_core_config(c); }
    assert(c.netid == 137 && c.tls && c.port == 443 && c.password.find("雪") != std::string::npos);
    ProxyConfig restored; assert(parse_proxy(proxy_json(c), restored)); assert(restored.password == c.password);
    auto route = proxy_routes(c);
    assert(route.find("IncludedApplications=org.telegram.plus,com.whatsapp\n") != std::string::npos);
    assert(route.find(c.password) == std::string::npos && route.find(c.username) == std::string::npos);
    picojson::value root; assert(picojson::parse(root, proxy_core_config(c)).empty());
    assert(root.get("routing-mark").get<double>() == 0xf0089);
    const auto& rules = root.get("rules").get<picojson::array>();
    assert(rules.size() == 2 && rules[0].get<std::string>() == "NETWORK,UDP,REJECT" && rules[1].get<std::string>() == "MATCH,Hecker TCP");
    assert(root.get("external-controller").get<std::string>().empty());
    const auto& proxy = root.get("proxies").get<picojson::array>()[0];
    assert(!proxy.get("skip-cert-verify").get<bool>() && proxy.get("password").get<std::string>() == c.password);
    for (const auto& field : {"port", "mixed-port", "redir-port", "tproxy-port", "socks-port"}) assert(root.get(field).get<double>() == 0);
    const auto& dns = root.get("dns");
    assert(dns.get("enhanced-mode").get<std::string>() == "redir-host");
    assert(dns.get("nameserver").get<picojson::array>()[0].get<std::string>() == "https://1.1.1.1/dns-query#Hecker TCP");
    auto reject = [&](const char* key, const picojson::value& value) {
        picojson::value v; assert(picojson::parse(v, valid).empty()); v.get<picojson::object>()[key] = value;
        ProxyConfig ignored; assert(!parse_proxy(v.serialize(), ignored));
    };
    reject("netid", picojson::value(99.0)); reject("netid", picojson::value(65536.0));
    reject("netid", picojson::value(137.5)); reject("port", picojson::value(0.0)); reject("port", picojson::value(443.1));
    reject("server", picojson::value("proxy.example")); reject("server", picojson::value("203.0.113.1;id"));
    reject("sni", picojson::value("proxy.example\nHeader: evil")); reject("username", picojson::value("u:ser"));
    reject("password", picojson::value("a\rb")); reject("tls", picojson::value("true"));
    reject("apps", picojson::value("com.app,$(id)")); reject("apps", picojson::value("com.app,"));
    reject("apps", picojson::value("")); reject("mode", picojson::value("all"));
    reject("external-controller", picojson::value(":9090"));
    assert(!parse_proxy(std::string(20000, '['), c));
    assert(!parse_proxy("[[[[[[[[[[[]]]]]]]]]]]", c));
    assert(!parse_proxy(valid + " garbage", c));
    c = restored; c.tls = false; c.mode = "exclude"; c.applications = "com.whatsapp";
    assert(parse_proxy(proxy_json(c), restored)); assert(proxy_routes(c).find("ExcludedApplications=com.whatsapp") != std::string::npos);
    std::cout << "Root TCP config boundary, UTF-8 credentials, fixed routing, DNS and UDP rejection: PASS\n";
}
