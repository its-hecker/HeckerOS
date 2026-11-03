// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

/** Fit a transparent subject onto the background canvas without changing its aspect ratio. */
public final class DepthLayerGeometry {
    public static float[] fit(int bw, int bh, int fw, int fh, int size, int x, int y) {
        if (bw <= 0 || bh <= 0 || fw <= 0 || fh <= 0) throw new IllegalArgumentException("Empty layer");
        float scale = Math.min((float) bw / fw, (float) bh / fh)
                * Math.max(25, Math.min(200, size)) / 100f;
        return new float[]{scale, (bw - fw * scale) / 2 + bw * Math.max(-100, Math.min(100, x)) / 100f,
                (bh - fh * scale) / 2 + bh * Math.max(-100, Math.min(100, y)) / 100f};
    }
    private DepthLayerGeometry() { }
}
