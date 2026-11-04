// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;
/** A configured interface alone is never evidence of contact with a peer. */
public final class VpnConnectionState {
    private VpnConnectionState() {}
    public static String classify(boolean configured, long handshake, long nowSeconds) {
        if (!configured) return "disconnected";
        if (handshake <= 0) return "waiting_peer";
        long age = nowSeconds - handshake;
        return age >= -5 && age <= 180 ? "active" : "idle";
    }
}
