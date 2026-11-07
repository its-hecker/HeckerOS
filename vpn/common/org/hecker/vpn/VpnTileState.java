// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

/** Shared, credential-free state contract for the Quick Settings switch. */
public final class VpnTileState {
    public static final String ACTION_CHANGED = "org.hecker.vpn.action.TILE_STATE_CHANGED";
    public static final String CONTROL_PERMISSION = "org.hecker.vpn.permission.CONTROL";
    public static final String VERSION = "tile_state_version";

    // The switch represents enabled routing. Its subtitle reports peer connectivity.
    public static boolean enabled(boolean desired, boolean configured, boolean cleanupRequired) {
        return desired || configured || cleanupRequired;
    }

    private VpnTileState() {}
}
