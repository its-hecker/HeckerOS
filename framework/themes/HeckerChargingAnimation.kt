/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.provider.Settings
import android.os.UserHandle
import android.view.View
import android.view.WindowManager
import java.util.WeakHashMap
import kotlin.math.*

/** Original energy orbit, rendered in the native trusted charging window. */
object HeckerChargingAnimation {
    private val active = WeakHashMap<WindowManager, ChargingView>()
    @JvmStatic fun show(context: Context, manager: WindowManager, parameters: WindowManager.LayoutParams, level: Int): Boolean {
        if (Settings.Secure.getIntForUser(context.contentResolver,
                "hecker_charging_animation", 1, UserHandle.USER_CURRENT) == 0) return false
        if (!ValueAnimator.areAnimatorsEnabled()) return true
        if (active.containsKey(manager)) return true
        return try {
            val view = ChargingView(context, level.coerceIn(0, 100))
            val params = WindowManager.LayoutParams().apply {
                copyFrom(parameters); packageName = context.opPackageName
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
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

    private class ChargingView(context: Context, val level: Int) : View(context) {
        private val renderer = HeckerAmbientRenderer(context)
        private val art = Settings.Secure.getIntForUser(context.contentResolver,
            "hecker_charging_animation_style", 0, UserHandle.USER_CURRENT)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var fraction = 0f
        private var animator: ValueAnimator? = null
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        fun start(done: () -> Unit) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1800
                addUpdateListener { fraction = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) = done() })
                start()
            }
        }
        fun stop() { animator?.removeAllListeners(); animator?.cancel(); animator = null }
        override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            val radius = min(width, height) * .22f
            if (radius <= 0f) return
            if (art == 1) {
                val density = resources.displayMetrics.density
                canvas.save(); canvas.scale(density, density)
                renderer.intro(canvas, 1, level.toFloat(),
                    context.getString(com.android.systemui.res.R.string.hecker_charge_charging),
                    width / (2f * density), height / (2f * density), radius / density, fraction)
                canvas.restore()
                return
            }
            val opacity = min(1f, min(fraction * 7f, (1f - fraction) * 6f)).coerceIn(0f, 1f)
            val cx = width / 2f; val cy = height / 2f
            canvas.save(); canvas.translate(cx, cy)
            val scale = .88f + .12f * min(1f, fraction * 6f)
            canvas.scale(scale, scale)
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(0f, 0f, radius * 1.55f,
                intArrayOf(0x330fbcff, 0x227e59ff, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            paint.alpha = (255 * opacity).toInt(); canvas.drawCircle(0f, 0f, radius * 1.55f, paint)
            paint.shader = null; paint.color = 0xe610192b.toInt(); paint.alpha = (225 * opacity).toInt()
            canvas.drawCircle(0f, 0f, radius * .92f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = radius * .035f
            paint.shader = SweepGradient(0f, 0f, intArrayOf(0xff49dfff.toInt(), 0xffa78bfa.toInt(), 0xff49dfff.toInt()), null)
            paint.alpha = (255 * opacity).toInt()
            canvas.save(); canvas.rotate(fraction * 210f)
            canvas.drawArc(-radius, -radius, radius, radius, 12f, 286f, false, paint)
            canvas.restore(); paint.shader = null
            paint.color = 0xffad9aff.toInt(); paint.strokeWidth = radius * .012f
            canvas.drawArc(-radius*1.13f, -radius*1.13f, radius*1.13f, radius*1.13f,
                -fraction*160f, 130f, false, paint)
            repeat(8) { i ->
                val a = (i * PI / 4 + fraction * PI).toFloat()
                paint.style = Paint.Style.FILL; paint.color = if (i % 2 == 0) 0xff49dfff.toInt() else 0xffad9aff.toInt()
                canvas.drawCircle(cos(a)*radius*1.2f, sin(a)*radius*1.2f, radius*.018f, paint)
            }
            paint.style = Paint.Style.STROKE; paint.color = Color.WHITE
            paint.strokeWidth = radius * .055f; paint.strokeJoin = Paint.Join.ROUND
            val bolt = Path().apply {
                moveTo(radius*.08f,-radius*.47f); lineTo(-radius*.13f,-radius*.11f)
                lineTo(radius*.12f,-radius*.11f); lineTo(-radius*.07f,radius*.16f)
            }
            canvas.drawPath(bolt, paint)
            paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            paint.textSize = radius * .29f
            canvas.drawText("$level%", 0f, radius*.53f, paint)
            canvas.restore()
        }
    }
}
