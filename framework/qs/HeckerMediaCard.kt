/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.android.systemui.hecker

import android.media.session.PlaybackState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.sin
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
import androidx.compose.ui.draw.blur
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

/** Compact real-session player. The native host still decides keyguard visibility. */
@Composable
fun HeckerMediaCard(modifier: Modifier = Modifier, square: Boolean = false, hideWhenInactive: Boolean = false,
    nativeFallback: (@Composable () -> Unit)? = null,
    sessionToken: android.media.session.MediaSession.Token? = null, musicOnly: Boolean = false,
    onInactive: (() -> Unit)? = null) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<HeckerQsMedia.Snapshot?>(null) }
    val media = remember(context, sessionToken, musicOnly) {
        HeckerQsMedia(context, { snapshot = it }, musicOnly, sessionToken)
    }
    DisposableEffect(media) { media.start(); onDispose { media.stop() } }
    val state = snapshot
    LaunchedEffect(state?.active) { if (state != null && !state.active) onInactive?.invoke() }
    if (hideWhenInactive && state?.active != true) {
        // Native media resumption can outlive an active session. Keep its real
        // resume action and dismissal behavior when there is no controller.
        if (state != null) nativeFallback?.invoke()
        return
    }
    val ambient by HeckerAmbientCharging.state.collectAsState()
    val motion = heckerFlowMotionAllowed() && ambient.screenAwake
    var position by remember { mutableStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    LaunchedEffect(state, seeking, motion, ambient.screenAwake) {
        if (seeking || !ambient.screenAwake) return@LaunchedEffect
        do {
            position = state?.positionNow() ?: 0
            if (state?.playing != true) break
            delay(1000)
        } while (isActive)
    }
    val art = state?.artwork
    val flow = LocalHeckerFlowGlass.current
    val shape = RoundedCornerShape(if (square) 30.dp else 28.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(if (flow) Color.White.copy(alpha = .10f) else Color(0xCC173C50))
        .border(1.dp, Color.White.copy(alpha = .16f), shape)) {
        if (!flow) Crossfade(art, modifier=Modifier.matchParentSize(), animationSpec = if (motion) tween(320, easing = HeckerFlowEasing) else snap(), label = "mediaBackdrop") { image ->
            if (image != null) Image(image.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(32.dp))
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(if (flow) listOf(Color(0x18FFFFFF), Color(0x08FFFFFF)) else listOf(Color(0x9C213D55), Color(0xDC0A2639)))))
        Column(Modifier.fillMaxWidth().padding(horizontal = if (square) 12.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (square) 8.dp else 4.dp)) {
            if (square) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    MediaArtwork(art, 40)
                    MediaButton(R.drawable.hecker_qs_headphones, R.string.hecker_qs_media_output) { media.output() }
                }
                MediaTitles(state, compact = true)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MediaArtwork(art, 64)
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { MediaTitles(state) }
                            MediaPulse(state?.playing == true, motion)
                        }
                        val duration = state?.duration ?: 0L
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(mediaTime(position), color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
                            Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                                enabled = duration > 0 && state?.supports(PlaybackState.ACTION_SEEK_TO) == true,
                                onValueChange = { seeking = true; position = it.toLong() },
                                onValueChangeFinished = { media.seek(position); seeking = false }, thumb = {},
                                track = { progress -> Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .23f), CircleShape)) {
                                    Box(Modifier.fillMaxWidth((progress.value / duration.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)).height(3.dp).background(Color.White, CircleShape))
                                } }, modifier = Modifier.weight(1f).height(28.dp).semantics { contentDescription = context.getString(R.string.hecker_media_seek) })
                            Text(if (duration > 0) "−${mediaTime((duration - position).coerceAtLeast(0))}" else "", color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                MediaButton(R.drawable.hecker_qs_previous, R.string.hecker_media_previous,
                    state?.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS) == true) { media.previous() }
                MediaButton(if (state?.playing == true) R.drawable.hecker_qs_pause else R.drawable.hecker_qs_play,
                    if (state?.playing == true) R.string.hecker_qs_pause else R.string.hecker_qs_play) { media.playPause() }
                MediaButton(R.drawable.hecker_qs_next, R.string.hecker_media_next,
                    state?.supports(PlaybackState.ACTION_SKIP_TO_NEXT) == true) { media.next() }
                if (!square) MediaButton(R.drawable.hecker_qs_headphones, R.string.hecker_qs_media_output) { media.output() }
            }
        }
    }
}

@Composable
private fun MediaArtwork(art: android.graphics.Bitmap?, size: Int) {
    val ambient by HeckerAmbientCharging.state.collectAsState()
    val motion=heckerFlowMotionAllowed() && ambient.screenAwake
    val arrival=remember { Animatable(1f) }
    LaunchedEffect(art,motion) {
        if(motion){arrival.snapTo(.94f);arrival.animateTo(1f,spring(dampingRatio=.9f,stiffness=430f))}
        else arrival.snapTo(1f)
    }
    Box(Modifier.size(size.dp).graphicsLayer { scaleX=arrival.value;scaleY=arrival.value }
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

@Composable
private fun MediaPulse(playing:Boolean,motion:Boolean) {
    val activity by animateFloatAsState(if(playing&&motion)1f else .35f,
        if(motion)spring(dampingRatio=.9f,stiffness=430f) else snap(),label="mediaPlaybackState")
    Canvas(Modifier.size(18.dp).clearAndSetSemantics { }) {
        repeat(5){i->
            val amplitude=.35f+activity*.65f*(sin(i*1.1f)+1f)/2f
            val x=size.width*(i+1)/6f;val h=size.height*.7f*amplitude
            drawLine(Color.White.copy(alpha=.55f),Offset(x,(size.height-h)/2),Offset(x,(size.height+h)/2),strokeWidth=size.width/12,cap=StrokeCap.Round)
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
