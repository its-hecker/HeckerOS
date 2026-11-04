/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.animateElementFloatAsState
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.scene.shared.model.Scenes

/** A host is provided by both native QS paths, so scene progress owns the player bounds. */
val LocalHeckerQsContentScope = staticCompositionLocalOf<ContentScope?> { null }
private val SquareWeight = ValueKey("HeckerMediaSquareWeight")
private val RowWeight = ValueKey("HeckerMediaRowWeight")

/** Shared across QQS/full-QS copies; a fresh playback event restores a dismissed card. */
object HeckerMediaVisibility {
    private var user by mutableIntStateOf(-1)
    private var hidden by mutableStateOf(false)
    private var token: android.media.session.MediaSession.Token? = null
    private var updated = 0L
    private var identity: String? = null
    private var stopped = false
    fun observe(snapshot: HeckerQsMedia.Snapshot?) {
        val current = android.app.ActivityManager.getCurrentUser()
        if (user != current) { user = current; hidden = false; token = null; stopped = false }
        if (!hidden || snapshot == null) return
        if (!snapshot.playing) stopped = true
        if (snapshot.playing && (stopped || snapshot.token != token || snapshot.updated > updated || snapshot.identity != identity)) hidden = false
    }
    fun dismiss(snapshot: HeckerQsMedia.Snapshot?) {
        user = android.app.ActivityManager.getCurrentUser(); token = snapshot?.token
        identity = snapshot?.identity; updated = snapshot?.updated ?: 0L; stopped = snapshot?.playing != true; hidden = true
    }
    fun isHidden(): Boolean = hidden && user == android.app.ActivityManager.getCurrentUser()
}

/** Legacy QS closes outside its inner QQS/QS scene. Follow its native panel callbacks as well. */
object HeckerQsPanelState {
    private var legacy by mutableStateOf(false)
    private var shown by mutableStateOf(false)
    private var nativeExpansion by mutableFloatStateOf(0f)
    @JvmStatic fun setVisible(visible: Boolean) { legacy = true; shown = visible }
    @JvmStatic fun setExpansion(fraction: Float) { legacy = true; nativeExpansion = fraction }
    fun alpha(scope: ContentScope?): Float {
        val state = scope?.layoutState?.transitionState
        val transition = state as? TransitionState.Transition
        return MediaCardGeometry.shadeAlpha(legacy, shown, nativeExpansion,
            state is TransitionState.Idle && state.currentScene == Scenes.Gone,
            transition?.toContent == Scenes.Gone, transition?.fromContent == Scenes.Gone,
            transition?.progress ?: 0f)
    }
}

@Composable
fun HeckerQsMediaPlayer(modifier: Modifier = Modifier, square: Boolean = false,
    compactRow: Boolean = false, hideWhenInactive: Boolean = false, quickQs: Boolean = false) {
    if (quickQs && HeckerMediaVisibility.isHidden()) {
        val context = androidx.compose.ui.platform.LocalContext.current
        // Dismissal removes the compact layout, while this small subscription watches for resume.
        DisposableEffect(context) {
            val media = HeckerQsMedia(context, { HeckerMediaVisibility.observe(it) })
            media.start(); onDispose { media.stop() }
        }
        return
    }
    val scope = LocalHeckerQsContentScope.current
    if (scope == null) {
        HeckerMediaCard(modifier.graphicsLayer { alpha = HeckerQsPanelState.alpha(null) },
            square, hideWhenInactive, compactRow = compactRow,
            visible = { HeckerQsPanelState.alpha(null) > 0f }, dismissible = true)
        return
    }
    with(scope) {
        ElementWithValues(HeckerQsScene.Media, modifier) {
            val tile = animateElementFloatAsState(if (square) 1f else 0f, SquareWeight, canOverflow = false)
            val row = animateElementFloatAsState(if (compactRow) 1f else 0f, RowWeight, canOverflow = false)
            content {
                HeckerMediaCard(modifier = Modifier.drawWithContent {
                    if (HeckerQsPanelState.alpha(scope) > .001f) drawContent()
                }.graphicsLayer {
                    alpha = HeckerQsPanelState.alpha(scope)
                }, square = square, compactRow = compactRow,
                    hideWhenInactive = hideWhenInactive,
                    squareWeight = { tile.value }, rowWeight = { row.value },
                    visible = { HeckerQsPanelState.alpha(scope) > 0f }, dismissible = true)
            }
        }
    }
}

/** Controls fade independently; no fading ancestor is allowed to dim the shared media surface. */
@Composable
fun heckerQsElement(key: ElementKey): Modifier {
    val scope = LocalHeckerQsContentScope.current ?: return Modifier
    return with(scope) { Modifier.element(key) }
}

/** Included in the actual QQS grid in scene-container and compose-fragment configurations. */
@Composable
fun ContentScope.HeckerQuickQsContent(modifier: Modifier = Modifier,
    tiles: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHeckerQsContentScope provides this) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.element(HeckerQsScene.Compact)) { tiles() }
            CompositionLocalProvider(LocalHeckerFlowGlass provides heckerCustomizedLayout()) {
                // Reserve the same immediate player target as expanded QS, including play-last.
                HeckerQsMediaPlayer(quickQs = true)
            }
        }
    }
}
