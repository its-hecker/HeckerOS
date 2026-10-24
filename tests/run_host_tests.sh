#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -eo pipefail
suite_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$suite_root"
python3 tools/check_source.py
python3 -m unittest discover -s tests -p 'test_*.py'
suite_test_output="$(mktemp -d)"
trap 'rm -rf -- "$suite_test_output"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    prebuilts/librepods/hecker/SoundcoreR50iCodec.java tests/SoundcoreR50iTest.java
java -cp "$suite_test_output" SoundcoreR50iTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    app/src/org/hecker/suite/RawFrameDecoder.java tests/RawFrameDecoderTest.java
java -cp "$suite_test_output" org.hecker.suite.RawFrameDecoderTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    tests/host-stubs/android/content/Context.java \
    tests/host-stubs/android/content/pm/ApplicationInfo.java \
    tests/host-stubs/android/content/pm/PackageManager.java \
    tests/host-stubs/android/os/Binder.java tests/host-stubs/android/os/UserHandle.java \
    framework/HeckerSuiteAppLockAccess.java tests/HeckerSuiteAccessTest.java
java -cp "$suite_test_output" com.android.server.pm.HeckerSuiteAccessTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    app/src/org/hecker/suite/GamingPolicy.java tests/GamingPolicyTest.java
java -cp "$suite_test_output" org.hecker.suite.GamingPolicyTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    app/src/org/hecker/suite/GamingPanelLayout.java tests/GamingPanelLayoutTest.java
java -cp "$suite_test_output" org.hecker.suite.GamingPanelLayoutTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    app/src/org/hecker/suite/NativeIconPackSelection.java tests/NativeIconPackSelectionTest.java
java -cp "$suite_test_output" org.hecker.suite.NativeIconPackSelectionTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/lyrics/LyricsPolicy.java tests/LyricsPolicyTest.java
java -cp "$suite_test_output" com.android.systemui.hecker.LyricsPolicyTest

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/headphones/HeadphoneBattery.java tests/HeadphoneBatteryTest.java
java -cp "$suite_test_output" com.android.systemui.hecker.HeadphoneBatteryTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/sandbox/HeckerSandboxRules.java tests/HeckerSandboxRulesTest.java
java -cp "$suite_test_output" com.android.internal.hecker.HeckerSandboxRulesTest

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/faceisland/FaceIslandState.java tests/FaceIslandStateTest.java
java -cp "$suite_test_output" com.android.systemui.hecker.FaceIslandStateTest

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/qs/HeckerQsTileOrder.java tests/HeckerQsTileOrderTest.java
java -cp "$suite_test_output" com.android.systemui.hecker.HeckerQsTileOrderTest

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/themes/ThemeIconNames.java tests/ThemeIconNamesTest.java
java -cp "$suite_test_output" com.android.systemui.hecker.ThemeIconNamesTest

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/gaming/GameTelemetry.java tests/GameTelemetryTest.java
java -cp "$suite_test_output" com.android.server.hecker.GameTelemetryTest

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/themes/RetiredThemeSelection.java tests/RetiredThemeSelectionTest.java
java -cp "$suite_test_output" RetiredThemeSelectionTest
