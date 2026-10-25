/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

/** One bounded native pulse request. Old asynchronous suppression cannot cancel a new plug. */
public final class AmbientPulsePolicy {
    public static final long TIMEOUT_MILLIS = 10000;
    private long generation, deadline = -1;

    public long begin(long now) { deadline = now + TIMEOUT_MILLIS; return ++generation; }
    public long generation() { return generation; }
    public boolean owns(long token) { return token == generation; }
    public boolean pending(long token, long now) {
        return owns(token) && deadline >= 0 && now >= deadline - TIMEOUT_MILLIS && now <= deadline;
    }
    public void accepted(long token) { if (owns(token)) deadline = -1; }
    public void cancel() { ++generation; deadline = -1; }
}
