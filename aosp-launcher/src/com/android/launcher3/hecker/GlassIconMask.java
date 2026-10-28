// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

/** Pixel-only fallback for legacy opaque foregrounds. Native monochrome alpha is retained. */
public final class GlassIconMask {
    public static void extract(int[] pixels, int width, int height) {
        int transparent = 0;
        for (int color : pixels) if ((color >>> 24) < 32) transparent++;
        if (transparent > pixels.length / 20) return;
        // Older opaque icons have no alpha mask: retain luminance detail instead of a white square.
        long edge = 0; int count = 0;
        for (int x = 0; x < width; x++) { edge += light(pixels[x]); edge += light(pixels[(height - 1) * width + x]); count += 2; }
        for (int y = 1; y < height - 1; y++) { edge += light(pixels[y * width]); edge += light(pixels[y * width + width - 1]); count += 2; }
        int background = (int) (edge / Math.max(1, count));
        for (int i = 0; i < pixels.length; i++) {
            int alpha = Math.min(255, Math.max(0, Math.abs(light(pixels[i]) - background) - 12) * 3);
            pixels[i] = (alpha << 24) | 0xffffff;
        }
    }
    private static int light(int color) { return ((color >> 16 & 255) * 54 + (color >> 8 & 255) * 183 + (color & 255) * 19) >> 8; }
    public static int[] bounds(int[] pixels, int width, int height) {
        int left = width, top = height, right = 0, bottom = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if ((pixels[y * width + x] >>> 24) > 24) {
            left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1);
        }
        return right <= left || bottom <= top ? new int[]{0, 0, width, height} : new int[]{left, top, right, bottom};
    }
    private GlassIconMask() { }
}
