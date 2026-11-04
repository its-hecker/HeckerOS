/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: GPL-3.0-or-later */
package com.drdisagree.xmorphclock.nativehost

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.drdisagree.xmorphclock.engine.render.BackdropCache
import kotlin.math.max
import kotlin.math.roundToInt

/** Bounded static wallpaper source for the glass faces. Call from a worker thread. */
object XMorphWallpaper {
    private data class Cached(val user: Int, val wallpaper: Int, val bitmap: Bitmap)
    private var cached: Cached? = null

    @JvmStatic @Synchronized fun load(context: Context, user: Int): Bitmap? = try {
        val manager = WallpaperManager.getInstance(context)
        val lock = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val flag = if (lock > 0) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        val id = manager.getWallpaperId(flag)
        if (manager.getWallpaperInfo(flag) != null) null
        else cached?.takeIf { it.user == user && it.wallpaper == id }?.bitmap ?: run {
            val bitmap = manager.getWallpaperFile(flag)?.use { descriptor ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                check(options.outWidth > 0 && options.outHeight > 0)
                var sample = 1
                while (max(options.outWidth / sample, options.outHeight / sample) > 1600) sample *= 2
                options.inJustDecodeBounds = false; options.inSampleSize = sample
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
            } ?: manager.getBuiltInDrawable(0, 0, true, .5f, .5f, flag)?.let { drawable ->
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val scale = minOf(1f, 1600f / max(width, height))
                Bitmap.createBitmap((width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                    drawable.setBounds(0, 0, it.width, it.height); drawable.draw(Canvas(it))
                }
            }
            bitmap?.also { BackdropCache.prepare(it); cached = Cached(user, id, it) }
        }
    } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
}
