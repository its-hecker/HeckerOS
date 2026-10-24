/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
    Canvas(modifier = Modifier.layout { measurable, constraints ->
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
        contentDescription = contentDescription) {
        drawable.update(level, viewModel.isCharging, viewModel.attribution == BatteryGlyph.Plus,
            viewModel.glyphList.isNotEmpty(), colors().fill.toArgb())
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawable.draw(drawContext.canvas.nativeCanvas)
    }
    return true
}
