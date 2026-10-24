// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

/** Window limits in dp. Content scrolls inside these limits, including at large font scales. */
final class GamingPanelLayout {
    final int width, height;
    private GamingPanelLayout(int width, int height) { this.width = width; this.height = height; }

    static GamingPanelLayout fit(int safeWidth, int safeHeight) {
        int availableWidth = Math.max(1, safeWidth - 16);
        int availableHeight = Math.max(1, safeHeight - 20);
        boolean landscape = safeWidth > safeHeight;
        return new GamingPanelLayout(Math.min(landscape ? Math.min(304, Math.round(safeWidth * .36f)) : 328, availableWidth),
                Math.min(landscape ? 352 : 472, availableHeight));
    }
}
