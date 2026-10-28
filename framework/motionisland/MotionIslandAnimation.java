// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Finite gesture choreography; the rope's top attachment never moves. */
final class MotionIslandAnimation {
    private static boolean active(long age) { return age >= 0 && age < MotionIslandState.ACTION_DURATION; }
    private static float smooth(float progress) {
        float t = Math.max(0, Math.min(1, progress));
        return t * t * (3 - 2 * t);
    }
    static float alpha(long age) {
        if (!active(age)) return 0;
        return smooth(age / 180f) * smooth((MotionIslandState.ACTION_DURATION - age) / 420f);
    }
    static float ropeLength(long age, int action) {
        if (!active(age)) return 0;
        float enter = smooth(age / 320f);
        float retract = smooth((MotionIslandState.ACTION_DURATION - age) / 500f);
        // Play gets a welcoming bounce; the origin remains pinned beneath the capsule.
        double bounce = action == MotionIslandState.PLAY
                ? 8 * Math.sin(age * Math.PI / 230) * Math.exp(-age / 650.0) : 0;
        return (float) Math.max(0, (47 + bounce) * enter * retract);
    }
    static float angle(long age, int direction) {
        if (!active(age)) return 0;
        // Negative Android rotation sends the hanging body to the right first.
        return (float) (-direction * 27 * Math.sin(age * Math.PI / 640) * Math.exp(-age / 1800.0));
    }
    static int pose(long age, int action, int direction) {
        if (!active(age) || age < 180 || age >= 1800) return 0;
        if (action == MotionIslandState.PLAY) {
            // A single open-arm welcome held at the end of the soft entrance.
            return age < 320 || age >= 1250 ? 1 : 2;
        }
        if (action == MotionIslandState.PAUSE) {
            // A repeated bye-bye wave, distinct from the held welcome pose.
            return (age - 180) / 190 % 2 == 0 ? 1 : 2;
        }
        if (direction < 0) {
            // Reach into the left swing, then lift the palm as the body returns.
            return age < 860 ? 1 : age < 1180 ? 2 : age < 1500 ? 1 : 0;
        }
        if (direction > 0) {
            // Cheerful arm pumps and a balancing wiggle during the right swing.
            return age < 330 ? 2 : age < 520 ? 1 : age < 770 ? 2
                    : age < 1020 ? 1 : age < 1280 ? 2 : age < 1500 ? 1 : 0;
        }
        return 0;
    }
    static float bodyRock(long age, int action, int direction) {
        if (!active(age)) return 0;
        if (action == MotionIslandState.PAUSE) {
            float t = Math.max(0, Math.min(1, (age - 750) / 850f));
            return (float) (-8 * Math.sin(t * Math.PI)); // A small goodbye bow.
        }
        if (action == MotionIslandState.PLAY)
            return (float) (-4 * Math.sin(age * Math.PI / 440) * Math.exp(-age / 900.0));
        double period = direction > 0 ? 260 : 430;
        return (float) (direction * 6 * Math.sin(age * Math.PI / period) * Math.exp(-age / 1000.0));
    }
    static float welcomeSparkle(long age) {
        if (age <= 300 || age >= 1100) return 0;
        return (float) Math.sin((age - 300) * Math.PI / 800);
    }
    static float bar(long time, int index) {
        return (float) (3 + 10 * (.5 + .5 * Math.sin(time / (135.0 + index * 21) + index * 1.7)));
    }
    private MotionIslandAnimation() { }
}
