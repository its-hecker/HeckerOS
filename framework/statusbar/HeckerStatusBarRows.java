// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.*;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.*;

/** Inline children of PhoneStatusBarView. Retains all native views, parents and touch dispatch. */
public final class HeckerStatusBarRows {
    private static final Map<View,Host> HOSTS=new WeakHashMap<>();
    private static final String[] KEYS={"hecker_dual_statusbar","hecker_dual_statusbar_height",
        "hecker_dual_statusbar_layout","hecker_statusbar_row_gap","hecker_statusbar_icon_size",
        "hecker_statusbar_lyrics","hecker_statusbar_lyric_width","hecker_statusbar_lyric_size",
        "hecker_statusbar_lyric_position","hecker_statusbar_lyric_color","hecker_statusbar_lyric_transition"};
    public static void attach(View root) {
        if(root instanceof FrameLayout&&!HOSTS.containsKey(root)){
            Host host=new Host((FrameLayout)root);HOSTS.put(root,host);host.start();
        }
    }
    public static void detach(View root){Host h=HOSTS.remove(root);if(h!=null)h.close();}
    public static void beforeMeasure(View root){Host h=HOSTS.get(root);if(h!=null)h.beforeMeasure();}
    public static void layout(View root){Host h=HOSTS.get(root);if(h!=null)h.layout();}
    public static void visibilityChanged(View root){Host h=HOSTS.get(root);if(h!=null)h.lifecycle(true);}
    private HeckerStatusBarRows(){}

