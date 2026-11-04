// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import com.android.systemui.res.R;

/** Decorative window: rope and astronaut never receive touches. No idle animation loop. */
final class MotionAstronautView extends View {
    private static final java.util.concurrent.ExecutorService ART=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final android.util.LruCache<Integer,Bitmap> CHARACTERS=new android.util.LruCache<>(3);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap atlas;
    private final float density;
    private Bitmap characterBitmap;
    private int character;
    private final float[] characterPose=new float[5];
    private final RectF characterBounds=new RectF();
    private long when = -1, sequence = -1;
    private int direction, action;
    private boolean animate;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            invalidate();
            if (when >= 0 && SystemClock.elapsedRealtime() - when < MotionIslandState.ACTION_DURATION)
                postDelayed(this, 33);
        }
    };
    MotionAstronautView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
        atlas = BitmapFactory.decodeResource(getResources(), R.drawable.hecker_motion_astronaut, options);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private float dp(float n) { return n * density; }
    void update(MotionIslandState state, boolean animate) {
        if(character!=state.character){character=state.character;characterBitmap=null;
            if(character>0){
                final int requested=character;
                ART.execute(()->{
                    Bitmap image=CHARACTERS.get(requested);
                    if(image==null){
                        int id=new int[]{0,R.drawable.hecker_motion_blue_monster,R.drawable.hecker_motion_goblin,R.drawable.hecker_motion_ninja}[requested];
                        BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inSampleSize=4;
                        image=BitmapFactory.decodeResource(getResources(),id,options);
                        if(image!=null)CHARACTERS.put(requested,image);
                    }
                    final Bitmap ready=image;
                    post(()->{if(character==requested&&isAttachedToWindow()){characterBitmap=ready;invalidate();}});
                });
            }
        }
        direction = state.direction; action = state.action;
        if (sequence != state.sequence || this.animate != animate) {
            stop(); sequence = state.sequence; when = state.when;
            if (animate) post(frame);
        }
        this.animate = animate;
        invalidate();
    }
    void stop() { removeCallbacks(frame); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long age = SystemClock.elapsedRealtime() - when;
        if (when < 0 || age < 0 || age >= MotionIslandState.ACTION_DURATION || atlas == null) return;
        float alpha = animate ? MotionIslandAnimation.alpha(age) : 1;
        if (alpha <= 0) return;
        if(character>0){if(characterBitmap!=null)drawCharacter(canvas,age,alpha);return;}
        int pose = animate ? MotionIslandAnimation.pose(age, action, direction) : 0;
        int save = canvas.save();
        // The window starts exactly at the painted capsule's bottom. Clip before all
        // transforms, including on the host preview, so no rope/body/sparkle can draw over it.
        canvas.clipRect(0, 0, getWidth(), getHeight());
        canvas.translate(getWidth() / 2f, dp(.4f));
        if (animate) canvas.rotate(MotionIslandAnimation.angle(age, direction));
        float rope = dp(animate ? MotionIslandAnimation.ropeLength(age, action) : 47)*getHeight()/dp(150);
        paint.setColor(0xffe7edf2); paint.setAlpha(Math.round(235 * alpha));
        paint.setStrokeWidth(dp(.8f)); paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(0, 0, 0, rope + dp(2), paint);
        // Each source cell is selected directly from the transparent atlas. The raised gripping
        // hand is registered to the rope endpoint across poses, independent of the free waving arm.
        float size = dp(105)*getHeight()/dp(150);
        canvas.translate(0, rope);
        if (animate) canvas.rotate(MotionIslandAnimation.bodyRock(age, action, direction));
        if (animate && action == MotionIslandState.PLAY) {
            float blend = MotionIslandAnimation.welcomePose(age);
            // Opaque articulated poses avoid the previous double-arm ghost during play.
            canvas.translate(0,dp((float)(-3*Math.sin(age*Math.PI/480)*Math.exp(-age/900.0))));
            drawPose(canvas,Math.round(blend),size,alpha);
        } else drawPose(canvas, pose, size, alpha);
        if (animate && action == MotionIslandState.PLAY) {
            float glow = MotionIslandAnimation.welcomeSparkle(age);
            paint.setColor(0xffb3e9fa); paint.setAlpha(Math.round(255 * alpha * glow));
            paint.setStrokeWidth(dp(.8f));
            sparkle(canvas, -dp(48), -dp(16), dp(2.7f));
            sparkle(canvas, dp(5), dp(10), dp(2));
        }
        canvas.restoreToCount(save);
    }
    private void drawCharacter(Canvas canvas,long age,float alpha){
        float[] pose=characterPose;
        MotionCharacterPose.sample(pose,character,action,direction,age);
        int save=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());
        float size=Math.min(getHeight()*.82f,getWidth()*.62f);
        canvas.translate(getWidth()/2f+size*pose[0],getHeight()*.55f+size*pose[1]);
        canvas.rotate(pose[2]);canvas.scale(pose[3],pose[4]);
        paint.setAlpha(Math.round(alpha*255));
        characterBounds.set(-size/2,-size/2,size/2,size/2);
        canvas.drawBitmap(characterBitmap,null,characterBounds,paint);
        canvas.restoreToCount(save);
    }
    private void drawPose(Canvas canvas, int pose, float size, float alpha) {
        int cell = atlas.getWidth() / 3;
        float[] gripX = {.762f, .697f, .612f}; float gripY = .292f;
        Rect source = new Rect(pose * cell, 0, (pose + 1) * cell, atlas.getHeight());
        RectF target = new RectF(-size * gripX[pose], -size * gripY,
                size * (1 - gripX[pose]), size * (1 - gripY));
        paint.setAlpha(Math.round(255 * alpha)); canvas.drawBitmap(atlas, source, target, paint);
    }
    private void sparkle(Canvas canvas, float x, float y, float radius) {
        canvas.drawLine(x - radius, y, x + radius, y, paint);
        canvas.drawLine(x, y - radius, x, y + radius, paint);
    }
}
