# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class GestureNavigationTests(unittest.TestCase):
    def test_atomic_toggle_failure_and_user_isolation(self):
        sources = {
            "android/content/Context.java": """package android.content;
public class Context { public android.content.om.OverlayManager manager = new android.content.om.OverlayManager();
public <T> T getSystemService(Class<T> type) { return type.cast(manager); } }""",
            "android/os/UserHandle.java": """package android.os; public class UserHandle {
public int id; public int getIdentifier() { return id; } }""",
            "android/os/Process.java": """package android.os; public class Process { public static int user;
public static UserHandle myUserHandle() { UserHandle h = new UserHandle(); h.id = user; return h; } }""",
            "android/content/om/OverlayIdentifier.java": """package android.content.om;
public class OverlayIdentifier { public String name; public OverlayIdentifier(String name) { this.name = name; } }""",
            "android/content/om/OverlayInfo.java": """package android.content.om; public class OverlayInfo {
public String target; public boolean enabled; public String getTargetPackageName() { return target; }
public boolean isEnabled() { return enabled; } }""",
            "android/content/om/OverlayManagerTransaction.java": """package android.content.om;
public class OverlayManagerTransaction { public java.util.List<String> keys = new java.util.ArrayList<>();
public java.util.List<Boolean> states = new java.util.ArrayList<>();
public static class Builder { OverlayManagerTransaction t = new OverlayManagerTransaction();
public Builder setEnabled(OverlayIdentifier id, boolean state, int user) {
t.keys.add(user + ":" + id.name); t.states.add(state); return this; }
public OverlayManagerTransaction build() { return t; } } }""",
            "android/content/om/OverlayManager.java": """package android.content.om; public class OverlayManager {
public boolean fail, missing, missingLegacy, wrongTarget, wrongLegacyTarget; public int commits;
public java.util.Map<String, Boolean> states = new java.util.HashMap<>();
public OverlayInfo getOverlayInfo(String name, android.os.UserHandle user) {
if (missing || (missingLegacy && name.endsWith("navspace"))) return null; OverlayInfo i = new OverlayInfo();
i.target = wrongTarget || (wrongLegacyTarget && name.endsWith("navspace"))
    ? "untrusted" : name.endsWith("hidepill") ? "com.google.android.apps.nexuslauncher" : "android";
i.enabled = states.getOrDefault(user.id + ":" + name, false); return i; }
public void commit(OverlayManagerTransaction t) { if (fail) throw new SecurityException();
if (t.keys.size() < 1 || t.keys.size() > 2) throw new AssertionError("Invalid overlay transaction"); commits++;
for (int n = 0; n < t.keys.size(); n++) states.put(t.keys.get(n), t.states.get(n)); } }""",
            "org/hecker/suite/GestureProbe.java": """package org.hecker.suite;
public class GestureProbe {
static void check(boolean condition) { if (!condition) throw new AssertionError(); }
public static void main(String[] args) {
android.content.Context c = new android.content.Context();
c.manager.states.put("0:unrelated.overlay", true);
check(!GestureNavigationController.enabled(c));
// Simulate the legacy ROM state: both pill and zero taskbar inset active.
c.manager.states.put("0:org.hecker.overlay.framework.navspace", true);
check(GestureNavigationController.restoreLegacySpacing(c));
check(!c.manager.states.get("0:org.hecker.overlay.framework.navspace"));
c.manager.states.put("0:org.hecker.overlay.framework.navspace", true);
check(GestureNavigationController.apply(c, true)); check(GestureNavigationController.enabled(c));
check(!c.manager.states.get("0:org.hecker.overlay.framework.navspace"));
check(c.manager.states.get("0:unrelated.overlay"));
check(c.manager.commits == 2);
c.manager.fail = true; check(!GestureNavigationController.apply(c, false));
check(GestureNavigationController.enabled(c)); c.manager.fail = false;
// The migration and toggle are strictly scoped to the current user.
android.os.Process.user = 10; check(!GestureNavigationController.enabled(c));
c.manager.states.put("10:org.hecker.overlay.framework.navspace", true);
check(GestureNavigationController.apply(c, true));
check(!c.manager.states.get("10:org.hecker.overlay.framework.navspace"));
check(GestureNavigationController.apply(c, false));
check(!GestureNavigationController.enabled(c)); android.os.Process.user = 0;
check(GestureNavigationController.enabled(c)); check(GestureNavigationController.apply(c, false));
check(!GestureNavigationController.enabled(c));
// A future build may remove the legacy overlay completely.
c.manager.missingLegacy = true; check(GestureNavigationController.available(c));
check(GestureNavigationController.apply(c, true)); check(GestureNavigationController.enabled(c));
c.manager.missingLegacy = false;
c.manager.wrongLegacyTarget = true;
check(GestureNavigationController.available(c)); check(!GestureNavigationController.apply(c, false));
c.manager.wrongLegacyTarget = false;
c.manager.missing = true; check(!GestureNavigationController.apply(c, true));
c.manager.missing = false; c.manager.wrongTarget = true;
check(!GestureNavigationController.available(c)); check(!GestureNavigationController.apply(c, true));
System.out.println("Gesture toggle: visual-only handle, zero-inset migration, failure preservation, user isolation and target validation PASS");
} }""",
        }
        with tempfile.TemporaryDirectory(prefix="hecker-gestures-") as temp:
            out = Path(temp)
            for name, source in sources.items():
                path = out / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(out / "classes"),
                *map(str, out.rglob("*.java")),
                str(ROOT / "app/src/org/hecker/suite/GestureNavigationController.java")], check=True)
            subprocess.run(["java", "-cp", str(out / "classes"), "org.hecker.suite.GestureProbe"], check=True)

    def test_zero_inset_overlay_not_packaged(self):
        common = (ROOT / "rom/common.mk").read_text()
        active = [line.strip() for line in common.splitlines()
                  if line.strip().startswith("PRODUCT_PACKAGES +=")]
        self.assertFalse(any("HeckerGestureSpace" in line for line in active))
        self.assertFalse(any("HeckerHideGesturePill" in line for line in active))
        # Keep the legacy package name known to migration code, but not in
        # the installed ROM so the framework's original inset is retained.
        controller = (ROOT / "app/src/org/hecker/suite/GestureNavigationController.java").read_text()
        self.assertIn('LEGACY_SPACE_OVERLAY = "org.hecker.overlay.framework.navspace"', controller)
        self.assertIn('transaction.setEnabled(new OverlayIdentifier(LEGACY_SPACE_OVERLAY), false', controller)
        self.assertIn('restoreLegacySpacing(Context context)', controller)

    def test_removed_from_suite_navigation(self):
        suite = (ROOT / "app/src/org/hecker/suite/SuiteActivity.java").read_text()
        router = (ROOT / "app/src/org/hecker/suite/SuiteRouterActivity.java").read_text()
        manifest = (ROOT / "app/AndroidManifest.xml").read_text()
        self.assertNotIn('v->open("GestureNavigationActivity")', suite)
        self.assertNotIn('case "GestureNavigationActivity"', router)
        self.assertNotIn('<activity android:name=".GestureNavigationActivity"', manifest)

    def test_migration_runs_off_ui_thread(self):
        source = (ROOT / "app/src/org/hecker/suite/GestureNavigationPreference.java").read_text()
        self.assertIn("WORK.execute(() -> {", source)
        self.assertIn("GestureNavigationController.restoreLegacySpacing(activity)", source)
        self.assertIn("toggle.setChecked(GestureNavigationController.enabled(activity))", source)
        self.assertIn("toggle.setEnabled(GestureNavigationController.available(activity))", source)

    def test_pill_overlay_only_changes_cosmetic_colors(self):
        overlay = (ROOT / "rom/overlays/HideGesturePill/res/values/colors.xml").read_text()
        self.assertIn("taskbar_stashed_handle_light_color", overlay)
        self.assertIn("taskbar_stashed_handle_dark_color", overlay)
        self.assertNotIn("taskbar_stashed_size", overlay)
