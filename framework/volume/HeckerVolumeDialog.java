// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.Dialog;
import android.content.*;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import com.android.systemui.plugins.VolumeDialog;
import com.android.systemui.plugins.VolumeDialogController;
import com.android.systemui.plugins.VolumeDialogController.State;
import com.android.systemui.plugins.VolumeDialogController.StreamState;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.volume.SafetyWarningDialogDelegate;
import com.android.systemui.volume.CsdWarningDialogDelegate;
import java.util.*;

/** Compact/expanded TouchWiz interaction, styled to match Hecker's One UI QS. */
public final class HeckerVolumeDialog implements VolumeDialog,VolumeDialogController.Callbacks {
    public static final String SETTING="hecker_oneui_volume";
    private final Context context;private final VolumeDialogController controller;private final VolumeDialog stock;
    private final SafetyWarningDialogDelegate.Factory safetyFactory;
    private final CsdWarningDialogDelegate.Factory csdFactory;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<Integer,HeckerVolumeSlider> sliders=new LinkedHashMap<>();
    private Dialog dialog;private SystemUIDialog safety,csd;private CsdWarningDialogDelegate csdDelegate;
    private HeckerBrightnessControl brightnessControl;private Button ringerButton;
    private State state;private Callback callback;private int type;private boolean enabled,expanded,dragging,registered;
    private final Runnable timeout=()->dismiss();
    private final ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean self){select();}};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){dismiss();if(Intent.ACTION_USER_SWITCHED.equals(i.getAction()))select();}};
    public HeckerVolumeDialog(Context context,VolumeDialogController controller,VolumeDialog stock,
            SafetyWarningDialogDelegate.Factory safetyFactory,CsdWarningDialogDelegate.Factory csdFactory){
        this.context=context;this.controller=controller;this.stock=stock;this.safetyFactory=safetyFactory;this.csdFactory=csdFactory;
    }
    @Override public void init(int type,Callback callback){
        this.type=type;this.callback=callback;
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(SETTING),false,observer,UserHandle.USER_ALL);
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_SWITCHED);f.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);select();
    }
    private void select(){
        boolean selected=Settings.Secure.getIntForUser(context.getContentResolver(),SETTING,1,android.app.ActivityManager.getCurrentUser())!=0;
        if(registered&&enabled==selected)return;
        if(registered){if(enabled){dismiss();controller.removeCallback(this);}else stock.destroy();}
        enabled=selected;registered=true;
        if(enabled){controller.addCallback(this,main);controller.getState();}else stock.init(type,callback);
    }
    @Override public void destroy(){
        if(registered){if(enabled)controller.removeCallback(this);else stock.destroy();}
        dismiss();if(safety!=null)safety.dismiss();if(csd!=null)csd.dismiss();if(csdDelegate!=null)csdDelegate.destroy();
        context.getContentResolver().unregisterContentObserver(observer);context.unregisterReceiver(receiver);registered=false;
    }
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private TextView label(CharSequence text,int size){TextView v=new TextView(context);v.setText(text);v.setTextColor(0xFFEAF4FF);v.setTextSize(size);v.setGravity(Gravity.CENTER);return v;}
    private Button button(int title,Runnable action){Button b=new Button(context);b.setText(title);b.setTextColor(0xFFEAF4FF);b.setAllCaps(false);b.setOnClickListener(v->{action.run();schedule();});return b;}
    private int active(){return state!=null&&state.states.get(state.activeStream)!=null?state.activeStream:AudioManager.STREAM_MUSIC;}
    private void render(){
        if(dialog==null)return;if(brightnessControl!=null){brightnessControl.close();brightnessControl=null;}ringerButton=null;sliders.clear();
        LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(10),dp(10),dp(10));
        GradientDrawable background=new GradientDrawable();background.setColor(0xDA12283D);background.setCornerRadius(dp(30));background.setStroke(dp(1),0x40FFFFFF);root.setBackground(background);
        LinearLayout bars=new LinearLayout(context);bars.setGravity(Gravity.CENTER);bars.setBaselineAligned(false);
        LinkedHashSet<Integer> streams=new LinkedHashSet<>();streams.add(active());
        if(expanded){streams.add(AudioManager.STREAM_MUSIC);streams.add(AudioManager.STREAM_RING);streams.add(AudioManager.STREAM_NOTIFICATION);streams.add(AudioManager.STREAM_ALARM);}
        for(int stream:streams){
            StreamState ss=state==null?null:state.states.get(stream);if(ss==null)continue;
            LinearLayout column=new LinearLayout(context);column.setOrientation(LinearLayout.VERTICAL);column.setGravity(Gravity.CENTER);
            CharSequence title=ss.remoteLabel;
            if(title==null&&ss.name!=0)try{title=context.getText(ss.name);}catch(RuntimeException ignored){}
            if(title==null)title=context.getString(R.string.hecker_volume_media);
            TextView text=label(title,10);text.setMaxLines(2);column.addView(text,new LinearLayout.LayoutParams(-1,dp(34)));
            HeckerVolumeSlider slider=new HeckerVolumeSlider(context);slider.setContentDescription(title);
            slider.listener(new HeckerVolumeSlider.Listener(){
                public void change(int progress){controller.setActiveStream(stream,true);controller.setStreamVolume(stream,progress,true);controller.userActivity();schedule();}
                public void tracking(boolean active){dragging=active;if(active)main.removeCallbacks(timeout);else schedule();}
            });column.addView(slider,new LinearLayout.LayoutParams(dp(48),dp(expanded?172:206)));sliders.put(stream,slider);
            bars.addView(column,new LinearLayout.LayoutParams(dp(54),-2));
        }
        root.addView(bars);
        if(expanded){
            TextView brightness=label(context.getText(R.string.hecker_volume_brightness),12);root.addView(brightness);
            SeekBar slider=new SeekBar(context);slider.setContentDescription(brightness.getText());
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF9DD2FF));
            root.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));brightnessControl=new HeckerBrightnessControl(context,slider);
            slider.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){dragging=true;main.removeCallbacks(timeout);}else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;schedule();}return false;});
            Button ringer=button(R.string.hecker_volume_ringer,()->{int mode=state==null?AudioManager.RINGER_MODE_NORMAL:state.ringerModeInternal;
                int next=mode==AudioManager.RINGER_MODE_NORMAL?(controller.hasVibrator()?AudioManager.RINGER_MODE_VIBRATE:AudioManager.RINGER_MODE_SILENT):mode==AudioManager.RINGER_MODE_VIBRATE?AudioManager.RINGER_MODE_SILENT:AudioManager.RINGER_MODE_NORMAL;controller.setRingerMode(next,false);});ringerButton=ringer;root.addView(ringer);
        }
        Button expand=button(expanded?R.string.hecker_volume_collapse:R.string.hecker_volume_expand,()->{expanded=!expanded;render();});
        expand.setText(expanded?"⌃":"•••");expand.setContentDescription(context.getText(expanded?R.string.hecker_volume_collapse:R.string.hecker_volume_expand));root.addView(expand);
        if(expanded){root.addView(button(R.string.hecker_volume_settings,()->{dismiss();HeckerQsActions.settings(Settings.ACTION_SOUND_SETTINGS);}));
            root.addView(button(R.string.hecker_volume_captions,()->controller.getCaptionsEnabledState(true)));}
        ScrollView scroll=new ScrollView(context);scroll.addView(root);dialog.setContentView(scroll);Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            window.setType(type);window.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);window.setBackgroundBlurRadius(dp(36));
            WindowManager.LayoutParams p=window.getAttributes();p.width=expanded?Math.min(dp(340),context.getResources().getDisplayMetrics().widthPixels-dp(32)):dp(82);int maxHeight=Math.max(dp(120),context.getResources().getDisplayMetrics().heightPixels-dp(64));root.measure(View.MeasureSpec.makeMeasureSpec(p.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST));p.height=Math.min(maxHeight,root.getMeasuredHeight());p.x=dp(14);p.setTitle("Hecker One UI volume");window.setAttributes(p);}
        update();
    }
    private void update(){
        if(state==null)return;
        if(ringerButton!=null)ringerButton.setText(state.ringerModeInternal==AudioManager.RINGER_MODE_NORMAL?R.string.hecker_volume_sound:state.ringerModeInternal==AudioManager.RINGER_MODE_VIBRATE?R.string.hecker_volume_vibrate:R.string.hecker_volume_mute);
        for(Map.Entry<Integer,HeckerVolumeSlider> row:sliders.entrySet()){
            int stream=row.getKey();StreamState ss=state.states.get(stream);if(ss==null)continue;
            row.getValue().range(ss.levelMin,ss.levelMax,ss.muted?ss.levelMin:ss.level);
            boolean blocked=stream==AudioManager.STREAM_MUSIC?state.disallowMedia:stream==AudioManager.STREAM_ALARM?state.disallowAlarms:(stream==AudioManager.STREAM_RING||stream==AudioManager.STREAM_NOTIFICATION)?state.disallowRinger:stream==AudioManager.STREAM_SYSTEM?state.disallowSystem:false;
            row.getValue().setEnabled(!blocked);
        }
    }
    private void schedule(){
        main.removeCallbacks(timeout);if(dragging)return;
        AccessibilityManager accessibility=context.getSystemService(AccessibilityManager.class);
        int delay=accessibility.getRecommendedTimeoutMillis(expanded?6000:3500,AccessibilityManager.FLAG_CONTENT_CONTROLS|AccessibilityManager.FLAG_CONTENT_TEXT);
        main.postDelayed(timeout,delay);controller.userActivity();
    }
    private void dismiss(){if(brightnessControl!=null){brightnessControl.close();brightnessControl=null;}main.removeCallbacks(timeout);dragging=false;if(dialog!=null){Dialog old=dialog;dialog=null;old.dismiss();}sliders.clear();controller.notifyVisible(false);}
    @Override public void onShowRequested(int reason,boolean locked,int taskMode){
        if(!enabled)return;
        if(dialog==null){expanded=false;dialog=new Dialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setCanceledOnTouchOutside(true);
            dialog.setOnDismissListener(d->{if(dialog==d)dialog=null;controller.notifyVisible(false);main.removeCallbacks(timeout);});render();dialog.show();render();}
        controller.notifyVisible(true);controller.getState();schedule();
    }
    @Override public void onDismissRequested(int reason){dismiss();}
    @Override public void onStateChanged(State next){int old=active();state=next;if(dialog!=null&&(old!=active()||sliders.isEmpty()))render();else update();}
    @Override public void onLayoutDirectionChanged(int direction){if(dialog!=null)render();}
    @Override public void onConfigurationChanged(){dismiss();}
    @Override public void onShowVibrateHint(){}
    @Override public void onShowSilentHint(){}
    @Override public void onScreenOff(){dismiss();}
    @Override public void onShowSafetyWarning(int flags){if(safety==null){safety=safetyFactory.create(()->safety=null).createDialog();safety.show();}}
    @Override public void onShowCsdWarning(int warning,int duration){
        if(csd!=null)csd.dismiss();
        csdDelegate=csdFactory.create(warning,()->{csd=null;if(csdDelegate!=null)csdDelegate.destroy();csdDelegate=null;},Optional.empty());
        csd=csdDelegate.createDialog();csdDelegate.maybeShow(csd);SystemUIDialog owned=csd;
        if(duration>0&&owned!=null)main.postDelayed(owned::dismiss,duration);
    }
    @Override public void onAccessibilityModeChanged(Boolean show){if(dialog!=null)render();}
    @Override public void onCaptionComponentStateChanged(Boolean enabled,Boolean tooltip){}
    @Override public void onCaptionEnabledStateChanged(Boolean enabled,Boolean toggle){if(Boolean.TRUE.equals(toggle))controller.setCaptionsEnabledState(!Boolean.TRUE.equals(enabled));}
    @Override public void onVolumeChangedFromKey(){if(dialog!=null)schedule();}
}
