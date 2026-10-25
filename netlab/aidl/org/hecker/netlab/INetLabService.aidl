// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;
import android.os.Bundle;
/** Fixed operations, signature permission and caller-UID checks; no shell or arbitrary rules. */
interface INetLabService {
    Bundle state();
    Bundle pause(String mac, long durationMillis);
    Bundle restore(String mac);
    Bundle restoreAll();
}
