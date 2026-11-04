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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.roundToInt

internal val LocalHeckerNativeClockDepth = staticCompositionLocalOf { false }

/** Mask only the native clock element, including clocks provided by a loaded plugin. */
@Composable
fun HeckerNativeClockDepthModifier(context: Context, customRenderer: Boolean = false): Modifier {
    // The same factory can host previews without a depth backdrop.
    if (!LocalHeckerNativeClockDepth.current) return Modifier
    val state by HeckerAmbientCharging.state.collectAsState()
    val placement = if (customRenderer || state.customClock) Modifier else HeckerClockPlacementModifier(context)
    // Custom clocks own their existing Canvas mask. AOD keeps the native clock rendering.
    if ((state.customClock && !customRenderer) || HeckerDepthScene.awakeFraction <= 0f) return placement
    val foreground = HeckerDepthScene.foreground(context, state) ?: return placement
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val transform = remember { Matrix() }
    val values = remember { FloatArray(9) }
    return placement.onGloballyPositioned { coordinates = it }
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

/** Shared by native/plugin and Hecker clocks, independent of the wallpaper or chosen face. */
@Composable
fun HeckerClockPlacementModifier(context: Context): Modifier {
    val state by HeckerAmbientCharging.state.collectAsState()
    val handoff = LocalHeckerHomeHandoff.current
    val density = LocalDensity.current
    val screen = with(density) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }
    val bottom = with(density) { 72.dp.roundToPx() }
    var top by remember { mutableFloatStateOf(0f) }
    var mediaActive by remember { mutableStateOf(false) }
    // Media space is part of the clock's measured layout, so moving the clock cannot cover it.
    DisposableEffect(context, state.clockY > 0) {
        val media = if (state.clockY > 0) HeckerQsMedia(context, { mediaActive = it.active }) else null
        media?.start(); onDispose { media?.stop() }
    }
    val font = LocalConfiguration.current.fontScale.coerceIn(1f, 2f)
    val reserve = with(density) { (if (mediaActive) maxOf(180f, 172f + (font-1f)*48f) else 0f).dp.roundToPx() }
    val motion = heckerFlowMotionAllowed()
    return Modifier.onGloballyPositioned { top = it.positionInRoot().y }
        .layout { measurable, constraints ->
            val child = measurable.measure(constraints.copy(minHeight = 0))
            val maximumScale = if (state.customClock) maxOf(state.clockScale / 100f, state.aodClockScale / 140f) else 1f
            val placement = LockscreenClockLayout.place(constraints.maxHeight, child.height, screen,
                top.roundToInt(), bottom, reserve, state.clockY, maximumScale)
            val height = constraints.constrainHeight(placement.height)
            layout(constraints.maxWidth, height) {
                child.placeRelativeWithLayer(0, placement.top) {
                    val awake = HeckerDepthScene.awakeFraction.coerceIn(0f, 1f)
                    val dismiss = if (motion) handoff().coerceIn(0f, 1f) else 0f
                    val settle = dismiss * dismiss * (3f - 2f * dismiss)
                    val aod = state.aodClockScale / 140f
                    val base = if (state.customClock) aod + (state.clockScale / 100f - aod) * awake else 1f
                    transformOrigin = TransformOrigin(.5f, 0f)
                    scaleX = placement.fit * base * (1f - .08f * settle) * (if (motion) .96f + .04f * awake else 1f)
                    scaleY = scaleX
                    translationX = child.width * state.clockX / 300f
                    translationY = -child.height * .12f * settle
                    alpha = 1f - settle
                }
            }
        }
}
