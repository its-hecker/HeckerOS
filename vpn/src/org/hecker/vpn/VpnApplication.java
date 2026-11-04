// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;

import android.app.Application;
public final class VpnApplication extends Application {
    private VpnEngine engine;
    @Override public void onCreate() {
        super.onCreate();
        if (android.os.UserHandle.getUserHandleForUid(android.os.Process.myUid()).getIdentifier() == 0) engine = new VpnEngine(this);
    }
    VpnEngine engine() { return engine; }
}
