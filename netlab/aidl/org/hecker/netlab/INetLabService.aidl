// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;
import android.os.Bundle;
/** Fixed operations, signature permission and caller-UID checks; no arbitrary commands/rules. */
interface INetLabService {
    Bundle state();
    Bundle pause(String mac, long durationMillis);
    Bundle restore(String mac);
    Bundle restoreAll();
    Bundle lanProbe();
    Bundle lanScan();
    Bundle lanStart(in String[] addresses, in String[] macs, boolean block, int uploadKbps, int downloadKbps, int seconds);
    Bundle lanRestore(String address);
    Bundle lanStop();
    Bundle lanUpdate(in String[] addresses, in String[] macs, boolean block, int uploadKbps, int downloadKbps);
}
