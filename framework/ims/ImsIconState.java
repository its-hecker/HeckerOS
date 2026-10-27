// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Voice availability and registration are independent IMS signals. */
public final class ImsIconState {
    public static final int NONE = 0;
    public static final int LTE = 1;
    public static final int WIFI = 2;
    public static final int CROSS_SIM = 3;
    private boolean registered;
    private boolean voice;
    private int access = NONE;

    public void registered(int technology) {
        registered = true;
        access = technology;
    }

    public void voice(boolean available) { voice = available; }

    public void clear() {
        registered = false;
        voice = false;
        access = NONE;
    }

    public int icon(boolean showVolte, boolean showVowifi, boolean airplane) {
        if (!registered || !voice) return NONE;
        if (access == WIFI && showVowifi) return WIFI;
        if (access == LTE && showVolte && !airplane) return LTE;
        return NONE;
    }
}
