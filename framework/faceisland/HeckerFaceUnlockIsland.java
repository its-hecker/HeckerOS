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
                    dp(160), dp(36), WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.setFitInsetsTypes(0);
            params.y = dp(6);
            // Move beneath a central camera cutout; Pixel 4's forehead needs no offset.
            DisplayCutout cutout = windows.getCurrentWindowMetrics().getWindowInsets().getDisplayCutout();
            if (cutout != null) {
                int center = windows.getCurrentWindowMetrics().getBounds().width() / 2;
                for (Rect bounds : cutout.getBoundingRects()) {
                    if (bounds.top == 0 && bounds.left < center + dp(80)
                            && bounds.right > center - dp(80)) {
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
        private final android.text.TextPaint text = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final String scanningLabel;
        private final String unlockedLabel;
        private ValueAnimator animator;
        private int state;
        private float phase;
        private float progress;

        IslandView(Context context) {
            super(context);
            scanningLabel = context.getString(R.string.hecker_face_island_label);
            unlockedLabel = context.getString(R.string.hecker_face_island_unlocked);
            text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
            text.setTextSize(11.5f);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        }

        void showState(int value) {
            if (state == value) return;
            stop();
            state = value;
            progress = 0;
            phase = 0;
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
            float width = success ? 124 + 16 * progress : 124;
            text.setTextSize(11.5f * getResources().getConfiguration().fontScale);
            CharSequence fitted = android.text.TextUtils.ellipsize(success ? unlockedLabel : scanningLabel, text,
                    width - 53, android.text.TextUtils.TruncateAt.END);
            // Center the measured icon, gap and label as a single group.
            float contentWidth = 16 + 9 + text.measureText(fitted.toString());
            float x = cx - contentWidth / 2 + 8;
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xF5080D13);
            rect.set(cx - width / 2, 2, cx + width / 2, 34);
            canvas.drawRoundRect(rect, 16, 16, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(.7f);
            paint.setColor(success ? 0x665DDC91 : 0x554F94C7);
            canvas.drawRoundRect(rect, 16, 16, paint);
            paint.setColor(success ? 0xFF91E6AA : Color.WHITE);
            paint.setStrokeWidth(1.8f); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
            if (success) {
                // Lock shackle swings open, then resolves into a completion check.
                paint.setAlpha(Math.round(255 * (1 - progress)));
                rect.set(x - 4.5f, 8 - progress * 2, x + 4.5f, 19 - progress * 2);
                canvas.drawArc(rect, 180 + progress * 45, 180 - progress * 65, false, paint);
                rect.set(x - 6.5f, 16, x + 6.5f, 26);
                canvas.drawRoundRect(rect, 3, 3, paint);
                paint.setAlpha(Math.round(255 * progress));
                path.reset(); path.moveTo(x - 6, 18); path.lineTo(x - 1, 23); path.lineTo(x + 8, 13);
                canvas.drawPath(path, paint);
            } else {
                // Open corners frame the face; a quiet sweep shows active recognition.
                path.reset();
                path.moveTo(x - 8, 14); path.lineTo(x - 8, 10); path.lineTo(x - 4, 10);
                path.moveTo(x + 4, 10); path.lineTo(x + 8, 10); path.lineTo(x + 8, 14);
                path.moveTo(x - 8, 22); path.lineTo(x - 8, 26); path.lineTo(x - 4, 26);
                path.moveTo(x + 4, 26); path.lineTo(x + 8, 26); path.lineTo(x + 8, 22);
                canvas.drawPath(path, paint);
                canvas.drawPoint(x - 3, 16, paint); canvas.drawPoint(x + 3, 16, paint);
                path.reset(); path.moveTo(x - 3, 21); path.quadTo(x, 23, x + 3, 21);
                canvas.drawPath(path, paint);
                paint.setColor(0xFFAAC9FF);
                paint.setAlpha((int) Math.round(70 + 150 * Math.sin(Math.PI * phase)));
                canvas.drawLine(x - 5, 11 + phase * 14, x + 5, 11 + phase * 14, paint);
            }
            text.setColor(success ? 0xFFB8EFCC : 0xFFE7EFF7);
            Paint.FontMetrics metrics = text.getFontMetrics();
            canvas.drawText(fitted.toString(), x + 17, 18 - (metrics.ascent + metrics.descent) / 2, text);
            canvas.restore();
        }
    }
}
