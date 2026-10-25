// SPDX-License-Identifier: Apache-2.0
package android.os;
import android.os.Bundle;
/** @hide Platform-signed Suite/SystemUI only; caller user is checked by the service. */
interface IHeckerMemoryService {
    String[] manageablePackages();
    Bundle state();
    Bundle boost();
}
