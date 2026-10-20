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
fun HeckerLockscreenLyrics(context: Context) {
    var snapshot by remember { mutableStateOf<LyricsController.Snapshot?>(null) }
    var active by remember { mutableStateOf(-1) }
    DisposableEffect(context) {
        val controller = LyricsController(context) { snapshot = it }
        onDispose { controller.close() }
    }
    LaunchedEffect(snapshot) {
        val state = snapshot ?: return@LaunchedEffect
        do {
            active = state.activeIndex(SystemClock.elapsedRealtime())
            if (!state.playing) break
            delay(100)
        } while (isActive)
    }
    val state = snapshot ?: return
    if (active !in state.lines.indices) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (relative in -1..1) {
            val line = state.lines.getOrNull(active + relative)?.text.orEmpty()
            Text(
                text = line,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (relative == 0) 1f else 0.45f),
                fontSize = if (relative == 0) 17.sp else 13.sp,
                fontWeight = if (relative == 0) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
