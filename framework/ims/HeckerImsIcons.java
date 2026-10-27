// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;

/** Normal status-bar slots: native tint, dark mode, overflow and accessibility. */
@SysUISingleton
public final class HeckerImsIcons implements CoreStartable {
    private static final String TAG = "HeckerImsIcons";
    private static final String VOLTE = "hecker_show_volte_icon";
    private static final String VOWIFI = "hecker_show_vowifi_icon";
    private static final String[] SLOTS = {"hecker_ims_sim1", "hecker_ims_sim2"};
    private final Context context;
    private final StatusBarIconController icons;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor executor = command -> handler.post(command);
    private final Map<Integer, Subscription> subscriptions = new ConcurrentHashMap<>();
    private boolean started;

    @Inject
    public HeckerImsIcons(@Application Context context, StatusBarIconController icons) {
        this.context = context;
        this.icons = icons;
    }

    @Override public void start() {
        if (started) return;
        started = true;
        ContentObserver observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { render(); }
        };
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(VOLTE),
                false, observer, UserHandle.ALL.getIdentifier());
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(VOWIFI),
                false, observer, UserHandle.ALL.getIdentifier());
        IntentFilter filter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) { render(); }
        }, filter, null, handler, Context.RECEIVER_NOT_EXPORTED);
        SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
        if (manager != null) {
            manager.addOnSubscriptionsChangedListener(executor,
                    new SubscriptionManager.OnSubscriptionsChangedListener() {
                        @Override public void onSubscriptionsChanged() { reconcile(); }
                    });
        }
        reconcile();
    }

    private void reconcile() {
        SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> active;
        try {
            active = manager == null ? null : manager.getActiveSubscriptionInfoList();
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot read active SIMs", error);
            // Drop old evidence when the subscription service is unavailable.
            active = null;
            handler.removeCallbacks(reconcileRetry);
            handler.postDelayed(reconcileRetry, 5000);
        }
        Map<Integer, Integer> desired = new HashMap<>();
        if (active != null) for (SubscriptionInfo info : active) {
            int slot = info.getSimSlotIndex();
            if (!info.isOpportunistic() && slot >= 0 && slot < SLOTS.length) {
                desired.put(info.getSubscriptionId(), slot);
            }
        }
        for (Subscription old : new ArrayList<>(subscriptions.values())) {
            if (!Integer.valueOf(old.slot).equals(desired.get(old.id))) {
                subscriptions.remove(old.id);
                old.stop();
            }
        }
        for (Map.Entry<Integer, Integer> entry : desired.entrySet()) {
            if (subscriptions.containsKey(entry.getKey())) continue;
            Subscription item = new Subscription(entry.getKey(), entry.getValue());
            subscriptions.put(item.id, item);
            item.connect();
        }
        render();
    }

    private final Runnable reconcileRetry = this::reconcile;

    private void render() {
        boolean volte = Settings.Secure.getIntForUser(context.getContentResolver(),
                VOLTE, 1, UserHandle.CURRENT.getIdentifier()) != 0;
        boolean vowifi = Settings.Secure.getIntForUser(context.getContentResolver(),
                VOWIFI, 1, UserHandle.CURRENT.getIdentifier()) != 0;
        boolean airplane = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        int[] kinds = new int[SLOTS.length];
        for (Subscription item : subscriptions.values()) {
            kinds[item.slot] = item.state.icon(volte, vowifi, airplane);
        }
        for (int slot = 0; slot < SLOTS.length; slot++) {
            int kind = kinds[slot];
            if (kind == ImsIconState.NONE) {
                icons.setIconVisibility(SLOTS[slot], false);
                continue;
            }
            int drawable = kind == ImsIconState.WIFI
                    ? R.drawable.ic_hecker_vowifi : R.drawable.ic_hecker_volte;
            int description = kind == ImsIconState.WIFI
                    ? R.string.hecker_vowifi_description : R.string.hecker_volte_description;
            icons.setIcon(SLOTS[slot], drawable, context.getString(description, slot + 1));
            icons.setIconVisibility(SLOTS[slot], true);
        }
    }

    private final class Subscription {
        final int id;
        final int slot;
        final ImsIconState state = new ImsIconState();
        final Runnable retry = this::connect;
        ImsMmTelManager manager;
        RegistrationManager.RegistrationCallback registration;
        ImsMmTelManager.CapabilityCallback capabilities;
        boolean stateAttached;
        int generation;
        int retrySeconds = 2;

        Subscription(int id, int slot) { this.id = id; this.slot = slot; }
        boolean live() { return subscriptions.get(id) == this; }
        boolean live(int epoch) { return live() && generation == epoch; }

        final ImsStateCallback service = new ImsStateCallback() {
            @Override public void onAvailable() { if (live()) connect(); }
            @Override public void onUnavailable(int reason) {
                if (!live()) return;
                detachVoice();
                render();
                retry();
            }
            @Override public void onError() {
                if (!live()) return;
                detachVoice();
                if (manager != null && stateAttached) {
                    try { manager.unregisterImsStateCallback(this); }
                    catch (RuntimeException ignored) { }
                }
                stateAttached = false;
                render();
                retry();
            }
        };

        void retry() {
            if (!live()) return;
            handler.removeCallbacks(retry);
            handler.postDelayed(retry, retrySeconds * 1000L);
            retrySeconds = Math.min(60, retrySeconds * 2);
        }

        void connect() {
            if (!live()) return;
            handler.removeCallbacks(retry);
            try {
                if (manager == null) {
                    ImsManager ims = context.getSystemService(ImsManager.class);
                    if (ims == null) { retry(); return; }
                    manager = ims.getImsMmTelManager(id);
                }
                if (!stateAttached) {
                    manager.registerImsStateCallback(executor, service);
                    stateAttached = true;
                }
                final int epoch = generation;
                if (registration == null) {
                    RegistrationManager.RegistrationCallback callback =
                            new RegistrationManager.RegistrationCallback() {
                        @Override public void onRegistered(ImsRegistrationAttributes attributes) {
                            if (!live(epoch)) return;
                            int technology = attributes.getRegistrationTechnology();
                            int access = technology == ImsRegistrationImplBase.REGISTRATION_TECH_LTE
                                    ? ImsIconState.LTE
                                    : technology == ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN
                                            ? ImsIconState.WIFI : ImsIconState.CROSS_SIM;
                            state.registered(access);
                            render();
                        }
                        @Override public void onRegistering(ImsRegistrationAttributes attributes) {
                            if (live(epoch)) { state.clear(); render(); }
                        }
                        @Override public void onUnregistered(ImsReasonInfo reason) {
                            if (live(epoch)) { state.clear(); render(); }
                        }
                    };
                    manager.registerImsRegistrationCallback(executor, callback);
                    registration = callback;
                }
                if (capabilities == null) {
                    ImsMmTelManager.CapabilityCallback callback = new ImsMmTelManager.CapabilityCallback() {
                        @Override public void onCapabilitiesStatusChanged(MmTelCapabilities available) {
                            if (!live(epoch)) return;
                            state.voice(available.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VOICE));
                            render();
                        }
                    };
                    manager.registerMmTelCapabilityCallback(executor, callback);
                    capabilities = callback;
                }
                retrySeconds = 2;
            } catch (ImsException | RuntimeException error) {
                // The modem/IMS service may not exist yet at SystemUI startup.
                Log.w(TAG, "IMS callbacks unavailable for slot " + (slot + 1));
                retry();
            }
        }

        void detachVoice() {
            generation++; // Queued callbacks from a previous binding are no longer evidence.
            state.clear();
            if (manager != null && registration != null) {
                try { manager.unregisterImsRegistrationCallback(registration); }
                catch (RuntimeException ignored) { }
            }
            if (manager != null && capabilities != null) {
                try { manager.unregisterMmTelCapabilityCallback(capabilities); }
                catch (RuntimeException ignored) { }
            }
            registration = null;
            capabilities = null;
        }

        void stop() {
            handler.removeCallbacks(retry);
            detachVoice();
            if (manager != null && stateAttached) {
                try { manager.unregisterImsStateCallback(service); }
                catch (RuntimeException ignored) { }
            }
            stateAttached = false;
        }
    }

    @Override public void dump(PrintWriter writer, String[] args) {
        writer.println("HeckerImsIcons: activeSubscriptions=" + subscriptions.size());
        for (Subscription item : subscriptions.values()) {
            writer.println("  SIM " + (item.slot + 1)
                    + " voiceIcon=" + item.state.icon(true, true, false)
                    + " callbacks=" + (item.registration != null && item.capabilities != null));
        }
    }
}
