// SPDX-License-Identifier: Apache-2.0
package org.hecker.vpn;
import android.os.Bundle;
/** Signature-only structured requests; configuration never enters a shell API. */
interface IHeckerVpnService {
    Bundle call(in Bundle request);
}
