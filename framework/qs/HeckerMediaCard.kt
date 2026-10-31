/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.android.systemui.hecker

import org.hecker.motion.MediaGlowSpec

import android.media.session.PlaybackState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.os.SystemClock
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.res.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** One real-session renderer for keyguard, QQS and the two expanded dashboards. */
@Composable
fun HeckerMediaCard(modifier: Modifier = Modifier, square: Boolean = false, hideWhenInactive: Boolean = false,
    nativeFallback: (@Composable () -> Unit)? = null,
    sessionToken: android.media.session.MediaSession.Token? = null, musicOnly: Boolean = false,
    onInactive: (() -> Unit)? = null, compactRow: Boolean = false,
    squareWeight: () -> Float = { if (square) 1f else 0f },
    rowWeight: () -> Float = { if (compactRow) 1f else 0f }) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<HeckerQsMedia.Snapshot?>(null) }
    val media = remember(context, sessionToken, musicOnly) {
        HeckerQsMedia(context, { snapshot = it }, musicOnly, sessionToken)
    }
    DisposableEffect(media) { media.start(); onDispose { media.stop() } }
    val state = snapshot
    LaunchedEffect(state?.active) { if (state != null && !state.active) onInactive?.invoke() }
    if (hideWhenInactive && state?.active != true) {
        if (state != null) nativeFallback?.invoke()
        return
    }
    val ambient by HeckerAmbientCharging.state.collectAsState()
    val motion = heckerFlowMotionAllowed() && ambient.screenAwake
    var position by remember { mutableStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    LaunchedEffect(state, seeking, ambient.screenAwake) {
        if (seeking || !ambient.screenAwake) return@LaunchedEffect
        do {
            position = state?.positionNow() ?: 0
            if (state?.playing != true) break
            delay(1000)
        } while (isActive)
    }
    val art = state?.artwork
    val flow = LocalHeckerFlowGlass.current
    val shape = RoundedCornerShape(28.dp)
    val fontScale = LocalConfiguration.current.fontScale.coerceIn(1f, 2f)
    val preferredHeight = (if (compactRow) 80f + (fontScale - 1) * 32
        else if (square) 178f + (fontScale - 1) * 64 else 164f + (fontScale - 1) * 48).dp
    Box(modifier.fillMaxWidth().clip(shape)
        .background(if (flow) Color(0xB3191923) else Color(0xE0141721))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .24f),
            Color(0xFFB9CFFF).copy(alpha = .32f))), shape)) {
        // Fixed-cost radial shaders provide the soft light; the text and icons are never blurred.
        HeckerMediaGlow(state?.playing == true, motion, Modifier.matchParentSize())
        Layout(modifier = Modifier.fillMaxWidth().heightIn(min = preferredHeight), content = {
            MediaArtwork(art)
            MediaTitles(state)
            val duration = state?.duration ?: 0L
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(mediaTime(position), color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    enabled = !square && !compactRow && duration > 0 && state?.supports(PlaybackState.ACTION_SEEK_TO) == true,
                    onValueChange = { seeking = true; position = it.toLong() },
                    onValueChangeFinished = { media.seek(position); seeking = false }, thumb = {},
                    track = { progress -> Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .23f), CircleShape)) {
                        Box(Modifier.fillMaxWidth((progress.value / duration.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f))
                            .height(3.dp).background(Color(0xFFD1F7FF), CircleShape))
                    } }, modifier = Modifier.weight(1f).height(28.dp)
                        .semantics { contentDescription = context.getString(R.string.hecker_media_seek) })
                Text(if (duration > 0) "−${mediaTime((duration - position).coerceAtLeast(0))}" else "",
                    color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
            }
            MediaButton(R.drawable.hecker_qs_previous, R.string.hecker_media_previous,
                !compactRow && state?.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS) == true) { media.previous() }
            MediaButton(if (state?.playing == true) R.drawable.hecker_qs_pause else R.drawable.hecker_qs_play,
                if (state?.playing == true) R.string.hecker_qs_pause else R.string.hecker_qs_play) { media.playPause() }
            MediaButton(R.drawable.hecker_qs_next, R.string.hecker_media_next,
                !compactRow && state?.supports(PlaybackState.ACTION_SKIP_TO_NEXT) == true) { media.next() }
            MediaButton(R.drawable.hecker_qs_headphones, R.string.hecker_qs_media_output) { media.output() }
        }) { children, constraints ->
            val width = constraints.maxWidth
            val height = constraints.constrainHeight(preferredHeight.roundToPx())
            // Shared values are read during measurement, after both native scene targets exist.
            val tile = squareWeight(); val row = rowWeight()
            val bounds = (0 until MediaCardGeometry.COUNT).map { index ->
                MediaCardGeometry.bounds(index, width / density, height / density, tile, row, fontScale)
            }
            val items = children.mapIndexed { index, child ->
                val b = bounds[index]
                child.measure(Constraints.fixed((b[2] * density).roundToInt().coerceAtLeast(1),
                    (b[3] * density).roundToInt().coerceAtLeast(1)))
            }
            layout(width, height) {
                items.forEachIndexed { index, item ->
                    val b = bounds[index]
                    // Alpha-zero children are not placed, so hidden transport/seek targets cannot intercept input.
                    if (b[4] > .001f) item.placeRelativeWithLayer((b[0] * density).roundToInt(),
                        (b[1] * density).roundToInt()) { alpha = b[4] }
                }
            }
        }
    }
}

