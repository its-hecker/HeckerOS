// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;

/** Provider protocol fixtures; no credentials or requests reach an external service. */
public final class HeckerVpnProviderTest {
    static final String KEY = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
    static final String ACCOUNT = "0000000000000000"; // Explicit fixture, never sent to a real provider.
    record Expected(String method, String path, String response, int status, String token, String field) {}
    static final ArrayDeque<Expected> expected = new ArrayDeque<>();
    static void add(String method, String path, String response, String token, String field) {
        expected.add(new Expected(method, path, response, 200, token, field));
    }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                if (!url.getHost().equals("api.mullvad.net")) throw new AssertionError("Unexpected host");
                Expected step = expected.remove();
                if (!url.getPath().equals(step.path)) throw new AssertionError("Wrong API path");
                return new HttpURLConnection(url) {
                    final ByteArrayOutputStream body = new ByteArrayOutputStream();
                    public void connect() {} public void disconnect() {} public boolean usingProxy() { return false; }
                    public OutputStream getOutputStream() { return body; }
                    public int getResponseCode() {
                        if (!getRequestMethod().equals(step.method) || getInstanceFollowRedirects()) throw new AssertionError("Method/redirect policy");
                        if (!java.util.Objects.equals(getRequestProperty("Authorization"), step.token)) throw new AssertionError("Credential scope");
                        String payload = body.toString(StandardCharsets.UTF_8);
                        if (step.field != null && !payload.contains(step.field)) throw new AssertionError("Missing registration field");
                        if (payload.contains("private") || payload.contains("PrivateKey")) throw new AssertionError("Private key sent to provider");
                        return step.status;
                    }
                    public InputStream getInputStream() { return new ByteArrayInputStream(step.response.getBytes(StandardCharsets.UTF_8)); }
                };
            }
        } : null);
        String relay = "{\"type\":\"wireguard\",\"active\":true,\"hostname\":\"de-test-wg-001\",\"country_code\":\"de\",\"country_name\":\"Germany\",\"city_name\":\"Test City\",\"ipv4_addr_in\":\"192.0.2.1\",\"pubkey\":\"" + KEY + "\"}";
        add("GET", "/www/relays/all", "[" + relay + "," + relay.replace("true", "false") + "," + relay.replace("192.0.2.1", "192.0.2.1;id") + "]", null, null);
        List<JSONObject> servers = MullvadProvider.servers();
        if (servers.size() != 1 || !servers.get(0).getString("country").equals("DE")) throw new AssertionError("Catalog filtering");
        String device = "{\"id\":\"fixture-device\",\"pubkey\":\"" + KEY + "\",\"ipv4_address\":\"10.64.0.2/32\",\"ipv6_address\":\"fc00::2/128\"}";
        add("POST", "/auth/v1/webtoken", "{\"access_token\":\"fixture-token\"}", null, "account_number");
        add("GET", "/accounts/v1/devices", "[]", "Bearer fixture-token", null);
        add("POST", "/accounts/v1/devices", device, "Bearer fixture-token", "pubkey");
        JSONObject registered = MullvadProvider.register(ACCOUNT, KEY);
        if (!registered.getString("id").equals("fixture-device")) throw new AssertionError("Device registration");
        add("POST", "/auth/v1/webtoken", "{\"access_token\":\"fixture-token\"}", null, "account_number");
        add("GET", "/accounts/v1/devices", "[" + device + "]", "Bearer fixture-token", null);
        MullvadProvider.register(ACCOUNT, KEY); // Reuses an existing device after a response is lost.
        add("POST", "/auth/v1/webtoken", "{\"access_token\":\"fixture-token\"}", null, "account_number");
        add("DELETE", "/accounts/v1/devices/fixture-device", "", "Bearer fixture-token", null);
        MullvadProvider.remove(ACCOUNT, "fixture-device");
        String config = MullvadProvider.config(new JSONObject().put("private", KEY).put("ipv4", "10.64.0.2/32").put("ipv6", "fc00::2/128"), servers.get(0));
        VpnConfig parsed = VpnConfig.parse(config);
        if (!parsed.peers.get(0).get("Endpoint").equals("192.0.2.1:51820") || !parsed.peers.get(0).get("AllowedIPs").contains("::/0")) throw new AssertionError("Generated profile routes");
        expected.add(new Expected("GET", "/www/relays/all", "", 302, null, null));
        try { MullvadProvider.servers(); throw new AssertionError("Followed provider redirect"); } catch (IllegalArgumentException correct) {}
        if (!expected.isEmpty()) throw new AssertionError("Unexecuted API steps");
        System.out.println("HeckerVPN provider catalog, account/device retry, key privacy, TLS host/redirect policy and generated dual-stack profiles: PASS");
    }
}
