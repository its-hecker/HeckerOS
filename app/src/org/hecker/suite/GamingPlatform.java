// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Process;
import android.provider.Settings;
import android.view.WindowManager;
import java.util.HashSet;
import java.util.Set;

/** Hidden calls are isolated; the prepare step checks their real framework declarations. */
final class GamingPlatform {
    private static int nativeMode(Context c, String pkg) throws ReflectiveOperationException {
        Object manager = c.getSystemService("game");
        return (int) manager.getClass().getMethod("getGameMode", String.class).invoke(manager, pkg);
    }
    static int gameMode(Context c, String pkg) {
        if (pkg == null) return 1;
        try {
            int actual = nativeMode(c, pkg);
            if (actual != 4) return actual;
            int saved = GamingPrefs.get(c).getInt("profile_" + pkg, 1);
            return customMatches(c, pkg, saved) ? saved : actual;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return 1; }
    }
    private static boolean nativeSupports(Context c, String pkg, int wanted) throws ReflectiveOperationException {
        Object manager = c.getSystemService("game");
        int[] modes = (int[]) manager.getClass().getMethod("getAvailableGameModes", String.class).invoke(manager, pkg);
        for (int mode : modes) if (mode == wanted) return true;
        return false;
    }
    private static Object modeInfo(Context c, String pkg) throws ReflectiveOperationException {
        Object manager = c.getSystemService("game");
        return manager.getClass().getMethod("getGameModeInfo", String.class).invoke(manager, pkg);
    }
    private static boolean customAllowed(Context c, String pkg) throws ReflectiveOperationException {
        if (!nativeSupports(c, pkg, 4)) return false;
        Object info = modeInfo(c, pkg);
        return info != null && (boolean) info.getClass().getMethod("isFpsOverrideAllowed").invoke(info);
    }
    private static int profileFps(Context c, int mode) {
        if (mode == 3) return 30;
        android.view.Display display = c.getSystemService(DisplayManager.class).getDisplay(0);
        int peak = 60;
        if (display != null) for (android.view.Display.Mode supported : display.getSupportedModes())
            peak = Math.max(peak, Math.round(supported.getRefreshRate()));
        return Math.min(90, peak);
    }
    private static float profileScale(Object info, int mode) throws ReflectiveOperationException {
        boolean allowed = (boolean) info.getClass().getMethod("isDownscalingAllowed").invoke(info);
        return mode == 3 && allowed ? .8f : 1f;
    }
    private static boolean customMatches(Context c, String pkg, int mode) throws ReflectiveOperationException {
        if (mode != 2 && mode != 3) return false;
        Object info = modeInfo(c, pkg);
        if (info == null) return false;
        Object config = info.getClass().getMethod("getGameModeConfiguration", int.class).invoke(info, 4);
        if (config == null) return false;
        return (int) config.getClass().getMethod("getFpsOverride").invoke(config) == profileFps(c, mode)
                && Math.abs((float) config.getClass().getMethod("getScalingFactor").invoke(config) - profileScale(info, mode)) < .001f;
    }
    static boolean performanceSupported(Context c, String pkg) { return modeSupported(c, pkg, 2); }
    static boolean modeSupported(Context c, String pkg, int wanted) {
        if (pkg == null || wanted < 1 || wanted > 3) return false;
        try { return nativeSupports(c, pkg, wanted) || wanted != 1 && customAllowed(c, pkg); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }
    static boolean gameMode(Context c, String pkg, int mode) {
        if (pkg == null || mode < 1 || mode > 3) return false;
        try {
            Object manager = c.getSystemService("game");
            int target = mode;
            if (!nativeSupports(c, pkg, mode)) {
                if (mode == 1 || !customAllowed(c, pkg)) return false;
                Class<?> configClass = Class.forName("android.app.GameModeConfiguration");
                Class<?> builderClass = Class.forName("android.app.GameModeConfiguration$Builder");
                Object builder = builderClass.getConstructor().newInstance();
                builderClass.getMethod("setFpsOverride", int.class).invoke(builder, profileFps(c, mode));
                builderClass.getMethod("setScalingFactor", float.class).invoke(builder, profileScale(modeInfo(c, pkg), mode));
                Object config = builderClass.getMethod("build").invoke(builder);
                manager.getClass().getMethod("updateCustomGameModeConfiguration", String.class, configClass).invoke(manager, pkg, config);
                target = 4;
            }
            manager.getClass().getMethod("setGameMode", String.class, int.class).invoke(manager, pkg, target);
            if (nativeMode(c, pkg) != target || target == 4 && !customMatches(c, pkg, mode)) return false;
            GamingPrefs.get(c).edit().putInt("profile_" + pkg, mode).apply();
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }
    static boolean ownDisplayTask(ActivityManager.RunningTaskInfo task) {
        try {
            return task.getClass().getField("displayId").getInt(task) == 0
                    && task.getClass().getField("userId").getInt(task) == Process.myUid() / 100000;
        } catch (ReflectiveOperationException e) { return false; }
    }
    static boolean freeform(ActivityManager.RunningTaskInfo task) {
        try { return (boolean) task.getClass().getMethod("isFreeform").invoke(task); }
        catch (ReflectiveOperationException e) { return false; }
    }
    static boolean supportsPopup(Context c, Intent launch) {
        // Android's task policy also supports compatibility resizing. A manifest-only
        // resizeMode check incorrectly rejects those apps and launcher aliases.
        return launch != null && launch.resolveActivity(c.getPackageManager()) != null
                && (c.getPackageManager().hasSystemFeature("android.software.freeform_window_management")
                    || Settings.Global.getInt(c.getContentResolver(), "enable_freeform_support", 0) != 0);
    }
    static void popup(Context c, Intent launch) throws ReflectiveOperationException {
        if (!supportsPopup(c, launch)) throw new IllegalArgumentException("This app does not support floating windows");
        Rect screen = c.getSystemService(WindowManager.class).getCurrentWindowMetrics().getBounds();
        int width = Math.min(screen.width() * (screen.width() > screen.height() ? 46 : 88) / 100,
                screen.height() * 90 / 100);
        int height = Math.min(screen.height() * 90 / 100, width * 16 / 10);
        int left = screen.left + (screen.width() - width) / 2;
        int top = screen.top + (screen.height() - height) / 2;
        ActivityOptions options = ActivityOptions.makeBasic();
        ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class).invoke(options, 5);
        // Always-on-top is optional; an unavailable decoration must not cancel a valid launch.
        try { ActivityOptions.class.getMethod("setTaskAlwaysOnTop", boolean.class).invoke(options, true); }
        catch (NoSuchMethodException unavailable) { }
        options.setLaunchDisplayId(0);
        options.setLaunchBounds(new Rect(left, top, left + width, top + height));
        // NEW_TASK keeps the shortcut out of the game's activity stack. No embedded ActivityView.
        // A reused fullscreen task can ignore freeform bounds. Give the shortcut its own task.
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        c.startActivity(launch, options.toBundle());
    }
    static void brightness(Context c, float value) throws ReflectiveOperationException {
        DisplayManager.class.getMethod("setTemporaryBrightness", int.class, float.class)
                .invoke(c.getSystemService(DisplayManager.class), 0, value);
    }
    static int clean(Context c, String game) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        Set<Integer> protectedUids = new HashSet<>();
        Set<String> protectedPackages = new HashSet<>(GamingPrefs.shortcuts(c));
        protectedPackages.add(game); protectedPackages.add(c.getPackageName());
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo launcher = c.getPackageManager().resolveActivity(home, 0);
        if (launcher != null && launcher.activityInfo != null) protectedPackages.add(launcher.activityInfo.packageName);
        String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null && ime.contains("/")) protectedPackages.add(ime.substring(0, ime.indexOf('/')));
        for (String pkg : protectedPackages) {
            try { protectedUids.add(c.getPackageManager().getApplicationInfo(pkg, 0).uid); }
            catch (PackageManager.NameNotFoundException ignored) { }
        }
        Set<String> candidates = new HashSet<>();
        java.util.List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) return 0;
        Set<Integer> systemUids = new HashSet<>();
        // Protect the complete UID if any sibling process owns an active service.
        // killBackgroundProcesses acts on a package, rather than one selected PID.
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED) protectedUids.add(process.uid);
            if (process.pkgList == null) { protectedUids.add(process.uid); continue; }
            boolean system = false;
            for (String pkg : process.pkgList) {
                try {
                    ApplicationInfo info = c.getPackageManager().getApplicationInfo(pkg, 0);
                    system |= (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_PERSISTENT)) != 0;
                } catch (PackageManager.NameNotFoundException e) { system = true; }
            }
            if (system) systemUids.add(process.uid);
        }
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.pkgList == null) continue;
            if (GamingPolicy.canClean(process.uid, Process.myUid(), process.importance, systemUids.contains(process.uid), protectedUids)) {
                for (String pkg : process.pkgList) if (!protectedPackages.contains(pkg)) candidates.add(pkg);
            }
        }
        for (String pkg : candidates) am.killBackgroundProcesses(pkg);
        return candidates.size();
    }
    private GamingPlatform() {}
}
