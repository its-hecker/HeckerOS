/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.animateElementFloatAsState

/** A host is provided by both native QS paths, so scene progress owns the player bounds. */
val LocalHeckerQsContentScope = staticCompositionLocalOf<ContentScope?> { null }
private val SquareWeight = ValueKey("HeckerMediaSquareWeight")
private val RowWeight = ValueKey("HeckerMediaRowWeight")

@Composable
fun HeckerQsMediaPlayer(modifier: Modifier = Modifier, square: Boolean = false,
    compactRow: Boolean = false, hideWhenInactive: Boolean = false) {
    val scope = LocalHeckerQsContentScope.current
    if (scope == null) {
        HeckerMediaCard(modifier, square, hideWhenInactive, compactRow = compactRow)
        return
    }
    with(scope) {
        ElementWithValues(HeckerQsScene.Media, modifier) {
            val tile = animateElementFloatAsState(if (square) 1f else 0f, SquareWeight, canOverflow = false)
            val row = animateElementFloatAsState(if (compactRow) 1f else 0f, RowWeight, canOverflow = false)
            content {
                HeckerMediaCard(square = square, compactRow = compactRow,
                    hideWhenInactive = hideWhenInactive,
                    squareWeight = { tile.value }, rowWeight = { row.value })
            }
        }
    }
}

/** Controls fade independently; no fading ancestor is allowed to dim the shared media surface. */
@Composable
fun heckerQsElement(key: ElementKey): Modifier {
    val scope = LocalHeckerQsContentScope.current ?: return Modifier
    return with(scope) { Modifier.element(key) }
}

/** Included in the actual QQS grid in scene-container and compose-fragment configurations. */
@Composable
fun ContentScope.HeckerQuickQsContent(modifier: Modifier = Modifier,
    tiles: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHeckerQsContentScope provides this) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.element(HeckerQsScene.Compact)) { tiles() }
            CompositionLocalProvider(LocalHeckerFlowGlass provides heckerCustomizedLayout()) {
                HeckerQsMediaPlayer(hideWhenInactive = true)
            }
        }
    }
}
