/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Observe the saved choice directly; rendering never waits for a theme-service Binder call. */
object HeckerThemeAssets {
    private const val KEY = "theme_engine_data"
    private val packages = ConcurrentHashMap<String, Resources>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val listeners = CopyOnWriteArraySet<Runnable>()
    private val generation = MutableStateFlow(0L)
    val version = generation.asStateFlow()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HeckerThemeAssets").apply { isDaemon = true }
    }
    @Volatile private var selection: Map<String, String> = emptyMap()
    private var initialized = false

    @JvmStatic fun initialize(context: Context) { init(context) }

    @Synchronized private fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        app.contentResolver.registerContentObserver(Settings.Secure.getUriFor(KEY), false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { reload(app) }
            }, UserHandle.USER_ALL)
        for (key in listOf("hecker_battery_colors", "hecker_battery_scale",
                "hecker_battery_charging_animation", "hecker_battery_bolt", "hecker_battery_bolt_style")) {
            app.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { reload(app) }
                }, UserHandle.USER_ALL)
        }
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { reload(app) }
        }, IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED)
        initialized = true
        reload(app)
    }

    private fun reload(context: Context) {
        worker.execute {
            val next = try {
                val raw = Settings.Secure.getStringForUser(context.contentResolver, KEY, UserHandle.USER_CURRENT)
                val themes = if (raw.isNullOrBlank()) null else JSONObject(raw).optJSONObject("themes")
                buildMap<String, String> {
                    for (category in listOf("statusbar_wifi", "battery_style")) {
                        val theme = themes?.optJSONObject(category) ?: continue
                        val pkg = theme.optString("packageName")
                        if (theme.optBoolean("enabled", false) && pkg.isNotBlank()) put(category, pkg)
                    }
                }
            } catch (error: Exception) {
                Log.w("HeckerThemeAssets", "Cannot read the selected theme resources", error)
                emptyMap()
            }
            context.mainExecutor.execute {
                if (selection != next) Log.i("HeckerThemeAssets", "Selected resources: $next")
                selection = next
                packages.clear()
                missing.clear()
                generation.value++
                listeners.forEach { listener ->
                    try { listener.run() }
                    catch (error: Exception) { Log.w("HeckerThemeAssets", "Theme listener failed", error) }
                }
            }
        }
    }

    @JvmStatic fun addChangeListener(context: Context, listener: Runnable) {
        listeners.add(listener)
        init(context)
    }
    @JvmStatic fun removeChangeListener(listener: Runnable) { listeners.remove(listener) }
    @JvmStatic fun packageName(context: Context, category: String): String? {
        init(context)
        return selection[category]
    }
    @JvmStatic fun resources(context: Context, category: String): Resources? {
        val pkg = packageName(context, category) ?: return null
        if (missing.contains(pkg)) return null
        return packages[pkg] ?: try {
            context.packageManager.getResourcesForApplication(pkg).also { packages[pkg] = it }
        } catch (error: Exception) {
            if (missing.add(pkg)) Log.w("HeckerThemeAssets", "Theme resources unavailable: $pkg", error)
            null
        }
    }
    @JvmStatic fun drawable(context: Context, category: String, name: String): Drawable? {
        val res = resources(context, category) ?: return null
        val pkg = packageName(context, category) ?: return null
        return try {
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) {
                if (missing.add("$pkg:$name")) Log.w("HeckerThemeAssets", "Missing theme drawable: $pkg/$name")
                null
            } else res.getDrawable(id, context.theme)?.mutate()
        } catch (error: Exception) {
            if (missing.add("$pkg:$name")) Log.w("HeckerThemeAssets", "Cannot load theme drawable: $pkg/$name", error)
            null
        }
    }
    @JvmStatic fun wifi(context: Context, id: Int): Drawable? {
        val name = try { context.resources.getResourceEntryName(id) } catch (_: Exception) { return null }
        val themedName = ThemeIconNames.wifi(name) ?: return null
        return drawable(context, "statusbar_wifi", themedName)
    }
    @JvmStatic fun signal(context: Context, level: Int, levels: Int): Drawable? {
        val name = ThemeIconNames.signal(level, levels) ?: return null
        return drawable(context, "statusbar_signal", name)
    }
}
