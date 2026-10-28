/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel

/** Hecker's compact connectivity module keeps its coloured, glass dashboard identity. */
@Composable
fun HeckerGlassCompact(tiles: List<TileViewModel>, listening: () -> Boolean, modifier: Modifier) {
    TileListener(tiles, listening)
    GlassModule(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            tiles.take(6).forEach { ReferenceTile(it, 44, colored = true) }
        }
    }
}

/** Glass control centre, using the native editor's tiles and native brightness policy. */
@Composable
fun HeckerGlassQuickSettings(tiles: List<TileViewModel>, listening: () -> Boolean,
    brightness: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    TileListener(tiles, listening)
    val largeType = LocalConfiguration.current.fontScale >= 1.4f
    val connectionSpecs = setOf("wifi", "internet", "bt", "airplane", "cell", "hotspot")
    val connections = tiles.filter { it.spec.spec in connectionSpecs }.take(4)
    val utilities = tiles.filter { it.spec.spec in setOf("rotation", "cast", "dnd") }
    val promoted = connections + utilities
    val others = tiles.filter { it !in promoted }
    val columns = if (largeType) 3 else 4
    val pages = others.chunked(columns * 3).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassModule(Modifier.weight(1f).heightIn(min = 150.dp)) {
                connections.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { ReferenceTile(it, if (largeType) 48 else 56, colored = true) }
                    }
                }
            }
            Box(Modifier.weight(1f)) { HeckerMediaCard(Modifier.heightIn(min = 150.dp), square = true) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    utilities.filter { it.spec.spec != "dnd" }.forEach { ReferenceTile(it, 56, colored = true) }
                }
                utilities.firstOrNull { it.spec.spec == "dnd" }?.let { tile ->
                    GlassModule(Modifier.fillMaxWidth().heightIn(min = 70.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            ReferenceTile(tile, 48, colored = true)
                            Text(androidx.compose.ui.platform.LocalContext.current.getString(com.android.systemui.res.R.string.hecker_qs_focus), color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
            if (brightness != null) GlassVerticalControl {
                CompositionLocalProvider(LocalHeckerVerticalBrightness provides true, LocalHeckerReferenceGlass provides true) { brightness() }
            }
            GlassVerticalControl {
                CompositionLocalProvider(LocalHeckerVerticalBrightness provides true, LocalHeckerReferenceGlass provides true) { HeckerVolumeSlider() }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                pages[page].chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { ReferenceTile(it, if (largeType) 52 else 60, colored = true) }
                        repeat(columns - row.size) { Spacer(Modifier.size(if (largeType) 52.dp else 60.dp)) }
                    }
                }
            }
        }
        if (pages.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { index ->
                Box(Modifier.padding(3.dp).size(if (index == pager.currentPage) 16.dp else 5.dp, 5.dp)
                    .background(Color.White.copy(alpha = if (index == pager.currentPage) .9f else .3f), CircleShape))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { HeckerAutoBrightnessButton() }
    }
}

@Composable
private fun GlassModule(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Column(modifier.clip(shape).background(Color.White.copy(alpha = .13f)).border(1.dp, Color.White.copy(alpha = .15f), shape)
        .padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), content = content)
}

@Composable
private fun GlassVerticalControl(content: @Composable () -> Unit) {
    // Both controls share the exact same track and measure policy. Rotation does
    // not resize a child, so measure it at the transposed dimensions first.
    val thickness = HeckerSliderValues.verticalThickness(LocalConfiguration.current.fontScale).dp
    Box(Modifier.size(thickness, 178.dp).clip(RoundedCornerShape(thickness / 2)), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(178.dp, thickness).graphicsLayer { rotationZ = -90f }, contentAlignment = Alignment.Center) { content() }
    }
}
