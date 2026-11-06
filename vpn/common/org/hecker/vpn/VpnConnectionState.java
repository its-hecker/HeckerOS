// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;
/** A configured interface alone is never evidence of contact with a peer. */
public final class VpnConnectionState {
    public static final long PEER_TIMEOUT_MS = 45_000;
    private VpnConnectionState() {}
    public static String classify(boolean configured, long handshake, long nowSeconds) {
        if (!configured) return "disconnected";
        if (handshake <= 0) return "waiting_peer";
        long age = nowSeconds - handshake;
        return age >= -5 && age <= 180 ? "active" : "idle";
    }
    /** elapsed time is monotonic; changing the phone clock cannot postpone a timeout. */
    public static String classify(boolean configured, long handshake, long nowSeconds, long waitingMs) {
        if (configured && handshake <= 0 && waitingMs >= PEER_TIMEOUT_MS) return "peer_timeout";
        return classify(configured, handshake, nowSeconds);
    }
}
