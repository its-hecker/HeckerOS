// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Shared full-screen crop for the backdrop and the foreground drawn over the clock. */
public final class DepthWallpaperGeometry {
    private DepthWallpaperGeometry() {}
    public static float[] crop(int imageWidth, int imageHeight, float screenWidth,
            float screenHeight, float reveal, boolean foreground) {
        if (imageWidth <= 0 || imageHeight <= 0 || screenWidth <= 0 || screenHeight <= 0)
            throw new IllegalArgumentException("Empty depth geometry");
        float t = Math.max(0, Math.min(1, Float.isFinite(reveal) ? reveal : 1));
        // A normal photo still contains its subject. Keep both crops identical to avoid
        // double edges; depth comes from the clock between these aligned layers.
        float zoom = 1.025f - .025f * t;
        float scale = Math.max(screenWidth / imageWidth, screenHeight / imageHeight) * zoom;
        return new float[]{scale, (screenWidth - imageWidth * scale) / 2,
                (screenHeight - imageHeight * scale) / 2};
    }
    public static boolean validIdentity(String value) {
        return value != null && value.matches("[a-f0-9]{32}");
    }
}
