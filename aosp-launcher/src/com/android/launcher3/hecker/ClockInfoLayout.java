// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

/** Clock and information never share a rectangle. Values are pixels, in local view coordinates. */
public final class ClockInfoLayout {
    public static float[] bounds(float width, float height, float density, int position, boolean message, boolean weather) {
        if (width <= 0 || height <= 0 || density <= 0) return new float[8];
        if (!message && !weather) return new float[]{0, 0, width, height, 0, 0, 0, 0};
        float gap = Math.min(8 * density, Math.min(width, height) / 12);
        if (position == 2 || position == 3) {
            float side = Math.min(132 * density, width * .34f);
            return position == 2 ? new float[]{side + gap, 0, width - side - gap, height, 0, 0, side, height}
                    : new float[]{0, 0, width - side - gap, height, width - side, 0, side, height};
        }
        float row = Math.min(height * .45f, ((message ? 34 : 0) + (weather ? 26 : 0) + 12) * density);
        return position == 1 ? new float[]{0, 0, width, height - row - gap, 0, height - row, width, row}
                : new float[]{0, row + gap, width, height - row - gap, 0, 0, width, row};
    }
    private ClockInfoLayout() { }
}
