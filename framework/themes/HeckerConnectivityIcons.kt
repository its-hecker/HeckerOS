/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import com.android.systemui.common.shared.model.Icon

/** Compose status icons need the same category updates as the legacy view binders. */
@Composable
fun heckerWifiIcon(nativeIcon: Icon): Icon {
    val context = LocalContext.current
    HeckerThemeAssets.initialize(context)
    val generation by HeckerThemeAssets.version.collectAsState()
    return remember(context, nativeIcon, generation) {
        val resource = nativeIcon as? Icon.Resource
        val drawable = resource?.let { HeckerThemeAssets.wifi(context, it.resId) }
        if (drawable == null) nativeIcon else Icon.Loaded(drawable, nativeIcon.contentDescription)
    }
}

/** Returns false when native error/carrier/satellite presentation should remain in charge. */
@Composable
fun HeckerSignalIcon(level: Int, levels: Int, color: Color, modifier: Modifier): Boolean {
    val context = LocalContext.current
    HeckerThemeAssets.initialize(context)
    val generation by HeckerThemeAssets.version.collectAsState()
    val drawable = remember(context, level, levels, generation) {
        HeckerThemeAssets.signal(context, level, levels)
    } ?: return false
    Canvas(modifier) {
        drawable.setTint(color.toArgb())
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        val canvas = drawContext.canvas.nativeCanvas
        val checkpoint = canvas.save()
        if (layoutDirection == LayoutDirection.Rtl) {
            canvas.translate(size.width, 0f)
            canvas.scale(-1f, 1f)
        }
        drawable.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }
    return true
}
