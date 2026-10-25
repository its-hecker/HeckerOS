// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import android.content.*;

/** No exported control broadcast; the notification holds a restore-only PendingIntent. */
public final class NetLabReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NetLabEngine engine = ((NetLabApplication) context.getApplicationContext()).engine();
        if (engine != null) engine.signal("org.hecker.netlab.RESTORE".equals(intent.getAction()));
    }
}
