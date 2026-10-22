// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.om.OverlayInfo;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import java.util.List;
import java.util.function.Consumer;

/** Stage a choice, then change HOME and the signed Recents overlay immediately before reboot. */
final class LauncherController {
    static final String HECKER = "com.android.launcher3";
    static final String PIXEL = "com.google.android.apps.nexuslauncher";
    static final String OVERLAY = "org.hecker.launcher.recents";
    private final Context context;
    private final RoleManager roles;
    private final OverlayManager overlays;
    private final SharedPreferences preferences;

    LauncherController(Context context) {
        this.context = context.getApplicationContext();
        roles = context.getSystemService(RoleManager.class);
        overlays = context.getSystemService(OverlayManager.class);
        preferences = this.context.getSharedPreferences("launcher_switch", Context.MODE_PRIVATE);
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
        String home = selected();
        return available() && (HECKER.equals(home) || PIXEL.equals(home))
                && (!home.equals(current()) || HECKER.equals(home) != heckerRecents());
    }

    String selected() { return preferences.getString("pending_home", current()); }

    boolean matches(String packageName) {
        return packageName.equals(selected());
    }

    void select(String packageName, Consumer<Boolean> result) {
        if (!primaryUser() || !available() || !installed(packageName)) { result.accept(false); return; }
        try {
            // Keep the working HOME/Quickstep pair until the explicit restart action.
            result.accept(preferences.edit().putString("pending_home", packageName).commit());
        } catch (RuntimeException failure) {
            result.accept(false);
        }
    }

    void restart(Runnable reboot, Consumer<RuntimeException> onFailure) {
        String home = selected();
        if (!primaryUser() || !available() || !installed(home)) {
            onFailure.accept(new IllegalStateException("Selected system launcher is unavailable"));
            return;
        }
        String previousHome = current();
        boolean previous = heckerRecents();
        try {
            roles.addRoleHolderAsUser(RoleManager.ROLE_HOME, home,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, UserHandle.SYSTEM,
                    context.getMainExecutor(), success -> {
                        if (!success || !home.equals(current())) {
                            onFailure.accept(new IllegalStateException("HOME role change was rejected"));
                            return;
                        }
                        try {
                            // TYPE_SET_ENABLED also moves the overlay to highest priority
                            // in OverlayManagerService, in the same transaction.
                            overlays.commit(new OverlayManagerTransaction.Builder()
                                    .setEnabled(new OverlayIdentifier(OVERLAY), HECKER.equals(home),
                                            UserHandle.SYSTEM.getIdentifier()).build());
                            if (heckerRecents() != HECKER.equals(home)) {
                                throw new IllegalStateException("Recents overlay could not be enabled");
                            }
                            if (!preferences.edit().remove("pending_home").commit()) {
                                throw new IllegalStateException("Launcher choice could not be saved");
                            }
                            reboot.run();
                        } catch (RuntimeException failure) {
                            preferences.edit().putString("pending_home", home).commit();
                            rollback(previousHome, home, previous, failure, onFailure);
                        }
                    });
        } catch (RuntimeException failure) {
            onFailure.accept(failure);
        }
    }

    private void rollback(String previousHome, String selectedHome, boolean previous,
            RuntimeException failure, Consumer<RuntimeException> onFailure) {
        try { overlays.setEnabled(OVERLAY, previous, UserHandle.SYSTEM); }
        catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
        Consumer<Boolean> restored = success -> {
            if (!success) failure.addSuppressed(new IllegalStateException("HOME rollback was rejected"));
            onFailure.accept(failure);
        };
        try {
            if (previousHome.isEmpty()) {
                roles.removeRoleHolderAsUser(RoleManager.ROLE_HOME, selectedHome,
                        RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, UserHandle.SYSTEM,
                        context.getMainExecutor(), restored);
            } else {
                roles.addRoleHolderAsUser(RoleManager.ROLE_HOME, previousHome,
                        RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, UserHandle.SYSTEM,
                        context.getMainExecutor(), restored);
            }
        } catch (RuntimeException rollback) {
            failure.addSuppressed(rollback);
            onFailure.accept(failure);
        }
    }
}
