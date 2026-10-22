# SPDX-License-Identifier: Apache-2.0
"""Exercise the denied-listener path from the device's launcher crash."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class LauncherAutomationTests(unittest.TestCase):
    def test_optional_registration_does_not_take_down_home_or_run_invalid_cleanup(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            files = {
                'android/util/Log.java': 'package android.util; public class Log { public static int w(String t,String m,Throwable e){return 0;} }',
                'com/android/launcher3/util/SafeCloseable.java': 'package com.android.launcher3.util; public interface SafeCloseable extends AutoCloseable { void close(); }',
                'Test.java': '''import com.android.launcher3.hecker.HeckerAutomationListener;
public class Test {
  public static void main(String[] args) {
    int[] cleanup = {0};
    var rejected = HeckerAutomationListener.register(
        () -> { throw new SecurityException("getRoleHoldersAsUser"); }, () -> cleanup[0]++);
    rejected.close();
    if(cleanup[0] != 0) throw new AssertionError("Unregistered a rejected listener");
    int[] registration = {0};
    var accepted = HeckerAutomationListener.register(() -> registration[0]++, () -> cleanup[0]++);
    if(registration[0] != 1 || cleanup[0] != 0) throw new AssertionError("Registration order changed");
    accepted.close();
    if(cleanup[0] != 1) throw new AssertionError("Registered listener was leaked");
    HeckerAutomationListener.register(() -> {}, () -> {throw new SecurityException("role revoked");}).close();
    boolean propagated = false;
    try { HeckerAutomationListener.register(() -> {throw new IllegalStateException("bug");}, () -> {}); }
    catch(IllegalStateException expected) { propagated = true; }
    if(!propagated) throw new AssertionError("Unrelated programming failure was hidden");
  }
}'''}
            paths = []
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                paths.append(str(path))
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root/'classes'),
                            *paths, str(ROOT/'launcher/src/HeckerAutomationListener.java')], check=True)
            subprocess.run(['java', '-cp', str(root/'classes'), 'Test'], check=True)
