package com.drdisagree.xmorphclock.engine.render

import android.graphics.Bitmap
import androidx.core.graphics.scale
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

internal object BackdropCache {

    private val cache = WeakHashMap<Bitmap, BackdropBlur>()

    fun of(source: Bitmap): BackdropBlur {
        synchronized(cache) { cache[source] }?.let { return it }
        val input = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val blurred = BackdropBlur(
            boxBlur(downscale(input, BLEND_DOWNSCALE), BLEND_BLUR_RADIUS),
            boxBlur(downscale(input, GLASS_DOWNSCALE), GLASS_BLUR_RADIUS)
        )
        synchronized(cache) { cache[source] = blurred }
        return blurred
    }

    fun prepare(source: Bitmap) {
        of(source)
    }

    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (radius <= 0 || width <= 1 || height <= 1) return source
        val pixels = IntArray(width * height)
        val scratch = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(BLUR_PASSES) {
            blurPass(pixels, scratch, width, height, radius, horizontal = true)
            blurPass(scratch, pixels, width, height, radius, horizontal = false)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blurPass(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            fun at(position: Int): Int {
                val clamped = position.coerceIn(0, length - 1)
                return if (horizontal) input[line * width + clamped] else input[clamped * width + line]
            }
            for (offset in -radius..radius) {
                val c = at(offset)
                a += c ushr 24
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (position in 0 until length) {
                val index = if (horizontal) line * width + position else position * width + line
                output[index] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val out = at(position - radius)
                val incoming = at(position + radius + 1)
                a += (incoming ushr 24) - (out ushr 24)
                r += ((incoming shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((incoming shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (incoming and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun downscale(source: Bitmap, factor: Int): Bitmap {
        var bitmap = source
        var remaining = factor
        while (remaining > 1) {
            val step = if (remaining >= 4) 4 else remaining
            bitmap = bitmap.scale(max(1, bitmap.width / step), max(1, bitmap.height / step))
            remaining /= step
        }
        return bitmap
    }

    private const val BLEND_DOWNSCALE = 8
    private const val BLEND_BLUR_RADIUS = 6
    private const val GLASS_BLUR_RADIUS = 1
    private const val BLUR_PASSES = 3
    private const val GLASS_DOWNSCALE = 2
}
