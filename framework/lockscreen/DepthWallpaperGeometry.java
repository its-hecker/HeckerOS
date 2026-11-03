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

    /** Map root-space subject pixels into the current native clock's local coordinates. */
    public static boolean rootToLocal(float[] result, float originX, float originY,
            float rightX, float rightY, float downX, float downY, float basis) {
        if (result == null || result.length < 9 || !Float.isFinite(basis) || basis <= 0
                || !Float.isFinite(originX) || !Float.isFinite(originY)
                || !Float.isFinite(rightX) || !Float.isFinite(rightY)
                || !Float.isFinite(downX) || !Float.isFinite(downY)) return false;
        float xx = (rightX - originX) / basis, xy = (downX - originX) / basis;
        float yx = (rightY - originY) / basis, yy = (downY - originY) / basis;
        float determinant = xx * yy - xy * yx;
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 0.000001f) return false;
        result[0] = xx; result[1] = xy; result[2] = originX;
        result[3] = yx; result[4] = yy; result[5] = originY;
        result[6] = 0; result[7] = 0; result[8] = 1;
        return true;
    }
}
