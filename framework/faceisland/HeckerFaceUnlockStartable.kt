/* SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.content.Context
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject

@SysUISingleton
class HeckerFaceUnlockStartable @Inject constructor(
    @Application private val context: Context,
    private val monitor: KeyguardUpdateMonitor,
) : CoreStartable {
    override fun start() {
        HeckerFaceUnlockIsland.start(context, monitor)
    }
}
