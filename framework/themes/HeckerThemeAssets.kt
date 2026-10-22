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

    @JvmStatic fun initialize(context: Context) { init(context) }

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
        val themedName = ThemeIconNames.wifi(name) ?: return null
        return drawable(context, "statusbar_wifi", themedName)
    }
    @JvmStatic fun signal(context: Context, level: Int, levels: Int): Drawable? {
        val name = ThemeIconNames.signal(level, levels) ?: return null
        return drawable(context, "statusbar_signal", name)
    }
}
