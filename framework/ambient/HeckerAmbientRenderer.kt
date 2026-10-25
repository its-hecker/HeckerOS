/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.BatteryManager
import com.android.systemui.res.R
import java.util.Locale
import kotlin.math.*

/** Original vector artwork: a geometric clock, open magenta gauge and a finite plasma intro. */
class HeckerAmbientRenderer(private val context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val white = 0xffe5e5e5.toInt()
    private val secondary = 0xff8a8a8a.toInt()
    private val digits = mapOf(
        '0' to "0,0 44,0 44,64 0,64 0,0",
        '1' to "12,0 12,64",
        '2' to "0,0 44,0 44,32 0,32 0,64 44,64",
        '3' to "0,0 44,0 44,64 0,64|0,32 44,32",
        '4' to "0,0 0,32 44,32|44,0 44,64",
        '5' to "44,0 0,0 0,32 44,32 44,64 0,64",
        '6' to "44,0 0,0 0,64 44,64 44,32 0,32",
        '7' to "0,0 44,0 44,64",
        '8' to "0,0 44,0 44,64 0,64 0,0|0,32 44,32",
        '9' to "44,32 0,32 0,0 44,0 44,64 0,64",
    )
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float,
        color: Int = white, bold: Boolean = false) {
        paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.FILL
        paint.color = color; paint.textAlign = Paint.Align.CENTER; paint.textSize = size
        paint.typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        c.drawText(value, x, y, paint)
    }
    fun clock(c: Canvas, time: String, date: String, width: Float, top: Float,
        height: Float = 60f, color: Int = white) {
        val extent = time.fold(0f) { total, glyph ->
            total + if (glyph == ':') 16f else if (glyph == '1') 24f else 50f
        }
        val scale = min(height / 64f, (width - 32f) / (extent * 1.12f))
        c.save(); c.translate((width - extent * scale * 1.12f) / 2f, top)
        c.scale(scale * 1.12f, scale)
        paint.reset(); paint.isAntiAlias = true; paint.color = color
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.pathEffect = CornerPathEffect(3f)
        for (glyph in time) {
            if (glyph == ':') {
                paint.style = Paint.Style.FILL
                c.drawCircle(4f, 23f, 1.8f, paint); c.drawCircle(4f, 43f, 1.8f, paint)
                paint.style = Paint.Style.STROKE; c.translate(16f, 0f)
            } else {
                digits[glyph]?.split('|')?.forEach { stroke ->
                    val path = Path()
                    stroke.split(' ').forEachIndexed { index, pair ->
                        val xy = pair.split(','); val x = xy[0].toFloat(); val y = xy[1].toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    c.drawPath(path, paint)
                }
                c.translate(if (glyph == '1') 24f else 50f, 0f)
            }
        }
        c.restore()
        text(c, date, width / 2, top + 64f * scale + 21f, 11f, color)
    }
    fun status(s: HeckerAmbientCharging.State): String = context.getString(when(s.status) {
        BatteryManager.BATTERY_STATUS_FULL -> R.string.hecker_charge_full
        BatteryManager.BATTERY_STATUS_CHARGING -> R.string.hecker_charge_charging
        else -> R.string.hecker_charge_paused
    })
    private fun percent(s: HeckerAmbientCharging.State, decimals: Boolean): String =
        if (!s.level.isFinite()) "—" else String.format(Locale.getDefault(),
            if (decimals) "%.2f%%" else "%.0f%%", s.level)
    fun charging(c: Canvas, s: HeckerAmbientCharging.State, time: String, date: String,
        width: Float, height: Float, intro: Float?) {
        paint.reset(); paint.color = Color.BLACK
        c.drawRect(0f, 0f, width, height, paint)
        val minimal = s.style == 2
        if (!minimal && s.style != 0 && (width < 360f || height < 560f)) {
            val scale = min(width / 360f, height / 560f)
            if (scale <= 0f) return
            c.save(); c.translate((width - 360f * scale) / 2f, 0f); c.scale(scale, scale)
            charging(c,s,time,date,360f,max(560f,height/scale),intro)
            c.restore()
            return
        }
        val radius = min(106f, min(width * .29f, height * .21f))
        val centerX = width / 2
        val centerY = if (minimal || s.style == 0) height * .48f else
            min(height - radius - 100f, max(160f, height * .51f))
        if (!minimal && s.style != 0) clock(c, time, date, width, max(12f, height * .065f))
        if (intro != null) {
            intro(c, s.art, s.level, status(s), centerX, centerY, radius, intro)
            return
        }
        if (minimal) {
            text(c, percent(s, false), centerX, centerY + 8f, 48f, 0xff6b6b6b.toInt(), true)
            chargingLabel(c, status(s), centerX, centerY + 36f, 0xff626262.toInt(), 0xff72704b.toInt())
            return
        }
        if (s.style == 0) return
        paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 4f; paint.color = 0xff171920.toInt()
        c.drawArc(centerX-radius, centerY-radius, centerX+radius, centerY+radius, 135f, 270f, false, paint)
        paint.color = 0xffdf1254.toInt()
        if (s.level.isFinite()) c.drawArc(centerX-radius, centerY-radius, centerX+radius,
            centerY+radius, 135f, 270f * s.level / 100f, false, paint)
        text(c, percent(s, true), centerX, centerY + 9f, 29f, white, true)
        text(c, status(s).uppercase(Locale.getDefault()), centerX, centerY + 28f, 9f, secondary)
        val rowTop = centerY + radius + 13f
        val cell = min(78f, (width - 52f) / 3f)
        val values = arrayOf(
            if (s.temperature.isFinite()) String.format(Locale.getDefault(), "%.1f°C", s.temperature) else "—",
            if (s.voltage.isFinite()) String.format(Locale.getDefault(), "%.3f V", s.voltage) else "—",
            if (s.remaining > 0 && s.status == BatteryManager.BATTERY_STATUS_CHARGING)
                context.getString(R.string.hecker_charge_minutes, ceil(s.remaining / 60000.0).toInt()) else "—")
        val labels = intArrayOf(R.string.hecker_charge_temp, R.string.hecker_charge_voltage,
            R.string.hecker_charge_estimate)
        for (index in 0..2) {
            val x = centerX + (index - 1) * (cell + 5f)
            paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE
            paint.strokeWidth = .65f; paint.color = 0xff1d2027.toInt()
            c.drawRoundRect(x-cell/2f, rowTop, x+cell/2f, rowTop+48f, 7f, 7f, paint)
            text(c, values[index], x, rowTop + 20f, 12f, white, true)
            text(c, context.getString(labels[index]), x, rowTop + 36f, 9f, secondary)
        }
        if (s.watts.isFinite() && s.status == BatteryManager.BATTERY_STATUS_CHARGING)
            text(c, String.format(Locale.getDefault(), "%.2f W", s.watts), centerX,
                max(rowTop + 72f, height - 18f), 13f, white, true)
    }
    private fun chargingLabel(c: Canvas, value: String, x: Float, y: Float, color: Int, boltColor: Int) {
        paint.reset(); paint.isAntiAlias = true; paint.textSize = 12f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val span = paint.measureText(value)
        val left = x - (span + 19f) / 2f
        paint.color = boltColor; paint.style = Paint.Style.FILL
        val bolt = Path().apply { moveTo(left+7,y-13); lineTo(left,y-4); lineTo(left+5,y-4)
            lineTo(left+2,y+3); lineTo(left+13,y-8); lineTo(left+7,y-8); close() }
        c.drawPath(bolt, paint)
        text(c, value, left + 19f + span / 2f, y, 12f, color, true)
    }
    fun intro(c: Canvas, art: Int, level: Float, label: String, x: Float, y: Float, r: Float, phase: Float) {
        val opacity = min(1f, min(phase * 9f, (1f-phase) * 7f)).coerceIn(0f,1f)
        c.saveLayerAlpha(x-r*1.5f,y-r*1.5f,x+r*1.5f,y+r*1.5f,(opacity*255).toInt())
        paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.shader = SweepGradient(x,y,intArrayOf(0xff26e5ed.toInt(),0xff448dff.toInt(),
            0xff9659ff.toInt(),0xffef4ad9.toInt(),0xff26e5ed.toInt()),null)
        if (art == 1) {
            // Layered turbulent filaments, rather than an endlessly running AOD animation.
            for (layer in 0..5) {
                val path = Path()
                for (step in 0..240) {
                    val a = step * PI * 2 / 240
                    val noise = sin(a*23+phase*26+layer)*.017 + sin(a*37-phase*21+layer)*.012
                    val radius = r * (1 + noise + layer*.012)
                    val px = x + cos(a)*radius; val py = y + sin(a)*radius
                    if (step == 0) path.moveTo(px.toFloat(),py.toFloat()) else path.lineTo(px.toFloat(),py.toFloat())
                }
                path.close(); paint.strokeWidth = if(layer < 2) 8f else 1.3f
                paint.alpha = if(layer < 2) 26 else 210
                c.save(); c.rotate(phase*100 + layer*4,x,y)
                if (layer == 0) {
                    paint.strokeWidth = 22f; paint.alpha = 18; c.drawPath(path,paint)
                    paint.strokeWidth = 12f; paint.alpha = 36; c.drawPath(path,paint)
                    paint.strokeWidth = 6f; paint.alpha = 95
                }
                c.drawPath(path,paint); c.restore()
            }
            paint.strokeWidth = .8f; paint.alpha = 145
            repeat(72) { index ->
                val angle = index * PI * 2 / 72 + phase * .6
                val length = .04 + .065 * abs(sin(index * 17.0 + phase * 37))
                val filament = Path().apply {
                    moveTo((x+cos(angle)*r).toFloat(),(y+sin(angle)*r).toFloat())
                    lineTo((x+cos(angle+.012)*r*(1+length*.6)).toFloat(),
                        (y+sin(angle+.012)*r*(1+length*.6)).toFloat())
                    lineTo((x+cos(angle-.008)*r*(1+length)).toFloat(),
                        (y+sin(angle-.008)*r*(1+length)).toFloat())
                }
                c.drawPath(filament,paint)
            }
        } else {
            paint.strokeWidth = 3.4f
            c.save(); c.rotate(phase*210,x,y)
            c.drawArc(x-r,y-r,x+r,y+r,12f,286f,false,paint); c.restore()
            paint.strokeWidth = 1.2f; paint.alpha = 180
            c.drawArc(x-r*1.13f,y-r*1.13f,x+r*1.13f,y+r*1.13f,-phase*160f,130f,false,paint)
        }
        paint.shader = null
        text(c, if(level.isFinite()) String.format(Locale.getDefault(),"%.0f%%",level) else "—",
            x,y+10f,43f,white)
        chargingLabel(c,label,x,y+39f,white,0xffc2dd55.toInt())
        c.restore()
    }
}
