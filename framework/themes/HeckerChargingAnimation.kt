/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.content.res.ThemeEngine
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.WindowManager
import java.util.WeakHashMap
import kotlin.math.min

/** Uses Lunaris's named frame and Moto charging resources inside the native trusted window. */
object HeckerChargingAnimation {
    private val active = WeakHashMap<WindowManager, ChargingView>()
    @JvmStatic fun show(context: Context, manager: WindowManager, parameters: WindowManager.LayoutParams, level: Int): Boolean {
        val res = HeckerThemeAssets.resources(context, "charging_animation") ?: return false
        val pkg = ThemeEngine.getInstance(context)?.getEnabledPackage("charging_animation") ?: return false
        if (!ValueAnimator.areAnimatorsEnabled()) return true
        if (active.containsKey(manager)) return true
        return try {
            val view = ChargingView(context, res, pkg, level)
            val params = WindowManager.LayoutParams().apply { copyFrom(parameters); packageName = context.opPackageName }
            active[manager] = view
            manager.addView(view, params)
            view.start { remove(manager) }
            true
        } catch (_: Exception) { remove(manager); false }
    }
    private fun remove(manager: WindowManager) {
        active.remove(manager)?.let { view ->
            view.stop()
            try { manager.removeView(view) } catch (_: IllegalArgumentException) { }
        }
    }
    @JvmStatic fun cancel() { active.keys.toList().forEach { remove(it) } }

    private class ChargingView(context: Context, val res: Resources, val pkg: String, val level: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var fraction = 0f
        private var animator: ValueAnimator? = null
        private val frames: List<Drawable>
        private val background: Drawable?
        private val bolt: Drawable?
        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            val id = res.getIdentifier("config_chargingAnimFrames", "array", pkg)
            frames = if (id == 0) emptyList() else res.obtainTypedArray(id).let { array ->
                try { (0 until min(array.length(), 180)).mapNotNull { i -> array.getDrawable(i) } }
                finally { array.recycle() }
            }
            background = drawable("charging_circle_bg")
            bolt = drawable("charging_circle_bolt")
            require(frames.isNotEmpty() || background != null)
        }
        private fun drawable(name: String): Drawable? {
            val id = res.getIdentifier(name, "drawable", pkg)
            return if (id == 0) null else res.getDrawable(id, null)?.mutate()
        }
        fun start(done: () -> Unit) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2200
                addUpdateListener { fraction = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) = done() })
                start()
            }
        }
        fun stop() { animator?.removeAllListeners(); animator?.cancel(); animator = null }
        override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            val size = (min(width, height) * .68f).toInt()
            val bounds = Rect((width-size)/2, (height-size)/2, (width+size)/2, (height+size)/2)
            val alpha = (255 * min(1f, min(fraction * 8, (1-fraction) * 8))).toInt().coerceIn(0,255)
            if (frames.isNotEmpty()) {
                val frame = frames[(fraction * frames.size).toInt().coerceIn(0, frames.size-1)]
                frame.bounds = bounds; frame.alpha = alpha; frame.draw(canvas)
            } else {
                background?.apply { this.bounds = bounds; this.alpha = alpha; draw(canvas) }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .025f
                paint.color = if (level <= 15) 0xffff453a.toInt() else 0xff1a73e8.toInt(); paint.alpha = alpha
                val inset = size * .08f
                canvas.drawArc(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset,
                    -90f, 360f * level / 100f, false, paint)
                bolt?.apply { this.bounds = Rect(width/2-size/12, height/2-size/6, width/2+size/12, height/2); this.alpha = alpha; draw(canvas) }
            }
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.alpha = alpha
            paint.textAlign = Paint.Align.CENTER; paint.textSize = size * .13f
            canvas.drawText("$level%", width/2f, height/2f + size*.17f, paint)
        }
    }
}
