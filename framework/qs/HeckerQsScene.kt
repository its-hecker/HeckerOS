/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementMatcher
import com.android.compose.animation.scene.ContentKey

/** The compact tray has a different geometry from both full dashboards. */
object HeckerQsScene {
    val ExpandedHeader = ElementKey("HeckerExpandedQuickSettingsHeader")
    val ExpandedClock = ElementKey("HeckerExpandedQuickSettingsClock")
    val Media = ElementKey("HeckerSharedQuickSettingsMedia")
    val Connections = ElementKey("HeckerQsConnections")
    val Tiles = ElementKey("HeckerQsTiles")
    val Brightness = ElementKey("HeckerQsBrightness")
    val Volume = ElementKey("HeckerQsVolume")
    val Shortcuts = ElementKey("HeckerQsShortcuts")
    val Controls = object : ElementMatcher {
        override fun matches(key: ElementKey, content: ContentKey): Boolean =
            key in setOf(Connections, Tiles, Brightness, Volume, Shortcuts)
    }
    val Compact = ElementKey("HeckerCompactQuickSettings")
}