    private static final class Host {
        final WeakReference<FrameLayout> reference;
        final Context context;
        final Handler main=new Handler(Looper.getMainLooper());
        final Map<String,Integer> ids=new HashMap<>();
        final Set<View> changed=Collections.newSetFromMap(new IdentityHashMap<>());
        final IdentityHashMap<View,float[]> scales=new IdentityHashMap<>();
        final IdentityHashMap<ViewGroup,boolean[]> clipping=new IdentityHashMap<>();
        final ContentObserver settings=new ContentObserver(main){@Override public void onChange(boolean self){read();}};
        final ViewTreeObserver.OnPreDrawListener preDraw=()->{lifecycle(false);return true;};
        final BroadcastReceiver events=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            String action=i.getAction();
            if(Intent.ACTION_SCREEN_OFF.equals(action)){awake=false;unlocked=false;}
            else if(Intent.ACTION_USER_SWITCHED.equals(action)){
                if(subscription!=null){subscription.close();subscription=null;}snapshot=null;clearLine();read();
            }else if(Intent.ACTION_SCREEN_ON.equals(action)||Intent.ACTION_USER_PRESENT.equals(action)){
                PowerManager power=context.getSystemService(PowerManager.class);
                awake=power!=null&&power.isInteractive();unlocked=!locked();
            }else if(Intent.ACTION_BATTERY_CHANGED.equals(action)){
                int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,0);
                battery=level>=0&&scale>0?Math.min(1f,level/(float)scale):Float.NaN;
                charging=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;if(dual)requestLayout();
            }
            if(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)){
                PowerManager power=context.getSystemService(PowerManager.class);saver=power!=null&&power.isPowerSaveMode();
                if(!motion()&&lyric!=null){animationGeneration++;lyric.animate().cancel();lyric.setAlpha(1);if(!TextUtils.equals(lyric.getText(),line))lyric.setText(line);}
            }
            lifecycle(false);
        }};
        FrameLayout layer;
        TextView lyric,info;
        TextClock date;
        LyricsController subscription;
        LyricsController.Snapshot snapshot;
        boolean closed,awake,unlocked,saver,active,dual,lyricsEnabled,charging,observing,receiving,drawing;
        int heightPreset,rowLayout,gap,icon,width,font,alignment,color,transition;
        int animationGeneration,lastInk;
        boolean tinted;
        float battery=Float.NaN;
        String line="";
        ViewTreeObserver tree;

        Host(FrameLayout root){
            reference=new WeakReference<>(root);context=root.getContext().getApplicationContext();
            PowerManager power=context.getSystemService(PowerManager.class);
            awake=power!=null&&power.isInteractive();saver=power!=null&&power.isPowerSaveMode();unlocked=!locked();
        }
        boolean locked(){KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
            return keyguard==null||keyguard.isKeyguardLocked();}
        int value(String key,int fallback){return Settings.Secure.getIntForUser(context.getContentResolver(),
                key,fallback,ActivityManager.getCurrentUser());}
        int bounded(String key,int fallback,int min,int max){return Math.max(min,Math.min(max,value(key,fallback)));}
        void start(){
            try{
                for(String key:KEYS){context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key),
                        false,settings,UserHandle.USER_ALL);observing=true;}
                IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);
                filter.addAction(Intent.ACTION_USER_SWITCHED);filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                context.registerReceiver(events,filter,null,main,Context.RECEIVER_NOT_EXPORTED);receiving=true;
                FrameLayout root=reference.get();
                if(root!=null){tree=root.getViewTreeObserver();tree.addOnPreDrawListener(preDraw);}
                read();
            }catch(RuntimeException unavailable){close();}
        }
        void read(){
            if(closed)return;
            dual=value("hecker_dual_statusbar",0)!=0;
            lyricsEnabled=value("hecker_statusbar_lyrics",0)!=0;
            heightPreset=bounded("hecker_dual_statusbar_height",0,0,2);
            rowLayout=bounded("hecker_dual_statusbar_layout",0,0,1);
            gap=bounded("hecker_statusbar_row_gap",2,0,8);
            icon=bounded("hecker_statusbar_icon_size",16,12,20);
            width=bounded("hecker_statusbar_lyric_width",160,80,360);
            font=bounded("hecker_statusbar_lyric_size",11,9,18);
            alignment=bounded("hecker_statusbar_lyric_position",2,0,2);
            color=value("hecker_statusbar_lyric_color",0);
            transition=bounded("hecker_statusbar_lyric_transition",0,0,2);
            tinted=false;clearLine();lifecycle(true);if(active)updateLine();requestLayout();
        }
        View find(String name){
            FrameLayout root=reference.get();if(root==null)return null;
            String pkg=root.getResources().getResourcePackageName(root.getId());
            int id=ids.computeIfAbsent(name,key->root.getResources().getIdentifier(key,"id",pkg));
            return id==0?null:root.findViewById(id);
        }
        void lifecycle(boolean checkLock){
            if(closed)return;
            FrameLayout root=reference.get();
            if(!dual&&!lyricsEnabled){active=false;if(subscription!=null){subscription.close();subscription=null;snapshot=null;}
                if(layer!=null)layer.setVisibility(View.GONE);return;}
            if(checkLock)unlocked=!locked();
            boolean shown=root!=null&&root.isAttachedToWindow()&&root.isShown()
                    &&root.getWindowVisibility()==View.VISIBLE&&awake&&unlocked;
            View nativeStart=find("status_bar_start_side_except_heads_up");
            View nativeEnd=find("status_bar_end_side_content");
            if(layer!=null){
                View clock=find("clock");int ink=clock instanceof TextView?((TextView)clock).getCurrentTextColor():0xffffffff;
                if(!tinted||ink!=lastInk){lastInk=ink;tinted=true;info.setTextColor(ink);date.setTextColor(ink);lyric.setTextColor(color==0?ink:color);}
                boolean nativeVisible=shown&&nativeStart!=null&&nativeStart.getVisibility()==View.VISIBLE
                        &&nativeEnd!=null&&nativeEnd.getVisibility()==View.VISIBLE;
                layer.setVisibility(nativeVisible&&drawing?View.VISIBLE:View.GONE);
                layer.setAlpha(nativeVisible?Math.min(nativeStart.getAlpha(),nativeEnd.getAlpha()):0);
            }
            if(lyricsEnabled){
                if(subscription==null)subscription=new LyricsController(context,LyricsController.STATUS_BAR,shown,
                        state->{if(!closed){snapshot=state;updateLine();}});
                else if(active!=shown)subscription.setVisible(shown);
            }else if(subscription!=null){subscription.close();subscription=null;snapshot=null;clearLine();}
            if(active!=shown){active=shown;if(!shown)clearLine();else updateLine();requestLayout();}
            if(lyric!=null)lyric.setSelected(shown&&drawing&&transition==1&&motion()&&lyric.getVisibility()==View.VISIBLE);
        }
        boolean motion(){return !saver&&ValueAnimator.areAnimatorsEnabled();}
        void updateLine(){
            main.removeCallbacks(tick);
            if(closed||!active||!lyricsEnabled||!awake||!unlocked||snapshot==null){clearLine();return;}
            StatusBarLyricsPolicy.Frame frame=StatusBarLyricsPolicy.frame(snapshot,SystemClock.elapsedRealtime());
            if(!frame.text.equals(line))showLine(frame.text);
            if(frame.delayMs>=0)main.postDelayed(tick,frame.delayMs);
        }
        final Runnable tick=this::updateLine;
        void clearLine(){
            main.removeCallbacks(tick);line="";animationGeneration++;
            if(!dual&&layer!=null){drawing=false;layer.setVisibility(View.GONE);}
            if(lyric!=null){lyric.animate().cancel();lyric.setAlpha(1);lyric.setText("");lyric.setSelected(false);lyric.setVisibility(View.GONE);}
            requestLayout();
        }
        void showLine(String text){
            line=text;ensureLayer();if(lyric==null)return;
            int serial=++animationGeneration;
            lyric.animate().cancel();lyric.setSelected(false);
            if(text.isEmpty()){lyric.setText("");lyric.setVisibility(View.GONE);lyric.setAlpha(1);}
            else if(transition==0&&motion()&&lyric.getVisibility()==View.VISIBLE&&lyric.length()>0){
                lyric.animate().alpha(0).setDuration(100).withEndAction(()->{
                    if(closed||serial!=animationGeneration||line.isEmpty())return;
                    lyric.setText(line);lyric.animate().alpha(1).setDuration(140).start();
                }).start();
            }else{lyric.setAlpha(1);lyric.setText(text);lyric.setVisibility(View.VISIBLE);}
            requestLayout();
        }
        void ensureLayer(){
            FrameLayout root=reference.get();if(layer!=null||root==null)return;
            layer=new FrameLayout(root.getContext());layer.setClickable(false);layer.setFocusable(false);
            layer.setClipChildren(true);root.addView(layer,new FrameLayout.LayoutParams(-1,-1));
            lyric=text();lyric.setSingleLine(true);lyric.setMarqueeRepeatLimit(-1);
            info=text();info.setSingleLine(true);
            date=new TextClock(root.getContext());date.setSingleLine(true);
            date.setFormat12Hour("EEE d");date.setFormat24Hour("EEE d");date.setClickable(false);date.setFocusable(false);
            layer.addView(date,new FrameLayout.LayoutParams(-2,-2));
            layer.addView(info,new FrameLayout.LayoutParams(-2,-2));
            layer.addView(lyric,new FrameLayout.LayoutParams(-2,-2));
            lyric.setVisibility(line.isEmpty()?View.GONE:View.VISIBLE);
        }
        TextView text(){FrameLayout root=reference.get();TextView view=new TextView(root.getContext());
            view.setClickable(false);view.setFocusable(false);view.setGravity(Gravity.CENTER_VERTICAL);
            view.setIncludeFontPadding(false);view.setEllipsize(TextUtils.TruncateAt.END);return view;}
        void requestLayout(){FrameLayout root=reference.get();if(!closed&&root!=null)root.requestLayout();}
        int dp(float value){FrameLayout root=reference.get();return Math.round(value*(root==null?1:root.getResources().getDisplayMetrics().density));}
        Rect bounds(View view){
            Rect r=new Rect(0,0,view.getWidth(),view.getHeight());
            reference.get().offsetDescendantRectToMyCoords(view,r);return r;
        }
        void place(View view,int left,int top,int width,int height){
            if(view==null)return;width=Math.max(0,width);height=Math.max(0,height);
            FrameLayout root=reference.get();if(root==null)return;
            ViewParent ancestor=view.getParent();
            while(ancestor instanceof ViewGroup&&ancestor!=root){
                ViewGroup group=(ViewGroup)ancestor;
                clipping.computeIfAbsent(group,k->new boolean[]{k.getClipChildren(),k.getClipToPadding()});
                group.setClipChildren(false);group.setClipToPadding(false);ancestor=group.getParent();
            }
            Rect parent=new Rect();
            if(view.getParent() instanceof View&&view.getParent()!=root)parent=bounds((View)view.getParent());
            view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            view.layout(left-parent.left,top-parent.top,left-parent.left+width,top-parent.top+height);
            changed.add(view);
        }
        void scale(View view,float factor,boolean end){
            if(view==null)return;
            scales.computeIfAbsent(view,k->new float[]{k.getScaleX(),k.getScaleY(),k.getPivotX(),k.getPivotY()});
            boolean rtl=view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
            view.setPivotX(end?(rtl?0:view.getWidth()):(rtl?view.getWidth():0));view.setPivotY(view.getHeight()/2f);
            view.setScaleX(factor);view.setScaleY(factor);
        }
        void beforeMeasure(){
            // Force native XML measurement again, including parents whose wrap-content cache
            // would otherwise retain the previous line's reserved space.
            FrameLayout root=reference.get();
            for(View view:changed){View current=view;while(current!=null&&current!=root){
                current.forceLayout();current=current.getParent() instanceof View?(View)current.getParent():null;}}
            changed.clear();
            for(Map.Entry<View,float[]> entry:scales.entrySet()){View v=entry.getKey();float[] x=entry.getValue();
                v.setScaleX(x[0]);v.setScaleY(x[1]);v.setPivotX(x[2]);v.setPivotY(x[3]);}
            scales.clear();
            for(Map.Entry<ViewGroup,boolean[]> entry:clipping.entrySet()){
                entry.getKey().setClipChildren(entry.getValue()[0]);entry.getKey().setClipToPadding(entry.getValue()[1]);}
            clipping.clear();
        }
        void layout(){
            FrameLayout root=reference.get();drawing=false;if(closed||root==null)return;
            View clock=find("clock"),notifications=find("notification_icon_area"),system=find("system_icons");
            View start=find("status_bar_start_side_container"),end=find("status_bar_end_side_container"),content=find("status_bar_contents");
            if(clock==null||notifications==null||system==null||start==null||end==null||content==null)return;
            boolean stacked=dual&&root.getHeight()+1>=dp(StatusBarRowsPolicy.heightDp(heightPreset));
            if(!stacked&&line.isEmpty()){if(layer!=null)layer.setVisibility(View.GONE);return;}
            ensureLayer();
            int top=content.getPaddingTop(),bottom=content.getPaddingBottom();
            StatusBarRowsPolicy.Row rows=StatusBarRowsPolicy.rows(root.getHeight(),top,bottom,dp(gap),stacked);
            if(rows.height<dp(12)){layer.setVisibility(View.GONE);return;}
            int ink=clock instanceof TextView?((TextView)clock).getCurrentTextColor():0xffffffff;
            info.setTextColor(ink);date.setTextColor(ink);lyric.setTextColor(color==0?ink:color);
            float density=root.getResources().getDisplayMetrics().scaledDensity;
            lyric.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.min(font*density,rows.height*.78f));
            info.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.min(11*density,rows.height*.72f));
            date.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.min(10*density,rows.height*.68f));
            Rect left=bounds(start),right=bounds(end),c=bounds(clock),n=bounds(notifications),s=bounds(system);
            boolean rtl=root.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
            int edgeLeft=Math.min(left.left,right.left),edgeRight=Math.max(left.right,right.right);
            View nativeIcons=find("notificationIcons");
            int nativeIconHeight=nativeIcons==null?rows.height:Math.max(1,nativeIcons.getMeasuredHeight());
            int iconY=rows.top;
            boolean lyricsRow=stacked&&rowLayout==1&&lyricsEnabled;
            if(stacked){
                place(clock,c.left,rows.top+(rows.height-c.height())/2,c.width(),c.height());
                place(system,s.left,rows.top+(rows.height-s.height())/2,s.width(),s.height());
                float factor=Math.min(1f,Math.min(dp(icon),rows.height-2f)/Math.max(1,dp(20)));
                scale(system,factor,true);scale(clock,Math.min(1f,rows.height/Math.max(1f,c.height())),false);
                if(lyricsRow){place(notifications,n.left,rows.top,n.width(),rows.height);date.setVisibility(View.GONE);info.setVisibility(View.GONE);}
                else{
                    iconY=rows.second;
                    place(notifications,left.left,rows.second,Math.max(0,left.width()),rows.height);
                    String detail=Float.isFinite(battery)?NumberFormat.getPercentInstance().format(battery)+(charging?" ⚡":""):"";
                    if(!TextUtils.equals(info.getText(),detail))info.setText(detail);
                    info.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);info.setVisibility(Float.isFinite(battery)?View.VISIBLE:View.GONE);
                    place(info,rtl?right.left:right.right-Math.min(dp(64),right.width()),rows.second,Math.min(dp(64),right.width()),rows.height);
                    date.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
                    date.setVisibility(clock.getVisibility()==View.VISIBLE?View.VISIBLE:View.GONE);
                    int dateWidth=Math.max(0,(rtl?c.left-left.left:left.right-c.right)-dp(8));
                    place(date,rtl?c.left-dp(4)-dateWidth:c.right+dp(4),rows.top,dateWidth,rows.height);
                }
                if(nativeIcons!=null){Rect box=bounds(nativeIcons);place(nativeIcons,box.left,iconY+(rows.height-nativeIconHeight)/2,box.width(),nativeIconHeight);}
            }else{date.setVisibility(View.GONE);info.setVisibility(View.GONE);}
            if(!line.isEmpty()){
                int slotLeft=lyricsRow?edgeLeft:stacked?edgeLeft+(rtl?dp(70):0):rtl?left.left:c.right;
                int slotRight=lyricsRow?edgeRight:stacked?edgeRight-(rtl?0:dp(70)):rtl?c.left:left.right;
                int y=stacked?rows.second:rows.top;
                int reserve=lyricsRow?0:dp(24);
                StatusBarRowsPolicy.Slot slot=StatusBarRowsPolicy.slot(slotLeft,slotRight,dp(width),dp(48),reserve,dp(4),alignment);
                if(slot.lyricWidth>0){
                    lyric.setVisibility(View.VISIBLE);lyric.setGravity(Gravity.CENTER_VERTICAL|(alignment==0?Gravity.LEFT:alignment==1?Gravity.CENTER_HORIZONTAL:Gravity.RIGHT));
                    lyric.setEllipsize(transition==1&&motion()?TextUtils.TruncateAt.MARQUEE:TextUtils.TruncateAt.END);
                    lyric.setSelected(transition==1&&motion());place(lyric,slot.lyricLeft,y,slot.lyricWidth,rows.height);
                    if(!lyricsRow){place(notifications,slot.iconsLeft,y,slot.iconsWidth,rows.height);
                        View icons=find("notificationIcons");if(icons!=null)place(icons,slot.iconsLeft,y+(rows.height-nativeIconHeight)/2,slot.iconsWidth,nativeIconHeight);}
                }else lyric.setVisibility(View.GONE);
            }else lyric.setVisibility(View.GONE);
            View icons=find("notificationIcons");
            if(stacked)scale(icons,Math.min(1f,Math.min(dp(icon),rows.height-2f)/Math.max(1,dp(20))),false);
            if(icons instanceof ViewGroup){ViewGroup group=(ViewGroup)icons;
                clipping.computeIfAbsent(group,k->new boolean[]{k.getClipChildren(),k.getClipToPadding()});
                group.setClipChildren(true);group.setClipToPadding(true);}
            drawing=stacked||(!line.isEmpty()&&lyric.getVisibility()==View.VISIBLE);
            lifecycle(false);
        }
        void close(){
            if(closed)return;closed=true;main.removeCallbacksAndMessages(null);animationGeneration++;
            if(subscription!=null){subscription.close();subscription=null;}
            if(observing)context.getContentResolver().unregisterContentObserver(settings);
            if(receiving)context.unregisterReceiver(events);
            if(tree!=null&&tree.isAlive())tree.removeOnPreDrawListener(preDraw);
            beforeMeasure();
            FrameLayout root=reference.get();if(layer!=null){lyric.animate().cancel();if(root!=null)root.removeView(layer);}
            layer=null;lyric=null;info=null;date=null;HOSTS.values().remove(this);
        }
    }
}
