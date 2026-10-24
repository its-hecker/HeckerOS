// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;

/** One UI pill with proper key, TalkBack and set-progress support. */
public final class HeckerVolumeSlider extends View {
    public interface Listener { void change(int progress);void tracking(boolean active); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private Listener listener;
    private int min,max=1,value;
    private boolean tracking;
    private final float density;
    public HeckerVolumeSlider(Context context){super(context);density=getResources().getDisplayMetrics().density;setFocusable(true);setClickable(true);}
    public void listener(Listener value){listener=value;}
    public void range(int low,int high,int current){min=low;max=Math.max(low+1,high);if(!tracking)value=Math.max(min,Math.min(max,current));invalidate();}
    @Override protected void onDraw(Canvas canvas){
        float pad=4*density;rect.set(pad,pad,getWidth()-pad,getHeight()-pad);
        paint.setColor(isEnabled()?0xFF243C51:0xFF25303B);canvas.drawRoundRect(rect,rect.width()/2,rect.width()/2,paint);
        float fraction=(value-min)/(float)(max-min);int saved=canvas.save();
        Path clip=new Path();clip.addRoundRect(rect,rect.width()/2,rect.width()/2,Path.Direction.CW);canvas.clipPath(clip);
        paint.setColor(isEnabled()?0xFF9DD2FF:0xFF607385);canvas.drawRect(rect.left,rect.bottom-fraction*rect.height(),rect.right,rect.bottom,paint);canvas.restoreToCount(saved);
        paint.setColor(0xFFEAF4FF);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(12*density);
        canvas.drawText(Integer.toString(Math.round(fraction*100)),getWidth()/2f,28*density,paint);
    }
    private void update(int progress){value=Math.max(min,Math.min(max,progress));invalidate();if(listener!=null)listener.change(value);sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled())return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:tracking=true;getParent().requestDisallowInterceptTouchEvent(true);if(listener!=null)listener.tracking(true);break;
            case MotionEvent.ACTION_MOVE:break;
            case MotionEvent.ACTION_UP:performClick();break;
            case MotionEvent.ACTION_CANCEL:tracking=false;if(listener!=null)listener.tracking(false);return true;
            default:return false;
        }
        float fraction=1f-(event.getY()-4*density)/Math.max(1,getHeight()-8*density);
        update(min+Math.round(Math.max(0,Math.min(1,fraction))*(max-min)));
        if(event.getActionMasked()==MotionEvent.ACTION_UP){tracking=false;if(listener!=null)listener.tracking(false);}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(isEnabled()&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT)){
            update(value+((key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_RIGHT)?1:-1));return true;
        }return super.onKeyDown(key,event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,min,max,value));
        if(isEnabled()){info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(isEnabled()){
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){update(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));return true;}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){update(value+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1));return true;}
        }return super.performAccessibilityAction(action,args);
    }
}
