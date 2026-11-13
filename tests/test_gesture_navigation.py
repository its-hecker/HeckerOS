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
public boolean fail, missing, wrongTarget, legacyInstalled = true; public int commits;
public java.util.Map<String, Boolean> states = new java.util.HashMap<>();
public OverlayInfo getOverlayInfo(String name, android.os.UserHandle user) {
if (missing || name.endsWith("navspace") && !legacyInstalled) return null; OverlayInfo i = new OverlayInfo();
i.target = wrongTarget ? "untrusted" : name.endsWith("hidepill") ? "com.google.android.apps.nexuslauncher" : "android";
i.enabled = states.getOrDefault(user.id + ":" + name, false); return i; }
public void commit(OverlayManagerTransaction t) { if (fail) throw new SecurityException();
if (t.keys.size() < 1 || t.keys.size() > 2) throw new AssertionError("Incorrect overlay transaction"); commits++;
for (int n = 0; n < t.keys.size(); n++) { if (t.keys.get(n).endsWith("navspace") && t.states.get(n)) throw new AssertionError("Framework spacing re-enabled"); states.put(t.keys.get(n), t.states.get(n)); } } }""",
            "org/hecker/suite/GestureProbe.java": """package org.hecker.suite;
public class GestureProbe {
static void check(boolean condition) { if (!condition) throw new AssertionError(); }
public static void main(String[] args) {
android.content.Context c = new android.content.Context();
c.manager.states.put("0:unrelated.overlay", true);
c.manager.states.put("0:org.hecker.overlay.framework.navspace", true);
check(!GestureNavigationController.enabled(c));
check(GestureNavigationController.apply(c, true)); check(GestureNavigationController.enabled(c));
check(c.manager.commits == 1 && c.manager.states.get("0:unrelated.overlay"));
check(!c.manager.states.get("0:org.hecker.overlay.framework.navspace"));
c.manager.fail = true; check(!GestureNavigationController.apply(c, false));
check(GestureNavigationController.enabled(c)); c.manager.fail = false;
android.os.Process.user = 10; check(!GestureNavigationController.enabled(c));
check(GestureNavigationController.apply(c, true)); check(GestureNavigationController.apply(c, false));
check(!GestureNavigationController.enabled(c)); android.os.Process.user = 0;
check(GestureNavigationController.enabled(c)); check(GestureNavigationController.apply(c, false));
check(!GestureNavigationController.enabled(c));
c.manager.legacyInstalled = false;
check(GestureNavigationController.apply(c, true)); check(GestureNavigationController.enabled(c));
check(GestureNavigationController.restoreLegacySpacing(c));
c.manager.missing = true; check(!GestureNavigationController.apply(c, true));
c.manager.missing = false; c.manager.wrongTarget = true;
check(!GestureNavigationController.available(c)); check(!GestureNavigationController.apply(c, true));
System.out.println("Gesture toggle: atomic pair, failure preservation, restore, user isolation and target validation PASS");
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

    def test_rom_keeps_native_navigation_insets(self):
        product = (ROOT / "rom/common.mk").read_text()
        self.assertIn("HeckerHideGesturePill", product)
        self.assertNotIn("HeckerGestureSpace", product)
        strings = (ROOT / "app/res/values/gesture_navigation.xml").read_text()
        self.assertIn('name="gesture_hide_pill">Hide gesture pill</string>', strings)
        self.assertNotIn("Hide gesture pill and bottom space", strings)
