// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/** One HTTP CONNECT outbound. A subscription can never supply native configuration. */
final class ProxyConfig {
    final String server, sni, username, password;
    final int port;
    final boolean tls;
    ProxyConfig(String server, int port, boolean tls, String sni, String username, String password) {
        this.server = host(server); this.port = port; this.tls = tls;
        this.sni = host(sni == null || sni.isEmpty() ? server : sni);
        this.username = credential(username); this.password = credential(password);
        if (port < 1 || port > 65535 || this.username.contains(":")) fail();
    }
    private static String host(String value) {
        if (value == null || value.isEmpty() || value.length() > 253) { fail(); return ""; }
        String host = value;
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (host.contains(":")) { VpnConfig.ip(host); return host; }
        try { host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(java.util.Locale.ROOT); }
        catch (IllegalArgumentException bad) { fail(); }
        if (!host.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?") || host.contains("..")) fail();
        return host;
    }
    private static String credential(String value) {
        String text = value == null ? "" : value;
        if (text.getBytes(StandardCharsets.UTF_8).length > 512 || text.codePoints().anyMatch(Character::isISOControl)) fail();
        return text;
    }
    static ProxyConfig uri(String text) {
        try {
            if (text == null || text.length() > 4096) { fail(); return null; }
            URI uri = new URI(text.trim());
            boolean tls = "https".equalsIgnoreCase(uri.getScheme());
            if (!tls && !"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                    uri.getRawQuery() != null || uri.getRawFragment() != null ||
                    !(uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))) { fail(); return null; }
            String username = "", password = "";
            if (uri.getRawUserInfo() != null) {
                String[] pair = uri.getRawUserInfo().split(":", 2);
                username = decode(pair[0]); password = pair.length > 1 ? decode(pair[1]) : "";
            }
            return new ProxyConfig(uri.getHost(), uri.getPort() == -1 ? tls ? 443 : 80 : uri.getPort(), tls, uri.getHost(), username, password);
        } catch (Exception malformed) { throw new IllegalArgumentException("Use http:// or https://[user:password@]server:port. Proxy credentials must be URL-encoded."); }
    }
    private static String decode(String text) { return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8); }
    static ProxyConfig saved(String text) throws Exception {
        JSONObject o = new JSONObject(text);
        if (o.length() != 6) fail();
        return new ProxyConfig(o.getString("server"), o.getInt("port"), o.getBoolean("tls"), o.getString("sni"), o.getString("username"), o.getString("password"));
    }
    static ProxyConfig catalog(Map<?, ?> row) {
        Set<String> allowed = Set.of("name", "type", "server", "port", "tls", "sni", "username", "password", "skip-cert-verify");
        for (Object name : row.keySet()) if (!allowed.contains(name)) fail();
        if (!"http".equals(row.get("type"))) fail();
        Object tlsValue = row.get("tls");
        if (tlsValue != null && !(tlsValue instanceof Boolean)) fail();
        boolean tls = Boolean.TRUE.equals(tlsValue);
        if (tls && Boolean.TRUE.equals(row.get("skip-cert-verify")))
            throw new IllegalArgumentException("HTTPS proxies must support certificate verification");
        Object port = row.get("port");
        if (!(port instanceof Number) || ((Number) port).doubleValue() != ((Number) port).intValue()) { fail(); return null; }
        return new ProxyConfig(string(row, "server", null), ((Number) port).intValue(), tls,
                string(row, "sni", null), string(row, "username", ""), string(row, "password", ""));
    }
    private static String string(Map<?, ?> row, String name, String fallback) {
        Object v = row.get(name); if (v == null) return fallback;
        if (!(v instanceof String)) { fail(); return ""; } return (String) v;
    }
    String json() throws Exception {
        return new JSONObject().put("server", server).put("port", port).put("tls", tls).put("sni", sni)
                .put("username", username).put("password", password).toString();
    }
    static List<String> policy(String mode, List<String> apps) {
        if (!List.of("all", "include", "exclude").contains(mode)) throw new IllegalArgumentException("Invalid app routing mode");
        LinkedHashSet<String> names = new LinkedHashSet<>(apps);
        if (names.size() > 256 || String.join(",", names).length() > 12000) throw new IllegalArgumentException("Choose fewer apps for TCP routing");
        for (String name : names) if (!VpnConfig.packageName(name)) throw new IllegalArgumentException("Invalid app package");
        if (mode.equals("include") && names.isEmpty()) throw new IllegalArgumentException("Select at least one app");
        return mode.equals("all") ? List.of() : List.copyOf(names);
    }
    String request(int netid, String address, String mode, List<String> apps) throws Exception {
        VpnConfig.ip(address);
        return "TCP_UP\n" + new JSONObject(json()).put("server", address).put("netid", netid)
                .put("mode", mode).put("apps", String.join(",", policy(mode, apps))).toString();
    }
    private static void fail() { throw new IllegalArgumentException("Invalid HTTP/HTTPS proxy configuration"); }
}
