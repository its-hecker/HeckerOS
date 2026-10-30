/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import androidx.compose.runtime.*
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.scene.shared.model.Scenes
import org.hecker.motion.FlowClockRenderer

val LocalHeckerHomeHandoff = staticCompositionLocalOf<() -> Float> { { 0f } }

/** Read native dismissal progress at draw time; cancellation naturally returns the same geometry. */
@Composable
fun HeckerClockHandoff(layout: SceneTransitionLayoutState, content: @Composable () -> Unit) {
    val motion = heckerFlowMotionAllowed()
    val progress = remember(layout, motion) {
        {
            val state = layout.transitionState
            if (!motion || state !is TransitionState.Transition) 0f
            else if (state.fromContent == Scenes.Lockscreen && state.toContent == Scenes.Gone)
                FlowClockRenderer.unit(state.progress)
            else if (state.fromContent == Scenes.Gone && state.toContent == Scenes.Lockscreen)
                1f - FlowClockRenderer.unit(state.progress)
            else 0f
        }
    }
    CompositionLocalProvider(LocalHeckerHomeHandoff provides progress, content = content)
}
