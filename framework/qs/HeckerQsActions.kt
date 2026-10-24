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
import com.android.systemui.controls.dagger.ControlsComponent
import com.android.systemui.controls.ui.ControlsUiController
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
    private val controlsComponent: ControlsComponent,
) : CoreStartable {
    override fun start() {
        current = this
        HeckerStatusBarPopup.start(context)
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
        @JvmStatic fun boost() { current?.let { HeckerStatusBarPopup.boost(it.context) } }
        @JvmStatic fun settings(action: String) { current?.open(Intent(action)) }
        fun controls() {
            val owner = current ?: return
            val samsung = owner.context.packageManager.getLaunchIntentForPackage("com.samsung.android.oneconnect")
            if (samsung != null) { owner.open(samsung); return }
            val controller = owner.controlsComponent.getControlsUiController().orElse(null)
            // Resolve provider setup when no controls have been configured.
            owner.open(if (controller == null) Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                else Intent().setComponent(ComponentName(owner.context, controller.resolveActivity()))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(ControlsUiController.EXTRA_ANIMATE, true))
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
