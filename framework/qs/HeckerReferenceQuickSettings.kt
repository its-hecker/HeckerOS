/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.android.systemui.hecker

import android.app.UiModeManager
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.service.quicksettings.Tile
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.res.R
import kotlin.math.roundToInt

/** Scoped to the reference panel and brightness mirror, not global brightness controls. */
val LocalHeckerReferenceGlass = staticCompositionLocalOf { false }
val LocalHeckerFlowGlass = staticCompositionLocalOf { false }
private val Ink = Color(0xFF25323F)
private val Glass = Color.White.copy(alpha = .13f)
private val Disc = Color(0xFF092F4D).copy(alpha = .25f)
private val White = Color(0xFFF2F8F8)

/** Native blur capability includes power saver/thermal gates; keep a readable opaque fallback. */
fun heckerReferenceScrim(blurSupported: Boolean): Color =
    if (blurSupported) Color(0xFF08283F).copy(alpha = .22f) else Color(0xFF16384A)

@Composable
fun HeckerReferenceQuickSettings(
    tiles: List<TileViewModel>,
    listening: () -> Boolean,
    brightness: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    TileListener(tiles, listening)
    val connectivity = listOf("wifi", "bt").mapNotNull { spec -> tiles.firstOrNull { it.spec.spec == spec } }
    val regular = tiles.filter { it.spec.spec !in setOf("wifi", "bt", "dark", "controls")
        && !HeckerQsTileOrder.isDolby(it.spec.spec) }
    val pages = regular.chunked(8).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            connectivity.forEach { ReferenceConnection(it, Modifier.weight(1f)) }
            if (connectivity.size == 1) Spacer(Modifier.weight(1f))
        }
        GlassPanel {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(2) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(4) { column ->
                                val tile = pages[page].getOrNull(row * 4 + column)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (tile != null) ReferenceTile(tile)
                                    else Spacer(Modifier.size(50.dp))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 11.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                repeat(pages.size) { page ->
                    Box(Modifier.padding(horizontal = 2.dp).size(if (pager.currentPage == page) 24.dp else 6.dp, 3.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = if (pager.currentPage == page) .65f else .2f)))
                }
            }
        }
        if (brightness != null) GlassPanel {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalHeckerReferenceGlass provides true) { brightness() }
                }
                HeckerAutoBrightnessButton()
                ReferenceDarkButton(tiles.firstOrNull { it.spec.spec == "dark" })
            }
        }
        ReferenceVolume()
        ReferenceMedia()
        ReferenceShortcuts(tiles)
    }
}

/** Observe the native per-user mode, including changes from Display and game mode. */
@Composable
fun HeckerAutoBrightnessButton() {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var automatic by remember(context) { mutableStateOf(false) }
    var restricted by remember(context) { mutableStateOf(false) }
    fun read() {
        val user = ActivityManager.getCurrentUser()
        automatic = Settings.System.getIntForUser(resolver, "screen_brightness_mode", 0, user) == 1
        restricted = context.createContextAsUser(UserHandle.of(user), 0).getSystemService(UserManager::class.java)
            .hasUserRestriction(UserManager.DISALLOW_CONFIG_BRIGHTNESS)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = read()
        }
        val users = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = read()
        }
        resolver.registerContentObserver(Settings.System.getUriFor("screen_brightness_mode"), false, observer, UserHandle.USER_ALL)
        context.registerReceiver(users, IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED)
        read()
        onDispose { resolver.unregisterContentObserver(observer); context.unregisterReceiver(users) }
    }
    Box(Modifier.size(48.dp).clip(CircleShape)
        .background(if (automatic) White.copy(alpha = .88f) else Disc)
        .semantics {
            contentDescription = context.getString(R.string.hecker_qs_auto_brightness)
            role = Role.Switch
            stateDescription = context.getString(if (automatic) R.string.hecker_qs_auto_on else R.string.hecker_qs_auto_off)
            if (restricted) disabled()
        }
        .clickable(enabled = !restricted) {
            try {
                val user = ActivityManager.getCurrentUser()
                if (!context.createContextAsUser(UserHandle.of(user), 0).getSystemService(UserManager::class.java)
                        .hasUserRestriction(UserManager.DISALLOW_CONFIG_BRIGHTNESS)) {
                    val mode = Settings.System.getIntForUser(resolver, "screen_brightness_mode", 0, user)
                    Settings.System.putIntForUser(resolver, "screen_brightness_mode", if (mode == 1) 0 else 1, user)
                }
                read()
            } catch (_: SecurityException) { HeckerQsActions.settings(Settings.ACTION_DISPLAY_SETTINGS) }
        }, contentAlignment = Alignment.Center) {
        Text("A", color = if (automatic) Ink else White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Glass), content = content)
}

