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
    private int character, anchorOffset;
    private final float[] characterPose=new float[5], joints=new float[3], mesh=new float[21*21*2];
    private final RectF characterBounds=new RectF();
    private long when = -1, sequence = -1;
    private int direction, action;
    private boolean animate;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            invalidate();
            if (when >= 0 && SystemClock.elapsedRealtime() - when < MotionIslandState.ACTION_DURATION)
                postOnAnimationDelayed(this, 32);
        }
    };
    MotionAstronautView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
        atlas = BitmapFactory.decodeResource(getResources(), R.drawable.hecker_motion_astronaut, options);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setAnchorOffset(int value) { anchorOffset = value; invalidate(); }
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
            if (animate) postOnAnimation(frame);
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
        canvas.translate(getWidth() / 2f + anchorOffset, dp(.4f));
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
        MotionCharacterPose.sample(pose,character,action,direction,animate?age:0);
        MotionCharacterPose.joints(joints,character,action,animate?age:0);
        int save=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());
        float size=Math.min(getHeight()*.65f,getWidth()*.62f);
        canvas.translate(getWidth()/2f+anchorOffset,0);
        if(animate)canvas.rotate(MotionIslandAnimation.angle(age,direction)*.65f);
        float center=getHeight()*.51f+size*pose[1];
        // Every companion hangs from the same capsule edge; the tether moves with its body.
        paint.setColor(0xffe7edf2);paint.setAlpha(Math.round(alpha*235));paint.setStrokeWidth(dp(.8f));
        canvas.drawLine(0,0,size*pose[0],center-size*.45f,paint);
        canvas.translate(size*pose[0],center);canvas.rotate(pose[2]);canvas.scale(pose[3],pose[4]);
        float hx=character==3?.60f:.5f,hy=character==1?.29f:.27f;
        float lx=character==1?.16f:character==2?.28f:.28f;
        float rx=character==1?.84f:character==2?.72f:.64f;
        for(int y=0;y<=20;y++)for(int x=0;x<=20;x++){
            float u=x/20f,v=y/20f,dx=0,dy=0;
            for(int part=0;part<3;part++){
                float px=part==0?hx:part==1?lx:rx, py=part==0?hy:character==3?.51f:.60f;
                float radiusX=part==0?.31f:.13f,radiusY=part==0?.23f:.24f;
                float weight=(float)Math.exp(-2*((u-px)*(u-px)/(radiusX*radiusX)+(v-py)*(v-py)/(radiusY*radiusY)));
                float angle=(float)Math.toRadians(joints[part])*weight;
                dx+=(float)(Math.cos(angle)*(u-px)-Math.sin(angle)*(v-py)-(u-px));
                dy+=(float)(Math.sin(angle)*(u-px)+Math.cos(angle)*(v-py)-(v-py));
            }
            int i=(y*21+x)*2;mesh[i]=(u+dx-.5f)*size;mesh[i+1]=(v+dy-.5f)*size;
        }
        paint.setColor(0xffffffff);paint.setAlpha(Math.round(alpha*255));
        // A continuous mesh articulates hands/head without seams or duplicate painted limbs.
        canvas.drawBitmapMesh(characterBitmap,20,20,mesh,0,null,0,paint);
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
