// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
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

    LauncherController(Context context) {
        this.context = context.getApplicationContext();
        roles = context.getSystemService(RoleManager.class);
        overlays = context.getSystemService(OverlayManager.class);
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
        String home = current();
        return available() && (HECKER.equals(home) || PIXEL.equals(home))
                && HECKER.equals(home) != heckerRecents();
    }

    boolean matches(String packageName) {
        return packageName.equals(current());
    }

    void select(String packageName, Consumer<Boolean> result) {
        if (!primaryUser() || !available() || !installed(packageName)) { result.accept(false); return; }
        try {
            // A HOME change must not replace framework resources under the running
            // Recents processes. Apply that overlay only in the explicit restart action.
            roles.addRoleHolderAsUser(RoleManager.ROLE_HOME, packageName,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, UserHandle.SYSTEM,
                    context.getMainExecutor(), success -> {
                        result.accept(success && packageName.equals(current()));
                    });
        } catch (RuntimeException failure) {
            result.accept(false);
        }
    }

    void restart(Runnable reboot) {
        String home = current();
        if (!primaryUser() || !available() || !installed(home)) {
            throw new IllegalStateException("Selected system launcher is unavailable");
        }
        boolean previous = heckerRecents();
        try {
            overlays.setEnabled(OVERLAY, HECKER.equals(home), UserHandle.SYSTEM);
            reboot.run();
        } catch (RuntimeException failure) {
            try { overlays.setEnabled(OVERLAY, previous, UserHandle.SYSTEM); }
            catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
}
