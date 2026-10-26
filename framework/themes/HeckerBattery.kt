/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.animation.ValueAnimator
import android.content.Context
import android.view.Display
import android.widget.ImageView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryGlyph
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel

@Composable
fun HeckerBattery(viewModel: BatteryViewModel, colors: () -> BatteryColors,
    modifier: Modifier, contentDescription: String): Boolean {
    val context = LocalContext.current
    HeckerThemeAssets.resources(context, "battery_style")
    val generation by HeckerThemeAssets.version.collectAsState()
    val drawable = remember(context, generation) { HeckerBatteryDrawable.create(context) } ?: return false
    val level = viewModel.level ?: return false
    if (viewModel.attribution == BatteryGlyph.Defend || viewModel.attribution == BatteryGlyph.Question) return false
    AndroidView(factory = { ChargingBatteryView(it) }, modifier = Modifier.layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        val scale = HeckerBatteryDrawable.scale(context)
        val width = (child.width * scale).roundToInt()
        val height = (child.height * scale).roundToInt()
        layout(width, height) {
            child.placeRelativeWithLayer((width - child.width) / 2, (height - child.height) / 2) {
                scaleX = scale; scaleY = scale
            }
        }
    }.then(modifier).aspectRatio(drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight),
        update = { view ->
            view.contentDescription = contentDescription
            view.updateBattery(drawable, level, viewModel.isCharging,
                viewModel.attribution == BatteryGlyph.Plus,
                viewModel.glyphList.isNotEmpty(), colors().fill.toArgb())
        })
    return true
}

/** ImageView supplies Drawable.Callback and actual window/display visibility. */
private class ChargingBatteryView(context: Context) : ImageView(context) {
    private var visibleToUser = false
    private var screenOn = false
    private val motionListener = ValueAnimator.DurationScaleChangeListener { syncAnimation() }

    init { scaleType = ScaleType.FIT_XY }

    fun updateBattery(battery: HeckerBatteryDrawable, level: Int, charging: Boolean,
        powerSave: Boolean, percent: Boolean, tint: Int) {
        if (drawable !== battery) {
            (drawable as? HeckerBatteryDrawable)?.stop()
            setImageDrawable(battery)
        }
        battery.update(level, charging, powerSave, percent, tint)
        syncAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        screenOn = display?.state == Display.STATE_ON
        ValueAnimator.registerDurationScaleChangeListener(motionListener)
        syncAnimation()
    }

    override fun onDetachedFromWindow() {
        (drawable as? HeckerBatteryDrawable)?.stop()
        ValueAnimator.unregisterDurationScaleChangeListener(motionListener)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visibleToUser = isVisible
        syncAnimation()
    }

    override fun onScreenStateChanged(screenState: Int) {
        super.onScreenStateChanged(screenState)
        screenOn = screenState == SCREEN_STATE_ON
        syncAnimation()
    }

    private fun syncAnimation() {
        val battery = drawable as? HeckerBatteryDrawable ?: return
        if (isAttachedToWindow && visibleToUser && isShown && windowVisibility == VISIBLE
            && screenOn && display?.state == Display.STATE_ON) battery.start()
        else battery.stop()
    }
}
