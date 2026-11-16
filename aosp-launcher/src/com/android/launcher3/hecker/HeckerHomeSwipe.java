// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

/** One-shot empty-workspace swipe; opens the shade on a deliberate downward MOVE. */
public final class HeckerHomeSwipe {
    private boolean eligible;
    private float x, y;
    private long time;

    public void down(float x, float y, long time) {
        this.x = x;
        this.y = y;
        this.time = time;
        eligible = true;
    }

    public void cancel() { eligible = false; }

    public boolean move(float x, float y, long time, float slop, float threshold) {
        if (!eligible) return false;
        float dx = x - this.x, dy = y - this.y;
        // Leave horizontal workspace paging, upward app-drawer gestures and
        // long-press drags alone. Noise smaller than touch slop is ignored.
        if (dy < -slop || (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy))) {
            cancel();
            return false;
        }
        if (time >= this.time && time - this.time <= 1500 &&
                dy >= threshold && Math.abs(dx) <= dy * 0.65f) {
            cancel();  // One shade opening per gesture, even if MOVE keeps arriving.
            return true;
        }
        return false;
    }

    /** Fallback for fast flicks that deliver no intermediate MOVE above threshold. */
    public boolean up(float x, float y, long time, float threshold) {
        float dx = x - this.x, dy = y - this.y;
        boolean result = eligible && time >= this.time && time - this.time <= 1500 &&
                dy >= threshold && Math.abs(dx) <= dy * 0.65f;
        cancel();
        return result;
    }
}
