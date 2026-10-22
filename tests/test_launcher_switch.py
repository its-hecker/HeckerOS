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
 public static final int MODE_PRIVATE=0;
 public final SharedPreferences preferences=new SharedPreferences();
 public SharedPreferences getSharedPreferences(String name,int mode) { return preferences; }
 public Context getApplicationContext() { return this; }
 public <T> T getSystemService(Class<T> type) {
  return type.cast(type == android.app.role.RoleManager.class ? roles : overlays);
 }
 public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
 public java.util.concurrent.Executor getMainExecutor() { return Runnable::run; }
}''',
    "android/content/SharedPreferences.java": '''package android.content;
public class SharedPreferences {
 private final java.util.Map<String,String> values=new java.util.HashMap<>();
 public String getString(String name,String fallback) { return values.getOrDefault(name,fallback); }
 public Editor edit() { return new Editor(); }
 public class Editor {
  private String key,value;
  public Editor putString(String name,String text) { key=name;value=text;return this; }
  public Editor remove(String name) { key=name;value=null;return this; }
  public boolean commit() { if(value==null) values.remove(key);else values.put(key,value);return true; }
 }
}''',
    "android/app/role/RoleManager.java": '''package android.app.role;
public class RoleManager {
 public static final String ROLE_HOME="home";
 public static final int MANAGE_HOLDERS_FLAG_DONT_KILL_APP=1;
 public String home="com.google.android.apps.nexuslauncher";
 public boolean permit=true, fail=false;
 public int writes=0;
 public boolean isRoleAvailable(String role) { return true; }
 public java.util.List<String> getRoleHolders(String role) { return java.util.List.of(home); }
 public void addRoleHolderAsUser(String role,String pkg,int flags,android.os.UserHandle user,
   java.util.concurrent.Executor executor,java.util.function.Consumer<Boolean> result) {
  writes++;
  if (fail) throw new SecurityException("role rejected");
  executor.execute(() -> { if (permit) home=pkg; result.accept(permit); });
 }
 public void removeRoleHolderAsUser(String role,String pkg,int flags,android.os.UserHandle user,
   java.util.concurrent.Executor executor,java.util.function.Consumer<Boolean> result) {
  writes++; home=""; executor.execute(() -> result.accept(true));
 }
}''',
    "android/content/om/OverlayIdentifier.java": '''package android.content.om;
public class OverlayIdentifier { public OverlayIdentifier(String pkg) {} }''',
    "android/content/om/OverlayManagerTransaction.java": '''package android.content.om;
public class OverlayManagerTransaction {
 public boolean enabled;
 public static class Builder {
  private boolean enabled;
  public Builder setEnabled(OverlayIdentifier id,boolean value,int user) { enabled=value;return this; }
  public OverlayManagerTransaction build() {
   OverlayManagerTransaction result=new OverlayManagerTransaction(); result.enabled=enabled;return result;
  }
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
 public boolean enabled=false, priorityFail=false;
 public int priorities=0;
 public void commit(OverlayManagerTransaction transaction) {
  if(priorityFail) throw new SecurityException("priority denied");
  enabled=transaction.enabled; writes++; if(enabled) priorities++;
 }
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
public class UserHandle { public static final UserHandle SYSTEM=new UserHandle(); public int getIdentifier() { return 0; } }''',
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
  if(args[0].equals("empty")) context.roles.home="";
  controller.select(LauncherController.HECKER, success -> check(success));
  check(controller.matches(LauncherController.HECKER));
  check(controller.restartNeeded());
  check((args[0].equals("empty") ? "" : LauncherController.PIXEL).equals(controller.current()));
  check(context.roles.writes==0 && context.overlays.writes==0);
  if (args[0].equals("home")) {
   controller.select(LauncherController.PIXEL, success -> check(success));
   check(!controller.restartNeeded());
   check(context.roles.writes==0 && context.overlays.writes==0);
  } else if (args[0].equals("failure")) {
   context.roles.permit=false;
   controller.restart(() -> { throw new AssertionError("must not reboot"); }, error -> {});
   check(LauncherController.PIXEL.equals(controller.current()));
   check(context.overlays.writes==0 && controller.restartNeeded());
   context.roles.fail=true;
   controller.restart(() -> { throw new AssertionError("must not reboot"); }, error -> {});
   check(context.overlays.writes==0 && controller.restartNeeded());
  } else if (args[0].equals("empty")) {
   boolean[] reported={false};
   controller.restart(() -> { throw new SecurityException("reboot denied"); }, error -> reported[0]=true);
   check(reported[0] && controller.current().isEmpty());
   check(!context.overlays.enabled && controller.matches(LauncherController.HECKER));
  } else if (args[0].equals("priority")) {
   context.overlays.priorityFail=true;
   boolean[] reported={false};
   controller.restart(() -> { throw new AssertionError("must not reboot"); }, error -> reported[0]=true);
   check(reported[0] && LauncherController.PIXEL.equals(controller.current()));
   check(!context.overlays.enabled && controller.restartNeeded());
  } else {
   controller.restart(() -> {
    check(context.overlays.enabled && context.overlays.priorities==1);
    check(LauncherController.HECKER.equals(controller.current()));
   }, error -> { throw new AssertionError(error); });
   check(!controller.restartNeeded());
   controller.select(LauncherController.PIXEL, success -> check(success));
   boolean[] reported={false};
   controller.restart(() -> { throw new SecurityException("reboot denied"); }, error -> reported[0]=true);
   check(reported[0]);
   check(LauncherController.HECKER.equals(controller.current()) && context.overlays.enabled);
   check(controller.restartNeeded() && controller.matches(LauncherController.PIXEL));
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

    def test_selection_keeps_both_running_home_and_recents(self):
        self.invoke("home")

    def test_failed_home_role_request_never_writes_overlay(self):
        self.invoke("failure")

    def test_explicit_restart_applies_home_and_priority_and_rolls_back_on_failure(self):
        self.invoke("restart")

    def test_priority_failure_restores_home_and_overlay_without_reboot(self):
        self.invoke("priority")

    def test_failed_restart_preserves_an_empty_previous_home_role(self):
        self.invoke("empty")
