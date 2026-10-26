// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;

/** An authenticated bound service, not a world-readable ServiceManager endpoint. */
public final class NetLabService extends Service {
    private void enforce() {
        enforceCallingPermission("org.hecker.netlab.permission.CONTROL", "Hecker NetLab");
        int uid = Binder.getCallingUid();
        String[] packages = getPackageManager().getPackagesForUid(uid);
        if (!NetLabAccess.allows(UserHandle.getUserHandleForUid(uid).getIdentifier(), packages, true,
                getPackageManager().checkSignatures(uid, android.os.Process.myUid()) == PackageManager.SIGNATURE_MATCH))
            throw new SecurityException("Owner-user Hecker Suite only");
    }
    private Bundle call(String operation, String mac, long duration) {
        enforce();
        NetLabEngine engine = ((NetLabApplication) getApplication()).engine();
        if (engine == null) throw new SecurityException("Owner user only");
        return engine.call(operation, mac, duration);
    }
    private NetLabEngine engine(boolean localNetwork) {
        enforce();
        if (localNetwork && checkCallingPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Grant Hecker Suite local-network access first");
        NetLabEngine engine = ((NetLabApplication) getApplication()).engine();
        if (engine == null) throw new SecurityException("Owner user only"); return engine;
    }
    private Bundle result(NetLabEngine engine, String result) {
        Bundle state = engine.call("state", null, 0); state.putString("result", result); return state;
    }
    private final INetLabService.Stub binder = new INetLabService.Stub() {
        public Bundle state() { return call("state", null, 0); }
        public Bundle pause(String mac, long duration) { return call("pause", mac, duration); }
        public Bundle restore(String mac) { return call("restore", mac, 0); }
        public Bundle restoreAll() { return call("restore_all", null, 0); }
        public Bundle lanProbe() { NetLabEngine e = engine(true); return result(e, e.lan().probe()); }
        public Bundle lanScan() { NetLabEngine e = engine(true); return result(e, e.lan().scan()); }
        public Bundle lanStart(String[] addresses, String[] macs, boolean block, int up, int down, int seconds) {
            NetLabEngine e = engine(true); return result(e, e.lan().start(addresses, macs, block, up, down, seconds));
        }
        public Bundle lanUpdate(String[] addresses, String[] macs, boolean block, int up, int down) {
            NetLabEngine e = engine(true); return result(e, e.lan().update(addresses, macs, block, up, down));
        }
        // Revoking local access must never prevent stopping/restoring an owned session.
        public Bundle lanRestore(String address) { NetLabEngine e = engine(false); return result(e, e.lan().restore(address)); }
        public Bundle lanStop() { NetLabEngine e = engine(false); return result(e, e.lan().stop()); }
    };
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
        int uid=Binder.getCallingUid();
        if(uid!=android.os.Process.myUid()&&uid!=android.os.Process.SYSTEM_UID&&uid!=android.os.Process.SHELL_UID&&uid!=0){writer.println("Permission denied");return;}
        NetLabEngine e=((NetLabApplication)getApplication()).engine();if(e==null){writer.println("Owner user only");return;}
        Bundle snapshot=e.call("state",null,0),lan=snapshot.getBundle("lan");
        writer.println("HeckerNetLab IPv4 diagnostics");writer.println("controller_error="+snapshot.getString("error",""));
        if(lan==null){writer.println("lan_state=unavailable");return;}
        for(String key:new String[]{"status","error","action","scope","last_end"})writer.println(key+"="+lan.getString(key,""));
        writer.println("root="+lan.getBoolean("root")+" busy="+lan.getBoolean("busy")+" active="+lan.getBoolean("active")+" helper_exit="+lan.getInt("helper_exit",-1));
        java.util.ArrayList<Bundle> peers=lan.getParcelableArrayList("devices",Bundle.class);
        if(peers!=null)for(Bundle peer:peers){
            writer.println("peer="+peer.getStringArrayList("addresses")+" state="+peer.getString("state")+" mode="+peer.getString("mode","")
                    +" requested="+peer.getString("requested_mode","")+" policy_pending="+peer.getBoolean("policy_pending")
                    +" up_kbps="+peer.getInt("up_kbps")+" down_kbps="+peer.getInt("down_kbps")
                    +" forwarded_up="+peer.getLong("forwarded_up")+" forwarded_down="+peer.getLong("forwarded_down")
                    +" dropped_up="+peer.getLong("dropped_up")+" dropped_down="+peer.getLong("dropped_down"));
        }
        writer.println("Scope: selected IPv4 gateway traffic; IPv6 and Wi-Fi association remain outside this controller.");
    }
}
