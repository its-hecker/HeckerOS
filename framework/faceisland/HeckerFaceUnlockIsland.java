// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.biometrics.BiometricSourceType;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.res.R;

/** Native keyguard feedback: observes face callbacks without delaying or requesting an unlock. */
public final class HeckerFaceUnlockIsland {
    private static final String SETTING = "hecker_face_unlock_island";
    private static final String TAG = "HeckerFaceIsland";
    private static HeckerFaceUnlockIsland instance;
    private final Context context;
    private final KeyguardUpdateMonitor monitor;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FaceIslandState state = new FaceIslandState();
    private IslandView view;
    private boolean visible;
    private boolean awake;

    public static void start(Context context, KeyguardUpdateMonitor monitor) {
        if (instance != null) return;
        instance = new HeckerFaceUnlockIsland(context, monitor);
    }

    private HeckerFaceUnlockIsland(Context context, KeyguardUpdateMonitor monitor) {
        this.context = context;
        this.monitor = monitor;
        windows = context.getSystemService(WindowManager.class);
        visible = monitor.isKeyguardVisible();
        awake = monitor.isDeviceInteractive();
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(SETTING),
                false, new ContentObserver(handler) {
                    @Override public void onChange(boolean selfChange) { refresh(); }
                }, UserHandle.USER_ALL);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                remove();
                state.expire();
                refresh();
            }
        }, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        monitor.registerCallback(callback);
        refresh();
    }

    private final KeyguardUpdateMonitorCallback callback = new KeyguardUpdateMonitorCallback() {
        @Override public void onKeyguardVisibilityChanged(boolean showing) {
            visible = showing;
            refresh();
        }
        @Override public void onStartedWakingUp() { awake = true; refresh(); }
        @Override public void onStartedGoingToSleep(int why) { awake = false; refresh(); }
        @Override public void onUserSwitchComplete(int userId) {
            // Invalidate an old user's successful session even when keyguard stays visible.
            state.environment(false, false, false, userId);
            remove();
            refresh();
        }
        @Override public void onStrongAuthStateChanged(int userId) {
            if (userId == ActivityManager.getCurrentUser()) {
                state.expire();
                remove();
            }
        }
        @Override public void onBiometricRunningStateChanged(boolean running, BiometricSourceType type) {
            environment();
            state.running(type == BiometricSourceType.FACE, running);
            render();
        }
        @Override public void onBiometricAuthenticated(int userId, BiometricSourceType type, boolean strong) {
            environment();
            if (state.authenticated(type == BiometricSourceType.FACE, userId,
                    monitor.isUnlockingWithBiometricAllowed(strong))) {
                render();
                handler.removeCallbacks(expire);
                handler.postDelayed(expire, 1100);
            }
        }
        @Override public void onBiometricAuthFailed(BiometricSourceType type) {
            state.failed(type == BiometricSourceType.FACE); render();
        }
        @Override public void onBiometricError(int code, String message, BiometricSourceType type) {
            state.failed(type == BiometricSourceType.FACE); render();
        }
    };

    private void environment() {
        boolean enabled = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT
                && Settings.Secure.getIntForUser(context.getContentResolver(), SETTING, 1,
                        ActivityManager.getCurrentUser()) != 0;
        state.environment(enabled, visible, awake, ActivityManager.getCurrentUser());
    }

    private void refresh() {
        environment();
        if (monitor.isFaceAuthOrDetectionRunning()) state.running(true, true);
        render();
    }

    private final Runnable expire = () -> { state.expire(); remove(); };

    private void render() {
        if (state.state() == FaceIslandState.HIDDEN) { remove(); return; }
        if (view == null && windows != null) {
            IslandView next = new IslandView(context);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    dp(200), dp(48), WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.setFitInsetsTypes(0);
            params.y = dp(8);
            // Move beneath a central camera cutout; Pixel 4's forehead needs no offset.
            DisplayCutout cutout = windows.getCurrentWindowMetrics().getWindowInsets().getDisplayCutout();
            if (cutout != null) {
                int center = windows.getCurrentWindowMetrics().getBounds().width() / 2;
                for (Rect bounds : cutout.getBoundingRects()) {
                    if (bounds.top == 0 && bounds.left < center + dp(100)
                            && bounds.right > center - dp(100)) {
                        params.y = Math.max(params.y, cutout.getSafeInsetTop() + dp(4));
                    }
                }
            }
            params.setTitle("HeckerFaceUnlockIsland");
            try { windows.addView(next, params); view = next; }
            catch (RuntimeException unavailable) {
                Log.w(TAG, "Face feedback window unavailable", unavailable);
                return;
            }
        }
        if (view != null) view.showState(state.state());
    }

    private void remove() {
        handler.removeCallbacks(expire);
        if (view == null) return;
        view.stop();
        try { windows.removeViewImmediate(view); }
        catch (IllegalArgumentException alreadyRemoved) { }
        view = null;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private static final class IslandView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private ValueAnimator animator;
        private int state;
        private float phase;
        private float progress;

        IslandView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        }

        void showState(int value) {
            if (state == value) return;
            stop();
            state = value;
            setContentDescription(getContext().getString(value == FaceIslandState.SUCCESS
                    ? R.string.hecker_face_island_unlocked : R.string.hecker_face_island_scanning));
            if (!ValueAnimator.areAnimatorsEnabled()) { progress = 1; invalidate(); return; }
            animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(value == FaceIslandState.SUCCESS ? 420 : 1100);
            animator.setInterpolator(new DecelerateInterpolator());
            if (value == FaceIslandState.SCANNING) animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                if (state == FaceIslandState.SUCCESS) progress = fraction; else phase = fraction;
                invalidate();
            });
            animator.start();
        }

        void stop() { if (animator != null) { animator.cancel(); animator = null; } }

        @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float unit = getResources().getDisplayMetrics().density;
            canvas.save();
            canvas.scale(unit, unit);
            float cx = getWidth() / unit / 2f;
            boolean success = state == FaceIslandState.SUCCESS;
            float width = success ? 138 + 36 * progress : 138;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.BLACK);
            rect.set(cx - width / 2, 2, cx + width / 2, 44);
            canvas.drawRoundRect(rect, 21, 21, paint);
            float x = cx - width / 2 + 27;
            paint.setColor(success ? 0xFF91E6AA : Color.WHITE);
            paint.setStrokeWidth(2.4f); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
            if (success) {
                // Lock shackle swings open, then resolves into a completion check.
                paint.setAlpha(Math.round(255 * (1 - progress)));
                rect.set(x - 6, 10 - progress * 3, x + 6, 25 - progress * 3);
                canvas.drawArc(rect, 180 + progress * 45, 180 - progress * 65, false, paint);
                rect.set(x - 9, 21, x + 9, 34);
                canvas.drawRoundRect(rect, 3, 3, paint);
                paint.setAlpha(Math.round(255 * progress));
                path.reset(); path.moveTo(x - 9, 23); path.lineTo(x - 2, 30); path.lineTo(x + 11, 16);
                canvas.drawPath(path, paint);
            } else {
                rect.set(x - 10, 12, x + 10, 34);
                canvas.drawRoundRect(rect, 7, 7, paint);
                canvas.drawPoint(x - 4, 20, paint); canvas.drawPoint(x + 4, 20, paint);
                path.reset(); path.moveTo(x - 4, 27); path.quadTo(x, 31, x + 4, 27);
                canvas.drawPath(path, paint);
                paint.setColor(0xFFAAC9FF);
                rect.set(x - 15, 7, x + 15, 39);
                canvas.drawArc(rect, phase * 360, 90, false, paint);
            }
            paint.setAlpha(255); paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
            paint.setTextSize(13);
            String label = getContext().getString(success
                    ? R.string.hecker_face_island_unlocked : R.string.hecker_face_island_label);
            float available = width - 62;
            CharSequence fitted = android.text.TextUtils.ellipsize(label, new android.text.TextPaint(paint),
                    available, android.text.TextUtils.TruncateAt.END);
            canvas.drawText(fitted.toString(), x + 23, 28, paint);
            canvas.restore();
        }
    }
}
