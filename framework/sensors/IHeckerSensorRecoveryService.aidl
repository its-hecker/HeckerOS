// SPDX-License-Identifier: Apache-2.0
package android.os;
import android.os.Bundle;
/** @hide Manual sensor recovery. Calls are authenticated against the signed Suite UID. */
interface IHeckerSensorRecoveryService {
    Bundle requestRecovery();
    Bundle getState();
}
