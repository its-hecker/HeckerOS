// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

/** Reserve the entire below-dock search slot, including its inset and icon gap. */
public final class DockSearchLayout {
    private DockSearchLayout() {}
    public static int extraHeight(int nativeBottomPadding, int bottomOffset, int height, int gap) {
        return Math.max(0, bottomOffset + height + gap - nativeBottomPadding);
    }
}
