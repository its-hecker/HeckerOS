/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: GPL-3.0-or-later */
package com.drdisagree.xmorphclock.nativehost

import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.text.format.DateFormat
import android.view.View
import android.widget.FrameLayout
import com.drdisagree.xmorphclock.engine.ClockFaceView
import com.drdisagree.xmorphclock.domain.model.FaceData
import java.util.concurrent.Executors

/** The same packaged renderer as SystemUI, with a launcher-owned selection and lifecycle. */
class XMorphHomeView(context: Context) : FrameLayout(context) {
    companion object { private val io = Executors.newSingleThreadExecutor() }
    private val main = Handler(Looper.getMainLooper())
    private var clock: ClockFaceView? = null
    private var requested: String? = null
    private var generation = 0
    private var motion = true
    private var awake = true
    private var battery: Int? = null
    private var charging = false
    private var attached = false
    private var backdrop: Bitmap? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> awake = false
                Intent.ACTION_SCREEN_ON -> awake = true
                Intent.ACTION_WALLPAPER_CHANGED -> load()
                Intent.ACTION_BATTERY_CHANGED -> {
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    battery = if (scale > 0 && level >= 0) (level * 100 / scale).coerceIn(0, 100) else null
                    charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                }
            }
            update()
        }
    }
    fun setPreset(id: String) { if (requested != id) { requested = id; if (attached) load() } }
    fun setMotionEnabled(value: Boolean) { motion = value; update() }
    private fun update() {
        val power = context.getSystemService(PowerManager::class.java)
        clock?.animationsEnabled = attached && awake && isShown && windowVisibility == View.VISIBLE && motion &&
            android.animation.ValueAnimator.areAnimatorsEnabled() && !power.isPowerSaveMode
        clock?.data = FaceData(use24Hour = DateFormat.is24HourFormat(context), batteryLevel = battery, charging = charging)
    }
    private fun load() {
        val token = ++generation; val id = requested
        io.execute {
            try {
                val entry = XMorphNative.selected(context, id)
                val wall = XMorphWallpaper.loadHome(context, Process.myUid() / 100000)
                main.post {
                    if (!attached || token != generation) return@post
                    backdrop = wall
                    val child = clock ?: ClockFaceView(context, entry.face).also {
                        clock = it; it.isClickable = false; it.isLongClickable = false
                        addView(it, LayoutParams(-1, -2))
                    }
                    child.face = entry.face; child.setLargeProgress(1f)
                    child.setSleepProgress(0f); child.setAodProgress(0f); child.setBackdrop(wall)
                    update(); requestLayout()
                }
            } catch (failure: Exception) { android.util.Log.w("HeckerXMorph", "Home clock unavailable", failure) }
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true
        awake = context.getSystemService(PowerManager::class.java).isInteractive
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED); addAction(Intent.ACTION_WALLPAPER_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED); load()
    }
    override fun onDetachedFromWindow() {
        attached = false; generation++; context.unregisterReceiver(receiver)
        clock?.animationsEnabled = false; clock?.setBackdrop(null); backdrop = null
        super.onDetachedFromWindow()
    }
    override fun onVisibilityAggregated(visible: Boolean) { super.onVisibilityAggregated(visible); update() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (attached) update() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec); val height = MeasureSpec.getSize(heightMeasureSpec)
        clock?.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(width, height)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        clock?.let { child ->
            val fit = if (child.measuredHeight > 0) minOf(1f, height.toFloat() / child.measuredHeight) else 1f
            child.pivotX = width / 2f; child.pivotY = 0f; child.scaleX = fit; child.scaleY = fit
            child.layout(0, ((height - child.measuredHeight * fit) / 2).toInt(), width,
                ((height - child.measuredHeight * fit) / 2).toInt() + child.measuredHeight)
        }
    }
}
