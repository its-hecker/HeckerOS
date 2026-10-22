// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.List;

/** Compact native game tools. Only this panel's bounds intercept touches; the game stays visible. */
final class GamingOverlay {
    interface Actions {
        void panelChanged(boolean open);
        void clean(); void screenshot(); void record(); void focus(); void internet();
        void shortcut(String pkg); void chooseShortcuts(); void toggleShortcut(String pkg);
        void brightness(float brightness);
        boolean performanceSupported(); int gameMode(); void gameMode(int mode);
        String gameLabel(); void gameSpace();
    }
    private final Context c;
    private final WindowManager wm;
    private final Actions actions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View handle, panel, touchLock;
    private ScrollView content;
    private TextView hud, tempText, batteryText, wifiText, recordText, toolsTab, centerTab;
    private GamingUi.EngineGauge gauge;
    private GamingUi.Chart chart;
    private WindowManager.LayoutParams hudParams;
    private String fps = "—", temperature = "—", battery = "—", wifi = "—";
    private boolean visible, open, captureHidden, centerSelected;
    private List<GamingCatalog.App> picker;
    private final Runnable unlock = this::unlock;

    GamingOverlay(Context context, Actions actions) {
        Context display = context.createDisplayContext(context.getSystemService(DisplayManager.class).getDisplay(0));
        c = display.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        wm = c.getSystemService(WindowManager.class);
        this.actions = actions;
    }
    boolean isOpen() { return open; }
    private int dp(float value) { return GamingUi.dp(c, value); }
    private boolean left() { return GamingPrefs.get(c).getBoolean("left", true); }
    private WindowManager.LayoutParams params(int width, int height, boolean focusable) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | (focusable ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE), PixelFormat.TRANSLUCENT);
        p.setTitle("Hecker Game Space");
        p.gravity = (left() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
        return p;
    }
    void show() { show(true); }
    private void show(boolean expanded) {
        if (visible) return;
        visible = true;
        try {
            buildHandle();
            if (GamingPrefs.get(c).getBoolean("fps", true)) buildHud();
            if (expanded) openPanel();
        } catch (RuntimeException e) { hide(); throw e; }
    }
    void hide() {
        visible = false; captureHidden = false;
        closePanel(); unlock(); remove(handle); remove(hud);
        handle = null; hud = null;
    }
    void restyle() {
        boolean wasVisible = visible, wasOpen = open;
        List<GamingCatalog.App> oldPicker = picker;
        hide();
        if (wasVisible) {
            show(wasOpen);
            if (wasOpen && oldPicker != null) showShortcutPicker(oldPicker);
        }
    }
    private void buildHandle() {
        FrameLayout v = new FrameLayout(c);
        v.setBackground(GamingUi.engineBackground(c, 235));
        GamingUi.Icon icon = new GamingUi.Icon(c, "gamepad", GamingUi.accent(c));
        v.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        v.setContentDescription(c.getString(R.string.gaming_open_dashboard));
        v.setFocusable(true); v.setOnClickListener(w -> openPanel());
        v.setOnTouchListener(new View.OnTouchListener() {
            float down;
            public boolean onTouch(View view, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { down = e.getRawX(); return true; }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float inward = left() ? e.getRawX()-down : down-e.getRawX();
                    if (inward > dp(12)) openPanel();
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP) { view.performClick(); return true; }
                return true;
            }
        });
        WindowManager.LayoutParams p = params(dp(28), dp(56), false);
        wm.addView(v, p); handle = v;
    }
    private void buildHud() {
        hud = GamingUi.text(c, "", 11, GamingUi.TEXT, true);
        hud.setPadding(dp(9), dp(6), dp(9), dp(6));
        hud.setBackground(GamingUi.engineBackground(c, 225)); hud.setFocusable(true);
        hud.setContentDescription(c.getString(R.string.gaming_open_dashboard));
        Rect screen = wm.getCurrentWindowMetrics().getBounds();
        hudParams = params(-2, -2, false); hudParams.gravity = Gravity.LEFT | Gravity.TOP;
        hudParams.x = Math.round(GamingPrefs.get(c).getFloat("hud_x", .06f)*Math.max(0, screen.width()-dp(210)));
        hudParams.y = dp(32)+Math.round(GamingPrefs.get(c).getFloat("hud_y", .18f)*Math.max(0, screen.height()-dp(100)));
        hud.setOnClickListener(v -> openPanel());
        hud.setOnTouchListener(new View.OnTouchListener() {
            float x, y; int px, py; boolean moved;
            public boolean onTouch(View view, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    x=e.getRawX(); y=e.getRawY(); px=hudParams.x; py=hudParams.y; moved=false; return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE && e.getEventTime()-e.getDownTime()>250) {
                    if (Math.abs(e.getRawX()-x)+Math.abs(e.getRawY()-y)>dp(8)) moved=true;
                    if (moved) {
                        hudParams.x=Math.max(0, Math.min(screen.width()-view.getWidth(), px+(int)(e.getRawX()-x)));
                        hudParams.y=Math.max(dp(24), Math.min(screen.height()-view.getHeight()-dp(24), py+(int)(e.getRawY()-y)));
                        wm.updateViewLayout(view, hudParams);
                    }
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!moved) view.performClick();
                    else GamingPrefs.get(c).edit()
                            .putFloat("hud_x", hudParams.x/(float)Math.max(1, screen.width()-dp(210)))
                            .putFloat("hud_y", (hudParams.y-dp(32))/(float)Math.max(1, screen.height()-dp(100))).apply();
                    return true;
                }
                return true;
            }
        });
        wm.addView(hud, hudParams); update(fps, temperature, battery, wifi);
    }
    void openPanel() {
        if (!visible || open || captureHidden) return;
        open=true; picker=null;
        WindowMetrics metrics = wm.getCurrentWindowMetrics();
        Rect screen = metrics.getBounds();
        Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        float density = c.getResources().getDisplayMetrics().density;
        GamingPanelLayout bounds = GamingPanelLayout.fit(
                (int)((screen.width()-bars.left-bars.right)/density),
                (int)((screen.height()-bars.top-bars.bottom)/density));
        LinearLayout shell = new LinearLayout(c) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { closePanel(); return true; }
                return super.dispatchTouchEvent(event);
            }
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) closePanel();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        shell.setOrientation(LinearLayout.VERTICAL);
        int alpha = Math.round(Math.max(60, Math.min(100, GamingPrefs.get(c).getInt("opacity", 94)))*2.55f);
        shell.setBackground(GamingUi.engineBackground(c, alpha));
        shell.setPadding(dp(10), dp(4), dp(10), dp(8));
        shell.setElevation(dp(12)); shell.setFocusableInTouchMode(true);
        buildTabs(shell);
        // AT_MOST preserves intrinsic height. Short content can never produce the old empty full-height sheet.
        final int maxContentHeight = Math.max(1, dp(bounds.height)-dp(60));
        content = new ScrollView(c) {
            @Override protected void onMeasure(int width, int height) {
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(maxContentHeight, MeasureSpec.AT_MOST));
            }
        };
        content.setFillViewport(false); content.setVerticalScrollBarEnabled(false);
        shell.addView(content, new LinearLayout.LayoutParams(-1, -2));
        showPage();
        WindowManager.LayoutParams p = params(dp(bounds.width), -2, true);
        p.x = dp(8)+(left() ? bars.left : bars.right);
        p.y = (bars.top-bars.bottom)/2;
        if (wm.isCrossWindowBlurEnabled()) {
            p.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            p.setBlurBehindRadius(dp(20));
        }
        try {
            wm.addView(shell, p); panel=shell; shell.requestFocus();
            if (handle != null) handle.setVisibility(View.GONE);
            if (hud != null) hud.setVisibility(View.GONE);
            actions.panelChanged(true);
        } catch (RuntimeException e) { open=false; throw e; }
    }
    private void buildTabs(LinearLayout shell) {
        LinearLayout tabs=GamingUi.row(c);
        toolsTab=tab(R.string.gaming_game_tools, false);
        centerTab=tab(R.string.gaming_game_center, true);
        tabs.addView(toolsTab, new LinearLayout.LayoutParams(0, dp(44), 1));
        tabs.addView(centerTab, new LinearLayout.LayoutParams(0, dp(44), 1));
        tabs.addView(iconButton("close", R.string.gaming_close_dashboard, this::closePanel),
                new LinearLayout.LayoutParams(dp(40), dp(44)));
        shell.addView(tabs);
    }
    private TextView tab(int title, boolean center) {
        TextView tab=GamingUi.text(c, c.getString(title), 12, GamingUi.TEXT, true);
        tab.setGravity(Gravity.CENTER); tab.setFocusable(true);
        tab.setOnClickListener(v -> { centerSelected=center; picker=null; showPage(); });
        return tab;
    }
    private void showPage() {
        clearTelemetry();
        toolsTab.setSelected(!centerSelected); centerTab.setSelected(centerSelected);
        toolsTab.setTextColor(centerSelected ? GamingUi.MUTED : GamingUi.accent(c));
        centerTab.setTextColor(centerSelected ? GamingUi.accent(c) : GamingUi.MUTED);
        toolsTab.setBackground(GamingUi.background(c, centerSelected ? Color.TRANSPARENT : 0xff33333f, 10, false));
        centerTab.setBackground(GamingUi.background(c, centerSelected ? 0xff33333f : Color.TRANSPARENT, 10, false));
        LinearLayout main=GamingUi.column(c); main.setPadding(0, dp(4), 0, 0);
        if (centerSelected) buildCenter(main); else buildTools(main);
        content.removeAllViews(); content.addView(main); content.scrollTo(0, 0);
    }
    private View iconButton(String icon, int title, Runnable action) {
        FrameLayout v=new FrameLayout(c);
        v.addView(new GamingUi.Icon(c, icon, GamingUi.MUTED),
                new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        v.setContentDescription(c.getString(title)); v.setFocusable(true);
        v.setOnClickListener(w -> action.run()); return v;
    }
    private void buildTools(LinearLayout main) {
        LinearLayout telemetry=GamingUi.row(c);
        LinearLayout thermal=GamingUi.column(c);
        thermal.addView(GamingUi.text(c, c.getString(R.string.gaming_battery_temp), 9, GamingUi.MUTED, false));
        tempText=GamingUi.text(c, temperature, 13, GamingUi.TEXT, true); thermal.addView(tempText);
        wifiText=GamingUi.text(c, wifi, 10, GamingUi.MUTED, false); thermal.addView(wifiText);
        telemetry.addView(thermal, new LinearLayout.LayoutParams(0, -2, 1));
        gauge=new GamingUi.EngineGauge(c); gauge.setFps(fps);
        telemetry.addView(gauge, new LinearLayout.LayoutParams(dp(116), dp(70)));
        LinearLayout power=GamingUi.column(c); power.setGravity(Gravity.RIGHT);
        power.addView(GamingUi.text(c, c.getString(R.string.gaming_battery_label), 9, GamingUi.MUTED, false));
        batteryText=GamingUi.text(c, battery, 13, GamingUi.TEXT, true); power.addView(batteryText);
        telemetry.addView(power, new LinearLayout.LayoutParams(0, -2, 1)); main.addView(telemetry);
        chart=new GamingUi.Chart(c); main.addView(chart, new LinearLayout.LayoutParams(-1, dp(8)));
        GamingUi.gap(main, 5);
        if (actions.performanceSupported()) {
            LinearLayout modes=GamingUi.row(c);
            int current=actions.gameMode();
            mode(modes, R.string.gaming_balanced, 1, current);
            mode(modes, R.string.gaming_performance, 2, current);
            main.addView(modes);
        } else {
            TextView standard=GamingUi.text(c, c.getString(R.string.gaming_standard_profile), 11, GamingUi.MUTED, false);
            standard.setGravity(Gravity.CENTER); main.addView(standard, new LinearLayout.LayoutParams(-1, dp(28)));
        }
        GamingUi.gap(main, 7);
        LinearLayout first=GamingUi.row(c);
        tile(first, "clean", R.string.gaming_clean, false, actions::clean);
        tile(first, "focus", R.string.gaming_focus,
                GamingPrefs.get(c).getBoolean("focus", false)
                && c.getSystemService(android.app.NotificationManager.class).isNotificationPolicyAccessGranted(), actions::focus);
        main.addView(first); GamingUi.gap(main, 5);
        LinearLayout second=GamingUi.row(c);
        tile(second, "screenshot", R.string.gaming_screenshot, false, actions::screenshot);
        recordText=tile(second, "record", GamingRecordingService.recording ? R.string.gaming_stop_record : R.string.gaming_record,
                GamingRecordingService.recording, actions::record);
        main.addView(second); GamingUi.gap(main, 4);
        LinearLayout utilities=GamingUi.row(c);
        utility(utilities, "lock", R.string.gaming_touch_lock, this::lock);
        utility(utilities, "wifi", R.string.gaming_wifi, actions::internet);
        utility(utilities, "apps", R.string.gaming_shortcuts, () -> { centerSelected=true; showPage(); });
        main.addView(utilities);
        int brightness=android.provider.Settings.System.getInt(c.getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS, 128);
        slider(main, "sun", R.string.gaming_brightness, 255, brightness,
                value -> actions.brightness(Math.max(.01f, value/255f)));
        AudioManager audio=c.getSystemService(AudioManager.class);
        slider(main, "volume", R.string.gaming_volume, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                value -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0));
    }
    private void mode(LinearLayout row, int label, int value, int current) {
        TextView v=GamingUi.text(c, c.getString(label), 11, current==value ? Color.WHITE : GamingUi.MUTED, true);
        v.setGravity(Gravity.CENTER); v.setFocusable(true); v.setSelected(current==value);
        v.setBackground(GamingUi.background(c, current==value ? GamingUi.accent(c) : GamingUi.CARD, 9, false));
        v.setOnClickListener(w -> actions.gameMode(value));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0, dp(36), 1);
        p.setMargins(dp(2), 0, dp(2), 0); row.addView(v, p);
    }
    private TextView tile(LinearLayout row, String icon, int label, boolean selected, Runnable action) {
        LinearLayout tile=GamingUi.row(c); tile.setPadding(dp(10), dp(5), dp(7), dp(5));
        tile.setBackground(GamingUi.background(c, selected ? 0xff482833 : GamingUi.CARD, 10, false));
        tile.addView(new GamingUi.Icon(c, icon, selected ? GamingUi.accent(c) : GamingUi.TEXT),
                new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView name=GamingUi.text(c, c.getString(label), 11, GamingUi.TEXT, true);
        name.setPadding(dp(7), 0, 0, 0); name.setMaxLines(2);
        tile.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        tile.setFocusable(true); tile.setSelected(selected); tile.setContentDescription(c.getString(label));
        tile.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0, dp(48), 1);
        p.setMargins(dp(2), 0, dp(2), 0); row.addView(tile, p); return name;
    }
    private void utility(LinearLayout row, String icon, int label, Runnable action) {
        LinearLayout item=GamingUi.column(c); item.setGravity(Gravity.CENTER);
        item.addView(new GamingUi.Icon(c, icon, GamingUi.TEXT), new LinearLayout.LayoutParams(dp(17), dp(17)));
        TextView name=GamingUi.text(c, c.getString(label), 9, GamingUi.MUTED, false);
        name.setGravity(Gravity.CENTER); name.setPadding(0, dp(3), 0, 0); name.setMaxLines(2); item.addView(name);
        item.setFocusable(true); item.setContentDescription(c.getString(label)); item.setOnClickListener(v -> action.run());
        row.addView(item, new LinearLayout.LayoutParams(0, dp(48), 1));
    }
    private interface Progress { void change(int value); }
    private void slider(LinearLayout main, String icon, int label, int max, int progress, Progress callback) {
        LinearLayout row=GamingUi.row(c);
        row.addView(new GamingUi.Icon(c, icon, GamingUi.MUTED), new LinearLayout.LayoutParams(dp(17), dp(17)));
        SeekBar slider=new SeekBar(c); slider.setMax(max); slider.setProgress(progress);
        slider.setProgressTintList(ColorStateList.valueOf(GamingUi.accent(c)));
        slider.setThumbTintList(ColorStateList.valueOf(Color.WHITE)); slider.setContentDescription(c.getString(label));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
            public void onProgressChanged(SeekBar s, int value, boolean user) { if (user) callback.change(value); }
        });
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(36), 1)); main.addView(row);
    }
    private void buildCenter(LinearLayout main) {
        TextView game=GamingUi.text(c, actions.gameLabel(), 16, GamingUi.TEXT, true);
        game.setMaxLines(2); game.setPadding(dp(4), dp(7), dp(4), dp(10)); main.addView(game);
        for (String pkg : GamingPrefs.shortcuts(c)) {
            try {
                LinearLayout app=GamingUi.row(c); app.setPadding(dp(9), dp(5), dp(9), dp(5));
                app.setBackground(GamingUi.background(c, GamingUi.CARD, 10, false));
                ImageView icon=new ImageView(c); icon.setImageDrawable(c.getPackageManager().getApplicationIcon(pkg));
                app.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
                String label=GamingCatalog.label(c, pkg);
                TextView name=GamingUi.text(c, label, 12, GamingUi.TEXT, true); name.setPadding(dp(10), 0, 0, 0);
                GamingUi.weighted(app, name); app.setContentDescription(c.getString(R.string.gaming_open_popup, label));
                app.setFocusable(true); app.setOnClickListener(v -> { closePanel(); actions.shortcut(pkg); });
                main.addView(app, new LinearLayout.LayoutParams(-1, dp(48))); GamingUi.gap(main, 5);
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        if (GamingPrefs.shortcuts(c).isEmpty()) {
            main.addView(GamingUi.text(c, c.getString(R.string.gaming_shortcuts_summary), 12, GamingUi.MUTED, false));
            GamingUi.gap(main, 10);
        }
        main.addView(GamingUi.button(c, c.getString(R.string.gaming_shortcuts), false, actions::chooseShortcuts));
        GamingUi.gap(main, 8);
        main.addView(GamingUi.button(c, c.getString(R.string.gaming_open_library), true, () -> { closePanel(); actions.gameSpace(); }));
    }
    private void clearTelemetry() {
        gauge=null; tempText=null; batteryText=null; wifiText=null; chart=null; recordText=null;
    }
    void closePanel() {
        boolean wasOpen=open; open=false; picker=null;
        remove(panel); panel=null; content=null; toolsTab=null; centerTab=null; clearTelemetry();
        if (handle != null) handle.setVisibility(visible && !captureHidden ? View.VISIBLE : View.GONE);
        if (hud != null) hud.setVisibility(visible && !captureHidden ? View.VISIBLE : View.GONE);
        if (wasOpen) actions.panelChanged(false);
    }
    void update(String fps, String temperature, String battery, String wifi) {
        this.fps=fps; this.temperature=temperature; this.battery=battery; this.wifi=wifi;
        if (hud != null) hud.setText(c.getString(R.string.gaming_fps, fps)+"  ·  "+temperature);
        if (gauge != null) gauge.setFps(fps);
        if (tempText != null) tempText.setText(temperature);
        if (batteryText != null) batteryText.setText(battery);
        if (wifiText != null) wifiText.setText(wifi);
        if (recordText != null) recordText.setText(GamingRecordingService.recording ? R.string.gaming_stop_record : R.string.gaming_record);
    }
    void sample(float value) { if (chart != null) chart.sample(value); }
    void showShortcutPicker(List<GamingCatalog.App> apps) {
        if (!open || content == null) return;
        picker=apps; clearTelemetry();
        LinearLayout list=GamingUi.column(c);
        list.addView(GamingUi.text(c, c.getString(R.string.gaming_shortcuts_summary), 12, GamingUi.MUTED, false));
        GamingUi.gap(list, 8);
        list.addView(GamingUi.button(c, c.getString(R.string.gaming_done), true, () -> { picker=null; centerSelected=true; showPage(); }));
        GamingUi.gap(list, 8);
        for (GamingCatalog.App app : apps) {
            boolean selected=GamingPrefs.shortcuts(c).contains(app.info.packageName);
            list.addView(GamingUi.button(c, (selected ? "✓  " : "")+app.label, selected,
                    () -> actions.toggleShortcut(app.info.packageName)));
            GamingUi.gap(list, 5);
        }
        content.removeAllViews(); content.addView(list);
    }
    void captureHidden(boolean hidden) {
        captureHidden=hidden;
        if (hidden) closePanel();
        if (handle != null) handle.setVisibility(visible && !hidden ? View.VISIBLE : View.INVISIBLE);
        if (hud != null) hud.setVisibility(visible && !hidden ? View.VISIBLE : View.INVISIBLE);
    }
    private void lock() {
        closePanel(); unlock();
        FrameLayout veil=new FrameLayout(c); veil.setBackgroundColor(0x10000000);
        TextView instruction=GamingUi.text(c, c.getString(R.string.gaming_hold_unlock), 16, GamingUi.TEXT, true);
        instruction.setPadding(dp(18), dp(14), dp(18), dp(14)); instruction.setBackground(GamingUi.engineBackground(c, 240));
        veil.addView(instruction, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        veil.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { handler.postDelayed(unlock, 2000); instruction.setAlpha(.6f); }
            else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(unlock); instruction.setAlpha(1);
            }
            return true;
        });
        veil.setContentDescription(c.getString(R.string.gaming_hold_unlock));
        WindowManager.LayoutParams p=params(-1, -1, false); p.gravity=Gravity.TOP | Gravity.LEFT;
        wm.addView(veil, p); touchLock=veil;
    }
    private void unlock() { handler.removeCallbacks(unlock); remove(touchLock); touchLock=null; }
    private void remove(View view) {
        if (view != null && view.isAttachedToWindow())
            try { wm.removeViewImmediate(view); } catch (IllegalArgumentException ignored) {}
    }
}
