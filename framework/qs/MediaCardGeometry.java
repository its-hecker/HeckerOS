/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

/** Layout in dp, sampled by Compose during measurement, never by a second expansion timer. */
public final class MediaCardGeometry {
    public static final int ART = 0, TITLES = 1, SEEK = 2, PREVIOUS = 3,
            PLAY = 4, NEXT = 5, OUTPUT = 6, COUNT = 7;
    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
    private static float smooth(float value) {
        float t = unit(value); return t * t * (3 - 2 * t);
    }
    /** The real panel/scene owns visibility; a finishing shared-element animation cannot reopen it. */
    public static float shadeAlpha(boolean legacy, boolean shown, float expansion,
            boolean idleGone, boolean closing, boolean opening, float progress) {
        if (idleGone || legacy && (!shown || !Float.isFinite(expansion) || expansion <= 0)) return 0;
        if ((closing || opening) && !Float.isFinite(progress)) return 0;
        float panel = legacy ? unit(expansion) : 1;
        return panel * (closing ? unit(1 - progress / .25f) : opening ? unit(progress / .12f) : 1);
    }
    /** x, y, width, height, alpha. The two layout weights also work in reverse mid-gesture. */
    public static float[] bounds(int element, float width, float height, float square, float row) {
        return bounds(element, width, height, square, row, 1);
    }
    public static float[] bounds(int element, float width, float height, float square, float row, float fontScale) {
        float[] result = new float[5];
        boundsInto(result, element, width, height, square, row, fontScale);
        return result;
    }
    /** Reuse the caller's seven measurement buffers instead of allocating three whole layouts per child. */
    public static void boundsInto(float[] result, int element, float width, float height,
            float square, float row, float fontScale) {
        float r = unit(row), s = unit(square) * (1 - r), w = 1 - s - r;
        float titleHeight = Math.min(height - 16, 38 * Math.max(1, Math.min(2, fontScale)));
        float seekY = Math.max(70, 24 + titleHeight);
        switch (element) {
            case ART -> blend(result, w, s, r, 16, 14, 48, 48, 1,
                    12, 12, 40, 40, 1, 14, (height-36)/2, 36, 36, 1);
            case TITLES -> blend(result, w, s, r, 76, 16, width-92, titleHeight, 1,
                    12, 62, width-24, titleHeight, 1, 102, (height-titleHeight)/2, width-158, titleHeight, 1);
            case SEEK -> blend(result, w, s, r, 12, seekY, width-24, 32, 1,
                    12, 70, width-24, 32, 0, 12, 70, width-24, 32, 0);
            case PREVIOUS -> blend(result, w, s, r, 12, height-52, 44, 44, 1,
                    4, height-50, 44, 44, 1, 54, (height-44)/2, 44, 44, 0);
            case PLAY -> blend(result, w, s, r, (width-44)/2-(width-68)/6, height-52, 44, 44, 1,
                    (width-44)/2, height-50, 44, 44, 1, 54, (height-44)/2, 44, 44, 1);
            case NEXT -> blend(result, w, s, r, (width-44)/2+(width-68)/6, height-52, 44, 44, 1,
                    width-48, height-50, 44, 44, 1, width-56, (height-44)/2, 44, 44, 0);
            case OUTPUT -> blend(result, w, s, r, width-56, height-52, 44, 44, 1,
                    width-50, 8, 44, 44, 1, width-56, (height-44)/2, 44, 44, 1);
            default -> throw new IllegalArgumentException("Unknown media element: " + element);
        }
        if (element == TITLES) {
            // Go below the artwork before moving left. In the compact row, make
            // room on the right before the playback button travels into that row.
            float squareX = 76 + (12 - 76) * smooth((s - .28f) / .72f);
            float squareY = 16 + (62 - 16) * smooth(s / .55f);
            float arch = 28 + .32f * Math.max(0, width - 240);
            float rowX = 76 + 26 * r + arch * (float) Math.sin(Math.PI * r);
            result[0] = squareX * (1 - r) + (rowX - 76) + 76 * r;
            result[1] = squareY * (1 - r) + (height - titleHeight) / 2 * r;
            float right = width - 16 - 48 * ((float) Math.sin(Math.PI * s) * (1 - r) + smooth(r / .4f));
            result[2] = Math.max(1, right - result[0]);
        } else if (element == OUTPUT) {
            result[1] = (height - 52 + (8 - (height - 52)) * smooth(s / .8f)) * (1 - r)
                    + (height - 44) / 2 * r;
        } else if (element == SEEK) {
            result[4] = 1 - smooth((s + r) / .12f);
        } else if (element == PREVIOUS || element == NEXT) {
            result[4] = 1 - smooth(r / .35f);
        }
        result[2] = Math.max(1, Math.min(width, result[2]));
        result[3] = Math.max(1, Math.min(height, result[3]));
        result[0] = Math.max(0, Math.min(width - result[2], result[0]));
        result[1] = Math.max(0, Math.min(height - result[3], result[1]));
    }
    private static void blend(float[] out, float w, float s, float r,
            float ax, float ay, float aw, float ah, float aa,
            float bx, float by, float bw, float bh, float ba,
            float cx, float cy, float cw, float ch, float ca) {
        out[0]=ax*w+bx*s+cx*r; out[1]=ay*w+by*s+cy*r;
        out[2]=aw*w+bw*s+cw*r; out[3]=ah*w+bh*s+ch*r; out[4]=aa*w+ba*s+ca*r;
    }
    /** Absolute phase keeps both scene copies aligned when Android changes the drawing scene. */
    public static float glowPhase(long elapsedMillis) {
        return (Math.floorMod(elapsedMillis, 7200L) / 7200f) * (float) (Math.PI * 2);
    }
    private MediaCardGeometry() {}
}
