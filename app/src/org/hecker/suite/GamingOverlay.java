// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.content.pm.PackageManager;
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
import android.widget.TextView;
import java.util.List;

/** Compact native game tools. Only this panel's bounds intercept touches; the game stays visible. */
final class GamingOverlay {
    interface Actions {
        void panelChanged(boolean open);
        void clean(); void screenshot(); void record(); void focus(); void internet();
        void shortcut(String pkg); void chooseShortcuts(); void toggleShortcut(String pkg);
        void brightness(float brightness); int brightnessLevel();
        boolean performanceSupported(); boolean modeSupported(int mode); int gameMode(); void gameMode(int mode);
        String gameLabel(); android.graphics.drawable.Drawable gameIcon(); void gameSpace();
        int bypassState(); void toggle(String key);
    }
    private final Context c;
    private final WindowManager wm;
    private final Actions actions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View handle, panel, touchLock;
    private ScrollView content;
    private GamingBoosterUi.MetricPill hud;
    private TextView fpsText, ramText, recordText, systemText, bypassText;
    private GamingBoosterUi.LoadBar cpuBar, gpuBar;
    private GamingUi.Chart chart;
    private GamingBoosterUi.GlassSlider volumeSlider,brightnessSlider;
    private int cpu=-1, gpu=-1, ram=-1, page;
    private WindowManager.LayoutParams hudParams;
    private String fps = "—", temperature = "—", battery = "—", wifi = "—";
    private boolean visible, open, captureHidden;
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
    private boolean left() { return GamingPrefs.get(c).getBoolean("left", false); }
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
        int oldPage=page, oldScroll=content==null?0:content.getScrollY();
        hide(); page=oldPage;
        if (wasVisible) {
            show(wasOpen);
            if (wasOpen && oldPicker != null) showShortcutPicker(oldPicker);
            else if (content!=null) content.post(()->{if(content!=null)content.scrollTo(0,oldScroll);});
        }
    }
    private void buildHandle() {
        FrameLayout v = new FrameLayout(c);
        v.setBackground(GamingBoosterUi.glass(c, 0xd90b101b, 16));
        GamingUi.Icon icon = new GamingUi.Icon(c, "gamepad", Color.WHITE);
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
        hud = new GamingBoosterUi.MetricPill(c); hud.setFocusable(true);
        hud.setContentDescription(c.getString(R.string.gaming_open_dashboard));
        WindowMetrics metrics=wm.getCurrentWindowMetrics();
        Rect screen=metrics.getBounds();
        Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        final int minX=insets.left+dp(6),minY=insets.top+dp(6),width=Math.min(dp(170),Math.max(1,screen.width()-insets.left-insets.right-dp(12)));
        final int rangeX=Math.max(0,screen.width()-insets.right-dp(6)-width-minX),rangeY=Math.max(0,screen.height()-insets.bottom-dp(6)-dp(40)-minY);
        hudParams = params(width, dp(40), false); hudParams.gravity = Gravity.LEFT | Gravity.TOP;
        hudParams.x=minX+Math.round(GamingPrefs.get(c).getFloat("hud_x",1f)*rangeX);
        hudParams.y=minY+Math.round(GamingPrefs.get(c).getFloat("hud_y",0f)*rangeY);
        hud.setOnClickListener(v -> openPanel());
        hud.setOnTouchListener(new View.OnTouchListener() {
            float x, y; int px, py; boolean moved;
            public boolean onTouch(View view, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    x=e.getRawX(); y=e.getRawY(); px=hudParams.x; py=hudParams.y; moved=false; return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE ) {
                    if (Math.abs(e.getRawX()-x)+Math.abs(e.getRawY()-y)>dp(8)) moved=true;
                    if (moved) {
                        hudParams.x=Math.max(minX,Math.min(minX+rangeX,px+(int)(e.getRawX()-x)));
                        hudParams.y=Math.max(minY,Math.min(minY+rangeY,py+(int)(e.getRawY()-y)));
                        wm.updateViewLayout(view, hudParams);
                    }
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!moved) view.performClick();
                    else GamingPrefs.get(c).edit()
                            .putFloat("hud_x", (hudParams.x-minX)/(float)Math.max(1,rangeX))
                            .putFloat("hud_y", (hudParams.y-minY)/(float)Math.max(1,rangeY)).apply();
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
                    if (event.getAction() == KeyEvent.ACTION_UP) { if(page!=0||picker!=null){page=0;picker=null;showPage();}else closePanel(); }
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(GamingBoosterUi.glass(c,0xc90b101b,20));
        shell.setPadding(dp(10),dp(10),dp(10),dp(8));
        shell.setElevation(dp(10)); shell.setFocusableInTouchMode(true);
        content=new ScrollView(c); content.setFillViewport(false); content.setVerticalScrollBarEnabled(true); content.setScrollbarFadingEnabled(false); content.setVerticalFadingEdgeEnabled(true); content.setFadingEdgeLength(dp(16));
        shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        showPage(); buildDock(shell);
        WindowManager.LayoutParams p=params(dp(bounds.width),dp(bounds.height),true);
        p.x=dp(8)+(left()?bars.left:bars.right);p.y=(bars.top-bars.bottom)/2;
        try {
            wm.addView(shell, p); panel=shell; shell.requestFocus();
            if (handle != null) handle.setVisibility(View.GONE);
            if (hud != null) hud.setVisibility(View.GONE);
            actions.panelChanged(true);
        } catch (RuntimeException e) { open=false; throw e; }
    }
    private void showPage() {
        clearTelemetry();
        LinearLayout main=GamingUi.column(c);
        if(page==1)buildSettings(main);else if(page==2)buildMonitor(main);else buildTools(main);
        content.removeAllViews();content.addView(main);content.scrollTo(0,0);
        update(fps,temperature,battery,wifi);
    }
    private View iconButton(String icon, int title, Runnable action) {
        FrameLayout v=new FrameLayout(c);
        v.addView(new GamingUi.Icon(c, icon, GamingUi.MUTED),
                new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        v.setContentDescription(c.getString(title)); v.setFocusable(true);
        v.setOnClickListener(w -> action.run()); return v;
    }
    private void buildTools(LinearLayout main) {
        Rect bounds = wm.getCurrentWindowMetrics().getBounds();
        if (bounds.width() > bounds.height()) { buildCompactTools(main); return; }
        FrameLayout header=new FrameLayout(c);
        header.addView(new GamingBoosterUi.Rings(c,actions.gameMode()),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=GamingUi.row(c);top.setPadding(dp(10),dp(8),dp(10),0);
        cpuBar=new GamingBoosterUi.LoadBar(c,"CPU",actions.gameMode());
        top.addView(cpuBar,new LinearLayout.LayoutParams(dp(24),dp(55)));
        LinearLayout frames=metric("FPS",fps,22);fpsText=(TextView)frames.getChildAt(1);
        top.addView(frames,new LinearLayout.LayoutParams(0,dp(58),1));
        ImageView game=new ImageView(c);game.setImageDrawable(actions.gameIcon());game.setContentDescription(actions.gameLabel());
        top.addView(game,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout memory=metric("RAM",number(ram),22);ramText=(TextView)memory.getChildAt(1);
        top.addView(memory,new LinearLayout.LayoutParams(0,dp(58),1));
        gpuBar=new GamingBoosterUi.LoadBar(c,"GPU",actions.gameMode());top.addView(gpuBar,new LinearLayout.LayoutParams(dp(24),dp(55)));
        header.addView(top,new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP));
        LinearLayout modes=GamingUi.row(c);modes.setPadding(dp(6),0,dp(6),dp(8));
        mode(modes,R.string.gaming_performance,2);mode(modes,R.string.gaming_balanced,1);mode(modes,R.string.gaming_battery_saver,3);
        header.addView(modes,new FrameLayout.LayoutParams(-1,dp(45),Gravity.BOTTOM));
        main.addView(header,new LinearLayout.LayoutParams(-1,dp(102)));GamingUi.gap(main,12);
        actionRow(main,R.string.gaming_game_mode,pref("focus"),actions::focus,R.string.gaming_screenshot,false,actions::screenshot);
        LinearLayout record=GamingUi.row(c);
        recordText=tile(record,GamingRecordingService.recording?R.string.gaming_stop_record:R.string.gaming_record,GamingRecordingService.recording,actions::record);
        tile(record,R.string.gaming_block_navigation,pref("block_navigation"),()->actions.toggle("block_navigation"));main.addView(record);GamingUi.gap(main,8);
        actionRow(main,R.string.gaming_block_edge,pref("block_edge"),()->actions.toggle("block_edge"),
                R.string.gaming_block_brightness,pref("block_brightness"),()->actions.toggle("block_brightness"));
        AudioManager audio=c.getSystemService(AudioManager.class);
        int brightness=actions.brightnessLevel();
        slider(main,"sun",R.string.gaming_brightness,0,255,brightness,value->actions.brightness(Math.max(.01f,value/255f)));
        slider(main,"volume",R.string.gaming_volume,audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                value->audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0));
        GamingUi.gap(main,6);
        actionRow(main,R.string.gaming_block_assistant,pref("block_assistant"),()->actions.toggle("block_assistant"),
                R.string.gaming_floating_menu,GamingPrefs.get(c).getBoolean("fps",true),()->actions.toggle("fps"));
        actionRow(main,R.string.gaming_system_monitor,false,()->{page=2;showPage();},R.string.gaming_booster_settings,false,()->{page=1;showPage();});
    }
    private void buildCompactTools(LinearLayout main) {
        LinearLayout metrics = GamingUi.row(c);
        LinearLayout frames = metric("FPS", fps, 20); fpsText = (TextView) frames.getChildAt(1);
        metrics.addView(frames, new LinearLayout.LayoutParams(0, dp(44), 1));
        ImageView game = new ImageView(c); game.setImageDrawable(actions.gameIcon()); game.setContentDescription(actions.gameLabel());
        metrics.addView(game, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout memory = metric("RAM", number(ram), 20); ramText = (TextView) memory.getChildAt(1);
        metrics.addView(memory, new LinearLayout.LayoutParams(0, dp(44), 1)); main.addView(metrics);
        LinearLayout modes = GamingUi.row(c);
        mode(modes, R.string.gaming_performance, 2); mode(modes, R.string.gaming_balanced, 1); mode(modes, R.string.gaming_battery_saver, 3);
        main.addView(modes, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout first = GamingUi.row(c);
        tile(first, R.string.gaming_game_mode, pref("focus"), actions::focus);
        tile(first, R.string.gaming_screenshot, false, actions::screenshot);
        recordText = tile(first, GamingRecordingService.recording ? R.string.gaming_stop_record : R.string.gaming_record, GamingRecordingService.recording, actions::record);
        tile(first, R.string.gaming_block_navigation, pref("block_navigation"), () -> actions.toggle("block_navigation"));
        compactRow(main, first);
        LinearLayout second = GamingUi.row(c);
        tile(second, R.string.gaming_block_edge, pref("block_edge"), () -> actions.toggle("block_edge"));
        tile(second, R.string.gaming_block_brightness, pref("block_brightness"), () -> actions.toggle("block_brightness"));
        tile(second, R.string.gaming_block_assistant, pref("block_assistant"), () -> actions.toggle("block_assistant"));
        tile(second, R.string.gaming_floating_menu, GamingPrefs.get(c).getBoolean("fps", true), () -> actions.toggle("fps"));
        compactRow(main, second);
        AudioManager audio = c.getSystemService(AudioManager.class);
        LinearLayout sliders = GamingUi.row(c);
        brightnessSlider = new GamingBoosterUi.GlassSlider(c, "sun", c.getString(R.string.gaming_brightness), 0, 255, actions.brightnessLevel(), value -> actions.brightness(Math.max(.01f, value / 255f)));
        volumeSlider = new GamingBoosterUi.GlassSlider(c, "volume", c.getString(R.string.gaming_volume), audio.getStreamMinVolume(AudioManager.STREAM_MUSIC), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.getStreamVolume(AudioManager.STREAM_MUSIC), value -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0));
        for (View slider : new View[] { brightnessSlider, volumeSlider }) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1); p.setMargins(dp(3), dp(3), dp(3), dp(3)); sliders.addView(slider, p);
        }
        main.addView(sliders);
        LinearLayout last = GamingUi.row(c);
        tile(last, R.string.gaming_system_monitor, false, () -> { page = 2; showPage(); });
        tile(last, R.string.gaming_booster_settings, false, () -> { page = 1; showPage(); });
        compactRow(main, last);
    }
    private void compactRow(LinearLayout main, LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) ((TextView) row.getChildAt(i)).setTextSize(10);
        main.addView(row); GamingUi.gap(main, 3);
    }
    private boolean pref(String key){return GamingPrefs.get(c).getBoolean(key,false);}
    private String number(int value){return value<0?"—":Integer.toString(value);}
    private LinearLayout metric(String label,String value,int size){
        LinearLayout v=GamingUi.column(c);v.setGravity(Gravity.CENTER);
        TextView name=GamingUi.text(c,label,9,GamingBoosterUi.MUTED,false);name.setGravity(Gravity.CENTER);v.addView(name);
        TextView n=GamingUi.text(c,value,size,Color.WHITE,false);n.setGravity(Gravity.CENTER);n.setSingleLine();v.addView(n);return v;
    }
    private void mode(LinearLayout row,int label,int mode){
        boolean selected=actions.gameMode()==mode,supported=actions.modeSupported(mode);
        TextView v=GamingUi.text(c,c.getString(label),12,selected?Color.WHITE:GamingBoosterUi.MUTED,false);
        v.setGravity(Gravity.CENTER);v.setSingleLine();v.setTextScaleX(.92f);v.setFocusable(true);v.setSelected(selected);
        v.setBackground(GamingBoosterUi.mode(c,mode,selected));v.setAlpha(supported?1f:.46f);v.setOnClickListener(w->actions.gameMode(mode));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(selected?34:27),selected?1.2f:1f);p.setMargins(dp(3),0,dp(3),0);row.addView(v,p);
    }
    private TextView tile(LinearLayout row,int label,boolean selected,Runnable action){
        TextView v=GamingUi.text(c,c.getString(label),12,Color.WHITE,false);v.setGravity(Gravity.CENTER);
        v.setPadding(dp(6),dp(4),dp(6),dp(4));v.setMaxLines(2);v.setMinHeight(dp(44));
        v.setBackground(GamingBoosterUi.glass(c,selected?0xc546527c:0xef272e3d,28));
        v.setFocusable(true);v.setSelected(selected);v.setOnClickListener(w->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);row.addView(v,p);return v;
    }
    private void actionRow(LinearLayout main,int a,boolean selectedA,Runnable runA,int b,boolean selectedB,Runnable runB){
        LinearLayout row=GamingUi.row(c);tile(row,a,selectedA,runA);tile(row,b,selectedB,runB);main.addView(row);GamingUi.gap(main,8);
    }
    private interface Progress{void change(int value);}
    private void slider(LinearLayout main,String icon,int label,int min,int max,int value,Progress action){
        GamingBoosterUi.GlassSlider slider=new GamingBoosterUi.GlassSlider(c,icon,c.getString(label),min,max,value,action::change);
        if(icon.equals("sun"))brightnessSlider=slider;else volumeSlider=slider;
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));p.setMargins(dp(3),dp(4),dp(3),dp(4));main.addView(slider,p);
    }
    private void title(LinearLayout main,int label){
        LinearLayout row=GamingUi.row(c);row.addView(iconButton("back",R.string.back,()->{page=0;showPage();}),new LinearLayout.LayoutParams(dp(32),dp(44)));
        GamingUi.weighted(row,GamingUi.text(c,c.getString(label),18,Color.WHITE,false));
        row.addView(iconButton("close",R.string.gaming_close_dashboard,this::closePanel),new LinearLayout.LayoutParams(dp(36),dp(44)));main.addView(row);GamingUi.gap(main,10);
    }
    private int bypassMessage(int state){
        return state<0?R.string.gaming_bypass_missing:state==5?R.string.gaming_bypass_permission:state==1?R.string.gaming_bypass_active:state==2?R.string.gaming_bypass_connect:
                state==3?R.string.gaming_bypass_battery:state==4?R.string.gaming_bypass_error:R.string.gaming_bypass_ready;
    }
    private void buildSettings(LinearLayout main){
        title(main,R.string.gaming_booster_settings);
        LinearLayout charging=GamingUi.column(c);charging.setPadding(dp(16),dp(16),dp(16),dp(16));charging.setBackground(GamingBoosterUi.glass(c,0xa6000000,20));
        charging.addView(GamingUi.text(c,c.getString(R.string.gaming_bypass),14,Color.WHITE,false));GamingUi.gap(charging,10);
        int state=actions.bypassState();bypassText=GamingUi.text(c,c.getString(bypassMessage(state)),12,GamingBoosterUi.MUTED,false);charging.addView(bypassText);GamingUi.gap(charging,12);
        LinearLayout options=GamingUi.row(c);
        tile(options,R.string.gaming_bypass_off,!pref("bypass"),()->{if(pref("bypass"))actions.toggle("bypass");});
        TextView on=tile(options,R.string.gaming_bypass_on,pref("bypass"),()->{if(!pref("bypass"))actions.toggle("bypass");});on.setEnabled(state>=0&&state!=5);on.setAlpha(state<0||state==5?.4f:1f);charging.addView(options);main.addView(charging);
        GamingUi.gap(main,12);main.addView(GamingUi.button(c,c.getString(R.string.gaming_open_library),false,()->{closePanel();actions.gameSpace();}));
    }
    private void buildMonitor(LinearLayout main){
        title(main,R.string.gaming_system_monitor);systemText=GamingUi.text(c,"",14,Color.WHITE,false);systemText.setPadding(dp(12),dp(12),dp(12),dp(12));main.addView(systemText);
        chart=new GamingUi.Chart(c);main.addView(chart,new LinearLayout.LayoutParams(-1,dp(96)));GamingUi.gap(main,12);
        main.addView(GamingUi.text(c,c.getString(R.string.gaming_frame_note),12,GamingBoosterUi.MUTED,false));GamingUi.gap(main,12);
        main.addView(GamingUi.button(c,c.getString(R.string.gaming_clean),false,actions::clean));GamingUi.gap(main,8);
        main.addView(GamingUi.button(c,c.getString(R.string.gaming_touch_lock),false,this::lock));GamingUi.gap(main,8);
        main.addView(GamingUi.button(c,c.getString(R.string.gaming_wifi),false,actions::internet));
    }
    private void buildDock(LinearLayout shell){
        LinearLayout dock=GamingUi.row(c);dock.setPadding(dp(4),0,dp(4),0);dock.setBackground(GamingBoosterUi.glass(c,0xc9000209,20));
        LinearLayout apps=GamingUi.row(c);
        for(String pkg:GamingPrefs.shortcuts(c))try{
            ImageView icon=new ImageView(c);icon.setImageDrawable(c.getPackageManager().getApplicationIcon(pkg));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setPadding(dp(3),dp(6),dp(3),dp(6));
            icon.setContentDescription(c.getString(R.string.gaming_open_popup,GamingCatalog.label(c,pkg)));icon.setFocusable(true);icon.setOnClickListener(v->{closePanel();actions.shortcut(pkg);});apps.addView(icon,new LinearLayout.LayoutParams(0,dp(44),1));
        }catch(PackageManager.NameNotFoundException ignored){}
        if(apps.getChildCount()==0){TextView choose=GamingUi.text(c,c.getString(R.string.gaming_shortcuts),12,Color.WHITE,false);choose.setPadding(dp(8),0,0,0);choose.setGravity(Gravity.CENTER_VERTICAL);choose.setFocusable(true);choose.setOnClickListener(v->actions.chooseShortcuts());apps.addView(choose,new LinearLayout.LayoutParams(-1,dp(44)));}
        dock.addView(apps,new LinearLayout.LayoutParams(0,dp(44),1));
        dock.addView(iconButton("edit",R.string.gaming_shortcuts,actions::chooseShortcuts),new LinearLayout.LayoutParams(dp(36),dp(44)));
        View divider=new View(c);divider.setBackgroundColor(0x665e6370);dock.addView(divider,new LinearLayout.LayoutParams(dp(1),dp(22)));
        dock.addView(iconButton("more",R.string.gaming_booster_settings,()->{page=1;showPage();}),new LinearLayout.LayoutParams(dp(32),dp(44)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));p.topMargin=dp(6);shell.addView(dock,p);
    }
    private void clearTelemetry(){volumeSlider=null;brightnessSlider=null;fpsText=null;ramText=null;cpuBar=null;gpuBar=null;chart=null;recordText=null;systemText=null;bypassText=null;}
    void closePanel() {
        boolean wasOpen=open; open=false; picker=null;
        remove(panel); panel=null; content=null; clearTelemetry();
        if (handle != null) handle.setVisibility(visible && !captureHidden ? View.VISIBLE : View.GONE);
        if (hud != null) hud.setVisibility(visible && !captureHidden ? View.VISIBLE : View.GONE);
        if (wasOpen) actions.panelChanged(false);
    }
    void update(String fps, String temperature, String battery, String wifi) {
        this.fps=fps; this.temperature=temperature; this.battery=battery; this.wifi=wifi;
        if(hud!=null)hud.update(fps,cpu,gpu,ram);
        if(volumeSlider!=null)volumeSlider.external(c.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_MUSIC));
        if(brightnessSlider!=null)brightnessSlider.external(actions.brightnessLevel());
        if(fpsText!=null)fpsText.setText(fps);if(ramText!=null)ramText.setText(number(ram));
        if(cpuBar!=null)cpuBar.value(cpu);if(gpuBar!=null)gpuBar.value(gpu);
        if(bypassText!=null)bypassText.setText(bypassMessage(actions.bypassState()));
        if(systemText!=null)systemText.setText("FPS  "+fps+"\nCPU  "+number(cpu)+" %\nGPU  "+number(gpu)+" %\nRAM  "+number(ram)+" %\n"+c.getString(R.string.gaming_battery_temp)+"  "+temperature+"\n"+c.getString(R.string.gaming_battery_label)+"  "+battery+"   ·   "+wifi);
        if (recordText != null) recordText.setText(GamingRecordingService.recording ? R.string.gaming_stop_record : R.string.gaming_record);
    }
    void telemetry(int cpu,int gpu,int ram){this.cpu=cpu;this.gpu=gpu;this.ram=ram;update(fps,temperature,battery,wifi);}
    void sample(float value) { if (chart != null) chart.sample(value); }
    void showShortcutPicker(List<GamingCatalog.App> apps) {
        if (!open || content == null) return;
        picker=apps; clearTelemetry();
        LinearLayout list=GamingUi.column(c);
        list.addView(GamingUi.text(c, c.getString(R.string.gaming_shortcuts_summary), 12, GamingUi.MUTED, false));
        GamingUi.gap(list, 8);
        list.addView(GamingUi.button(c, c.getString(R.string.gaming_done), true, () -> { picker=null; restyle(); }));
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
