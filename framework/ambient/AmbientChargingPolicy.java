/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

/** Device-independent charging session rules. A sticky battery replay is never a plug event. */
public final class AmbientChargingPolicy {
    public static final long INTRO_MILLIS = 2200;
    private static final long PENDING_MILLIS = 10000;
    private boolean known, plugged;
    private long pendingAt = -1, startedAt = -1;

    public boolean battery(boolean connected, boolean interactive, boolean animate, long now) {
        boolean edge = known && !plugged && connected;
        known = true;
        plugged = connected;
        if (!connected || interactive) cancel();
        else if (edge) { pendingAt = animate ? now : -1; startedAt = -1; }
        return edge && !interactive;
    }
    public boolean visible(long now) {
        if (!plugged || pendingAt < 0) return false;
        long age = now - pendingAt;
        pendingAt = -1;
        if (age < 0 || age > PENDING_MILLIS) return false;
        startedAt = now;
        return true;
    }
    public void cancel() { pendingAt = startedAt = -1; }
    public boolean expirePending(long now) {
        if (pendingAt < 0 || now - pendingAt <= PENDING_MILLIS) return false;
        pendingAt = -1;
        return true;
    }
    public long startedAt() { return startedAt; }
    public boolean pending() { return pendingAt >= 0; }
    public static float percent(int level, int scale) {
        return scale <= 0 || level < 0 ? Float.NaN : Math.max(0f, Math.min(100f, 100f * level / scale));
    }
    public static double watts(int currentMicroamps, int voltageMillivolts) {
        if (currentMicroamps == Integer.MIN_VALUE || voltageMillivolts <= 0) return Double.NaN;
        double value = Math.abs((double) currentMicroamps) * voltageMillivolts / 1_000_000_000d;
        return value <= 200 ? value : Double.NaN;
    }
    public AmbientChargingPolicy() {}
}
