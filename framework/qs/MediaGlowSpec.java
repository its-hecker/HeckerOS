// SPDX-License-Identifier: Apache-2.0
package org.hecker.motion;

/** One absolute-time glow shared by media surfaces and the Settings brand card. */
public final class MediaGlowSpec {
    public static final int COUNT = 3;
    public static final long PERIOD_MILLIS = 7200L;
    public static final int UNDERLAY = 0x47131322;
    public static final int RIM = 0x1AB3DEFF;
    private static final int[] COLORS = {0xFF9A82FF, 0xFF88DFFF, 0xFFB1ADFF};

    public static float phase(long elapsedMillis) {
        return (Math.floorMod(elapsedMillis, PERIOD_MILLIS) / (float) PERIOD_MILLIS)
                * (float) (Math.PI * 2);
    }

    private static float offset(int index, float phase) { return phase + index * 2.094395f; }
    public static int color(int index) { return COLORS[index]; }
    public static float centerX(int index, float phase) {
        return .50f + .29f * (float) Math.sin(offset(index, phase));
    }
    public static float centerY(int index, float phase) {
        return .93f + .12f * (float) Math.sin(offset(index, phase) * 2f);
    }
    public static float radius(int index, float phase) {
        return .48f + .04f * (float) Math.sin(offset(index, phase));
    }
    public static float centerAlpha(int index) { return .30f + .07f * index; }
    public static int centerColor(int index) {
        return (Math.round(255 * centerAlpha(index)) << 24) | (color(index) & 0xFFFFFF);
    }
    public static int middleColor(int index) { return 0x1A000000 | (color(index) & 0xFFFFFF); }
    private MediaGlowSpec() {}
}
