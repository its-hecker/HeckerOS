/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.animation.ValueAnimator
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.systemui.plugins.statusbar.StatusBarStateController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.hecker.motion.FlowClockRenderer

@Composable
private fun dozing(controller: StatusBarStateController): Boolean {
    var value by remember(controller) { mutableStateOf(controller.isDozing) }
    DisposableEffect(controller) {
        val listener = object : StatusBarStateController.StateListener {
            override fun onDozingChanged(dozing: Boolean) { value = dozing }
        }
        controller.addCallback(listener)
        onDispose { controller.removeCallback(listener) }
    }
    return value
}

@Composable
private fun clockText(context: Context): Pair<String,String> {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { now = System.currentTimeMillis() }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIME_CHANGED).apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_LOCALE_CHANGED)
        }, Context.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(context) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(60000 - now % 60000)
        }
    }
    val date = Date(now)
    val locale = Locale.getDefault()
    val time = SimpleDateFormat(if(DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.US).format(date)
    val pattern = DateFormat.getBestDateTimePattern(locale,"EEEMMMd")
    return Pair(time, SimpleDateFormat(pattern,locale).format(date))
}

/** Returns true only while owning the AOD upper region; stock UI is composed otherwise. */
@Composable
fun HeckerAmbientUpperRegion(context: Context, controller: StatusBarStateController,
    modifier: Modifier = Modifier): Boolean {
    DisposableEffect(context) { HeckerAmbientCharging.start(context); onDispose {} }
    val state by HeckerAmbientCharging.state.collectAsState()
    if (!dozing(controller) || !state.ambient || !state.plugged ||
        (state.style == 0 && state.introStart < 0 && !state.introPending)) return false
    val renderer = remember(context) { HeckerAmbientRenderer(context) }
    val clock = if (state.style == 1) clockText(context) else Pair("", "")
    var elapsed by remember(state.introStart) { mutableLongStateOf(0) }
    LaunchedEffect(state.introStart) {
        if (state.introStart < 0) return@LaunchedEffect
        while (isActive) {
            elapsed = SystemClock.elapsedRealtime() - state.introStart
            if (elapsed >= AmbientChargingPolicy.INTRO_MILLIS) break
            withFrameNanos { }
        }
        HeckerAmbientCharging.finishIntro()
    }
    val fraction = if (state.introStart >= 0)
        (elapsed.toFloat()/AmbientChargingPolicy.INTRO_MILLIS).coerceIn(0f,1f)
        else if (state.introPending) 0f else null
    Canvas(modifier.fillMaxSize().clipToBounds().semantics {
        contentDescription = renderer.status(state) + " " + if(state.level.isFinite()) "${state.level.toInt()}%" else ""
    }) {
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            c.save(); c.scale(density,density)
            renderer.charging(c,state,clock.first,clock.second,size.width/density,size.height/density,fraction)
            c.restore()
        }
    }
    return true
}

/** Replaces the selected plugin's entire clock region, so its native clock/date cannot overlap. */
@Composable
fun HeckerCustomClockEnabled(context: Context): Boolean {
    DisposableEffect(context) { HeckerAmbientCharging.start(context); onDispose {} }
    val state by HeckerAmbientCharging.state.collectAsState()
    return state.customClock
}

@Composable
fun HeckerCustomLockscreenClock(context: Context, small: Boolean, controller: StatusBarStateController,
    modifier: Modifier = Modifier): Boolean {
    DisposableEffect(context) { HeckerAmbientCharging.start(context); onDispose {} }
    val state by HeckerAmbientCharging.state.collectAsState()
    if (!state.customClock) return false
    val clock = clockText(context)
    val renderer = remember(context) { HeckerAmbientRenderer(context) }
    val reference = remember { HeckerLockscreenClockRenderer() }
    val living = remember(context) { FlowClockRenderer().also { org.hecker.motion.FlowClockFonts.apply(context, it) } }
    val ambient = dozing(controller)
    val awake = heckerAwakeFraction(controller)
    var onKeyguard by remember(controller) { mutableStateOf(controller.state == 1) }
    DisposableEffect(controller) {
        val listener = object : StatusBarStateController.StateListener {
            override fun onStateChanged(value: Int) { onKeyguard = value == 1 }
        }
        controller.addCallback(listener)
        onDispose { controller.removeCallback(listener) }
    }
    // Keep one selected clock throughout doze: swapping renderers caused the old wake jump.
    val style = if (ambient && state.clockStyle < 2) 0 else state.clockStyle
    val parts = clock.first.split(':')
    val hour = parts[0].toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(style, ambient, onKeyguard, state.screenAwake, state.powerSave) {
        if (style != 1 || ambient || !onKeyguard || !state.screenAwake || state.powerSave || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (isActive && ValueAnimator.areAnimatorsEnabled()) {
            withFrameNanos { rotation = ((it - start) / 1_000_000_000f * 20f) % 360f }
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val homeHandoff = LocalHeckerHomeHandoff.current
    val foreground = if (FlowClockRenderer.supportsDepth(style)) HeckerDepthScene.foreground(context, state) else null
    val height = if (small) { if (style == 1) 218.dp else 136.dp }
        else when (style) { 1 -> 310.dp; 2, 3, 5 -> 480.dp; 4 -> 240.dp; else -> 184.dp }
    Canvas(modifier.fillMaxWidth().height(height).clipToBounds()
        .onGloballyPositioned { origin = it.positionInRoot() }
        .semantics { contentDescription = clock.first + " " + clock.second }) {
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            // Mask the clock with the subject alpha. This reveals the one background scene
            // without painting a second, differently faded/blurred subject over the digits.
            val layer = if (foreground != null && awake > 0f) c.saveLayer(0f, 0f, size.width, size.height, null) else c.save()
            c.save(); c.scale(density,density)
            when (style) {
                1 -> reference.word(c, hour, minute, clock.second.uppercase(Locale.getDefault()),
                    size.width / density, size.height / density, rotation, small)
                2, 3, 4, 5 -> living.draw(c, hour, minute, DateFormat.is24HourFormat(context), clock.second,
                    Locale.getDefault(), size.width / density, size.height / density,
                    if (style == 2) 3 else style, awake, small, false, state.clockOpacity / 100f,
                    state.clockColor, state.clockScale, state.clockX, state.clockY, homeHandoff())
                else -> renderer.clock(c,clock.first,clock.second,size.width/density,if(small) 36f else 52f,
                    if(small) 52f else 72f)
            }
            c.restore()
            if (awake > 0f) foreground?.invoke(c, origin)
            c.restoreToCount(layer)
        }
    }
    return true
}
