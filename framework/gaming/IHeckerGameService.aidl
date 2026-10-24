// SPDX-License-Identifier: Apache-2.0
package android.os;
import android.os.Bundle;
/** @hide Trusted, leased game controls. Only the platform-signed Suite UID may call. */
interface IHeckerGameService {
    Bundle update(IBinder owner, int taskId, int flags);
    void end(IBinder owner);
}
