/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.animation.ValueAnimator
import androidx.compose.ui.platform.LocalContext
import org.hecker.motion.FlowClockRenderer
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.*
import com.android.systemui.plugins.statusbar.StatusBarStateController

val HeckerFlowEasing = CubicBezierEasing(.22f, 1f, .36f, 1f)
val HeckerHandoffEasing = CubicBezierEasing(.4f, 0f, .2f, 1f)

/** Use the actual native doze frames, including reversal, instead of starting a second wake timer. */
@Composable
fun heckerAwakeFraction(controller: StatusBarStateController): Float {
    var amount by remember(controller) { mutableFloatStateOf(FlowClockRenderer.unit(controller.dozeAmount)) }
    DisposableEffect(controller) {
        val listener = object : StatusBarStateController.StateListener {
            override fun onDozeAmountChanged(linear: Float, eased: Float) { amount = FlowClockRenderer.unit(linear) }
            override fun onDozingChanged(value: Boolean) { amount = FlowClockRenderer.unit(controller.dozeAmount) }
        }
        controller.addCallback(listener)
        onDispose { controller.removeCallback(listener) }
    }
    val state by HeckerAmbientCharging.state.collectAsState()
    return if (!state.flow || state.powerSave || !ValueAnimator.areAnimatorsEnabled()) {
        if (controller.isDozing) 0f else 1f
    } else FlowClockRenderer.wake(amount)
}

@Composable
fun heckerFlowMotionAllowed(): Boolean {
    val context = LocalContext.current
    DisposableEffect(context) { HeckerAmbientCharging.start(context); onDispose {} }
    val state by HeckerAmbientCharging.state.collectAsState()
    return state.flow && !state.powerSave && ValueAnimator.areAnimatorsEnabled()
}