/** The reference's drifting blue/lavender under-light, independent of metadata/seek updates. */
@Composable
private fun HeckerMediaGlow(playing: Boolean, motion: Boolean, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val frame = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val strength by animateFloatAsState(if (playing) 1f else .42f,
        if (motion) tween(500, easing = HeckerFlowEasing) else snap(), label = "mediaGlowActivity")
    LaunchedEffect(playing, motion, lifecycle) {
        if (!playing || !motion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) withFrameNanos { frame.longValue = SystemClock.elapsedRealtime() }
        }
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val phase = MediaGlowSpec.phase(frame.longValue)
        val activity = strength
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(MediaGlowSpec.UNDERLAY))))
        repeat(MediaGlowSpec.COUNT) { index ->
            val tint = Color(MediaGlowSpec.color(index))
            val center = Offset(size.width * MediaGlowSpec.centerX(index, phase),
                size.height * MediaGlowSpec.centerY(index, phase))
            val radius = size.width * MediaGlowSpec.radius(index, phase)
            drawRect(Brush.radialGradient(listOf(tint.copy(alpha = MediaGlowSpec.centerAlpha(index) * activity),
                tint.copy(alpha = .10f * activity), Color.Transparent), center, radius.coerceAtLeast(1f)))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(MediaGlowSpec.RIM).copy(alpha = .10f * activity))))
    }
}

@Composable
private fun MediaArtwork(art: android.graphics.Bitmap?) {
    val ambient by HeckerAmbientCharging.state.collectAsState()
    val motion=heckerFlowMotionAllowed() && ambient.screenAwake
    val arrival=remember { Animatable(1f) }
    LaunchedEffect(art,motion) {
        if(motion){arrival.snapTo(.94f);arrival.animateTo(1f,spring(dampingRatio=.9f,stiffness=430f))}
        else arrival.snapTo(1f)
    }
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX=arrival.value;scaleY=arrival.value }
        .clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha=.14f)), contentAlignment=Alignment.Center) {
        Crossfade(art,animationSpec=if(motion)tween(320,easing=HeckerFlowEasing) else snap(),label="mediaArtwork") { image ->
            if(image!=null) Image(image.asImageBitmap(),null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){ReferenceIcon(R.drawable.hecker_qs_music,24)}
        }
    }
}

@Composable
private fun MediaTitles(state: HeckerQsMedia.Snapshot?, compact: Boolean = false) {
    val context=LocalContext.current
    val ambient by HeckerAmbientCharging.state.collectAsState()
    val motion=heckerFlowMotionAllowed()&&ambient.screenAwake
    val caption=Pair(state?.title?:context.getString(R.string.hecker_qs_play_last),state?.artist.orEmpty())
    // Metadata is the target, so the playback-position clock never restarts a title animation.
    Crossfade(caption,animationSpec=if(motion)tween(280,easing=HeckerFlowEasing) else snap(),label="mediaTitles") { value ->
        Column {
            Text(value.first,color=Color.White,fontSize=if(compact)12.sp else 14.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(value.second.isNotBlank())Text(value.second,color=Color.White.copy(alpha=.7f),fontSize=if(compact)10.sp else 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

private fun mediaTime(value: Long): String = "%d:%02d".format(value / 60000, value / 1000 % 60)

@Composable
private fun MediaButton(icon: Int, label: Int, enabled: Boolean = true, click: () -> Unit) {
    val context = LocalContext.current
    val interactions=remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val ambient by HeckerAmbientCharging.state.collectAsState()
    val motion=heckerFlowMotionAllowed()&&ambient.screenAwake
    val scale by animateFloatAsState(if(pressed&&motion).90f else 1f,spring(dampingRatio=.86f,stiffness=650f),label="mediaPress")
    Box(Modifier.size(44.dp).graphicsLayer { scaleX=scale;scaleY=scale }.clip(CircleShape).semantics { contentDescription = context.getString(label); role = Role.Button }
        .clickable(enabled = enabled, interactionSource=interactions, indication=androidx.compose.foundation.LocalIndication.current, onClick = click), contentAlignment = Alignment.Center) {
        Crossfade(icon,animationSpec=if(motion)tween(180,easing=HeckerFlowEasing) else snap(),label="mediaTransport") { value ->
            ReferenceIcon(value,24,Color.White.copy(alpha=if(enabled)1f else .3f))
        }
    }
}
