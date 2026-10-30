/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.UserHandle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import org.hecker.motion.FlowClockRenderer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.android.systemui.plugins.statusbar.StatusBarStateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class DepthPair(val user: Int, val id: String, val back: Bitmap, val front: Bitmap)
internal data class DepthViewport(val origin: Offset = Offset.Zero, val width: Float = 0f, val height: Float = 0f)

/** A single bounded cache is shared by the backdrop and the foreground over the clock. */
internal object HeckerDepthScene {
    var viewport by mutableStateOf(DepthViewport())
    var reveal by mutableFloatStateOf(1f)
    private var cache: DepthPair? = null
    private val loadMutex = Mutex()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    @Composable
    private fun pair(context: Context, state: HeckerAmbientCharging.State): DepthPair? {
        if (!state.depth || !state.customClock || !FlowClockRenderer.supportsDepth(state.clockStyle) ||
                !DepthWallpaperGeometry.validIdentity(state.depthId)) return null
        val id = state.depthId ?: return null
        val image by produceState<DepthPair?>(null, state.user, id, state.wakeGeneration) {
            // Retry a failed pre-unlock load on wake, while retaining successful imports in memory.
            val loaded = withContext(Dispatchers.IO) {
                loadMutex.withLock {
                val old = cache
                if (old != null && old.user == state.user && old.id == id) return@withLock old
                val next = try {
                    val resolver = context.createContextAsUser(UserHandle.of(state.user), 0).contentResolver
                    fun read(layer: String): Bitmap? = resolver.openInputStream(
                        Uri.parse("content://org.hecker.suite.depth/$id/$layer"))?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    val back = read("background")
                    val front = read("foreground")
                    if (back == null || front == null || back.width != front.width || back.height != front.height ||
                            maxOf(back.width, back.height) > 1600) null else DepthPair(state.user, id, back, front)
                } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
                cache = next
                next
                }
            }
            value = loaded
        }
        return image?.takeIf { it.user == state.user && it.id == id }
    }

    private fun draw(canvas: NativeCanvas, image: Bitmap, viewport: DepthViewport,
            reveal: Float, foreground: Boolean, mask: Boolean = false) {
        if (viewport.width <= 0 || viewport.height <= 0) return
        val transform = DepthWallpaperGeometry.crop(image.width, image.height,
            viewport.width, viewport.height, reveal, foreground)
        val rectangle = RectF(transform[1], transform[2], transform[1] + image.width * transform[0],
            transform[2] + image.height * transform[0])
        if (mask) maskPaint.alpha = (255 * FlowClockRenderer.depthMask(reveal)).toInt()
        canvas.drawBitmap(image, null, rectangle, if (mask) maskPaint else imagePaint)
    }

    @Composable
    fun foreground(context: Context, state: HeckerAmbientCharging.State): ((NativeCanvas, Offset) -> Unit)? {
        val image = pair(context, state) ?: return null
        val bounds = viewport
        val fraction = reveal
        return { canvas, origin ->
            canvas.save(); canvas.translate(bounds.origin.x - origin.x, bounds.origin.y - origin.y)
            draw(canvas, image.front, bounds, fraction, true, mask = true)
            canvas.restore()
        }
    }

    @Composable
    fun backdrop(context: Context, controller: StatusBarStateController, modifier: Modifier) {
        DisposableEffect(context) { HeckerAmbientCharging.start(context); onDispose {} }
        val state by HeckerAmbientCharging.state.collectAsState()
        val image = pair(context, state)
        val homeHandoff = LocalHeckerHomeHandoff.current
        val awakeFraction = heckerAwakeFraction(controller)
        // Clock, subject crop, opacity and blur all follow the same reversible native doze frames.
        // Depth opt-out and reduced motion settle immediately; AOD never draws a wallpaper.
        val animated = state.flow && state.depthWake && !state.powerSave && ValueAnimator.areAnimatorsEnabled()
        reveal = if (animated) awakeFraction else 1f
        if (image == null || awakeFraction <= 0f) return
        val fraction = reveal
        val blur = if (animated && awakeFraction < 1f) (1f - awakeFraction) * 18f else 0f
        val effect = remember(blur) { if (blur > .1f) BlurEffect(blur, blur, TileMode.Clamp) else null }
        Canvas(modifier.fillMaxSize().graphicsLayer {
            // Keep the new Home clock covered until the travelling lock digits nearly converge.
            alpha = awakeFraction * (1f - FlowClockRenderer.homeFade(homeHandoff())); renderEffect = effect
        }
            .clipToBounds().onGloballyPositioned {
            viewport = DepthViewport(it.positionInRoot(), it.size.width.toFloat(), it.size.height.toFloat())
        }) {
            val bounds = DepthViewport(Offset.Zero, size.width, size.height)
            drawIntoCanvas { canvas ->
                draw(canvas.nativeCanvas, image.back, bounds, fraction, false)
                draw(canvas.nativeCanvas, image.front, bounds, fraction, true)
            }
        }
    }
}

/** A native, non-interactive background inside the lockscreen scene; it cannot capture gestures. */
@Composable
fun HeckerDepthLockscreenBackdrop(context: Context, controller: StatusBarStateController,
    modifier: Modifier = Modifier) = HeckerDepthScene.backdrop(context, controller, modifier)
