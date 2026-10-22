/*
 * Copyright (C) 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Hecker's reference layout, backed by the native Android 17 QSTile models.
 */
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.android.systemui.hecker

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.service.quicksettings.Tile
import android.widget.ImageView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState

private const val LAYOUT = "hecker_qs_layout"
private const val SUITE = "custom(org.hecker.suite/.SuiteTileService)"
private const val MOTION = "custom(org.hecker.suite/.MotionSenseTileService)"
private const val FACE = "custom(org.hecker.suite/.FaceUnlockTileService)"
private val FOOTER = setOf(SUITE, "wallet", "nfc", "controls")

@Composable
fun heckerCustomizedLayout(): Boolean {
    val context = LocalContext.current
    var value by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        fun read() { value = Settings.Secure.getIntForUser(context.contentResolver, LAYOUT, 0, ActivityManager.getCurrentUser()) == 1 }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = read()
        }
        val users = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read()
        }
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(LAYOUT), false, observer, UserHandle.USER_ALL)
        context.registerReceiver(users, IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED)
        read()
        onDispose { context.contentResolver.unregisterContentObserver(observer); context.unregisterReceiver(users) }
    }
    return value
}

/** The native tile editor still owns selection and order. All selected tiles remain reachable. */
@Composable
fun HeckerTileGrid(
    tiles: List<TileViewModel>,
    modifier: Modifier = Modifier,
    listening: () -> Boolean,
    compact: Boolean = false,
    brightness: (@Composable () -> Unit)? = null,
    includeFooter: Boolean = true,
) {
    if (compact && !heckerCustomizedLayout()) {
        HeckerReferenceCompact(tiles, listening, modifier)
        return
    }
    TileListener(tiles, listening)
    val columns = if (LocalConfiguration.current.fontScale >= 1.4f) 3 else 4
    val customized = !compact && heckerCustomizedLayout()
    val selected = if (compact) tiles.take(6) else tiles.filter { it.spec.spec !in FOOTER && (!customized || it.spec.spec !in setOf(MOTION, FACE)) }
    // Promote selected connectivity controls into the large cards. Other tiles
    // retain the native editor's order, including across additional pages.
    val connectivity = if (compact) emptyList() else selected.filter { it.spec.spec in setOf("internet", "wifi", "bt") }.take(2)
    val regular = connectivity + selected.filter { it !in connectivity }
    val foot = if (compact) emptyList() else tiles.filter { it.spec.spec in FOOTER }
    val pages = if (customized) {
        listOf(regular.drop(2).take(columns * 2)) +
            (regular.take(2) + regular.drop(2 + columns * 2)).chunked(2 + columns * 2)
    } else regular.chunked(2 + columns * 2).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    // One UI-style modules: connectivity, tile tray and controls have separate
    // surfaces, rather than disappearing into one large enclosing card.
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (compact) {
            HeckerRows(regular, columns, true, largeFirst = false)
        } else {
            if (customized && pager.currentPage == 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val suite = tiles.firstOrNull { it.spec.spec == SUITE }
                        if (suite != null) HeckerBrandCard(suite, Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(MOTION, FACE).forEach { spec ->
                                tiles.firstOrNull { it.spec.spec == spec }?.let {
                                    HeckerTile(it, false, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (brightness != null) VerticalControl { brightness() }
                    VerticalControl { HeckerVolumeSlider() }
                }
            }
            HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { index ->
                HeckerRows(pages[index], columns, false, largeFirst = !customized || index != 0)
            }
            if (pages.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { page ->
                    Box(Modifier.padding(4.dp).size(if (pager.currentPage == page) 7.dp else 5.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (pager.currentPage == page) .9f else .3f), CircleShape))
                }
            }
            if (!customized) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .94f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (brightness != null) brightness()
                HeckerVolumeSlider()
            }
            if (includeFooter && foot.isNotEmpty()) HeckerTileFooter(tiles)
        }
    }
}

