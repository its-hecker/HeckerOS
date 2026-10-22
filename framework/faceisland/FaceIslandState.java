// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Presentation state only; never participates in authentication or dismissal. */
public final class FaceIslandState {
    public static final int HIDDEN = 0;
    public static final int SCANNING = 1;
    public static final int SUCCESS = 2;
    private boolean eligible;
    private boolean acknowledged;
    private int user;
    private int state = HIDDEN;

    public int state() { return state; }

    public void environment(boolean enabled, boolean keyguard, boolean awake, int currentUser) {
        boolean next = enabled && keyguard && awake;
        if (!next || user != currentUser) {
            state = HIDDEN;
            acknowledged = false;
        }
        eligible = next;
        user = currentUser;
    }

    public void running(boolean face, boolean running) {
        if (!face || !eligible || acknowledged) return;
        state = running ? SCANNING : HIDDEN;
    }

    public boolean authenticated(boolean face, int authenticatedUser, boolean allowed) {
        if (!face || !eligible || authenticatedUser != user || !allowed || acknowledged) return false;
        acknowledged = true;
        state = SUCCESS;
        return true;
    }

    public void failed(boolean face) {
        if (face && !acknowledged) state = HIDDEN;
    }

    public void expire() { state = HIDDEN; }
}