/** Original full One UI row. Only the artwork is new; the richer player belongs to QQS/keyguard. */
@Composable
private fun ReferenceMedia() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<HeckerQsMedia.Snapshot?>(null) }
    val media = remember(context) { HeckerQsMedia(context) { snapshot = it } }
    DisposableEffect(media) { media.start(); onDispose { media.stop() } }
    val state = snapshot
    GlassPanel {
        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            state?.artwork?.let { artwork ->
                Image(artwork.asImageBitmap(), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
            }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).clickable { media.playPause() }
                .semantics {
                    contentDescription = context.getString(if (state?.playing == true) R.string.hecker_qs_pause else R.string.hecker_qs_play)
                    role = Role.Button
                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReferenceIcon(if (state?.playing == true) R.drawable.hecker_qs_pause else R.drawable.hecker_qs_play, 22)
                Text(state?.title ?: context.getString(R.string.hecker_qs_play_last), color = Color.White,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(24.dp)).background(Disc)
                .semantics { role = Role.Button }.clickable { media.output() }
                .padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(context.getString(R.string.hecker_qs_media_output), color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun HeckerReferenceCompact(tiles: List<TileViewModel>, listening: () -> Boolean, modifier: Modifier) {
    TileListener(tiles, listening)
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        tiles.take(6).forEach { ReferenceTile(it, 44) }
    }
}

@Composable
private fun ReferenceConnection(tile: TileViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val state by produceState(tile.currentState, tile) { tile.state.collect { value = it } }
    val ui = state.toUiState(context.resources)
    val active = ui.visualState == Tile.STATE_ACTIVE
    Row(modifier.clip(RoundedCornerShape(34.dp)).background(Glass)
        .then(referenceActions(tile, toggleFirst = false)).heightIn(min = 78.dp).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(White.copy(alpha = if (active) 1f else .88f))
            .then(referenceActions(tile)),
            contentAlignment = Alignment.Center) { ReferenceIcon(iconFor(tile.spec.spec), 31, Ink) }
        Column(Modifier.weight(1f)) {
            Text(ui.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ui.secondaryLabel.isNotBlank()) Text(ui.secondaryLabel, color = Color.White.copy(alpha = .72f),
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun referenceActions(tile: TileViewModel, toggleFirst: Boolean = true,
    interactions: MutableInteractionSource? = null): Modifier {
    val context = LocalContext.current
    val state by produceState(tile.currentState, tile) { tile.state.collect { value = it } }
    val ui = state.toUiState(context.resources)
    val accessibility = ui.accessibilityUiState
    return Modifier.semantics(mergeDescendants = true) {
        contentDescription = accessibility.contentDescription
        stateDescription = accessibility.stateDescription
        role = accessibility.accessibilityRole
        accessibility.toggleableState?.let { toggleableState = it }
    }.combinedClickable(interactionSource = interactions ?: remember { MutableInteractionSource() },
        indication = LocalIndication.current, enabled = ui.handlesMainClick,
        onClick = { if (toggleFirst && ui.handlesToggleClick) tile.toggleClick() else tile.mainClick(null) },
        onLongClick = if (ui.handlesSettingsClick) ({ tile.settingsClick(null) }) else null)
}

@Composable
internal fun ReferenceTileIcon(tile: TileViewModel, foreground: Color) {
    val context = LocalContext.current
    val state by produceState(tile.currentState, tile) { tile.state.collect { value = it } }
    val icon = iconFor(tile.spec.spec)
    if (icon != 0) ReferenceIcon(icon, 25, foreground)
    else AndroidView(factory = { ImageView(it).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        modifier = Modifier.size(25.dp), update = {
            it.setImageDrawable(state.toIconProvider().icon?.getDrawable(context)?.mutate())
            it.setColorFilter(foreground.toArgb())
        })
}

@Composable
internal fun ReferenceTile(tile: TileViewModel, diameter: Int = 50, colored: Boolean = false) {
    val context = LocalContext.current
    val state by produceState(tile.currentState, tile) { tile.state.collect { value = it } }
    val ui = state.toUiState(context.resources)
    val active = ui.visualState == Tile.STATE_ACTIVE
    val activeColor = if (!colored) White else when (tile.spec.spec) {
        "wifi", "internet", "bt" -> Color(0xFF258BFF)
        "cell", "hotspot" -> Color(0xFF27A96B)
        "dnd" -> Color(0xFF8067D8)
        else -> White
    }
    val foreground = if (active && activeColor == White) Ink else Color.White
    Box(Modifier.size(diameter.dp).clip(CircleShape).background(if (active) activeColor else Disc)
        .then(referenceActions(tile)), contentAlignment = Alignment.Center) {
        val icon = iconFor(tile.spec.spec)
        if (icon != 0) ReferenceIcon(icon, 27, foreground.copy(alpha = if (ui.handlesMainClick) 1f else .38f))
        else AndroidView(factory = { ImageView(it).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
            modifier = Modifier.size(27.dp), update = {
                it.setImageDrawable(state.toIconProvider().icon?.getDrawable(context)?.mutate())
                it.setColorFilter(foreground.toArgb())
            })
    }
}

@Composable
fun ReferenceIcon(resource: Int, size: Int = 24, tint: Color = Color.White) {
    AndroidView(factory = { ImageView(it).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        modifier = Modifier.size(size.dp), update = { it.setImageResource(resource); it.setColorFilter(tint.toArgb()) })
}

/** Measure at the final text size so the date never overlaps a scaled clock. */
@Composable
fun HeckerReferenceClock(onClick: () -> Unit, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    AndroidView(factory = { android.widget.TextClock(it).apply {
        textSize = 26f
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setTextColor(textColor.toArgb())
        isSingleLine = true
        gravity = android.view.Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    } }, update = {
        it.setTextColor(textColor.toArgb())
        it.format12Hour = android.text.format.DateFormat.getBestDateTimePattern(locale, "hm")
        it.format24Hour = android.text.format.DateFormat.getBestDateTimePattern(locale, "Hm")
        it.setOnClickListener { onClick() }
    }, modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.Button })
}

private fun iconFor(spec: String): Int = when (spec) {
    "wifi", "internet" -> R.drawable.hecker_qs_wifi
    "bt" -> R.drawable.hecker_qs_bluetooth
    "rotation" -> R.drawable.hecker_qs_rotation
    "airplane" -> R.drawable.hecker_qs_airplane
    "flashlight" -> R.drawable.hecker_qs_flashlight
    "cell" -> R.drawable.hecker_qs_data
    "cast" -> R.drawable.hecker_qs_cast
    "battery" -> R.drawable.hecker_qs_battery
    "location" -> R.drawable.hecker_qs_location
    "hotspot" -> R.drawable.hecker_qs_link
    "dnd" -> R.drawable.hecker_qs_modes
    "dark" -> R.drawable.hecker_qs_moon
    else -> 0
}

@Composable
private fun ReferenceDarkButton(tile: TileViewModel?) {
    val context = LocalContext.current
    val night = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    Box(Modifier.size(48.dp).clip(CircleShape).background(Disc)
        .semantics { contentDescription = context.getString(R.string.hecker_qs_dark) }
        .then(if (tile != null) referenceActions(tile) else Modifier.clickable { HeckerQsActions.dark(!night) }),
        contentAlignment = Alignment.Center) { ReferenceIcon(R.drawable.hecker_qs_moon, 28, White) }
}

/** Visual track only: native brightness still owns policy, gamma, falsing, haptics and drag events. */
@Composable
fun HeckerGlassTrack(fraction: Float, icon: Int, modifier: Modifier = Modifier, labelFraction: Float = fraction) {
    val vertical = LocalHeckerVerticalBrightness.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: java.util.Locale.getDefault()
    val percentage = HeckerSliderValues.percent(labelFraction)
    val text = remember(percentage, locale) { HeckerSliderValues.label(percentage / 100f, locale) }
    val progress = HeckerSliderValues.unit(fraction)
    val thickness = HeckerSliderValues.verticalThickness(configuration.fontScale).dp
    Box(modifier.fillMaxWidth().height(if (vertical) thickness else 44.dp), contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = .28f), cornerRadius = corner)
            drawRoundRect(White.copy(alpha = .88f), size = androidx.compose.ui.geometry.Size(
                (size.width * progress).coerceAtLeast(size.height).coerceAtMost(size.width), size.height), cornerRadius = corner)
        }
        Box(Modifier.padding(start = 15.dp).graphicsLayer { rotationZ = if (vertical) 90f else 0f }) { ReferenceIcon(icon, 23, Ink) }
        // The vertical control rotates its child. Counter-rotate the entire badge
        // so numbers and localized percent signs remain upright in both layouts.
        Box(Modifier.align(if (vertical) Alignment.Center else Alignment.CenterEnd)
            .padding(end = if (vertical) 0.dp else 12.dp)
            .graphicsLayer { rotationZ = if (vertical) 90f else 0f }
            .background(Ink.copy(alpha = .94f), RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp).clearAndSetSemantics { }) {
            Text(text, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun ReferenceVolume() {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val minimum = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    val limit = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val maximum = limit.coerceAtLeast(minimum + 1)
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var muted by remember { mutableStateOf(audio.isStreamMute(AudioManager.STREAM_MUSIC)) }
    var dragging by remember { mutableStateOf(false) }
    DisposableEffect(audio) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (!dragging) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION").apply { addAction("android.media.STREAM_MUTE_CHANGED_ACTION") }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    GlassPanel {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Slider(value = volume.coerceIn(minimum.toFloat(), maximum.toFloat()),
                valueRange = minimum.toFloat()..maximum.toFloat(), steps = (maximum - minimum - 1).coerceAtLeast(0),
                enabled = !audio.isVolumeFixed && limit > minimum,
                onValueChange = {
                    dragging = true
                    try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0) }
                    catch (_: SecurityException) { }
                    volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
                }, onValueChangeFinished = { dragging = false }, thumb = {},
                track = { HeckerGlassTrack(if (muted) 0f else HeckerSliderValues.fraction(volume, minimum.toFloat(), limit.toFloat()), R.drawable.hecker_qs_music,
                    labelFraction = if (muted) 0f else HeckerSliderValues.fraction(volume, 0f, limit.toFloat())) },
                modifier = Modifier.weight(1f).height(48.dp).semantics {
                    contentDescription = context.getString(R.string.hecker_media_volume)
                    stateDescription = HeckerSliderValues.label(if (muted) 0f else HeckerSliderValues.fraction(volume, 0f, limit.toFloat()), java.util.Locale.getDefault())
                })
            Box(Modifier.size(48.dp).clip(CircleShape).background(Disc)
                .semantics { contentDescription = context.getString(if (muted) R.string.hecker_qs_unmute else R.string.hecker_qs_mute) }
                .clickable(enabled = !audio.isVolumeFixed) {
                    try { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
                    catch (_: SecurityException) { }
                    muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
                    volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                }, contentAlignment = Alignment.Center) {
                ReferenceIcon(if (muted) R.drawable.hecker_qs_mute else R.drawable.hecker_qs_speaker, 27, White)
            }
        }
    }
}

@Composable
private fun ReferenceShortcuts(tiles: List<TileViewModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReferenceShortcut(R.string.hecker_qs_smart_view, R.string.hecker_qs_mirror_screen, R.drawable.hecker_qs_smart_view,
                Modifier.weight(1f)) { HeckerQsActions.settings(Settings.ACTION_CAST_SETTINGS) }
            ReferenceShortcut(R.string.hecker_qs_modes, 0, R.drawable.hecker_qs_modes,
                Modifier.weight(1f)) { HeckerQsActions.settings(Settings.ACTION_ZEN_MODE_SETTINGS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReferenceShortcut(R.string.hecker_qs_nearby, 0, R.drawable.hecker_qs_nearby,
                Modifier.weight(1f)) { HeckerQsActions.settings(Settings.ACTION_BLUETOOTH_SETTINGS) }
            ReferenceDolby(tiles.firstOrNull { HeckerQsTileOrder.isDolby(it.spec.spec) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReferenceDolby(tile: TileViewModel?, modifier: Modifier) {
    if (tile == null) {
        // During binding, or if the user removes the underlying tile in the
        // native editor, the existing Lunaris controls remain reachable.
        ReferenceShortcut(R.string.hecker_qs_dolby, R.string.hecker_qs_dolby_summary,
            R.drawable.hecker_qs_dolby, modifier) { HeckerQsActions.dolby() }
        return
    }
    val context = LocalContext.current
    val state by produceState(tile.currentState, tile) { tile.state.collect { value = it } }
    val ui = state.toUiState(context.resources)
    val active = ui.visualState == Tile.STATE_ACTIVE
    val accent = Color(0xFF64E4BD)
    Row(modifier.clip(RoundedCornerShape(38.dp))
        .background(if (active) accent.copy(alpha = .25f) else Glass)
        // Dispatch to the existing TileService; its repository owns the effect.
        // Native long-press handling opens Lunaris's tile preferences.
        .then(referenceActions(tile, toggleFirst = false))
        .heightIn(min = 76.dp).padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(if (active) accent.copy(alpha = .18f) else Disc, CircleShape),
            contentAlignment = Alignment.Center) {
            ReferenceIcon(R.drawable.hecker_qs_dolby, 29, if (active) accent else White)
        }
        Column(Modifier.weight(1f)) {
            Text(ui.label.ifBlank { context.getString(R.string.hecker_qs_dolby) }, color = Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val subtitle = ui.secondaryLabel.ifBlank { ui.accessibilityUiState.stateDescription }
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = .72f),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReferenceShortcut(title: Int, subtitle: Int, icon: Int, modifier: Modifier, click: () -> Unit) {
    val context = LocalContext.current
    Row(modifier.clip(RoundedCornerShape(38.dp)).background(Glass).clickable(onClick = click)
        .heightIn(min = 76.dp).padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(Disc, CircleShape), contentAlignment = Alignment.Center) { ReferenceIcon(icon, 29) }
        Column(Modifier.weight(1f)) {
            Text(context.getString(title), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != 0) Text(context.getString(subtitle), color = Color.White.copy(alpha = .72f),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
