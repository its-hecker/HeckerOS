/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

/** Screen-relative movement with a measured footprint, media slot and native bottom affordances. */
public final class LockscreenClockLayout {
    public static final class Placement {
        public final int top, height;
        public final float fit;
        Placement(int top, int height, float fit) { this.top = top; this.height = height; this.fit = fit; }
    }
    public static Placement place(int limit, int natural, int screen, int top, int bottom,
            int media, int position, float maximumScale) {
        top = Math.max(0, top); natural = Math.max(1, natural);
        float scale = Float.isFinite(maximumScale) ? Math.max(1f, maximumScale) : 1f;
        int available = Math.max(1, Math.min(limit, screen - top - Math.max(0, bottom) - Math.max(0, media)));
        float fit = Math.min(1f, available / (natural * scale));
        int footprint = Math.min(available, (int) Math.ceil(natural * scale * fit));
        position = Math.max(-100, Math.min(100, position));
        int shift = position >= 0 ? (available - footprint) * position / 100 : top * position / 100;
        return new Placement(shift, footprint + Math.max(0, shift), fit);
    }
    private LockscreenClockLayout() { }
}
