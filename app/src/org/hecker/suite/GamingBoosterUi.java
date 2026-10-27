// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** Reference geometry and glass tracks, with real View semantics for assistive input. */
final class GamingBoosterUi {
    static final int MUTED=0xffa4a7b0, INK=0xff25323f, WHITE=0xfff2f8f8;
    static int dp(Context c,float n){return GamingUi.dp(c,n);}
    static int accent(int mode){return mode==2?0xffa739ed:mode==3?0xff18b9a1:0xff506aff;}
    static GradientDrawable glass(Context c,int color,int radius){
        GradientDrawable d=GamingUi.background(c,color,radius,false);d.setStroke(dp(c,1),0x405e6473);return d;
    }
    static GradientDrawable mode(Context c,int mode,boolean selected){
        int a=accent(mode);
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                selected?new int[]{a,Color.argb(180,Color.red(a)/3,Color.green(a)/3,Color.blue(a)/3)}:
                        new int[]{0xd9535864,0xb7252932});
        d.setCornerRadius(dp(c,24));d.setStroke(dp(c,selected?2:1),selected?a:0x505d626c);return d;
    }
    static final class Rings extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int mode;
        Rings(Context c,int mode){super(c);this.mode=mode;setBackground(glass(c,0xc200030b,18));setClipToOutline(true);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight();int a=accent(mode);p.setStyle(Paint.Style.STROKE);
            for(int i=0;i<4;i++){
                p.setStrokeWidth(dp(getContext(),9));p.setColor(Color.argb(35-i*5,Color.red(a),Color.green(a),Color.blue(a)));
                c.drawOval(w*.08f-i*w*.10f,h*.27f+i*h*.10f,w*.92f+i*w*.10f,h*1.27f+i*h*.3f,p);
            }
        }
    }
    static final class LoadBar extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String label;private final int mode;private int value=-1;
        private float displayed; private android.animation.ValueAnimator animation;
        LoadBar(Context c,String label,int mode){super(c);this.label=label;this.mode=mode;}
        void value(int v){
            if(v==value)return;
            value=v;setContentDescription(label+" "+(v<0?"—":v+"%"));
            if(animation!=null)animation.cancel();
            if(v<0||!isShown()||!android.animation.ValueAnimator.areAnimatorsEnabled()){
                displayed=Math.max(0,v);invalidate();return;
            }
            animation=android.animation.ValueAnimator.ofFloat(displayed,v);animation.setDuration(220);
            animation.addUpdateListener(a->{displayed=(float)a.getAnimatedValue();invalidate();});animation.start();
        }
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),s=getResources().getDisplayMetrics().scaledDensity;
            p.setStyle(Paint.Style.FILL);p.setColor(MUTED);p.setTextSize(9*s);p.setTextAlign(Paint.Align.CENTER);c.drawText(label,w/2,10*s,p);
            float x=w/2-dp(getContext(),5),y=dp(getContext(),20),segment=dp(getContext(),9);
            for(int i=0;i<3;i++){
                float lit=value<0?0:Math.max(0,Math.min(1,(displayed-(2-i)*100/3f)/(100/3f)));
                p.setColor(blend(0xff353b45,accent(mode),lit));
                c.drawRoundRect(x,y+i*segment,x+dp(getContext(),11),y+i*segment+segment-dp(getContext(),1),dp(getContext(),1),dp(getContext(),1),p);
            }
        }
        private int blend(int from,int to,float f){return Color.rgb((int)(Color.red(from)+(Color.red(to)-Color.red(from))*f),
                (int)(Color.green(from)+(Color.green(to)-Color.green(from))*f),(int)(Color.blue(from)+(Color.blue(to)-Color.blue(from))*f));}
    }
    static final class MetricPill extends LinearLayout {
        private final TextView[] values=new TextView[4];
        MetricPill(Context c){super(c);setGravity(Gravity.CENTER);setPadding(dp(c,5),dp(c,2),dp(c,5),dp(c,2));setBackground(glass(c,0xd9000914,24));
            String[] labels={"FPS","CPU %","GPU %","RAM %"};
            for(int i=0;i<4;i++){
                LinearLayout col=GamingUi.column(c);col.setGravity(Gravity.CENTER);
                TextView label=GamingUi.text(c,labels[i],7,MUTED,false);col.addView(label);
                values[i]=GamingUi.text(c,"—",14,Color.WHITE,false);values[i].setSingleLine();col.addView(values[i]);
                addView(col,new LinearLayout.LayoutParams(0,-2,1));
            }
        }
        void update(String fps,int cpu,int gpu,int ram){String[] v={fps,number(cpu),number(gpu),number(ram)};for(int i=0;i<4;i++)values[i].setText(v[i]);
            setContentDescription("FPS "+fps+", CPU "+number(cpu)+"%, GPU "+number(gpu)+"%, RAM "+number(ram)+"%. "+getContext().getString(R.string.gaming_open_dashboard));}
        private String number(int n){return n<0?"—":Integer.toString(n);}
    }
    interface Change{void value(int value);}
    /** Same 44dp track, 28% white background and 88% #F2F8F8 fill as HeckerGlassTrack. */
    static final class GlassSlider extends FrameLayout {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int min,max;private int value;private final Change change;private final String label;private boolean dragging;
        GlassSlider(Context c,String icon,String label,int min,int max,int value,Change change){super(c);this.min=min;this.max=Math.max(min+1,max);this.value=value;this.change=change;this.label=label;
            setWillNotDraw(false);setFocusable(true);setContentDescription(label);
            GamingUi.Icon glyph=new GamingUi.Icon(c,icon,INK);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(c,23),dp(c,23),Gravity.START|Gravity.CENTER_VERTICAL);p.leftMargin=dp(c,15);addView(glyph,p);
        }
        @Override protected void onDraw(Canvas c){
            float h=getHeight(),w=getWidth(),fraction=(value-min)/(float)(max-min);paint.setColor(0x47ffffff);c.drawRoundRect(0,0,w,h,h/2,h/2,paint);
            paint.setColor(0xe0f2f8f8);c.drawRoundRect(0,0,Math.min(w,Math.max(h,w*fraction)),h,h/2,h/2,paint);
        }
        void external(int n){if(!dragging){value=Math.max(min,Math.min(max,n));invalidate();}}
        private void set(int n){n=Math.max(min,Math.min(max,n));if(n==value)return;value=n;change.value(n);invalidate();sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);}
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);}
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){set(min+Math.round((max-min)*Math.max(0,Math.min(1,e.getX()/Math.max(1,getWidth())))));if(action==MotionEvent.ACTION_UP){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);performClick();}return true;}
            if(action==MotionEvent.ACTION_CANCEL){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;}return false;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){set(value+(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)*Math.max(1,(max-min)/20));return true;}return super.onKeyDown(code,event);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(SeekBar.class.getName());info.setContentDescription(label);info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,min,max,value));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
        @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){set(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){set(value+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1)*Math.max(1,(max-min)/20));return true;}return super.performAccessibilityAction(action,args);}
    }
    private GamingBoosterUi(){}
}
