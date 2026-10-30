// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.format.DateFormat;
import android.view.View;
import android.database.ContentObserver;
import android.provider.Settings;
import org.hecker.motion.FlowClockRenderer;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

/** One native renderer for widget and Studio preview. No polling or animation while off screen. */
public final class HeckerClockView extends View implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final ExecutorService DATA = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final int widgetId;
    private String style, message = "";
    private String displayTime = "", displayDate = "";
    private long messageSlot = -1, cpuTotal, cpuIdle;
    private float battery = -1, temperature = Float.NaN, cpu = Float.NaN;
    private long usedRam, totalRam;
    private float phase;
    private boolean attached, running, sampling, screenAwake = true;
    private int generation;
    private ValueAnimator animator;
    private ValueAnimator homeAnimator;
    private float homeReveal = 1;
    private boolean arrivalPending;
    private final FlowClockRenderer livingClock = new FlowClockRenderer();
    private final ContentObserver livingSettings = new ContentObserver(main) {
        @Override public void onChange(boolean self) { invalidate(); }
    };
    private final Runnable tick = new Runnable() { public void run() {
        if (!running) return;
        refresh();
        main.postDelayed(this, style.equals("analog") ? 1000 : Math.min(30000, StudioPolicy.nextMinute(System.currentTimeMillis())));
    }};
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) screenAwake = false;
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) || Intent.ACTION_USER_PRESENT.equals(intent.getAction())) screenAwake = true;
            if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) arrivalPending = true;
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                battery = StudioPolicy.battery(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0));
                int t = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                temperature = t == Integer.MIN_VALUE ? Float.NaN : t / 10f;
            }
            syncVisibility(); refresh();
        }
    };

    public HeckerClockView(Context c, int id) {
        super(c); org.hecker.motion.FlowClockFonts.apply(c, livingClock); widgetId = id; style = readStyle(); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setMinimumHeight(HeckerStudio.dp(c, 160));
    }
    private String readStyle() {
        return StudioPolicy.clock(HeckerStudio.value(getContext(), widgetId < 0 ? "clock_style" : "clock_widget_" + widgetId,
                HeckerStudio.value(getContext(), "clock_style", "signature")));
    }
    public void setStyle(String value) { style = StudioPolicy.clock(value); messageSlot = -1; restartMotion(); refresh(); invalidate(); }
    public String style() { return style; }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true;
        PowerManager power = getContext().getSystemService(PowerManager.class);
        screenAwake = power == null || power.isInteractive();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED); filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT);
        getContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        HeckerStudio.prefs(getContext()).registerOnSharedPreferenceChangeListener(this); syncVisibility();
        for (String key : new String[]{"hecker_living_clock_home", "hecker_custom_lockscreen_clock",
                "hecker_lockscreen_clock_style", "hecker_lockscreen_clock_color",
                "hecker_lockscreen_clock_opacity", "hecker_lockscreen_clock_scale"})
            getContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, livingSettings);
    }
    @Override protected void onDetachedFromWindow() {
        attached = false; stop(); getContext().unregisterReceiver(receiver);
        getContext().getContentResolver().unregisterContentObserver(livingSettings);
        HeckerStudio.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }
    @Override public void onVisibilityAggregated(boolean visible) { super.onVisibilityAggregated(visible); syncVisibility(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); syncVisibility(); }
    private void syncVisibility() {
        if (main == null) return;
        boolean visible = attached && screenAwake && isShown() && getWindowVisibility() == VISIBLE;
        if (visible == running) { if (visible && arrivalPending) homeArrival(); return; }
        if (!visible) { stop(); return; }
        running = true; messageSlot = -1;
        restartMotion(); main.post(tick); if (arrivalPending) homeArrival();
    }
    private void stop() {
        running = false; generation++; sampling = false; main.removeCallbacks(tick);
        if (animator != null) { animator.cancel(); animator = null; }
        if (homeAnimator != null) { homeAnimator.cancel(); homeAnimator = null; }
        homeReveal = 1;
        HeckerClockWeather.stop(this);
    }
    private void homeArrival() {
        arrivalPending = false;
        if (homeAnimator != null) { homeAnimator.cancel(); homeAnimator = null; }
        homeReveal = 1;
        if (widgetId != -1 || !HeckerLauncherMotion.enabled(getContext())
                || Settings.Secure.getInt(getContext().getContentResolver(), "hecker_living_clock_home", 1) == 0
                || Settings.Secure.getInt(getContext().getContentResolver(), "hecker_custom_lockscreen_clock", 0) == 0) return;
        int selected = Settings.Secure.getInt(getContext().getContentResolver(), "hecker_lockscreen_clock_style", 0);
        if (!FlowClockRenderer.supportsDepth(selected)) return;
        homeReveal = 0; homeAnimator = ValueAnimator.ofFloat(0, 1); homeAnimator.setDuration(520);
        homeAnimator.setInterpolator(HeckerLauncherMotion.CURVE);
        homeAnimator.addUpdateListener(a -> { homeReveal = (float) a.getAnimatedValue(); invalidate(); });
        homeAnimator.start();
    }
    private void restartMotion() {
        if (running && HeckerStudio.enabled(getContext(), "clock_weather", true)) HeckerClockWeather.start(this); else HeckerClockWeather.stop(this);
        if (animator != null) { animator.cancel(); animator = null; }
        if (!running || !ValueAnimator.areAnimatorsEnabled() || !HeckerStudio.enabled(getContext(), "clock_animation", true)
                || !(style.equals("thermal") || style.equals("stacked") || style.equals("caracal"))) return;
        animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(9000);
        animator.setInterpolator(new android.view.animation.LinearInterpolator()); animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); }); animator.start();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key != null && key.startsWith(HeckerStudio.PREFIX + "clock")) { style = readStyle(); restartMotion(); refresh(); }
    }
    private void refresh() {
        if (!running) return;
        displayTime = time(); displayDate = date();
        long slot = System.currentTimeMillis() / 60000;
        if (slot != messageSlot) {
            cycleMessage();
            messageSlot = slot;
        }
        updateDescription();
        invalidate();
        if (sampling || !(style.equals("thermal") || style.equals("stacked") || style.equals("caracal"))) return;
        sampling = true; int request = generation;
        DATA.execute(() -> {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            getContext().getSystemService(ActivityManager.class).getMemoryInfo(info);
            long total = 0, idle = 0; boolean available = false;
            // Android may deny global CPU counters. An unavailable counter stays unavailable.
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
                String[] fields = reader.readLine().trim().split("\\s+");
                if (fields.length >= 6 && "cpu".equals(fields[0])) {
                    for (int i = 1; i <= Math.min(8, fields.length - 1); i++) total += Long.parseLong(fields[i]);
                    idle = Long.parseLong(fields[4]) + Long.parseLong(fields[5]); available = true;
                }
            } catch (IOException | RuntimeException ignored) { }
            final long nextTotal = total, nextIdle = idle; final boolean valid = available;
            main.post(() -> {
                if (!running || request != generation) return;
                totalRam = info.totalMem; usedRam = Math.max(0, totalRam - info.availMem);
                cpu = valid && cpuTotal > 0 && nextTotal > cpuTotal ? Math.max(0, Math.min(1, 1f - (float) (nextIdle - cpuIdle) / (nextTotal - cpuTotal))) : Float.NaN;
                cpuTotal = nextTotal; cpuIdle = nextIdle; sampling = false; invalidate();
            });
        });
    }
    public void cycleMessage() {
        String[] messages = getResources().getStringArray(HeckerStudio.res(getContext(), "array", "hs_clock_messages"));
        if (messages.length > 0) {
            int choice = random.nextInt(messages.length);
            if (messages.length > 1 && messages[choice].equals(message)) choice = (choice + 1) % messages.length;
            message = messages[choice];
        }
        updateDescription(); invalidate();
    }
    void weatherChanged() { updateDescription(); invalidate(); }
    private void updateDescription() {
        String weather = HeckerStudio.enabled(getContext(), "clock_weather", true) ? HeckerClockWeather.text() : null;
        setContentDescription(time() + ", " + date() + (battery >= 0 ? ", " + HeckerStudio.text(getContext(), "battery") + " " + percent(battery) : "")
                + (weather == null || weather.isEmpty() ? "" : ", " + weather) + ". " + message);
    }
    private int[] palette() {
        int[][] colors = {{0xffb4f8d9,0xff8edbed,0xffc5acff}, {0xffffd59c,0xffffaaa4,0xffffcc74},
                {0xff99eced,0xffa7c5ff,0xff88b8ff}, {0xfff4bcdf,0xffc8b1ff,0xffffa4ce},
                {0xfff5f2e8,0xffb8f3cc,0xffb397f0}};
        return colors[HeckerStudio.number(getContext(), "clock_palette", 0, 0, colors.length-1)];
    }
    private String time() { return DateFormat.getTimeFormat(getContext()).format(new Date()); }
    private String date() { return DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEddMMM"), new Date()).toString(); }
    private String percent(float value) { return value < 0 || !Float.isFinite(value) ? "—" : NumberFormat.getPercentInstance().format(value); }
    private String temp() { return Float.isFinite(temperature) ? NumberFormat.getIntegerInstance().format(temperature) + "°C" : "—"; }
    private String ram() {
        if (totalRam <= 0) return "—";
        return String.format(Locale.getDefault(), "%.1f / %.1f GB", usedRam / 1073741824d, totalRam / 1073741824d);
    }
    private void text(Canvas c, String text, float x, float y, float size, int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL); paint.setShader(null); paint.setColor(color); paint.setTextSize(size); paint.setTextAlign(align);
        paint.setTypeface(Typeface.create(style.equals("mono") ? "monospace" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        if (size >= 48 && HeckerStudio.number(getContext(), "clock_palette", 0, 0, 4) != 4) {
            int[] colors = palette();
            paint.setShader(new LinearGradient(20, y-size, 360, y, colors[0], colors[2], Shader.TileMode.CLAMP));
        }
        c.drawText(text, x, y, paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawLivingClock(canvas)) return;
        float logicalHeight = style.equals("caracal") ? 230 : style.equals("analog") || style.equals("stacked") || style.equals("thermal") ? 220 : 180;
        boolean weatherEnabled = HeckerStudio.enabled(getContext(), "clock_weather", true);
        float scale = Math.min(getWidth() / 380f, getHeight() / (logicalHeight + (weatherEnabled ? 30 : 0)));
        if (scale <= 0) return;
        float originX = (getWidth() - 380 * scale) / 2, originY = (getHeight() - (logicalHeight + (weatherEnabled ? 30 : 0)) * scale) / 2;
        int save = canvas.save(); canvas.translate(originX, originY); canvas.scale(scale, scale);
        int[] colors = palette();
        final int ivory = colors[0], muted = 0xffcad7e4, mint = colors[1], accent = colors[2];
        String time = displayTime.isEmpty() ? time() : displayTime, date = displayDate.isEmpty() ? date() : displayDate;
        if (style.equals("glass")) {
            paint.setShader(new LinearGradient(10, 0, 360, 140, new int[]{0x8869cfc3, 0x66394367, 0x887c74ca}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(12, 4, 368, 142, 24, 24, paint); paint.setShader(null);
            line(canvas, 12, 4, 368, 4, 0x70ffffff, 1);
            fitTime(canvas, time, 190, 83, 72, ivory, true, 330);
            text(canvas, date, 190, 119, 20, muted, false, Paint.Align.CENTER);
        } else if (style.equals("analog")) {
            drawAnalog(canvas); text(canvas, date, 190, 194, 15, muted, false, Paint.Align.CENTER);
        } else if (style.equals("stacked")) {
            Calendar now = Calendar.getInstance(); String hour = DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH" : "hh", now).toString();
            String minute = DateFormat.format("mm", now).toString();
            text(canvas, hour, 89, 84, 83, ivory, true, Paint.Align.CENTER); text(canvas, minute, 89, 157, 83, ivory, true, Paint.Align.CENTER);
            text(canvas, date, 89, 184, 14, muted, false, Paint.Align.CENTER); line(canvas, 181, 18, 181, 184, 0x405f6f7f, 1);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(8); paint.setColor(0xff404753);
            canvas.drawArc(225, 18, 339, 132, 140, 260, false, paint); paint.setColor(accent);
            if (battery >= 0) canvas.drawArc(225, 18, 339, 132, 140, 260 * battery, false, paint);
            paint.setStyle(Paint.Style.FILL);
            if (animator != null && battery >= 0) { float angle = (float) Math.toRadians(140 + 260 * battery * phase); canvas.drawCircle(282 + 57 * (float) Math.cos(angle), 75 + 57 * (float) Math.sin(angle), 4, paint); }
            text(canvas, percent(battery), 282, 82, 29, ivory, true, Paint.Align.CENTER); text(canvas, HeckerStudio.text(getContext(), "battery"), 282, 105, 12, muted, false, Paint.Align.CENTER);
            text(canvas, temp(), 282, 165, 29, ivory, true, Paint.Align.CENTER); text(canvas, HeckerStudio.text(getContext(), "battery_temp"), 282, 185, 12, muted, false, Paint.Align.CENTER);
        } else if (style.equals("caracal")) {
            drawCaracal(canvas, 18, 4, 105, phase, animator != null);
            line(canvas, 138, 20, 138, 111, 0x50677987, 1); fitTime(canvas, time, 260, 82, 68, ivory, true, 225);
            text(canvas, date, 260, 108, 18, muted, false, Paint.Align.CENTER);
            metric(canvas, 46, 135, temp(), "battery_temp", 0); metric(canvas, 142, 135, percent(cpu), "cpu_load", 2);
            metric(canvas, 237, 135, ram(), "ram", 2); metric(canvas, 333, 135, percent(battery), "battery", 1);
            if (HeckerStudio.enabled(getContext(), "clock_messages", true)) text(canvas, message, 190, 216, 16, ivory, false, Paint.Align.CENTER);
        } else {
            int color = style.equals("mono") ? mint : ivory;
            boolean bold = !style.equals("minimal");
            float size = style.equals("minimal") ? 70 : style.equals("compact") ? 66 : 80;
            float usedSize = fitTime(canvas, time, 190, 77, size, color, bold, 354);
            // Accent punctuation stays independent of the user's localized time representation.
            if ((style.equals("signature") || style.equals("digital")) && time.contains(":")) {
                paint.setTextSize(usedSize); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                float before = paint.measureText(time.substring(0, time.indexOf(':'))), width = paint.measureText(time);
                text(canvas, ":", 190 - width / 2 + before, 77, usedSize, style.equals("digital") ? 0xff8bccfa : 0xffec8c75, true, Paint.Align.LEFT);
            }
            if (style.equals("compact")) line(canvas, 355, 28, 355, 81, 0xfff2ce78, 3);
            text(canvas, date, 190, 108, 23, muted, false, Paint.Align.CENTER);
            if (style.equals("signature")) text(canvas, "H E C K E R  O S", 190, 135, 11, muted, false, Paint.Align.CENTER);
            if (style.equals("thermal")) {
                metric(canvas, 65, 117, temp(), "battery_temp", 0); metric(canvas, 190, 117, percent(battery), "battery", 1); metric(canvas, 315, 117, ram(), "ram", 2);
            }
            if (style.equals("minimal")) {
                AlarmManager.AlarmClockInfo alarm = getContext().getSystemService(AlarmManager.class).getNextAlarmClock();
                if (alarm != null) text(canvas, HeckerStudio.text(getContext(), "alarm") + " " + DateFormat.getTimeFormat(getContext()).format(new Date(alarm.getTriggerTime())), 190, 137, 14, accent, false, Paint.Align.CENTER);
            }
        }
        if (!style.equals("caracal") && HeckerStudio.enabled(getContext(), "clock_messages", true))
            text(canvas, message, 190, logicalHeight - 6, 14, accent, false, Paint.Align.CENTER);
        canvas.restoreToCount(save);
        HeckerHomeDepth.foreground(this, canvas);
        // Keep current weather readable above the depth subject, in its own reserved row.
        if (weatherEnabled) {
            save = canvas.save(); canvas.translate(originX, originY); canvas.scale(scale, scale);
            drawWeather(canvas, logicalHeight + 21); canvas.restoreToCount(save);
        }
    }
    private boolean drawLivingClock(Canvas canvas) {
        // The built-in Home clock follows keyguard. Independently configured widgets/previews keep their style.
        if (widgetId != -1 || Settings.Secure.getInt(getContext().getContentResolver(), "hecker_living_clock_home", 1) == 0
                || Settings.Secure.getInt(getContext().getContentResolver(), "hecker_custom_lockscreen_clock", 0) == 0) return false;
        int selected = Settings.Secure.getInt(getContext().getContentResolver(), "hecker_lockscreen_clock_style", 0);
        if (!FlowClockRenderer.supportsDepth(selected)) return false;
        Calendar now = Calendar.getInstance();
        int color = Settings.Secure.getInt(getContext().getContentResolver(), "hecker_lockscreen_clock_color", 0);
        int size = Settings.Secure.getInt(getContext().getContentResolver(), "hecker_lockscreen_clock_scale", 100);
        float opacity = Settings.Secure.getInt(getContext().getContentResolver(), "hecker_lockscreen_clock_opacity", 76) / 100f;
        float density = getResources().getDisplayMetrics().density;
        boolean weather = HeckerStudio.enabled(getContext(), "clock_weather", true);
        int saved = canvas.save(); canvas.scale(density, density);
        float height = getHeight() / density - (weather ? 24 : 0);
        livingClock.draw(canvas, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(getContext()), date(), Locale.getDefault(), getWidth() / density,
                height, selected == 2 ? 3 : selected, homeReveal, true, true, opacity, color, size, 0, 0);
        canvas.restoreToCount(saved);
        HeckerHomeDepth.foreground(this, canvas);
        if (weather) {
            saved = canvas.save(); canvas.scale(density, density);
            float scale = getWidth() / density / 380f;
            canvas.translate(0, height); canvas.scale(scale, scale); drawWeather(canvas, 18);
            canvas.restoreToCount(saved);
        }
        return true;
    }
    private void drawWeather(Canvas canvas, float baseline) {
        String weather = HeckerClockWeather.text(); if (weather == null || weather.isEmpty()) return;
        android.graphics.drawable.Drawable icon = HeckerClockWeather.icon();
        paint.reset(); paint.setAntiAlias(true); paint.setColor(0xffe5edf5); paint.setTextSize(17);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        String label = android.text.TextUtils.ellipsize(weather, new android.text.TextPaint(paint),
                icon == null ? 344 : 316, android.text.TextUtils.TruncateAt.END).toString();
        float width = paint.measureText(label), start = (380 - width - (icon == null ? 0 : 28)) / 2;
        if (icon != null) { icon.setBounds(Math.round(start), Math.round(baseline - 19), Math.round(start + 21), Math.round(baseline + 2)); icon.draw(canvas); start += 28; }
        paint.setTextAlign(Paint.Align.LEFT); canvas.drawText(label, start, baseline, paint);
    }
    private float fitTime(Canvas c, String value, float x, float y, float size, int color, boolean bold, float width) {
        paint.setTypeface(Typeface.create(style.equals("mono") ? "monospace" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); paint.setTextSize(size);
        float actual = Math.min(size, size * width / Math.max(1, paint.measureText(value)));
        text(c, value, x, y, actual, color, bold, Paint.Align.CENTER); return actual;
    }
    private void line(Canvas c, float x, float y, float xx, float yy, int color, float width) {
        paint.setShader(null); paint.setColor(color); paint.setStrokeWidth(width); c.drawLine(x, y, xx, yy, paint);
    }
    private void metric(Canvas c, float x, float y, String value, String label, int symbol) {
        int color = 0xffa6dbd4; float pulse = animator == null ? 1 : .8f + .2f * (float) Math.sin(phase * Math.PI * 2);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.7f); paint.setColor(color); paint.setAlpha(Math.round(255 * pulse));
        if (symbol == 0) { c.drawRoundRect(x - 3, y, x + 3, y + 15, 3, 3, paint); c.drawCircle(x, y + 20, 5, paint); }
        else if (symbol == 1) { c.drawRoundRect(x - 7, y + 2, x + 7, y + 24, 2, 2, paint); c.drawLine(x - 3, y - 1, x + 3, y - 1, paint); }
        else { c.drawRect(x - 8, y + 4, x + 8, y + 21, paint); for (int i = -5; i <= 5; i += 5) { c.drawLine(x + i, y, x + i, y + 4, paint); c.drawLine(x + i, y + 21, x + i, y + 25, paint); } }
        paint.setAlpha(255); paint.setStyle(Paint.Style.FILL);
        text(c, value, x, y + 45, label.equals("ram") ? 14 : 20, 0xfff5f2e8, true, Paint.Align.CENTER);
        text(c, HeckerStudio.text(getContext(), label), x, y + 60, 10, 0xffbbc4ce, false, Paint.Align.CENTER);
    }
    private void drawAnalog(Canvas c) {
        float cx = 190, cy = 88, r = 82; paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); paint.setColor(0xff57616b); c.drawCircle(cx, cy, r, paint);
        for (int i = 0; i < 60; i++) { double a = Math.toRadians(i * 6 - 90); float len = i % 5 == 0 ? 10 : 4;
            line(c, cx + (r - 5) * (float) Math.cos(a), cy + (r - 5) * (float) Math.sin(a), cx + (r - 5 - len) * (float) Math.cos(a), cy + (r - 5 - len) * (float) Math.sin(a), 0xfff5f2e8, i % 5 == 0 ? 2 : 1); }
        Calendar t = Calendar.getInstance(); hand(c, cx, cy, (t.get(Calendar.HOUR) + t.get(Calendar.MINUTE) / 60f) * 30, 42, 4, 0xfff5f2e8);
        hand(c, cx, cy, t.get(Calendar.MINUTE) * 6, 64, 3, 0xfff5f2e8); hand(c, cx, cy, t.get(Calendar.SECOND) * 6, 69, 1, 0xffcf9986);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xfff5f2e8); c.drawCircle(cx, cy, 3, paint);
    }
    private void hand(Canvas c, float x, float y, float degree, float length, float width, int color) {
        double a = Math.toRadians(degree - 90); line(c, x, y, x + length * (float) Math.cos(a), y + length * (float) Math.sin(a), color, width);
    }
    /** Original line art: independently moving ears, shaded glasses and a gentle head tilt. */
    public static void drawCaracal(Canvas c, float x, float y, float size, float phase, boolean animate) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xfff0f1e9); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeJoin(Paint.Join.ROUND);
        int save = c.save(); c.translate(x, y); c.scale(size / 100, size / 100);
        float wave = animate ? (float) Math.sin(phase * Math.PI * 2) : 0; c.rotate(wave * 3, 50, 65);
        Path head = new Path(); head.moveTo(16, 54); head.cubicTo(8, 70, 17, 87, 50, 98); head.cubicTo(83, 87, 92, 70, 84, 54); head.cubicTo(73, 40, 27, 40, 16, 54); c.drawPath(head, p);
        for (int side = 0; side < 2; side++) { int ear = c.save(); if (side == 1) c.scale(-1, 1, 50, 50); c.rotate(wave * (side == 0 ? 2 : -3), 21, 55);
            Path shape = new Path(); shape.moveTo(17, 57); shape.lineTo(6, 8); shape.quadTo(26, 18, 39, 43); c.drawPath(shape, p);
            p.setColor(0xff687780); Path inside = new Path(); inside.moveTo(20, 48); inside.lineTo(13, 22); inside.lineTo(29, 43); c.drawPath(inside, p); p.setColor(0xfff0f1e9);
            c.drawLine(6, 8, 3, 0, p); c.drawLine(6, 8, 7, -3, p); c.drawLine(9, 12, 11, -1, p); c.restoreToCount(ear); }
        p.setStyle(Paint.Style.FILL); p.setColor(0x995e6b73); c.drawRoundRect(17, 56, 44, 73, 6, 6, p); c.drawRoundRect(56, 56, 83, 73, 6, 6, p);
        p.setColor(0xfff0f1e9); p.setStyle(Paint.Style.STROKE); c.drawRoundRect(17, 56, 44, 73, 6, 6, p); c.drawRoundRect(56, 56, 83, 73, 6, 6, p); c.drawLine(44, 60, 56, 60, p);
        Path nose = new Path(); nose.moveTo(46, 79); nose.quadTo(50, 75, 54, 79); nose.lineTo(50, 83); nose.close(); c.drawPath(nose, p); c.drawArc(41, 78, 50, 89, 0, 180, false, p); c.drawArc(50, 78, 59, 89, 0, 180, false, p);
        c.restoreToCount(save);
    }
}
