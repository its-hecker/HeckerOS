/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import com.android.compose.animation.scene.ElementKey

/** The compact tray has a different geometry from both full dashboards. */
object HeckerQsScene {
    val ExpandedHeader = ElementKey("HeckerExpandedQuickSettingsHeader")
    val ExpandedClock = ElementKey("HeckerExpandedQuickSettingsClock")
    val Compact = ElementKey("HeckerCompactQuickSettings")
}
