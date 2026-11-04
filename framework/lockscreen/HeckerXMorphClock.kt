/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: GPL-3.0-or-later */
package com.android.systemui.hecker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RectF
import android.os.UserHandle
import android.text.format.DateFormat
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.drdisagree.xmorphclock.engine.ClockFaceView
import com.drdisagree.xmorphclock.domain.model.FaceData
import com.drdisagree.xmorphclock.nativehost.XMorphNative
import com.drdisagree.xmorphclock.nativehost.XMorphWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class XMorphFace(val user: Int, val requested: String?, val entry: XMorphNative.Entry)
private data class XMorphBackdrop(val user: Int, val bitmap: android.graphics.Bitmap?)

/** Host the upstream clock View directly in the native lockscreen scene. */
@Composable
fun HeckerXMorphLockscreenClock(context: Context, small: Boolean,
    controller: StatusBarStateController, modifier: Modifier): Boolean {
    val state by HeckerAmbientCharging.state.collectAsState()
    val user = state.user
    val preset = state.xmorphPreset
    val userContext = remember(context, user) { context.createContextAsUser(UserHandle.of(user), 0) }
    val entry by produceState<XMorphFace?>(null, context, preset, user) {
        value = withContext(Dispatchers.IO) {
            try { XMorphFace(user, preset, XMorphNative.selected(userContext, preset)) }
            catch (error: Exception) { android.util.Log.w("HeckerXMorph", "Cannot load packaged clock", error); null }
        }
    }
    val face = entry?.takeIf { it.user == state.user && it.requested == state.xmorphPreset }?.entry
        ?: return false // The existing Hecker clock remains the loading/error fallback.
    val backdrop by produceState<XMorphBackdrop?>(null, context, user, state.wakeGeneration) {
        value = withContext(Dispatchers.IO) {
            XMorphBackdrop(user, XMorphWallpaper.load(userContext, user))
        }
    }
    val awake = heckerAwakeFraction(controller)
    var keyguard by remember(controller) { mutableStateOf(controller.state == 1) }
    DisposableEffect(controller) {
        val listener = object : StatusBarStateController.StateListener {
            override fun onStateChanged(value: Int) { keyguard = value == 1 }
        }
        controller.addCallback(listener); onDispose { controller.removeCallback(listener) }
    }
    val moving = keyguard && state.screenAwake && !state.powerSave && awake > 0f &&
        state.flow && ValueAnimator.areAnimatorsEnabled()
    val depth = HeckerNativeClockDepthModifier(context, customRenderer = true)
    val handoff = LocalHeckerHomeHandoff.current
    var backdropFrame by remember { mutableStateOf(RectF()) }
    key(state.user) { AndroidView(
        factory = { ClockFaceView(userContext, face.face) },
        modifier = modifier.fillMaxWidth().heightIn(max = if (small) 218.dp else 520.dp)
            .graphicsLayer {
                val scale = if (awake <= 0f) state.aodClockScale / 140f else state.clockScale / 100f
                scaleX = scale; scaleY = scale
                val dismiss = org.hecker.motion.FlowClockRenderer.unit(handoff())
                alpha = (if (awake <= 0f) 1f else state.clockOpacity / 100f) *
                    (1f - dismiss * dismiss * (3f - 2f * dismiss))
                translationX = size.width * state.clockX / 300f
                translationY = size.height * state.clockY / 300f
            }.then(depth).layout { measurable, constraints ->
                val width = constraints.maxWidth
                val natural = measurable.measure(Constraints.fixedWidth(width))
                val height = natural.height.coerceAtMost(constraints.maxHeight)
                val fit = if (natural.height > 0) minOf(1f, height.toFloat() / natural.height) else 1f
                layout(width, height) {
                    natural.placeWithLayer(0, 0) {
                        transformOrigin = TransformOrigin(.5f, 0f)
                        scaleX = fit; scaleY = fit
                    }
                }
            }.onGloballyPositioned { coordinates ->
                val root = coordinates.findRootCoordinates()
                val start = coordinates.localPositionOf(root, Offset.Zero)
                val end = coordinates.localPositionOf(root, Offset(root.size.width.toFloat(), root.size.height.toFloat()))
                backdropFrame = RectF(start.x, start.y, end.x, end.y)
            },
        onReset = null,
        onRelease = { it.animationsEnabled = false; it.setBackdrop(null) },
        update = { view ->
            view.animationsEnabled = moving
            view.face = face.face
            view.data = FaceData(use24Hour = DateFormat.is24HourFormat(userContext))
            view.setLargeProgress(if (small) 0f else 1f)
            view.setSleepProgress(1f - awake)
            view.setAodProgress(1f - awake)
            view.setBackdrop(backdrop?.takeIf { it.user == state.user }?.bitmap)
            view.setBackdropFrame(backdropFrame)
            view.isClickable = false; view.isLongClickable = false
        },
    ) }
    return true
}
