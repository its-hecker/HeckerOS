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
import android.os.RemoteException;
import org.hecker.vpn.IHeckerVpnService;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Asynchronous Binder client; a failed bind is retryable without reopening Settings. */
final class HeckerVpnClient implements AutoCloseable {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Pending> waiting = new ArrayList<>();
    private IHeckerVpnService service;
    private boolean bound, closed;
    private static final class Pending {
        final Bundle request;
        final Consumer<Bundle> callback;
        Pending(Bundle request, Consumer<Bundle> callback) { this.request = request; this.callback = callback; }
    }
    HeckerVpnClient(Context context) { this.context = context.getApplicationContext(); }
    private final Runnable bindTimeout = () -> failWaiting(R.string.vpn_controller_timeout);
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed || !bound) return;
            main.removeCallbacks(bindTimeout);
            service = IHeckerVpnService.Stub.asInterface(binder);
            ArrayList<Pending> pending = new ArrayList<>(waiting); waiting.clear();
            for (Pending action : pending) dispatch(action);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if (closed) return;
            service = null;
            main.removeCallbacks(bindTimeout); main.postDelayed(bindTimeout, 10000);
        }
        @Override public void onNullBinding(ComponentName name) { failWaiting(R.string.vpn_controller_failed); }
        @Override public void onBindingDied(ComponentName name) { failWaiting(R.string.vpn_controller_failed); }
    };
    void call(Bundle request, Consumer<Bundle> callback) {
        if (closed) return;
        Pending action = new Pending(request, callback);
        if (service != null) { dispatch(action); return; }
        waiting.add(action);
        if (bound) return; // Android reconnects a temporarily disconnected live binding.
        try {
            bound = context.bindService(new Intent().setClassName("org.hecker.vpn", "org.hecker.vpn.VpnService"),
                    connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException denied) { failWaiting(R.string.vpn_controller_denied); return; }
        if (!bound) failWaiting(R.string.vpn_unavailable);
        else main.postDelayed(bindTimeout, 10000);
    }
    private void dispatch(Pending action) {
        IHeckerVpnService remote = service;
        if (remote == null) { action.callback.accept(error(R.string.vpn_controller_failed)); return; }
        io.execute(() -> {
            Bundle value;
            boolean disconnected = false;
            try {
                value = remote.call(action.request);
                if (value == null) value = error(R.string.vpn_controller_failed);
                else value.putBoolean("controller_available", true);
            } catch (SecurityException denied) { value = error(R.string.vpn_controller_denied); }
            catch (RemoteException failure) { value = error(R.string.vpn_controller_failed); disconnected = true; }
            catch (RuntimeException failure) { value = error(R.string.vpn_controller_failed); }
            Bundle result = value; boolean release = disconnected;
            main.post(() -> {
                if (closed) return;
                if (release && service == remote) unbind();
                action.callback.accept(result);
            });
        });
    }
    private Bundle error(int message) {
        Bundle result = new Bundle(); result.putString("status", "error");
        result.putBoolean("controller_available", false);
        result.putString("error", context.getString(message)); return result;
    }
    private void unbind() {
        main.removeCallbacks(bindTimeout); service = null;
        if (bound) { context.unbindService(connection); bound = false; }
    }
    private void failWaiting(int message) {
        unbind();
        ArrayList<Pending> pending = new ArrayList<>(waiting); waiting.clear();
        for (Pending action : pending) if (!closed) action.callback.accept(error(message));
    }
    @Override public void close() {
        closed = true; waiting.clear(); main.removeCallbacksAndMessages(null); unbind(); io.shutdown();
    }
    static Bundle request(String op) { Bundle request = new Bundle(); request.putString("op", op); return request; }
}
