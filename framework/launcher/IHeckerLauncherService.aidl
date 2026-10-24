// SPDX-License-Identifier: Apache-2.0
package android.os;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
/** @hide Only the preinstalled, active-user default Pixel Launcher may call. */
interface IHeckerLauncherService {
    Bundle configuration();
    ParcelFileDescriptor wallpaper();
    void sleep();
}
