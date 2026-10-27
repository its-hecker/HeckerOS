// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Shared deterministic choreography, evaluated only while its window is visible. */
final class MotionIslandAnimation {
    static float alpha(long age) {
        if (age < 0 || age >= MotionIslandState.ACTION_DURATION) return 0;
        return Math.min(1f, age / 180f) * Math.min(1f, (MotionIslandState.ACTION_DURATION - age) / 420f);
    }
    static float angle(long age, int direction) {
        if (age < 0) return 0;
        // Negative Android rotation sends the hanging body to the right first.
        return (float) (-direction * 27 * Math.sin(age * Math.PI / 640) * Math.exp(-age / 1800.0));
    }
    static int pose(long age, boolean wave) {
        if (!wave || age < 160 || age > 1700) return 0;
        return (age / 180) % 2 == 0 ? 1 : 2;
    }
    static float bar(long time, int index) {
        return (float) (3 + 10 * (.5 + .5 * Math.sin(time / (135.0 + index * 21) + index * 1.7)));
    }
    private MotionIslandAnimation() { }
}
