/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.content.Context
import android.graphics.Matrix
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

internal val LocalHeckerNativeClockDepth = staticCompositionLocalOf { false }

/** Mask only the native clock element, including clocks provided by a loaded plugin. */
@Composable
fun HeckerNativeClockDepthModifier(context: Context, customRenderer: Boolean = false): Modifier {
    // The same factory can host previews without a depth backdrop.
    if (!LocalHeckerNativeClockDepth.current) return Modifier
    val state by HeckerAmbientCharging.state.collectAsState()
    // Custom clocks own their existing Canvas mask. AOD keeps the unmodified native clock.
    if ((state.customClock && !customRenderer) || HeckerDepthScene.awakeFraction <= 0f) return Modifier
    val foreground = HeckerDepthScene.foreground(context, state) ?: return Modifier
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val transform = remember { Matrix() }
    val values = remember { FloatArray(9) }
    return Modifier.onGloballyPositioned { coordinates = it }
        // DST_OUT must clear this clock's pixels, never the scene behind it.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val layout = coordinates
            if (layout != null && layout.isAttached && HeckerDepthScene.awakeFraction > 0f) {
                // Read placement at draw time, including native scale, rotation and burn-in motion.
                val root = layout.findRootCoordinates()
                val origin = layout.localPositionOf(root, Offset.Zero)
                val right = layout.localPositionOf(root, Offset(1024f, 0f))
                val down = layout.localPositionOf(root, Offset(0f, 1024f))
                if (DepthWallpaperGeometry.rootToLocal(values, origin.x, origin.y,
                        right.x, right.y, down.x, down.y, 1024f)) {
                    transform.setValues(values)
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val save = native.save()
                        native.concat(transform)
                        foreground(native, Offset.Zero)
                        native.restoreToCount(save)
                    }
                }
            }
        }
}
