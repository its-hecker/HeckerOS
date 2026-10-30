// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** A completed, stationary pair of finger taps. No Android dependencies. */
public final class DoubleTapSleepGesture {
    private final int minInterval, timeout;
    private final float touchSlop, doubleTapSlop;
    private long downTime = -1, firstUp = -1;
    private float downX, downY, firstX, firstY;
    private boolean secondTap;

    public DoubleTapSleepGesture(int minInterval, int timeout, float touchSlop,
            float doubleTapSlop) {
        this.minInterval = minInterval;
        this.timeout = timeout;
        this.touchSlop = touchSlop;
        this.doubleTapSlop = doubleTapSlop;
    }

    public void reset() {
        downTime = firstUp = -1;
        secondTap = false;
    }

    public void down(long time, float x, float y) {
        if (downTime >= 0) reset();
        long interval = time - firstUp;
        secondTap = firstUp >= 0 && interval >= minInterval && interval <= timeout
                && Math.hypot(x - firstX, y - firstY) <= doubleTapSlop;
        firstUp = -1;
        downTime = time;
        downX = x;
        downY = y;
    }

    public void move(float x, float y) {
        if (downTime >= 0 && Math.hypot(x - downX, y - downY) > touchSlop) reset();
    }

    public boolean up(long time, float x, float y) {
        if (downTime < 0) return false;
        if (time < downTime || time - downTime > timeout
                || Math.hypot(x - downX, y - downY) > touchSlop) {
            reset();
            return false;
        }
        if (secondTap) {
            reset();
            return true;
        }
        firstUp = time;
        firstX = downX;
        firstY = downY;
        downTime = -1;
        return false;
    }
}
