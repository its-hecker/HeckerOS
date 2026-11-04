package com.drdisagree.xmorphclock.engine

import android.graphics.Bitmap
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

object FaceImages {

    private var loader: ((String) -> Bitmap?)? = null
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val misses = ConcurrentHashMap<String, Long>()

    @Volatile
    var mediaArt: Bitmap? = null

    fun init(loader: (String) -> Bitmap?) {
        this.loader = loader
        misses.clear()
    }

    fun get(id: String): Bitmap? {
        if (id.isEmpty()) return null
        cache[id]?.let { return it }
        val now = SystemClock.uptimeMillis()
        misses[id]?.let { if (now - it < RETRY_MS) return null }
        val bitmap = try {
            loader?.invoke(id)
        } catch (_: Throwable) {
            null
        }
        if (bitmap == null) {
            misses[id] = now
        } else {
            misses.remove(id)
            cache[id] = bitmap
        }
        return bitmap
    }

    fun remove(ids: Collection<String>) {
        ids.forEach { id ->
            cache.remove(id)
            misses.remove(id)
        }
    }

    private const val RETRY_MS = 5_000L
}
