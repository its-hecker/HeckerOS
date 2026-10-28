// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.Locale;

/** Native vector clock artwork; no wallpaper, hit target, editing border or extra window. */
public final class HeckerLockscreenClockRenderer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    public void word(Canvas canvas, int hour, int minute, String date, float width,
            float height, float rotation, boolean compact) {
        float scale = Math.min(width / 360f, height / (compact ? 218f : 310f));
        if (scale <= 0) return;
        canvas.save();
        canvas.translate((width - 360 * scale) / 2, 0);
        canvas.scale(scale, scale);
        smiley(canvas, compact ? 202 : 195, 9, compact ? 76 : 96);
        star(canvas, compact ? 290 : 300, compact ? 61 : 76, 17, 15, rotation, 0xfff43678, true);
        star(canvas, compact ? 272 : 273, compact ? 97 : 121, 11, 9, -rotation, 0xffffa144, false);
        String[] lines = WordClockText.lines(hour, minute);
        paint.reset(); paint.setAntiAlias(true); paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        float textSize = compact ? 27 : 34;
        paint.setTextSize(textSize);
        float longest = 0;
        for (String line : lines) longest = Math.max(longest, paint.measureText(line));
        if (longest > 268) textSize *= 268 / longest;
        paint.setTextSize(textSize);
        float baseline = compact ? 95 : 137;
        for (String line : lines) {
            canvas.drawText(line, 28, baseline, paint);
            baseline += textSize * .98f;
        }
        paint.setTextSize(compact ? 11 : 13);
        float dateWidth = paint.measureText(date);
        if (dateWidth > 304) paint.setTextSize(paint.getTextSize() * 304 / dateWidth);
        canvas.drawText(date, 28, compact ? 204 : 299, paint);
        canvas.restore();
    }
    private void smiley(Canvas c, float x, float y, float size) {
        c.save(); c.translate(x, y); c.scale(size / 100, size / 100);
        paint.reset(); paint.setAntiAlias(true);
        paint.setShader(new LinearGradient(0, 0, 0, 134,
                new int[]{0xff4428ff, 0xffd914a9, 0xffffa34c}, null, Shader.TileMode.CLAMP));
        path.reset(); path.moveTo(0, 50); path.cubicTo(0, -17, 100, -17, 100, 50);
        path.lineTo(100, 88); path.cubicTo(100, 100, 89, 100, 89, 88);
        path.lineTo(89, 82); path.cubicTo(89, 76, 80, 76, 80, 84);
        path.lineTo(80, 114); path.cubicTo(80, 127, 69, 127, 69, 114);
        path.lineTo(69, 103); path.cubicTo(69, 96, 59, 96, 59, 104);
        path.lineTo(59, 137); path.cubicTo(59, 149, 48, 149, 48, 137);
        path.lineTo(48, 100); path.cubicTo(48, 94, 39, 94, 39, 100);
        path.lineTo(39, 110); path.cubicTo(39, 122, 28, 122, 28, 110);
        path.lineTo(28, 88); path.cubicTo(28, 81, 19, 81, 19, 88);
        path.lineTo(19, 96); path.cubicTo(19, 108, 8, 108, 8, 96);
        path.lineTo(8, 82); path.cubicTo(8, 76, 0, 77, 0, 82); path.close();
        c.drawPath(path, paint); paint.setShader(null); paint.setColor(0xff210f29);
        c.drawOval(32, 27, 38, 43, paint); c.drawOval(64, 27, 70, 43, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        path.reset(); path.moveTo(21, 54); path.cubicTo(26, 61, 33, 75, 47, 76);
        path.cubicTo(64, 79, 77, 63, 79, 51); c.drawPath(path, paint);
        path.reset(); path.moveTo(17, 54); path.lineTo(25, 51);
        path.moveTo(75, 50); path.lineTo(83, 55); c.drawPath(path, paint);
        c.restore();
    }
    private void star(Canvas c, float x, float y, float radius, int points, float angle,
            int color, boolean filled) {
        c.save(); c.rotate(angle, x, y);
        paint.reset(); paint.setAntiAlias(true); paint.setColor(color);
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
        path.reset();
        for (int i = 0; i < points * 2; i++) {
            double a = Math.PI * i / points;
            float r = i % 2 == 0 ? radius : radius * .48f;
            float px = x + (float) Math.cos(a) * r, py = y + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close(); c.drawPath(path, paint); c.restore();
    }
    public void tall(Canvas c, int hour, int minute, boolean twentyFourHour, String date,
            float width, float height, float reveal, boolean compact) {
        if (width <= 0 || height <= 0) return;
        String h = String.format(Locale.US, "%02d", twentyFourHour ? hour : (hour + 11) % 12 + 1);
        String m = String.format(Locale.US, "%02d", minute);
        paint.reset(); paint.setAntiAlias(true); paint.setColor(Color.WHITE);
        paint.setAlpha(Math.round(255 * Math.max(0, Math.min(1, reveal))));
        paint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        float target = compact ? height * .56f : height * .43f;
        paint.setTextSize(200);
        Paint.FontMetrics fm = paint.getFontMetrics();
        paint.setTextSize(200 * target / (fm.descent - fm.ascent));
        paint.setTextScaleX(Math.min(.7f, (width - 56) / Math.max(1, paint.measureText(compact ? h + ":" + m : "00"))));
        fm = paint.getFontMetrics();
        float top = compact ? 12 : 22 + 24 * (1 - reveal);
        c.drawText(compact ? h + ":" + m : h, 28, top - fm.ascent, paint);
        if (!compact) c.drawText(m, 28, top + target * .98f - fm.ascent, paint);
        paint.setTextScaleX(1); paint.setTextSize(12);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        float dateWidth = paint.measureText(date);
        if (dateWidth > width - 60) paint.setTextSize(12 * Math.max(1, width - 60) / dateWidth);
        c.drawText(date, 30, height - 12, paint);
    }
}
