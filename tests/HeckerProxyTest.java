// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HeckerProxyTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt work) throws Exception {
        try { work.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }
    public static void main(String[] args) throws Exception {
        ProxyConfig p = ProxyConfig.uri("https://u%2Bser:p%3Aa%2Bss%20%E9%9B%AA@proxy.example:443");
        check(p.tls && p.username.equals("u+ser") && p.password.equals("p:a+ss 雪"));
        check(ProxyConfig.saved(p.json()).password.equals(p.password));
        ProxyConfig plus = ProxyConfig.uri("http://user:p+ss@proxy.example:8080"); check(plus.password.equals("p+ss"));
        ProxyConfig ipv6 = ProxyConfig.uri("https://[2001:db8::1]:443"); check(ipv6.server.equals("2001:db8::1"));
        JSONObject nativeRequest = new JSONObject(p.request(137, "203.0.113.1", "include", List.of("org.telegram.plus")).substring(7));
        check(nativeRequest.getString("sni").equals("proxy.example") && nativeRequest.getString("server").equals("203.0.113.1"));
        check(nativeRequest.getString("apps").equals("org.telegram.plus"));
        rejects(() -> p.request(137, "203.0.113.1", "include", List.of()));
        rejects(() -> ProxyConfig.policy("include", List.of("a;id")));
        check(ProxyConfig.policy("all", List.of("com.foo")).isEmpty());
        for (String bad : List.of("socks5://host:1", "https://host:0", "http://host/path", "http://host?x=1", "http://user:p%0ass@host:80", "http://u%3Aser:p@host:80", "https://host/#secret"))
            rejects(() -> ProxyConfig.uri(bad));
        String yaml = "proxies:\n  - {name: 'Free 🇩🇪', type: http, server: proxy.example, port: 8080, username: public, password: secret, tls: false}\n"
                + "  - {name: Verified, type: http, server: secure.example, port: 443, tls: true}\n"
                + "  - {name: Insecure, type: http, server: insecure.example, port: 443, tls: true, skip-cert-verify: true}\n"
                + "  - {name: Other, type: vless, server: elsewhere.example, port: 443}\n"
                + "rules: ['MATCH,DIRECT']\nexternal-controller: 0.0.0.0:9090\n";
        JSONArray catalog = TcpCatalog.parse(yaml); check(catalog.length() == 2);
        ProxyConfig first = ProxyConfig.saved(catalog.getJSONObject(0).getString("config")); check(first.password.equals("secret"));
        check(!first.json().contains("external-controller") && !first.json().contains("DIRECT"));
        check(TcpCatalog.parse(yaml).getJSONObject(0).getString("id").equals(catalog.getJSONObject(0).getString("id")));
        rejects(() -> TcpCatalog.parse("proxies: []\nproxies: []"));
        rejects(() -> TcpCatalog.parse("proxies: [!!java.net.URL ['https://evil.example']]"));
        rejects(() -> TcpCatalog.parse("proxies: &x [{type: http}]\nother: *x"));
        rejects(() -> TcpCatalog.parse("proxies: []"));
        rejects(() -> TcpCatalog.parse("[".repeat(30) + "]".repeat(30)));
        if (args.length > 0) {
            JSONArray live = TcpCatalog.parse(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
            check(live.length() > 0);
            System.out.println("Current public source parsed: " + live.length() + " supported entries (credentials omitted)");
        }
        System.out.println("HTTP URL import, UTF-8 authentication, safe YAML, verified TLS filtering and app policy: PASS");
    }
}
