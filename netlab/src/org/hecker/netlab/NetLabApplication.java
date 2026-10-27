// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import android.app.Application;
import android.os.UserHandle;

/** Separate, non-root persistent platform package; only the owner controls the global hotspot. */
public final class NetLabApplication extends Application {
    private NetLabEngine engine;
    @Override public void onCreate() {
        super.onCreate();
        if (UserHandle.myUserId() == 0) engine = new NetLabEngine(this);
    }
    NetLabEngine engine() { return engine; }
}
