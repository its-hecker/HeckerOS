// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import java.util.Arrays;

/** Keeps all profile credentials and networking operations out of the Settings UID. */
public final class VpnService extends Service {
    private final IHeckerVpnService.Stub binder = new IHeckerVpnService.Stub() {
        @Override public Bundle call(Bundle request) {
            enforceCallingPermission("org.hecker.vpn.permission.CONTROL", "HeckerVPN");
            int uid = Binder.getCallingUid();
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (UserHandle.getUserHandleForUid(uid).getIdentifier() != 0 || packages == null
                    || !Arrays.asList(packages).contains("org.hecker.suite")
                    || getPackageManager().checkSignatures(uid, android.os.Process.myUid()) != PackageManager.SIGNATURE_MATCH)
                throw new SecurityException("Owner-user HeckerLab only");
            VpnEngine engine = ((VpnApplication) getApplication()).engine();
            if (engine == null) throw new SecurityException("Owner user only");
            return engine.call(request);
        }
    };
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "disconnect".equals(intent.getAction())) {
            VpnEngine engine = ((VpnApplication) getApplication()).engine();
            if (engine != null) { Bundle request = new Bundle(); request.putString("op", "disconnect"); engine.enqueue(request); }
        }
        return START_NOT_STICKY;
    }
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        int uid = Binder.getCallingUid();
        if (uid != 0 && uid != android.os.Process.SHELL_UID && uid != android.os.Process.SYSTEM_UID) return;
        VpnEngine engine = ((VpnApplication) getApplication()).engine();
        if (engine == null) { writer.println("Owner user only"); return; }
        Bundle request = new Bundle(); request.putString("op", "state"); Bundle state = engine.call(request);
        writer.println("HeckerVPN kernel backend; credentials omitted");
        for (String key : new String[]{"status", "error", "backend"}) writer.println(key + "=" + state.getString(key, ""));
        writer.println("configured=" + state.getBoolean("configured") + " rx=" + state.getLong("rx")
                + " tx=" + state.getLong("tx") + " handshake=" + state.getLong("handshake"));
        writer.println("physicalNetwork="+state.getInt("underlying_netid"));
    }
}
