/*
 * Copyright (C) 2026 Project Infinity X
 * Copyright (C) 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Three-line synchronized presentation adapted from Infinity's LockscreenLyricsView.
 */
package com.android.systemui.hecker

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HeckerLockscreenLyrics(context: Context,
    statusBarState: com.android.systemui.plugins.statusbar.StatusBarStateController,
    modifier: Modifier = Modifier) {
    var dozing by remember(statusBarState) { mutableStateOf(statusBarState.isDozing) }
    var keyguard by remember(statusBarState) { mutableStateOf(statusBarState.state != 0) }
    DisposableEffect(statusBarState) {
        val callback = object : com.android.systemui.plugins.statusbar.StatusBarStateController.StateListener {
            override fun onDozingChanged(value: Boolean) { dozing = value }
            override fun onStateChanged(value: Int) { keyguard = value != 0 }
        }
        statusBarState.addCallback(callback)
        onDispose { statusBarState.removeCallback(callback) }
    }
    var snapshot by remember { mutableStateOf<LyricsController.Snapshot?>(null) }
    var active by remember { mutableStateOf(-1) }
    var subscription by remember { mutableStateOf<LyricsController?>(null) }
    DisposableEffect(context) {
        val controller = LyricsController(context, LyricsController.LOCKSCREEN, keyguard) { snapshot = it }
        subscription = controller
        onDispose { controller.close() }
    }
    LaunchedEffect(subscription, keyguard) { subscription?.setVisible(keyguard) }
    LaunchedEffect(snapshot, dozing) {
        val state = snapshot ?: return@LaunchedEffect
        do {
            active = state.activeIndex(SystemClock.elapsedRealtime())
            if (!state.playing) break
            delay(if (dozing) 1000 else 100)
        } while (isActive)
    }
    val state = snapshot ?: return
    if (!keyguard) return
    if (active !in state.lines.indices) return
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (relative in if (dozing) 0..0 else -1..1) {
            val line = state.lines.getOrNull(active + relative)?.text.orEmpty()
            Text(
                text = line,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (relative == 0) (if (dozing) .72f else 1f) else 0.45f),
                fontSize = if (dozing) 14.sp else if (relative == 0) 16.sp else 12.sp,
                fontWeight = if (relative == 0) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = if (relative == 0) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
