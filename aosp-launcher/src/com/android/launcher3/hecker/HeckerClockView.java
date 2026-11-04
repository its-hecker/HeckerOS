/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: GPL-3.0-or-later */
package com.android.launcher3.hecker;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.util.*;
import com.drdisagree.xmorphclock.nativehost.XMorphHomeView;

/** Native XMorph home/widget host. Info draws above the depth subject, outside the clock region. */
public final class HeckerClockView extends FrameLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final XMorphHomeView face;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int widgetId;
    private final Random random = new Random();
    private String style, message = "";
    private float[] infoBounds = new float[8];
    private boolean attached, watching;
    private final Runnable tick = new Runnable() { public void run() {
        if (!attached || !isShown()) return;
        cycleMessage(); main.postDelayed(this, StudioPolicy.nextMinute(System.currentTimeMillis()));
    }};
    public HeckerClockView(Context context, int id) {
        super(context); widgetId = id; style = readStyle();
        face = new XMorphHomeView(context); face.setPreset(style); addView(face);
        setMinimumHeight(HeckerStudio.dp(context, 112)); cycleMessage();
    }
    private String readStyle() {
        return StudioPolicy.clock(HeckerStudio.value(getContext(), widgetId < 0 ? "clock_style" : "clock_widget_" + widgetId,
                HeckerStudio.value(getContext(), "clock_style", StudioPolicy.CLOCKS[0])));
    }
    public String style() { return style; }
    public void setStyle(String value) { style = StudioPolicy.clock(value); face.setPreset(style); requestLayout(); }
    public void cycleMessage() {
        int id = HeckerStudio.res(getContext(), "array", "hs_clock_messages");
        if (id != 0) { String[] values = getResources().getStringArray(id);
            if (values.length > 0) message = values[random.nextInt(values.length)]; }
        requestLayout(); invalidate();
    }
    public void weatherChanged() { requestLayout(); invalidate(); }
    private boolean messages() { return HeckerStudio.enabled(getContext(), "clock_messages", true) && !message.isEmpty(); }
    private boolean weather() { return HeckerStudio.enabled(getContext(), "clock_weather", true) && HeckerClockWeather.text() != null && !HeckerClockWeather.text().isEmpty(); }
    private void sync() {
        if (face == null) return;
        face.setMotionEnabled(HeckerStudio.enabled(getContext(), "clock_animation", true));
        boolean active = attached && isShown() && getWindowVisibility() == VISIBLE;
        if (active && HeckerStudio.enabled(getContext(), "clock_weather", true)) {
            if (!watching) { watching = true; HeckerClockWeather.start(this); }
        } else if (watching) { watching = false; HeckerClockWeather.stop(this); }
        main.removeCallbacks(tick); if (active) main.postDelayed(tick, StudioPolicy.nextMinute(System.currentTimeMillis()));
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; HeckerStudio.prefs(getContext()).registerOnSharedPreferenceChangeListener(this); sync(); }
    @Override protected void onDetachedFromWindow() { attached = false; main.removeCallbacks(tick); HeckerClockWeather.stop(this); watching = false;
        HeckerStudio.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(this); super.onDetachedFromWindow(); }
    @Override public void onVisibilityAggregated(boolean visible) { super.onVisibilityAggregated(visible); sync(); }
    @Override protected void onWindowVisibilityChanged(int value) { super.onWindowVisibilityChanged(value); sync(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key != null && key.startsWith(HeckerStudio.PREFIX + "clock")) { setStyle(readStyle()); sync(); requestLayout(); invalidate(); }
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        infoBounds = ClockInfoLayout.bounds(w, h, getResources().getDisplayMetrics().density,
                HeckerStudio.number(getContext(), "clock_info_position", 0, 0, 3), messages(), weather());
        face.measure(MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(infoBounds[2])), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(infoBounds[3])), MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = Math.round(infoBounds[0]), y = Math.round(infoBounds[1]);
        face.layout(x, y, x + face.getMeasuredWidth(), y + face.getMeasuredHeight());
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas); HeckerHomeDepth.foreground(this, canvas);
        drawInfo(canvas, infoBounds, messages(), weather());
    }
    private void drawInfo(Canvas canvas, float[] area, boolean showMessage, boolean showWeather) {
        if (area[6] <= 0 || area[7] <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        float font = Math.min(1.4f, getResources().getConfiguration().fontScale);
        int saved = canvas.save(); canvas.translate(area[4], area[5]); canvas.clipRect(0, 0, area[6], area[7]);
        paint.reset(); paint.setAntiAlias(true); paint.setColor(0xa11a2630);
        canvas.drawRoundRect(0, 0, area[6], area[7], 18*d, 18*d, paint);
        float available = Math.max(1, area[6] - 20*d), center = area[6]/2;
        float weatherHeight = showWeather ? (showMessage ? Math.min(30*d, area[7]*.48f) : area[7]) : 0;
        float messageHeight = area[7] - weatherHeight;
        if (showMessage) {
            android.text.TextPaint textPaint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xffe1e7ed);
            textPaint.setTextSize(Math.min(12*d*font, Math.max(1, messageHeight-8*d)));
            android.text.StaticLayout text = android.text.StaticLayout.Builder.obtain(message, 0, message.length(),
                    textPaint, Math.max(1, Math.round(available))).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false).setMaxLines(messageHeight >= 30*d*font ? 2 : 1)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            int block = canvas.save(); canvas.clipRect(0, 0, area[6], messageHeight);
            canvas.translate(10*d, Math.max(0, (messageHeight-text.getHeight())/2));
            text.draw(canvas); canvas.restoreToCount(block);
        }
        if (showWeather) {
            int block = canvas.save(); canvas.clipRect(0, messageHeight, area[6], area[7]);
            paint.reset(); paint.setAntiAlias(true);
            paint.setTextSize(Math.min(14*d*font, Math.max(1, weatherHeight-6*d))); paint.setColor(0xffd1eee7);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            String weather = android.text.TextUtils.ellipsize(HeckerClockWeather.text(), new android.text.TextPaint(paint),
                    Math.max(1, available-25*d), android.text.TextUtils.TruncateAt.END).toString();
            android.graphics.drawable.Drawable icon = HeckerClockWeather.icon();
            float total = paint.measureText(weather) + (icon == null ? 0 : 24*d);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float x = center - total/2, y = messageHeight + weatherHeight/2 - (metrics.ascent+metrics.descent)/2;
            if (icon != null) { icon.setBounds(Math.round(x), Math.round(y-16*d), Math.round(x+19*d), Math.round(y+3*d)); icon.draw(canvas); x+=24*d; }
            paint.setTextAlign(Paint.Align.LEFT); canvas.drawText(weather, x, y, paint);
            canvas.restoreToCount(block);
        }
        canvas.restoreToCount(saved);
    }
}
