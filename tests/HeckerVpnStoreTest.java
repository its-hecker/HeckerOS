// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import org.json.JSONArray;
import org.json.JSONObject;

public final class HeckerVpnStoreTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        JSONObject wireGuard = new JSONObject().put("id", "wireguard").put("config", "wg-config")
                .put("mode", "include").put("apps", new JSONArray().put("org.example.app"));
        JSONObject provider = new JSONObject().put("id", "mullvad").put("source", "mullvad");
        JSONObject tcp = new JSONObject().put("id", "public-tcp").put("kind", "tcp");
        JSONObject manualTcp = new JSONObject().put("id", "imported").put("kind", "tcp");
        JSONObject account = new JSONObject().put("device", "fixture");
        JSONArray profiles = new JSONArray().put(tcp).put(wireGuard).put(manualTcp).put(provider);
        JSONObject data = new JSONObject().put("profiles", profiles).put("selected", "public-tcp")
                .put("mullvad", account).put("tcp_catalog", new JSONArray().put(tcp)).put("tcp_catalog_time", 1L);

        require(VpnStore.removeLegacyTcpProfiles(data), "Legacy data was not migrated");
        require(profiles.length() == 2 && profiles.getJSONObject(0) == wireGuard
                && profiles.getJSONObject(1) == provider, "WireGuard profiles or routing changed");
        require(data.getString("selected").equals("wireguard"), "Removed selection was not replaced");
        require(data.getJSONObject("mullvad") == account, "Provider identity changed");
        require(!data.has("tcp_catalog") && !data.has("tcp_catalog_time"), "Legacy catalog remains");
        require(!VpnStore.removeLegacyTcpProfiles(data), "Migration is not idempotent");

        data.put("selected", "mullvad");
        profiles.put(tcp);
        require(VpnStore.removeLegacyTcpProfiles(data) && data.getString("selected").equals("mullvad"),
                "Existing WireGuard selection changed");

        JSONObject tcpOnly = new JSONObject().put("profiles", new JSONArray().put(manualTcp)).put("selected", "imported");
        require(VpnStore.removeLegacyTcpProfiles(tcpOnly) && tcpOnly.getJSONArray("profiles").length() == 0
                && tcpOnly.getString("selected").isEmpty(), "TCP-only selection was not cleared");
        System.out.println("VPN profile upgrade: TCP profiles and cache removed, WireGuard data and valid selection preserved PASS");
    }
}