@Composable
private fun HeckerRows(tiles: List<TileViewModel>, columns: Int, compact: Boolean, largeFirst: Boolean = true) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (largeFirst && tiles.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.take(2).forEach { tile -> HeckerTile(tile, true, Modifier.weight(1f)) }
            if (tiles.size == 1) Spacer(Modifier.weight(1f))
        }
        val small = if (largeFirst) tiles.drop(2) else tiles
        if (small.isNotEmpty()) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .94f))
                .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            small.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tile -> HeckerTile(tile, false, Modifier.weight(1f), showLabels = !compact) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HeckerTile(tile: TileViewModel, large: Boolean, modifier: Modifier, showLabels: Boolean = true) {
    val context = LocalContext.current
    val resources = context.resources
    val state by produceState(tile.currentState, tile) { tile.state.collect { value = it } }
    val ui = state.toUiState(resources)
    val active = ui.visualState == Tile.STATE_ACTIVE
    val accent by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .10f), label = "Hecker tile state")
    val foreground = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val accessibility = ui.accessibilityUiState
    val actions = Modifier.semantics(mergeDescendants = true) {
        contentDescription = accessibility.contentDescription
        stateDescription = accessibility.stateDescription
        role = accessibility.accessibilityRole
        accessibility.toggleableState?.let { toggleableState = it }
    }.combinedClickable(
        enabled = ui.handlesMainClick,
        onClick = { if (!large && ui.handlesToggleClick) tile.toggleClick() else tile.mainClick(null) },
        onLongClick = if (ui.handlesSettingsClick) ({ tile.settingsClick(null) }) else null,
    )
    val icon: @Composable () -> Unit = {
        Box(Modifier.size(52.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ImageView(it).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
                modifier = Modifier.size(27.dp), update = { view ->
                    view.setImageDrawable(state.toIconProvider().icon?.getDrawable(context)?.mutate())
                    view.setColorFilter(android.graphics.Color.argb(if (ui.handlesMainClick) 255 else 100,
                        (foreground.red * 255).toInt(), (foreground.green * 255).toInt(), (foreground.blue * 255).toInt()))
                })
        }
    }
    val labels: @Composable () -> Unit = {
        Text(ui.label, color = if (large && active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, fontSize = if (large) 15.sp else 12.sp,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (large) TextAlign.Start else TextAlign.Center)
        if (ui.secondaryLabel.isNotBlank()) Text(ui.secondaryLabel, color = if (large && active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (large) TextAlign.Start else TextAlign.Center)
    }
    if (large) Row(modifier.clip(RoundedCornerShape(30.dp)).background(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .94f))
        .then(actions).heightIn(min = 92.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon()
        Column(Modifier.weight(1f)) { labels() }
    } else Column(modifier.clip(RoundedCornerShape(20.dp)).then(actions).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        icon()
        if (showLabels) labels()
    }
}

@Composable
private fun VerticalControl(content: @Composable () -> Unit) {
    Box(Modifier.size(width = 62.dp, height = 200.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(width = 200.dp, height = 62.dp).graphicsLayer { rotationZ = -90f }) { content() }
    }
}

/** Real media-stream volume, including fixed-volume and safe-volume framework policy. */
@Composable
fun HeckerVolumeSlider() {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()
    val minimum = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC).toFloat()
    DisposableEffect(audio) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (!dragging) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.onSurface
    Slider(value = volume.coerceIn(minimum, maximum), valueRange = minimum..maximum,
        enabled = !audio.isVolumeFixed, onValueChange = { value ->
            dragging = true
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.toInt(), 0) }
            catch (_: SecurityException) { }
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        }, onValueChangeFinished = { dragging = false },
        modifier = Modifier.fillMaxWidth().height(56.dp).semantics {
            contentDescription = context.getString(com.android.systemui.res.R.string.hecker_media_volume)
        }, thumb = {}, track = { _ ->
            Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                val progress = ((volume - minimum) / (maximum - minimum).coerceAtLeast(1f)).coerceIn(0f, 1f)
                drawRoundRect(fill, size = androidx.compose.ui.geometry.Size((size.width * progress).coerceAtLeast(size.height), size.height), cornerRadius = radius)
            }
        })
}

@Composable
fun HeckerTileFooter(tiles: List<TileViewModel>) {
    val foot = tiles.filter { it.spec.spec in FOOTER }
    if (foot.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        foot.forEach { tile -> HeckerTile(tile, false, Modifier.weight(1f)) }
    }
}

@Composable
private fun HeckerBrandCard(tile: TileViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(context) {
        try {
            val app = context.createPackageContext("org.hecker.suite", 0)
            val id = app.resources.getIdentifier("hecker_mascot", "drawable", "org.hecker.suite")
            if (id == 0) null else android.graphics.BitmapFactory.decodeResource(app.resources, id,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })
        } catch (_: Exception) { null }
    }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
        .combinedClickable(onClick = { tile.mainClick(null) }, onLongClick = { tile.settingsClick(null) }).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (image != null) AndroidView(factory = { ImageView(it) }, modifier = Modifier.size(56.dp).clip(CircleShape),
            update = { it.setImageBitmap(image); it.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        Column(Modifier.weight(1f)) {
            Text(context.getString(com.android.systemui.res.R.string.hecker_brand), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(context.getString(com.android.systemui.res.R.string.hecker_brand_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
fun HeckerHeaderActions(viewModel: com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel, edit: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState(initial = null)
    val reference = !heckerCustomizedLayout()
    val tint = if (reference) android.graphics.Color.WHITE else MaterialThemeColor.primaryText(context)
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = edit, modifier = Modifier.size(36.dp)) {
            AndroidView(factory = { ImageView(it) },
                modifier = Modifier.size(22.dp), update = {
                    it.setImageResource(com.android.systemui.res.R.drawable.hecker_qs_edit)
                    it.contentDescription = context.getString(com.android.systemui.res.R.string.hecker_edit_tiles)
                    it.setColorFilter(tint)
                })
        }
        listOfNotNull(viewModel.power?.let { it to com.android.systemui.res.R.drawable.hecker_qs_power },
            settings?.let { it to com.android.systemui.res.R.drawable.hecker_qs_settings }).forEach { (model, resource) ->
            com.android.compose.animation.Expandable(color = Color.Transparent, shape = CircleShape,
                onClick = model.onClick, modifier = Modifier.size(36.dp), useModifierBasedImplementation = true) {
                AndroidView(factory = { ImageView(it) }, modifier = Modifier.padding(7.dp).size(22.dp), update = { view ->
                    val icon = model.icon
                    view.setImageResource(resource)
                    view.setColorFilter(tint)
                    view.contentDescription = when (val description = icon.contentDescription) {
                        is com.android.systemui.common.shared.model.ContentDescription.Loaded -> description.description
                        is com.android.systemui.common.shared.model.ContentDescription.Resource -> context.getString(description.res)
                        null -> null
                    }
                })
            }
        }
    }
}

private object MaterialThemeColor {
    fun primaryText(context: Context): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, value, true)
        return if (value.resourceId == 0) value.data else context.getColor(value.resourceId)
    }
}
