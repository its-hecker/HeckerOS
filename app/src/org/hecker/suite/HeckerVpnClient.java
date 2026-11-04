// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.hecker.vpn.IHeckerVpnService;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Asynchronous Binder client; never performs network or tunnel work on the UI thread. */
final class HeckerVpnClient implements AutoCloseable {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Runnable> waiting = new ArrayList<>();
    private IHeckerVpnService service;
    private boolean bound, closed, unavailable;
    HeckerVpnClient(Context context) { this.context = context.getApplicationContext(); }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IHeckerVpnService.Stub.asInterface(binder);
            ArrayList<Runnable> pending = new ArrayList<>(waiting); waiting.clear(); for (Runnable action : pending) action.run();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; }
        @Override public void onNullBinding(ComponentName name) { failWaiting(); }
        @Override public void onBindingDied(ComponentName name) { failWaiting(); }
    };
    void call(Bundle request, Consumer<Bundle> callback) {
        if (closed) return;
        Runnable action = () -> {
            IHeckerVpnService remote = service;
            if (remote == null) { callback.accept(error()); return; }
            io.execute(() -> {
                Bundle value;
                try { value = remote.call(request); } catch (Exception failure) { value = error(); }
                Bundle result = value; main.post(() -> { if (!closed) callback.accept(result); });
            });
        };
        if (service != null) { action.run(); return; }
        if (unavailable) { callback.accept(error()); return; }
        waiting.add(action);
        if (!bound) {
            try { bound = context.bindService(new Intent().setClassName("org.hecker.vpn", "org.hecker.vpn.VpnService"), connection, Context.BIND_AUTO_CREATE); }
            catch (SecurityException failure) { bound = false; }
            if (!bound) failWaiting(); else main.postDelayed(() -> { if (service == null) failWaiting(); }, 10000);
        }
    }
    private Bundle error() { Bundle result = new Bundle(); result.putString("error", context.getString(R.string.vpn_unavailable)); return result; }
    private void failWaiting() {
        unavailable = true; service = null;
        ArrayList<Runnable> pending = new ArrayList<>(waiting); waiting.clear(); for (Runnable action : pending) action.run();
    }
    @Override public void close() {
        closed = true; waiting.clear(); main.removeCallbacksAndMessages(null);
        if (bound) { context.unbindService(connection); bound = false; } service = null; io.shutdown();
    }
    static Bundle request(String op) { Bundle request = new Bundle(); request.putString("op", op); return request; }
}
