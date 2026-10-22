# SPDX-License-Identifier: Apache-2.0
"""Run the real controller against role/overlay APIs with observable writes and failures."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    "android/content/Context.java": '''package android.content;
public class Context {
 public final android.app.role.RoleManager roles = new android.app.role.RoleManager();
 public final android.content.om.OverlayManager overlays = new android.content.om.OverlayManager();
 public Context getApplicationContext() { return this; }
 public <T> T getSystemService(Class<T> type) {
  return type.cast(type == android.app.role.RoleManager.class ? roles : overlays);
 }
 public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
 public java.util.concurrent.Executor getMainExecutor() { return Runnable::run; }
}''',
    "android/app/role/RoleManager.java": '''package android.app.role;
public class RoleManager {
 public static final String ROLE_HOME="home";
 public static final int MANAGE_HOLDERS_FLAG_DONT_KILL_APP=1;
 public String home="com.google.android.apps.nexuslauncher";
 public boolean permit=true, fail=false;
 public boolean isRoleAvailable(String role) { return true; }
 public java.util.List<String> getRoleHolders(String role) { return java.util.List.of(home); }
 public void addRoleHolderAsUser(String role,String pkg,int flags,android.os.UserHandle user,
   java.util.concurrent.Executor executor,java.util.function.Consumer<Boolean> result) {
  if (fail) throw new SecurityException("role rejected");
  executor.execute(() -> { if (permit) home=pkg; result.accept(permit); });
 }
}''',
    "android/content/om/OverlayInfo.java": '''package android.content.om;
public class OverlayInfo {
 private final boolean enabled;
 public OverlayInfo(boolean value) { enabled=value; }
 public String getTargetPackageName() { return "android"; }
 public boolean isEnabled() { return enabled; }
}''',
    "android/content/om/OverlayManager.java": '''package android.content.om;
public class OverlayManager {
 public boolean enabled=false;
 public int writes=0;
 public OverlayInfo getOverlayInfo(String pkg,android.os.UserHandle user) { return new OverlayInfo(enabled); }
 public void setEnabled(String pkg,boolean value,android.os.UserHandle user) { writes++; enabled=value; }
}''',
    "android/content/Intent.java": '''package android.content;
public class Intent {
 public static final String ACTION_MAIN="main", CATEGORY_HOME="home";
 public Intent(String action) {}
 public Intent addCategory(String category) { return this; }
 public Intent setPackage(String pkg) { return this; }
}''',
    "android/content/pm/ApplicationInfo.java": '''package android.content.pm;
public class ApplicationInfo {
 public static final int FLAG_SYSTEM=1, FLAG_UPDATED_SYSTEM_APP=2;
 public boolean enabled=true;
 public int flags=FLAG_SYSTEM;
}''',
    "android/content/pm/ActivityInfo.java": '''package android.content.pm;
public class ActivityInfo { public boolean enabled=true, exported=true; }''',
    "android/content/pm/ResolveInfo.java": '''package android.content.pm;
public class ResolveInfo { public ActivityInfo activityInfo=new ActivityInfo(); }''',
    "android/content/pm/PackageManager.java": '''package android.content.pm;
public class PackageManager {
 public static class NameNotFoundException extends Exception {}
 public ApplicationInfo getApplicationInfo(String pkg,int flags) throws NameNotFoundException {
  return new ApplicationInfo();
 }
 public java.util.List<ResolveInfo> queryIntentActivities(android.content.Intent intent,int flags) {
  return java.util.List.of(new ResolveInfo());
 }
}''',
    "android/os/UserHandle.java": '''package android.os;
public class UserHandle { public static final UserHandle SYSTEM=new UserHandle(); }''',
    "android/os/Process.java": '''package android.os;
public class Process { public static UserHandle myUserHandle() { return UserHandle.SYSTEM; } }''',
}
HARNESS = '''package org.hecker.suite;
import android.content.Context;
public class LauncherSwitchHarness {
 static void check(boolean value) { if (!value) throw new AssertionError(); }
 public static void main(String[] args) {
  Context context=new Context();
  LauncherController controller=new LauncherController(context);
  if (args[0].equals("home")) {
   controller.select(LauncherController.HECKER, success -> check(success));
   check(controller.matches(LauncherController.HECKER));
   check(controller.restartNeeded());
   check(context.overlays.writes==0 && !context.overlays.enabled);
   controller.select(LauncherController.PIXEL, success -> check(success));
   check(!controller.restartNeeded());
   check(context.overlays.writes==0);
  } else if (args[0].equals("failure")) {
   context.roles.permit=false;
   controller.select(LauncherController.HECKER, success -> check(!success));
   check(LauncherController.PIXEL.equals(context.roles.home));
   check(context.overlays.writes==0);
   context.roles.fail=true;
   controller.select(LauncherController.HECKER, success -> check(!success));
   check(context.overlays.writes==0);
  } else {
   controller.select(LauncherController.HECKER, success -> check(success));
   controller.restart(() -> check(context.overlays.enabled && context.overlays.writes==1));
   check(!controller.restartNeeded());
   context.overlays.enabled=false;
   context.overlays.writes=0;
   try {
    controller.restart(() -> { throw new SecurityException("reboot denied"); });
    throw new AssertionError("Restart failure was hidden");
   } catch (SecurityException expected) {}
   check(!context.overlays.enabled && context.overlays.writes==2);
   check(controller.restartNeeded());
  }
 }
}'''


class LauncherSwitchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.root = Path(cls.temporary.name)
        sources = []
        for relative, source in {**STUBS, "org/hecker/suite/LauncherSwitchHarness.java": HARNESS}.items():
            path = cls.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            sources.append(path)
        sources.append(ROOT / "app/src/org/hecker/suite/LauncherController.java")
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(cls.root),
                        *map(str, sources)], check=True, capture_output=True, text=True)

    def invoke(self, case):
        result = subprocess.run(["java", "-cp", str(self.root),
                                 "org.hecker.suite.LauncherSwitchHarness", case], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_home_switch_does_not_change_running_recents(self):
        self.invoke("home")

    def test_failed_home_role_request_never_writes_overlay(self):
        self.invoke("failure")

    def test_explicit_restart_applies_overlay_and_rolls_back_on_failure(self):
        self.invoke("restart")
