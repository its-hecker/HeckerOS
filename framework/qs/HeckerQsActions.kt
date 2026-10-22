/* SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import javax.inject.Inject

/** All activity launches go through native keyguard/provisioning handling. */
@SysUISingleton
class HeckerQsActions @Inject constructor(
    @Application private val context: Context,
    private val activityStarter: ActivityStarter,
) : CoreStartable {
    override fun start() {
        current = this
        migrate()
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { migrate() }
        }, IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun migrate() {
        val user = ActivityManager.getCurrentUser()
        val resolver = context.contentResolver
        if (Settings.Secure.getIntForUser(resolver, "hecker_qs_reference_migrated", 0, user) != 0) return
        val old = Settings.Secure.getStringForUser(resolver, "sysui_qs_tiles", user)
            ?: context.getString(R.string.quick_settings_tiles_default)
        if (Settings.Secure.putStringForUser(resolver, "sysui_qs_tiles", HeckerQsTileOrder.migrate(old), user)) {
            Settings.Secure.putIntForUser(resolver, "hecker_qs_layout", 0, user)
            Settings.Secure.putIntForUser(resolver, "hecker_qs_reference_migrated", 1, user)
        }
    }

    private fun open(intent: Intent) {
        try { activityStarter.postStartActivityDismissingKeyguard(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0) }
        catch (unavailable: RuntimeException) { Log.w("HeckerQuickSettings", "Shortcut unavailable", unavailable) }
    }

    companion object {
        @Volatile private var current: HeckerQsActions? = null
        fun settings(action: String) { current?.open(Intent(action)) }
        fun controls() {
            val owner = current ?: return
            val samsung = owner.context.packageManager.getLaunchIntentForPackage("com.samsung.android.oneconnect")
            owner.open(samsung ?: Intent().setComponent(ComponentName(owner.context.packageName,
                "com.android.systemui.controls.ui.ControlsActivity")))
        }
        fun dark(enabled: Boolean) {
            val owner = current ?: return
            try {
                if (!owner.context.getSystemService(UiModeManager::class.java).setNightModeActivated(enabled)) {
                    owner.open(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }
            }
            catch (_: RuntimeException) { owner.open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        }
    }
}
