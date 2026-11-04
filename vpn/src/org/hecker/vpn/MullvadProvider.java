// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Optional BYO-account adapter, based on mullvad/wg-tools. No shared account or keys. */
final class MullvadProvider {
    static final String HOST = "https://api.mullvad.net";
    private static String request(String path, String method, JSONObject body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(HOST + path).openConnection();
        connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(false); connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "HeckerVPN/0.1");
        connection.setRequestProperty("Accept", "application/json");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                if (status == 401 || status == 403) throw new IllegalArgumentException("Mullvad rejected this account or device. Check your account access.");
                if (status == 429) throw new IllegalArgumentException("Mullvad rate limit reached. Try again later.");
                throw new IllegalArgumentException("Mullvad request failed (HTTP " + status + "). Check account time and device limits.");
            }
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                long deadline = System.nanoTime() + 30_000_000_000L;
                byte[] buffer = new byte[8192]; int size;
                while ((size = input.read(buffer)) != -1) {
                    if (System.nanoTime() > deadline || result.size() + size > 6 * 1024 * 1024) throw new Exception("Provider response limit exceeded");
                    result.write(buffer, 0, size);
                }
                return result.toString(StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
    static List<JSONObject> servers() throws Exception {
        JSONArray response = new JSONArray(request("/www/relays/all", "GET", null, null));
        List<JSONObject> servers = new ArrayList<>();
        if (response.length() > 20000) throw new Exception("Unexpected server catalog");
        for (int i = 0; i < response.length(); i++) {
            JSONObject row = response.getJSONObject(i);
            if (!row.optString("type").equals("wireguard") || !row.optBoolean("active")) continue;
            try {
                String address = row.getString("ipv4_addr_in"); VpnConfig.ip(address);
                String key = VpnConfig.key(row.getString("pubkey"));
                String hostname = row.getString("hostname"), country = row.getString("country_code");
                if (!hostname.matches("[a-z0-9-]{1,64}") || !country.matches("[a-zA-Z]{2}")) continue;
                JSONObject safe = new JSONObject().put("host", hostname).put("country", country.toUpperCase(java.util.Locale.ROOT))
                        .put("country_name", label(row.getString("country_name"))).put("city", label(row.getString("city_name")))
                        .put("address", address).put("key", key);
                servers.add(safe);
            } catch (IllegalArgumentException | org.json.JSONException malformed) { /* Ignore invalid catalog rows. */ }
        }
        servers.sort(Comparator.comparing(s -> s.optString("country_name") + s.optString("city") + s.optString("host")));
        if (servers.isEmpty()) throw new IllegalArgumentException("No active WireGuard servers were returned");
        return servers;
    }
    private static String label(String text) {
        if (text.isEmpty() || text.length() > 100 || text.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid server label");
        return text;
    }
    static String token(String account) throws Exception {
        if (account == null || !account.matches("[0-9]{16}")) throw new IllegalArgumentException("Enter your 16-digit Mullvad account number");
        return new JSONObject(request("/auth/v1/webtoken", "POST", new JSONObject().put("account_number", account), null)).getString("access_token");
    }
    static JSONObject register(String account, String publicKey) throws Exception {
        String token = token(account);
        JSONArray devices = new JSONArray(request("/accounts/v1/devices", "GET", null, token));
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.getJSONObject(i);
            if (publicKey.equals(device.optString("pubkey"))) return device;
        }
        return new JSONObject(request("/accounts/v1/devices", "POST",
                new JSONObject().put("pubkey", publicKey).put("hijack_dns", false), token));
    }
    static void remove(String account, String device) throws Exception {
        if (!device.matches("[A-Za-z0-9-]{1,80}")) throw new IllegalArgumentException("Invalid saved provider device");
        request("/accounts/v1/devices/" + device, "DELETE", null, token(account));
    }
    static String config(JSONObject identity, JSONObject server) throws Exception {
        String config = "[Interface]\nPrivateKey=" + identity.getString("private")
                + "\nAddress=" + identity.getString("ipv4") + "," + identity.getString("ipv6")
                + "\nDNS=10.64.0.1,fc00:bbbb:bbbb:bb01::1\n[Peer]\nPublicKey=" + server.getString("key")
                + "\nEndpoint=" + server.getString("address") + ":51820\nAllowedIPs=0.0.0.0/0,::/0\nPersistentKeepalive=25\n";
        return VpnConfig.parse(config).render("all", List.of());
    }
}
