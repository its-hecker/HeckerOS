// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Strict, bounded WireGuard client config. Hooks and shell-oriented extensions are rejected. */
public final class VpnConfig {
    public static final int MAX_BYTES = 65536;
    public final Map<String, String> network = new LinkedHashMap<>();
    public final List<Map<String, String>> peers = new ArrayList<>();
    public final List<String> applications = new ArrayList<>();
    public String mode = "all";

    public static VpnConfig parse(String text) {
        if (text == null || text.length() > MAX_BYTES || text.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Configuration is missing or too large");
        VpnConfig result = new VpnConfig();
        Map<String, String> section = null;
        boolean foundInterface = false;
        for (String raw : text.replace("\uFEFF", "").split("\n")) {
            int comment = raw.indexOf('#');
            String line = (comment < 0 ? raw : raw.substring(0, comment)).trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("[Interface]")) {
                if (foundInterface || !result.peers.isEmpty()) fail("Duplicate or misplaced Interface section");
                foundInterface = true; section = result.network; continue;
            }
            if (line.equalsIgnoreCase("[Peer]")) {
                if (!foundInterface || result.peers.size() >= 32) fail("Invalid Peer section");
                section = new LinkedHashMap<>(); result.peers.add(section); continue;
            }
            int equals = line.indexOf('=');
            if (section == null || equals <= 0) fail("Expected a WireGuard section and key/value entry");
            String key = canonical(line.substring(0, equals).trim(), section == result.network);
            String value = line.substring(equals + 1).trim();
            if (value.isEmpty()) fail("Empty " + key);
            if (key.endsWith("Applications")) {
                if (!result.mode.equals("all")) fail("Use only one app inclusion/exclusion list");
                result.mode = key.startsWith("Included") ? "include" : "exclude";
                for (String name : csv(value)) {
                    if (!packageName(name)) fail("Invalid app package name");
                    if (!result.applications.contains(name)) result.applications.add(name);
                }
                continue;
            }
            if (section.containsKey(key)) {
                if (!key.equals("Address") && !key.equals("DNS") && !key.equals("AllowedIPs")) fail("Duplicate " + key);
                value = section.get(key) + "," + value;
            }
            section.put(key, validate(key, value));
        }
        if (!foundInterface || !result.network.containsKey("PrivateKey") || !result.network.containsKey("Address")
                || result.peers.isEmpty()) fail("PrivateKey, Address and at least one peer are required");
        for (Map<String, String> peer : result.peers) {
            if (!peer.containsKey("PublicKey") || !peer.containsKey("AllowedIPs") || !peer.containsKey("Endpoint"))
                fail("Each peer needs PublicKey, AllowedIPs and Endpoint");
        }
        if (result.render("all", List.of()).length() > MAX_BYTES) fail("Configuration is too large");
        return result;
    }

    private static String canonical(String key, boolean network) {
        String[] allowed = network ? new String[]{"PrivateKey", "Address", "DNS", "MTU", "ListenPort", "IncludedApplications", "ExcludedApplications"}
                : new String[]{"PublicKey", "PresharedKey", "Endpoint", "AllowedIPs", "PersistentKeepalive"};
        for (String name : allowed) if (name.equalsIgnoreCase(key)) return name;
        String label = key.replaceAll("[^A-Za-z]", "");
        throw new IllegalArgumentException("Unsupported configuration field: " + label.substring(0, Math.min(48, label.length())));
    }

    private static String validate(String key, String value) {
        switch (key) {
            case "PrivateKey": case "PublicKey": case "PresharedKey": return key(value);
            case "MTU": return integer(value, 1280, 9000);
            case "ListenPort": case "PersistentKeepalive": return integer(value, 0, 65535);
            case "Endpoint":
                String host, port;
                if (value.startsWith("[")) {
                    int close = value.indexOf("]:");
                    if (close < 0) { fail("Invalid IPv6 endpoint"); return ""; }
                    host = value.substring(1, close); ip(host); port = value.substring(close + 2);
                } else {
                    int colon = value.lastIndexOf(':');
                    if (colon <= 0) { fail("Endpoint needs a host and port"); return ""; }
                    host = value.substring(0, colon); port = value.substring(colon + 1);
                    if (host.length() > 253 || !host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?"))
                        fail("Invalid endpoint hostname");
                }
                integer(port, 1, 65535); return value;
            default:
                List<String> clean = new ArrayList<>();
                for (String address : csv(value)) {
                    if (key.equals("DNS")) ip(address);
                    else {
                        int slash = address.indexOf('/');
                        String addressHost = slash < 0 ? address : address.substring(0, slash);
                        ip(addressHost);
                        if (slash < 0 && key.equals("AllowedIPs")) fail("AllowedIPs needs CIDR prefixes");
                        if (slash >= 0) integer(address.substring(slash + 1), 0, addressHost.contains(":") ? 128 : 32);
                    }
                    clean.add(address);
                }
                if (clean.size() > 256) fail("Too many addresses");
                return String.join(",", clean);
        }
    }

    public static String key(String value) {
        try {
            byte[] raw = Base64.getDecoder().decode(value);
            if (raw.length != 32 || !Base64.getEncoder().encodeToString(raw).equals(value)) fail("Invalid WireGuard key");
        } catch (IllegalArgumentException bad) { throw new IllegalArgumentException("Invalid WireGuard key"); }
        return value;
    }
    public static void ip(String value) {
        try {
            if (value.contains(":")) {
                if (!value.matches("[0-9a-fA-F:]+") || InetAddress.getByName(value).getAddress().length != 16)
                    fail("Invalid IPv6 address");
            } else {
                String[] pieces = value.split("\\.", -1);
                if (pieces.length != 4) fail("Use a numeric IP address");
                for (String part : pieces) integer(part, 0, 255);
            }
        } catch (Exception bad) { throw new IllegalArgumentException("Use a valid numeric IP address"); }
    }
    private static String integer(String value, int min, int max) {
        try {
            if (!value.matches("[0-9]{1,5}")) fail("Invalid numeric value");
            int number = Integer.parseInt(value);
            if (number < min || number > max) fail("Numeric value outside supported range");
            return Integer.toString(number);
        } catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid numeric value"); }
    }
    private static List<String> csv(String value) {
        ArrayList<String> result = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            if (part.trim().isEmpty()) fail("Empty list entry");
            result.add(part.trim());
        }
        return result;
    }
    public static boolean packageName(String name) {
        return name != null && name.length() <= 255 && name.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*");
    }
    public String render(String policy, List<String> packages) {
        if (!List.of("all", "include", "exclude").contains(policy)) fail("Invalid routing mode");
        LinkedHashSet<String> names = new LinkedHashSet<>(packages);
        if (names.size() > 1024) fail("Too many selected apps");
        for (String name : names) if (!packageName(name)) fail("Invalid selected app");
        if (policy.equals("include") && names.isEmpty()) fail("Select at least one app");
        StringBuilder out = new StringBuilder("[Interface]\n");
        append(out, network);
        if (!policy.equals("all") && !names.isEmpty()) out.append(policy.equals("include")
                ? "IncludedApplications=" : "ExcludedApplications=").append(String.join(",", names)).append('\n');
        for (Map<String, String> peer : peers) { out.append("[Peer]\n"); append(out, peer); }
        if (out.length() > MAX_BYTES) fail("Configuration is too large");
        return out.toString();
    }
    private static void append(StringBuilder out, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet())
            out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
