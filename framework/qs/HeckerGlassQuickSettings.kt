/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.service.quicksettings.Tile
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toUiState

/** One native backdrop blur behind this panel; all controls and labels remain sharp. */
fun heckerLivingScrim(blurSupported: Boolean): Color =
    if (blurSupported) Color(0xFF110E20).copy(alpha = .72f) else Color(0xFF211F2C)

@Composable
fun HeckerGlassCompact(tiles: List<TileViewModel>, listening: () -> Boolean, modifier: Modifier) {
    TileListener(tiles, listening)
    FlowSurface(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tiles.take(if (LocalConfiguration.current.screenWidthDp < 350 || LocalConfiguration.current.fontScale >= 1.4f) 4 else 6).forEach { FlowTile(it, 44, label = false) }
        }
    }
}

/** ColorOS 17 reference: connection pills, player, paired vertical controls, round tile grid. */
@Composable
fun HeckerGlassQuickSettings(tiles: List<TileViewModel>, listening: () -> Boolean,
    brightness: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    TileListener(tiles, listening)
    val configuration = LocalConfiguration.current
    val narrow = configuration.fontScale >= 1.4f || configuration.screenWidthDp < 350
    val columns = if (configuration.fontScale >= 1.8f) 2 else if (narrow) 3 else 4
    val connections = listOf(setOf("wifi", "internet"), setOf("bt"))
        .mapNotNull { specs -> tiles.firstOrNull { it.spec.spec in specs } }
    // Preserve every selected native/custom tile, including overflow, in editor order.
    val pages = tiles.filter { it !in connections }.chunked(columns * 3).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (narrow) Column(heckerQsElement(HeckerQsScene.Connections), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            connections.forEach { FlowConnection(it, Modifier.fillMaxWidth()) }
        } else Row(heckerQsElement(HeckerQsScene.Connections).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            connections.forEach { FlowConnection(it, Modifier.weight(1f)) }
        }
        if (narrow) {
            CompositionLocalProvider(LocalHeckerFlowGlass provides true) {
                HeckerQsMediaPlayer(Modifier.heightIn(min = 154.dp), square = true)
            }
            Row(heckerQsElement(HeckerQsScene.Brightness).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FlowControls(brightness); HeckerAutoBrightnessButton()
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalHeckerFlowGlass provides true) {
                    HeckerQsMediaPlayer(Modifier.heightIn(min = 178.dp), square = true)
                }
            }
            Row(heckerQsElement(HeckerQsScene.Brightness), horizontalArrangement = Arrangement.spacedBy(12.dp)) { FlowControls(brightness) }
        }
        HorizontalPager(state = pager, modifier = heckerQsElement(HeckerQsScene.Tiles).fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                pages[page].chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(columns) { index ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                row.getOrNull(index)?.let { FlowTile(it, if (narrow) 54 else 58) }
                            }
                        }
                    }
                }
            }
        }
        if (pages.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { index ->
                Box(Modifier.padding(3.dp).size(if (index == pager.currentPage) 18.dp else 5.dp, 5.dp)
                    .background(Color.White.copy(alpha = if (index == pager.currentPage) .9f else .3f), CircleShape))
            }
        }
        if (!narrow) Row(heckerQsElement(HeckerQsScene.Shortcuts).fillMaxWidth(), horizontalArrangement = Arrangement.End) { HeckerAutoBrightnessButton() }
    }
}

@Composable
private fun RowScope.FlowControls(brightness: (@Composable () -> Unit)?) {
    if (brightness != null) GlassVerticalControl {
        CompositionLocalProvider(LocalHeckerVerticalBrightness provides true, LocalHeckerReferenceGlass provides true) { brightness() }
    }
    GlassVerticalControl {
        CompositionLocalProvider(LocalHeckerVerticalBrightness provides true, LocalHeckerReferenceGlass provides true) { HeckerVolumeSlider() }
    }
}

@Composable
private fun FlowSurface(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(modifier.clip(shape).background(Brush.verticalGradient(listOf(
            Color.White.copy(alpha = .16f), Color.White.copy(alpha = .075f))))
        .border(.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .33f),
            Color.White.copy(alpha = .07f), Color.White.copy(alpha = .17f))), shape), content = content)
}

@Composable
private fun FlowConnection(tile: TileViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val state by produceState(tile.currentState, tile) { tile.state.collect { value = it } }
    val ui = state.toUiState(context.resources)
    FlowSurface(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).then(referenceActions(tile, toggleFirst = false))
            .padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FlowTile(tile, 44, label = false)
            Column(Modifier.weight(1f)) {
                Text(ui.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ui.secondaryLabel.isNotBlank()) Text(ui.secondaryLabel, color = Color.White.copy(alpha = .86f),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FlowTile(tile: TileViewModel, diameter: Int = 58, label: Boolean = true) {
    val context = LocalContext.current
    val state by produceState(tile.currentState, tile) { tile.state.collect { value = it } }
    val ui = state.toUiState(context.resources)
    val active = ui.visualState == Tile.STATE_ACTIVE
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val motion = heckerFlowMotionAllowed()
    val scale by animateFloatAsState(if (pressed && motion) .93f else 1f,
        spring(dampingRatio = .86f, stiffness = 650f), label = "flowTilePress")
    val fill by animateColorAsState(if (active) Color.White.copy(alpha = .94f) else Color.White.copy(alpha = .13f),
        label = "flowTileState")
    Column(Modifier.widthIn(min = 48.dp).then(referenceActions(tile, interactions = interactions)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(diameter.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(fill)
            .border(.7.dp, Color.White.copy(alpha = if (pressed) .65f else .16f), CircleShape),
            contentAlignment = Alignment.Center) {
            ReferenceTileIcon(tile,
                (if (active) Color(0xFF1E73D8) else Color.White).copy(alpha = if (ui.handlesMainClick) 1f else .38f))
        }
        if (label) Text(ui.label, color = Color.White.copy(alpha = .88f), fontSize = 11.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GlassVerticalControl(content: @Composable () -> Unit) {
    val thickness = HeckerSliderValues.verticalThickness(LocalConfiguration.current.fontScale).dp
    Box(Modifier.size(thickness, 178.dp).clip(RoundedCornerShape(thickness / 2)), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(178.dp, thickness).graphicsLayer { rotationZ = -90f }, contentAlignment = Alignment.Center) { content() }
    }
}
