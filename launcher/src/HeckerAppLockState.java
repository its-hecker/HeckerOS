// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.content.PackageMonitor;
import com.android.launcher3.util.SafeCloseable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/** Native Android 17 App Lock state for private recent-app previews. */
public class HeckerAppLockState implements SafeCloseable {
    private final Context context;
    private final Executor uiExecutor;
    private final Map<String, Boolean> lockCache = new HashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final PackageMonitor packageMonitor = new PackageMonitor() {
        @Override public void onPackageAppLockEnabled(String packageName) { invalidate(); }
        @Override public void onPackageAppLockDisabled(String packageName) { invalidate(); }
        @Override public void onPackageModified(String packageName) { invalidate(); }
        @Override public void onPackageRemoved(String packageName, int uid) { invalidate(); }
    };

    public HeckerAppLockState(Context context, Executor uiExecutor) {
        this.context = context;
        this.uiExecutor = uiExecutor;
        // Native lock updates use PackageMonitor, rather than general broadcasts
        // or the Axion-only Binder service. The launcher already has cross-user access.
        packageMonitor.register(context, UserHandle.ALL, new Handler(Looper.getMainLooper()));
    }

    public void addChangeListener(Runnable listener) { listeners.addIfAbsent(listener); }

    public void removeChangeListener(Runnable listener) { listeners.remove(listener); }

    public boolean hasAppLock(String packageName, int userId) {
        if (packageName == null || packageName.isBlank()) return false;
        synchronized (lockCache) {
            String key = packageName + ":" + userId;
            Boolean cached = lockCache.get(key);
            if (cached != null) return cached;
            boolean locked;
            try {
                locked = context.createContextAsUser(UserHandle.of(userId), 0).getPackageManager()
                        .getApplicationInfo(packageName,
                                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_APP_LOCK_INFO))
                        .isAppLockEnabled;
            } catch (PackageManager.NameNotFoundException removed) {
                locked = false;
            } catch (RuntimeException failure) {
                // Keep previews private on a failed privileged read. Do not
                // cache the failure, so the next request can retry normally.
                Log.w("HeckerLauncher", "Unable to read native App Lock state", failure);
                return true;
            }
            lockCache.put(key, locked);
            return locked;
        }
    }

    private void invalidate() {
        // The same lock covers queries, preventing an in-flight binder result
        // from repopulating the cache after an enable/disable event cleared it.
        synchronized (lockCache) { lockCache.clear(); }
        uiExecutor.execute(() -> listeners.forEach(Runnable::run));
    }

    @Override public void close() {
        packageMonitor.unregister();
        listeners.clear();
        synchronized (lockCache) { lockCache.clear(); }
    }
}
