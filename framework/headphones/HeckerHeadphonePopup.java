// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Connection UI only. Pairing, codecs, routing and audio policy stay with Android. */
public final class HeckerHeadphonePopup {
    private static final String TAG = "HeckerHeadphones";
    private static final String ENABLED = "hecker_headphone_popup";
    private static final String GLASS = "hecker_headphone_glass";
    private static HeckerHeadphonePopup instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> shown = new HashMap<>();
    private final Set<Integer> wiredDevices = new HashSet<>();
    private SystemUIDialog dialog;
    private FrostedSurface frost;
    private TextView battery;
    private BluetoothDevice visibleDevice;
    private int visibleUser;
    private long expires;

    public static void start(Context context) {
        if (instance != null) return;
        try {
            instance = new HeckerHeadphonePopup(context);
        } catch (RuntimeException unavailable) {
            // An optional popup must never prevent SystemUI from starting.
            Log.w(TAG, "Could not register headphone connection UI", unavailable);
        }
    }

    private HeckerHeadphonePopup(Context context) {
        this.context = context;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction("android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED");
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)
                        || Intent.ACTION_USER_SWITCHED.equals(action)) {
                    dismiss(); shown.clear(); return;
                }
                BluetoothDevice device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                if (device == null) return;
                if ("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED".equals(action)) {
                    if (device.equals(visibleDevice)) updateBattery();
                    return;
                }
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                if (state == BluetoothProfile.STATE_CONNECTED) connected(device);
                else if (state == BluetoothProfile.STATE_DISCONNECTED
                        && device.equals(visibleDevice)) dismiss();
            }
        }, filter, null, handler, Context.RECEIVER_EXPORTED);
        ContentObserver preferences = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                if (!enabled()) dismiss();
                else if (frost != null) frost.refresh();
            }
        };
        context.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(ENABLED), false, preferences, UserHandle.USER_ALL);
        context.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(GLASS), false, preferences, UserHandle.USER_ALL);
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio != null) {
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                wiredDevices.add(device.getId());
            }
            audio.registerAudioDeviceCallback(new AudioDeviceCallback() {
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) {
                    for (AudioDeviceInfo device : devices) {
                        boolean fresh = wiredDevices.add(device.getId());
                        if (fresh && wired(device)) {
                            show(context.getString(R.string.hecker_headphones_wired), null);
                        }
                    }
                }
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) {
                    for (AudioDeviceInfo device : devices) wiredDevices.remove(device.getId());
                }
            }, handler);
        }
    }

    private static boolean wired(AudioDeviceInfo device) {
        return device.isSink() && (device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET);
    }

    private boolean enabled() {
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return Settings.Secure.getIntForUser(context.getContentResolver(), ENABLED, 1,
                ActivityManager.getCurrentUser()) != 0
                && (keyguard == null || !keyguard.isKeyguardLocked());
    }

    private void connected(BluetoothDevice device) {
        try {
            if (!enabled() || device.getBondState() != BluetoothDevice.BOND_BONDED) return;
            long now = SystemClock.elapsedRealtime();
            Long previous = shown.get(device.getAddress());
            if (previous != null && now - previous < 10000) return;
            shown.put(device.getAddress(), now);
            String name = device.getAlias();
            if (name == null || name.isBlank()) name = device.getName();
            if (name == null || name.isBlank()) name = context.getString(R.string.hecker_headphones);
            show(name, device);
        } catch (SecurityException unavailable) {
            Log.w(TAG, "Bluetooth connection details unavailable", unavailable);
        }
    }

    private void show(String name, BluetoothDevice device) {
        try {
            showCard(name, device);
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "Could not display connection card", unavailable);
            dismiss();
        }
    }

    private void showCard(String name, BluetoothDevice device) {
        if (!enabled()) return;
        dismiss();
        visibleUser = ActivityManager.getCurrentUser();
        visibleDevice = device;
        dialog = new SystemUIDialog(context, R.style.HeckerHeadphoneDialog);
        Context themed = dialog.getContext();
        boolean dark = (themed.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        // Android's Material 3 color roles follow the wallpaper palette and
        // light/dark mode. The illustration retains the headset's own colors.
        int surface = themed.getColor(dark ? android.R.color.system_surface_container_high_dark
                : android.R.color.system_surface_container_high_light);
        int onSurface = themed.getColor(dark ? android.R.color.system_on_surface_dark
                : android.R.color.system_on_surface_light);
        int secondary = themed.getColor(dark ? android.R.color.system_on_surface_variant_dark
                : android.R.color.system_on_surface_variant_light);
        int primary = themed.getColor(dark ? android.R.color.system_primary_dark
                : android.R.color.system_primary_light);
        int onPrimary = themed.getColor(dark ? android.R.color.system_on_primary_dark
                : android.R.color.system_on_primary_light);
        int container = themed.getColor(dark ? android.R.color.system_primary_container_dark
                : android.R.color.system_primary_container_light);
        int onContainer = themed.getColor(dark ? android.R.color.system_on_primary_container_dark
                : android.R.color.system_on_primary_container_light);
        int outline = themed.getColor(dark ? android.R.color.system_outline_variant_dark
                : android.R.color.system_outline_variant_light);
        int chipSurface = themed.getColor(dark ? android.R.color.system_surface_container_highest_dark
                : android.R.color.system_surface_container_highest_light);

        // Compact Material You connection card. Keep controls within one short
        // bottom sheet rather than stacking a full-size illustration and button.
        LinearLayout panel = new LinearLayout(themed);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(10), dp(18), dp(14));

        LinearLayout header = new LinearLayout(themed);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(themed);
        icon.setImageResource(R.drawable.hecker_headphones_bluetooth);
        icon.setImageTintList(ColorStateList.valueOf(onContainer));
        icon.setBackground(shape(container, 16));
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView state = text(themed, context.getString(R.string.hecker_headphones_connected), 12, secondary);
        state.setPadding(dp(10), 0, 0, 0);
        header.addView(state, new LinearLayout.LayoutParams(0, -2, 1));

        ImageButton close = new ImageButton(themed);
        close.setImageResource(R.drawable.hecker_headphones_close);
        close.setImageTintList(ColorStateList.valueOf(secondary));
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(onSurface, 0.12f)),
                null, shape(Color.WHITE, 20)));
        close.setContentDescription(context.getString(R.string.hecker_headphones_dismiss));
        close.setOnClickListener(view -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        panel.addView(header);

        LinearLayout content = new LinearLayout(themed);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(0, dp(6), 0, dp(8));

        if (device != null) {
            org.hecker.earbuds.HeckerEarbudsView artwork = new org.hecker.earbuds.HeckerEarbudsView(themed);
            artwork.setDeviceName(name);
            artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            content.addView(artwork, new LinearLayout.LayoutParams(dp(92), dp(80)));
        } else {
            ImageView artwork = new ImageView(themed);
            artwork.setImageResource(R.drawable.hecker_headphones_art);
            artwork.setImageTintList(ColorStateList.valueOf(primary));
            artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            content.addView(artwork, new LinearLayout.LayoutParams(dp(72), dp(76)));
        }

        LinearLayout details = new LinearLayout(themed);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        details.setPadding(dp(10), 0, 0, 0);
        TextView title = text(themed, name, 17, onSurface);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setAccessibilityHeading(true);
        details.addView(title, new LinearLayout.LayoutParams(-1, -2));

        battery = text(themed, "", 12, onSurface);
        battery.setMaxLines(2);
        battery.setEllipsize(android.text.TextUtils.TruncateAt.END);
        battery.setGravity(Gravity.CENTER_VERTICAL);
        battery.setPadding(dp(12), dp(6), dp(12), dp(6));
        battery.setBackground(chipShape(chipSurface, outline));
        // Battery is a content-sized chip, not an edge-to-edge progress bar.
        LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(-2, -2);
        batteryParams.topMargin = dp(6);
        details.addView(battery, batteryParams);
        content.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(content, new LinearLayout.LayoutParams(-1, -2));

        Button settings = new Button(themed);
        settings.setText(device == null ? R.string.hecker_headphones_sound_settings
                : R.string.hecker_headphones_settings);
        settings.setAllCaps(false);
        settings.setTextSize(13);
        settings.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        settings.setTextColor(onSurface);
        settings.setGravity(Gravity.CENTER);
        settings.setBackgroundTintList(null);
        settings.setStateListAnimator(null);
        settings.setPadding(dp(16), 0, dp(16), 0);
        settings.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(primary, .12f)),
                chipShape(chipSurface, outline), null));
        settings.setMinHeight(dp(40));
        settings.setOnClickListener(view -> {
            int user = visibleUser;
            dismiss();
            Intent target = device == null ? null : context.getPackageManager()
                    .getLaunchIntentForPackage("org.hecker.librepods");
            if (target == null) target = new Intent(device == null
                    ? Settings.ACTION_SOUND_SETTINGS : Settings.ACTION_BLUETOOTH_SETTINGS);
            if (device != null) target.putExtra("hecker_device_address", device.getAddress());
            try {
                context.startActivityAsUser(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), UserHandle.of(user));
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                Log.w(TAG, "Headphone details unavailable", unavailable);
            }
        });
        // Same neutral tonal outline as the battery chip; no large accent bar.
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(-2, dp(40));
        settingsParams.gravity = Gravity.END;
        settingsParams.topMargin = dp(4);
        panel.addView(settings, settingsParams);
        ScrollView scroll = new ScrollView(themed);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(panel);
        // Own the full container so AlertDialog's custom-panel padding cannot
        // double the Material spacing or leave an opaque layer over the glass.
        dialog.create();
        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    | WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            frost = new FrostedSurface(window, surface, outline);
        }
        final SystemUIDialog ownedDialog = dialog;
        final FrostedSurface ownedSurface = frost;
        dialog.setOnDismissListener(ignored -> {
            if (ownedSurface != null) ownedSurface.stop();
            if (dialog != ownedDialog) return;
            handler.removeCallbacks(refresh);
            visibleDevice = null; battery = null; dialog = null; frost = null;
        });
        try {
            dialog.show();
            if (window != null) window.setLayout(
                    Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(40), dp(396)), -2);
            if (ownedSurface != null) ownedSurface.start();
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                panel.setAlpha(0); panel.setTranslationY(dp(12));
                panel.animate().alpha(1).translationY(0).setDuration(190).start();
            }
            AccessibilityManager accessibility = context.getSystemService(AccessibilityManager.class);
            int timeout = accessibility == null ? 9000 : accessibility.getRecommendedTimeoutMillis(9000,
                    AccessibilityManager.FLAG_CONTENT_ICONS | AccessibilityManager.FLAG_CONTENT_TEXT
                            | AccessibilityManager.FLAG_CONTENT_CONTROLS);
            expires = SystemClock.elapsedRealtime() + timeout;
            updateBattery(); handler.postDelayed(refresh, 500);
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "Could not display connection card", unavailable);
            dismiss();
        }
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (dialog == null) return;
            if (!enabled() || visibleUser != ActivityManager.getCurrentUser()
                    || SystemClock.elapsedRealtime() >= expires) {
                dismiss(); return;
            }
            updateBattery(); handler.postDelayed(this, 1000);
        }
    };

    private void updateBattery() {
        if (battery == null) return;
        if (visibleDevice == null) { battery.setVisibility(View.GONE); return; }
        try {
            int left = metadata(BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY);
            int right = metadata(BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY);
            int chargingCase = metadata(BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY);
            StringBuilder detail = new StringBuilder();
            if (HeadphoneBattery.hasComponents(left, right, chargingCase)) {
                component(detail, R.string.hecker_headphones_left, left);
                component(detail, R.string.hecker_headphones_right, right);
                component(detail, R.string.hecker_headphones_case, chargingCase);
            } else {
                int level = HeadphoneBattery.valid(visibleDevice.getBatteryLevel());
                if (level < 0) level = metadata(BluetoothDevice.METADATA_MAIN_BATTERY);
                if (level >= 0) detail.append(context.getString(R.string.hecker_headphones_battery, level));
            }
            if (detail.length() == 0) detail.append(context.getString(R.string.hecker_headphones_battery_unknown));
            battery.setText(detail);
        } catch (RuntimeException unavailable) {
            battery.setText(R.string.hecker_headphones_battery_unknown);
        }
    }

    private int metadata(int key) {
        return HeadphoneBattery.level(visibleDevice.getMetadata(key));
    }

    private void component(StringBuilder text, int label, int level) {
        if (level < 0) return;
        if (text.length() > 0) text.append("  ·  ");
        text.append(context.getString(label)).append(' ').append(level).append('%');
    }

    private TextView text(Context themed, String value, int size, int color) {
        TextView view = new TextView(themed);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable chipShape(int fill, int outline) {
        GradientDrawable chip = shape(fill, 14);
        chip.setStroke(dp(1), alpha(outline, .48f));
        return chip;
    }

    private static int alpha(int color, float opacity) {
        return (Math.round(255 * opacity) << 24) | (color & 0x00FFFFFF);
    }

    /** Blur only the rounded window background, leaving all content crisp. */
    private final class FrostedSurface {
        private final Window window;
        private final int surface, outline;
        private final GradientDrawable background;
        private final WindowManager manager = context.getSystemService(WindowManager.class);
        private final Consumer<Boolean> listener = this::update;
        private boolean available, registered, stopped;

        FrostedSurface(Window window, int surface, int outline) {
            this.window = window; this.surface = surface; this.outline = outline;
            background = shape(surface, 26);
            window.setBackgroundDrawable(background);
        }

        void start() {
            if (manager == null) { update(false); return; }
            try {
                update(manager.isCrossWindowBlurEnabled());
                manager.addCrossWindowBlurEnabledListener(context.getMainExecutor(), listener);
                registered = true;
            } catch (RuntimeException unavailable) {
                update(false);
                Log.w(TAG, "Connection card uses solid Material surface", unavailable);
            }
        }

        void refresh() { update(available); }

        private void update(boolean blurAvailable) {
            if (stopped) return;
            available = blurAvailable;
            boolean glass = blurAvailable && Settings.Secure.getIntForUser(
                    context.getContentResolver(), GLASS, 1, visibleUser) != 0;
            try {
                // The blur API takes pixels. Avoid density-scaling this beyond
                // the compositor's recommended range on Floral's dense screen.
                window.setBackgroundBlurRadius(glass ? 56 : 0);
            } catch (RuntimeException unavailable) {
                glass = false;
                Log.w(TAG, "Connection card background blur unavailable", unavailable);
            }
            background.setColor(glass ? alpha(surface, 0.91f) : surface);
            background.setStroke(dp(1), alpha(outline, glass ? 0.55f : 0.25f));
        }

        void stop() {
            stopped = true;
            if (!registered || manager == null) return;
            registered = false;
            try { manager.removeCrossWindowBlurEnabledListener(listener); }
            catch (RuntimeException unavailable) { Log.w(TAG, "Could not release blur listener", unavailable); }
        }
    }

    private void dismiss() {
        handler.removeCallbacks(refresh);
        if (frost != null) frost.stop();
        if (dialog != null) dialog.dismiss();
        dialog = null; visibleDevice = null; battery = null; frost = null;
    }
}
