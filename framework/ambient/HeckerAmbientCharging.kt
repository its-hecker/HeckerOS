/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** Native doze presentation state. No activity, screen-on flag or application overlay is used. */
object HeckerAmbientCharging {
    const val ROUTE = "hecker_charging_on_ambient"
    const val AOD_ROUTE = "hecker_charging_on_aod"
    const val STYLE = "hecker_aod_charging_style"
    const val CLOCK = "hecker_custom_lockscreen_clock"
    const val ANIMATION = "hecker_charging_animation"
    const val ART = "hecker_charging_animation_style"
    data class State(
        val ambient: Boolean = true, val style: Int = 1, val customClock: Boolean = false,
        val animation: Boolean = true, val art: Int = 0, val plugged: Boolean = false,
        val level: Float = Float.NaN, val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN,
        val temperature: Float = Float.NaN, val voltage: Float = Float.NaN,
        val watts: Double = Double.NaN, val remaining: Long = -1,
        val introStart: Long = -1, val introPending: Boolean = false,
        val current: Int = Int.MIN_VALUE, val connectedAt: Long = -1,
    )
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = AmbientChargingPolicy()
    private var app: Context? = null
    private var pulse: Runnable? = null
    private var reconcile: Runnable? = null
    private var indication: Runnable? = null
    private var displayState = Display.STATE_UNKNOWN
    private var reading: Job? = null

    @JvmStatic fun start(context: Context) {
        if (app != null) return
        app = context.applicationContext
        val c = app ?: return
        readSettings()
        val observer = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) { readSettings(); reconcileDoze() }
        }
        for (key in arrayOf(ROUTE, AOD_ROUTE, "doze_always_on", STYLE, CLOCK, ANIMATION, ART)) {
            c.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false,
                observer, UserHandle.USER_ALL)
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_SWITCHED)
        }
        c.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> battery(intent)
                    Intent.ACTION_SCREEN_ON -> if (interactive(c)) cancelIntro()
                    Intent.ACTION_USER_SWITCHED -> { cancelIntro(); readSettings(); reconcileDoze() }
                }
            }
        }, filter, null, main, Context.RECEIVER_EXPORTED)
        io.launch {
            state.map { ownsIndication(it) }.distinctUntilChanged().collect { indication?.run() }
        }
    }
    private fun interactive(c: Context) = c.getSystemService(PowerManager::class.java)?.isInteractive != false
    private fun readSettings() {
        val c = app ?: return
        fun value(key: String, default: Int) = Settings.Secure.getIntForUser(c.contentResolver,
            key, default, UserHandle.USER_CURRENT)
        val previous = mutable.value
        val defaultId = c.resources.getIdentifier("config_dozeAlwaysOnEnabled", "bool", "android")
        val defaultAod = if (defaultId != 0 && c.resources.getBoolean(defaultId)) 1 else 0
        val route = AmbientChargingPolicy.route(value("doze_always_on", defaultAod) != 0,
            value(AOD_ROUTE, 1) != 0, value(ROUTE, 1) != 0)
        val next = previous.copy(ambient = route,
            style = value(STYLE, 1).coerceIn(0, 2), customClock = value(CLOCK, 0) != 0,
            animation = value(ANIMATION, 1) != 0, art = value(ART, 0).coerceIn(0, 2))
        mutable.value = next
        if (!next.ambient || !next.animation) cancelIntro()
        if (next.ambient && next.plugged && !interactive(c) &&
                (!previous.ambient || previous.style == 0 && next.style > 0)) requestPulse()
    }
    private fun battery(intent: Intent) {
        val c = app ?: return
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val now = SystemClock.elapsedRealtime()
        val current = mutable.value
        val animate = current.ambient && current.animation && ValueAnimator.areAnimatorsEnabled()
        val edge = session.battery(plugged, interactive(c), animate, now)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        mutable.value = current.copy(plugged = plugged,
            connectedAt = if (!plugged) -1 else if (!current.plugged) now else current.connectedAt,
            level = AmbientChargingPolicy.percent(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)),
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
            temperature = if (temperature == Int.MIN_VALUE) Float.NaN else temperature / 10f,
            voltage = if (voltage > 0) voltage / 1000f else Float.NaN,
            introStart = session.startedAt(), introPending = session.pending(),
            watts = if (plugged) current.watts else Double.NaN,
            remaining = if (plugged) current.remaining else -1)
        reading?.cancel()
        // Battery service queries stay off the UI thread; updates are driven by real broadcasts.
        reading = io.launch {
            val telemetry = withContext(Dispatchers.IO) {
                try {
                    val manager = c.getSystemService(BatteryManager::class.java)
                    val amps = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
                    Triple(AmbientChargingPolicy.watts(amps, voltage), manager?.computeChargeTimeRemaining() ?: -1L, amps)
                } catch (_: RuntimeException) { Triple(Double.NaN, -1L, Int.MIN_VALUE) }
            }
            mutable.value = mutable.value.copy(watts = if (plugged) telemetry.first else Double.NaN,
                remaining = if (plugged) telemetry.second else -1,
                current = if (plugged) telemetry.third else Int.MIN_VALUE)
        }
        reconcileDoze()
        if (edge && current.ambient && (current.animation || current.style > 0)) {
            if (displayState == Display.STATE_ON) onScreenState(displayState)
            requestPulse()
            main.postDelayed({
                if (session.expirePending(SystemClock.elapsedRealtime())) {
                    mutable.value = mutable.value.copy(introPending = false)
                }
            }, 10001)
        }
    }
    private fun cancelIntro() {
        session.cancel()
        mutable.value = mutable.value.copy(introStart = -1, introPending = false)
    }
    @JvmStatic fun attachDoze(context: Context, request: Runnable, update: Runnable) {
        start(context); pulse = request; reconcile = update
        reconcileDoze()
        if (session.pending()) requestPulse()
    }
    @JvmStatic fun detachDoze() {
        pulse = null; reconcile = null; displayState = Display.STATE_UNKNOWN; cancelIntro()
    }
    private fun requestPulse() { main.post { pulse?.run() } }
    private fun reconcileDoze() { main.post { reconcile?.run() } }
    @JvmStatic fun onScreenState(displayState: Int) {
        this.displayState = displayState
        // A real native doze pulse makes the panel refreshable. Start the intro only then.
        if (displayState == Display.STATE_ON && app?.let { !interactive(it) } == true &&
                session.visible(SystemClock.elapsedRealtime())) {
            mutable.value = mutable.value.copy(introStart = session.startedAt(), introPending = false)
        }
    }
    @JvmStatic fun finishIntro() { if (!session.pending()) cancelIntro() }
    @JvmStatic fun isChargingAmbient(): Boolean = mutable.value.let {
        it.ambient && it.plugged && it.style > 0
    }
    private fun ownsIndication(s: State): Boolean = s.ambient && s.plugged &&
        (s.style > 0 || s.introPending || s.introStart >= 0)
    @JvmStatic fun ownsChargingIndication(): Boolean = ownsIndication(mutable.value)
    @JvmStatic fun attachIndication(context: Context, refresh: Runnable) {
        start(context); indication = refresh; main.post { indication?.run() }
    }
    @JvmStatic fun detachIndication() { indication = null }
    @JvmStatic fun suppressRipple(context: Context): Boolean {
        start(context)
        return mutable.value.ambient && !interactive(context)
    }
}
