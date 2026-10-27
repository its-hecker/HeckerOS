// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.util.Log;
import com.android.launcher3.util.SafeCloseable;

/** Optional computer-control integration must never prevent HOME from starting. */
public final class HeckerAutomationListener {
    private HeckerAutomationListener() {}

    public static SafeCloseable register(Runnable registration, Runnable cleanup) {
        try {
            registration.run();
        } catch (SecurityException unavailable) {
            Log.w("HeckerLauncher", "Automation listener access is unavailable", unavailable);
            // Do not unregister a listener which the service never accepted.
            return () -> {};
        }
        return () -> {
            try { cleanup.run(); }
            catch (SecurityException revoked) {
                Log.w("HeckerLauncher", "Automation listener access was revoked", revoked);
            }
        };
    }
}
