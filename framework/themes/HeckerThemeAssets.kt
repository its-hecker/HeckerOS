/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.content.Context
import android.content.res.Resources
import android.content.res.ThemeEngine
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Symbol lookup avoids Android 16 resource ID assumptions in Android 17 System UI. */
object HeckerThemeAssets {
    private val packages = ConcurrentHashMap<String, Resources>()
    private val generation = MutableStateFlow(0L)
    val version = generation.asStateFlow()
    private var initialized = false
    private var engine: ThemeEngine? = null

    @Synchronized private fun init(context: Context) {
        if (initialized) return
        engine = ThemeEngine.getInstance(context) ?: return
        initialized = true
        engine?.addThemeChangeListener {
            context.mainExecutor.execute {
                packages.clear()
                generation.value++
            }
        }
    }
    @JvmStatic fun resources(context: Context, category: String): Resources? {
        init(context)
        val pkg = engine?.getEnabledPackage(category)?.takeIf { it.isNotEmpty() } ?: return null
        return packages[pkg] ?: try {
            context.packageManager.getResourcesForApplication(pkg).also { packages[pkg] = it }
        } catch (_: Exception) { null }
    }
    @JvmStatic fun drawable(context: Context, category: String, name: String): Drawable? {
        val res = resources(context, category) ?: return null
        return try {
            val pkg = engine?.getEnabledPackage(category) ?: return null
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) null else res.getDrawable(id, null)?.mutate()
        } catch (_: Exception) { null }
    }
    @JvmStatic fun wifi(context: Context, id: Int): Drawable? {
        val name = try { context.resources.getResourceEntryName(id) } catch (_: Exception) { return null }
        // Preserve the native no-internet/error indicators rather than concealing their state.
        val level = Regex("ic_wifi_(?:signal_)?([0-4])").matchEntire(name)?.groupValues?.get(1) ?: return null
        return drawable(context, "statusbar_wifi", "ic_wifi_signal_$level")
    }
    @JvmStatic fun signal(context: Context, level: Int, levels: Int): Drawable? {
        if (level !in 0 until levels) return null
        val bars = if (levels > 5) 5 else 4
        return drawable(context, "statusbar_signal", "ic_signal_cellular_${level}_${bars}_bar")
    }
}
