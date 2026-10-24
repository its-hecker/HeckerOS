/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

/** Cosmetic native-prompt adapter. Authentication and dismissal stay in BiometricViewBinder. */
public final class HeckerAppLockPrompt {
    private static final java.util.WeakHashMap<ImageView, Orbit> ACTIVE = new java.util.WeakHashMap<>();
    private HeckerAppLockPrompt() {}
    public static boolean isSuitePrompt(android.content.Context context, String title, String subtitle) {
        try {
            android.content.res.Resources res = context.getPackageManager().getResourcesForApplication("org.hecker.suite");
            int id = res.getIdentifier("authenticate_title", "string", "org.hecker.suite");
            int detail = res.getIdentifier("authenticate_subtitle", "string", "org.hecker.suite");
            return id != 0 && detail != 0 && res.getString(id).equals(title) && res.getString(detail).equals(subtitle);
        } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) { return false; }
    }
    public static void bind(ImageView logo, ImageView sensor) {
        bindLogo(logo);
        Orbit old = ACTIVE.remove(sensor);
        if (old != null) { old.stop(); sensor.removeOnAttachStateChangeListener(old.listener); }
        Orbit ring = new Orbit(null);
        ring.listener = new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { ring.start(); }
            public void onViewDetachedFromWindow(View v) { ring.stop(); }
        };
        ACTIVE.put(sensor, ring); sensor.setForeground(ring);
        sensor.addOnAttachStateChangeListener(ring.listener);
        if (sensor.isAttachedToWindow()) ring.start();
    }
    private static void bindLogo(ImageView logo) {
        Orbit old = ACTIVE.remove(logo);
        if (old != null) { old.stop(); logo.removeOnAttachStateChangeListener(old.listener); }
        Orbit art = new Orbit(logo.getDrawable());
        art.listener = new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { art.start(); }
            public void onViewDetachedFromWindow(View v) { v.animate().cancel(); art.stop(); }
        };
        ACTIVE.put(logo, art);
        logo.setImageDrawable(art);
        logo.addOnAttachStateChangeListener(art.listener);
        if (logo.isAttachedToWindow()) art.start();
        logo.animate().cancel(); logo.setAlpha(1f); logo.setScaleX(1f); logo.setScaleY(1f);
        if (ValueAnimator.areAnimatorsEnabled()) {
            logo.setScaleX(.82f); logo.setScaleY(.82f); logo.setAlpha(0f);
            logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }
    public static void state(ImageView logo, ImageView sensor, int state) {
        Orbit ring = ACTIVE.get(sensor);
        if (ring != null) ring.state(state);
        if (logo.getDrawable() instanceof Orbit) ((Orbit) logo.getDrawable()).state(state);
    }
    private static final class Orbit extends Drawable {
        final Drawable icon;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ValueAnimator animator;
        View.OnAttachStateChangeListener listener;
        float phase;
        int status, opacity = 255;
        Orbit(Drawable original) { icon = original == null ? null : original.mutate(); }
        void start() {
            if (!isVisible()) return;
            if (!ValueAnimator.areAnimatorsEnabled() || animator != null || status == 1) { invalidateSelf(); return; }
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1400); animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(a -> { phase = (Float) a.getAnimatedValue(); invalidateSelf(); });
            animator.start();
        }
        void stop() { if (animator != null) { animator.cancel(); animator.removeAllUpdateListeners(); animator = null; } }
        void state(int value) { if (status == 1) return; status = value; if (value == 1) stop(); else start(); invalidateSelf(); }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (visible) start(); else stop();
            return changed;
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float size = Math.min(b.width(), b.height());
            if (size <= 0) return;
            float x=b.exactCenterX(), y=b.exactCenterY(), radius=size*.46f;
            paint.setAlpha(opacity); paint.setStyle(Paint.Style.FILL); paint.setColor(0xff14273d);
            if (icon != null) c.drawCircle(x,y,radius,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(size*.045f);
            paint.setColor(status==1?0xff60e5ae:status==2?0xffff8e9c:0xff55d9ff);
            float a=phase*360f;
            c.drawArc(x-radius,y-radius,x+radius,y+radius,status==1?-90:a,status==1?360:225,false,paint);
            if(status==1) {
                Path check=new Path();check.moveTo(x-size*.16f,y);check.lineTo(x-size*.03f,y+size*.12f);check.lineTo(x+size*.19f,y-size*.12f);
                c.drawPath(check,paint);
            } else {
                paint.setColor(0xffb19aff); paint.setStrokeWidth(size*.02f);
                c.drawArc(x-radius*.83f,y-radius*.83f,x+radius*.83f,y+radius*.83f,-a,100,false,paint);
                if(icon!=null){int inset=Math.round(size*.24f);icon.setBounds(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);icon.setAlpha(opacity);icon.draw(c);}
            }
        }
        @Override public void setAlpha(int a) { opacity=a; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 96; }
        @Override public int getIntrinsicHeight() { return 96; }
    }
}
