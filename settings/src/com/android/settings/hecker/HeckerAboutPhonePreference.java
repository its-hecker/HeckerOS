// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemProperties;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One About Phone hero; native preferences below retain their original controllers. */
public final class HeckerAboutPhonePreference extends Preference {
    public static final String KEY = "hecker_about_phone";
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Map<Integer, CharSequence> mValues = new HashMap<>();
    private View mCard;
    private ExecutorService mWorker;
    private boolean mActive;
    private boolean mRegistered;
    private int mGeneration;
    private final BroadcastReceiver mBattery = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            CharSequence value = unavailable();
            if (level >= 0 && scale > 0 && level <= scale) {
                String percent = NumberFormat.getPercentInstance().format((double) level / scale);
                value = status == BatteryManager.BATTERY_STATUS_CHARGING
                        ? context.getString(R.string.hecker_about_battery_charging, percent) : percent;
            }
            mValues.put(R.id.hecker_about_battery, value);
            render();
        }
    };

    public HeckerAboutPhonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.hecker_about_phone);
        setSelectable(false);
        setPersistent(false);
    }

    /** Called by the native fragment so work and receivers stop when the page leaves. */
    public void setActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        int generation = ++mGeneration;
        if (!active) {
            if (mRegistered) {
                getContext().unregisterReceiver(mBattery);
                mRegistered = false;
            }
            if (mWorker != null) {
                mWorker.shutdownNow();
                mWorker = null;
            }
            mMain.removeCallbacksAndMessages(null);
            if (mCard != null) phoneArt().setActive(false);
            return;
        }
        // Storage and memory queries run off the UI thread. No synthetic capacity values.
        Context context = getContext().getApplicationContext();
        mWorker = Executors.newSingleThreadExecutor();
        mWorker.execute(() -> {
            Map<Integer, CharSequence> values = readDevice(context);
            mMain.post(() -> {
                if (!mActive || generation != mGeneration) return;
                mValues.putAll(values);
                render();
            });
        });
        Intent sticky = getContext().registerReceiver(mBattery,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        mRegistered = true;
        if (sticky != null) mBattery.onReceive(getContext(), sticky);
        render();
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        mCard = holder.itemView;
        mCard.findViewById(R.id.hecker_about_community).setOnClickListener(view -> {
            Intent about = new Intent().setClassName(getContext().getPackageName(),
                    "org.hecker.suite.SuiteActivity").putExtra("org.hecker.suite.SHOW_ABOUT", true);
            try {
                getContext().startActivity(about);
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(getContext(), R.string.hecker_suite_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        render();
    }

    @Override public void onDetached() {
        setActive(false);
        mCard = null;
        super.onDetached();
    }

    private Map<Integer, CharSequence> readDevice(Context context) {
        Map<Integer, CharSequence> values = new HashMap<>();
        values.put(R.id.hecker_about_rom, context.getString(R.string.hecker_about_rom,
                SystemProperties.get("ro.heckeros.version", unavailable())));
        String type = SystemProperties.get("ro.heckeros.build_type", "");
        values.put(R.id.hecker_about_build_type, context.getString("OFFICIAL".equals(type)
                ? R.string.hecker_about_official : "UNOFFICIAL".equals(type)
                ? R.string.hecker_about_unofficial : R.string.hecker_about_unspecified));
        values.put(R.id.hecker_about_model, Build.MODEL);
        values.put(R.id.hecker_about_codename, SystemProperties.get("ro.heckeros.device", Build.DEVICE));
        values.put(R.id.hecker_about_android, Build.VERSION.RELEASE);
        values.put(R.id.hecker_about_maintainer, context.getString(R.string.hecker_about_maintainer,
                SystemProperties.get("ro.heckeros.maintainer", unavailable())));
        values.put(R.id.hecker_about_processor, Build.UNKNOWN.equals(Build.SOC_MODEL)
                ? Build.HARDWARE : Build.SOC_MODEL);
        values.put(R.id.hecker_about_kernel, System.getProperty("os.version", unavailable()));
        values.put(R.id.hecker_about_memory, unavailable());
        values.put(R.id.hecker_about_storage, unavailable());
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager != null) {
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memory);
                if (memory.totalMem > 0) values.put(R.id.hecker_about_memory,
                        Formatter.formatShortFileSize(context, memory.totalMem));
            }
        } catch (RuntimeException unavailable) { /* Leave an honest unavailable state. */ }
        try {
            StatFs data = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = data.getTotalBytes();
            long used = Math.max(0, total - data.getAvailableBytes());
            if (total > 0) values.put(R.id.hecker_about_storage,
                    context.getString(R.string.hecker_about_storage_value,
                            Formatter.formatShortFileSize(context, used),
                            Formatter.formatShortFileSize(context, total)));
        } catch (RuntimeException unavailable) { /* Not a fabricated advertised storage size. */ }
        return values;
    }

    private String unavailable() {
        return getContext().getString(R.string.hecker_about_unavailable);
    }

    private HeckerPhoneArtView phoneArt() {
        return mCard.findViewById(R.id.hecker_about_phone_art);
    }

    private void render() {
        if (mCard == null) return;
        for (int id : new int[] {R.id.hecker_about_rom, R.id.hecker_about_build_type,
                R.id.hecker_about_model, R.id.hecker_about_codename, R.id.hecker_about_android,
                R.id.hecker_about_memory, R.id.hecker_about_storage, R.id.hecker_about_battery,
                R.id.hecker_about_processor, R.id.hecker_about_kernel, R.id.hecker_about_maintainer}) {
            ((TextView) mCard.findViewById(id)).setText(mValues.getOrDefault(id, unavailable()));
        }
        phoneArt().setActive(mActive);
    }
}
