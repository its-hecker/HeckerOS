// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import java.util.List;
import java.util.function.Consumer;

/** HOME role is the source of truth. A mutable signed RRO selects Recents at next boot. */
final class LauncherController {
    static final String HECKER = "com.android.launcher3";
    static final String PIXEL = "com.google.android.apps.nexuslauncher";
    static final String OVERLAY = "org.hecker.launcher.recents";
    private final Context context;
    private final RoleManager roles;
    private final OverlayManager overlays;
    private final SharedPreferences prefs;

    LauncherController(Context context) {
        this.context = context.getApplicationContext();
        roles = context.getSystemService(RoleManager.class);
        overlays = context.getSystemService(OverlayManager.class);
        prefs = context.getSharedPreferences("launcher_switch", 0);
        if (primaryUser() && available()) {
            int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            if (!prefs.contains("boot") || prefs.getInt("boot", -2) != boot) {
                prefs.edit().putInt("boot", boot).putBoolean("boot_hecker", heckerRecents()).commit();
            }
        }
    }

    static boolean primaryUser() { return Process.myUserHandle().equals(UserHandle.SYSTEM); }

    boolean available() {
        try {
            OverlayInfo info = overlays == null ? null : overlays.getOverlayInfo(OVERLAY, UserHandle.SYSTEM);
            return roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && info != null
                    && info.getTargetPackageName().equals("android");
        } catch (RuntimeException unavailable) { return false; }
    }

    boolean installed(String packageName) {
        if (!HECKER.equals(packageName) && !PIXEL.equals(packageName)) return false;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
            if (!app.enabled || (app.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0) return false;
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(packageName);
            for (ResolveInfo candidate : pm.queryIntentActivities(home, 0)) {
                if (candidate.activityInfo.enabled && candidate.activityInfo.exported) return true;
            }
        } catch (PackageManager.NameNotFoundException | SecurityException missing) { }
        return false;
    }

    String current() {
        try {
            List<String> holders = roles.getRoleHolders(RoleManager.ROLE_HOME);
            return holders.size() == 1 ? holders.get(0) : "";
        } catch (RuntimeException unavailable) { return ""; }
    }

    boolean heckerRecents() {
        OverlayInfo info = overlays.getOverlayInfo(OVERLAY, UserHandle.SYSTEM);
        return info != null && info.isEnabled();
    }

    boolean restartNeeded() {
        return available() && prefs.contains("boot_hecker")
                && prefs.getBoolean("boot_hecker", false) != heckerRecents();
    }

    boolean matches(String packageName) {
        return available() && HECKER.equals(packageName) == heckerRecents();
    }

    void select(String packageName, Consumer<Boolean> result) {
        if (!primaryUser() || !available() || !installed(packageName)) { result.accept(false); return; }
        boolean previous = heckerRecents();
        try {
            // Runtime resources can update now, but PMS/ATMS/SystemUI load Recents at boot.
            // Keep the previous provider until the user explicitly restarts their phone.
            overlays.setEnabled(OVERLAY, HECKER.equals(packageName), UserHandle.SYSTEM);
            roles.addRoleHolderAsUser(RoleManager.ROLE_HOME, packageName,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, UserHandle.SYSTEM,
                    context.getMainExecutor(), success -> {
                        boolean selected = success && packageName.equals(current());
                        if (!selected) {
                            try { overlays.setEnabled(OVERLAY, previous, UserHandle.SYSTEM); }
                            catch (RuntimeException failure) { /* UI reports the actual state. */ }
                        }
                        result.accept(selected);
                    });
        } catch (RuntimeException failure) {
            try { overlays.setEnabled(OVERLAY, previous, UserHandle.SYSTEM); }
            catch (RuntimeException ignored) { }
            result.accept(false);
        }
    }
}
