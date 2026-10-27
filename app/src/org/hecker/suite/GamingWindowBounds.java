// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

/** Absolute safe-area coordinates and normalized, size-independent saved placement. */
final class GamingWindowBounds {
    final int minX, minY, maxX, maxY;

    GamingWindowBounds(int left, int top, int right, int bottom, int width, int height) {
        minX = left;
        minY = top;
        maxX = Math.max(left, right - Math.max(1, width));
        maxY = Math.max(top, bottom - Math.max(1, height));
    }

    int x(int value) { return Math.max(minX, Math.min(maxX, value)); }
    int y(int value) { return Math.max(minY, Math.min(maxY, value)); }
    int fromX(float fraction) { return minX + Math.round(unit(fraction) * (maxX - minX)); }
    int fromY(float fraction) { return minY + Math.round(unit(fraction) * (maxY - minY)); }
    float fractionX(int value) { return (x(value) - minX) / (float) Math.max(1, maxX - minX); }
    float fractionY(int value) { return (y(value) - minY) / (float) Math.max(1, maxY - minY); }
    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : .5f;
    }
}
