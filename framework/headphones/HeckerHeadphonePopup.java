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
import android.database.ContentObserver;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Connection UI only. Pairing, codecs, routing and audio policy stay with Android. */
public final class HeckerHeadphonePopup {
    private static final String TAG = "HeckerHeadphones";
    private static final String ENABLED = "hecker_headphone_popup";
    private static HeckerHeadphonePopup instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> shown = new HashMap<>();
    private final Set<Integer> wiredDevices = new HashSet<>();
    private SystemUIDialog dialog;
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
        context.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(ENABLED), false, new ContentObserver(handler) {
                    @Override public void onChange(boolean selfChange) {
                        if (!enabled()) dismiss();
                    }
                }, UserHandle.USER_ALL);
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
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF101C28);
        background.setCornerRadius(dp(30));
        background.setStroke(dp(1), 0xFF304863);
        panel.setBackground(background);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.hecker_headphones_art);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(18), 0, 0, 0);
        labels.addView(text(name, 20, 0xFFE5EDFF));
        labels.addView(text(context.getString(R.string.hecker_headphones_connected), 14, 0xFFB2C1D8));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(row);
        battery = text("", 16, 0xFFAAC9FF);
        battery.setPadding(0, dp(14), 0, dp(10));
        panel.addView(battery);
        TextView settings = text(context.getString(device == null
                ? R.string.hecker_headphones_sound_settings : R.string.hecker_headphones_settings),
                14, 0xFFAAC9FF);
        settings.setGravity(Gravity.END);
        settings.setMinHeight(dp(48));
        settings.setOnClickListener(view -> {
            dismiss();
            context.startActivityAsUser(new Intent(device == null
                    ? Settings.ACTION_SOUND_SETTINGS : Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), UserHandle.of(visibleUser));
        });
        panel.addView(settings);
        dialog = new SystemUIDialog(context);
        dialog.setView(panel, 0, 0, 0, 0);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        }
        final SystemUIDialog ownedDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (dialog != ownedDialog) return;
            handler.removeCallbacks(refresh);
            visibleDevice = null; battery = null; dialog = null;
        });
        try {
            dialog.show();
            if (window != null) window.setLayout(
                    Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(32), dp(440)), -2);
            expires = SystemClock.elapsedRealtime() + 6500;
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

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void dismiss() {
        handler.removeCallbacks(refresh);
        if (dialog != null) dialog.dismiss();
        dialog = null; visibleDevice = null; battery = null;
    }
}
