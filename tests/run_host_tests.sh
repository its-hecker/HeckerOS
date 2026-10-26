#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -eo pipefail
suite_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$suite_root"
python3 tools/check_source.py
python3 -m unittest discover -s tests -p 'test_*.py'
python3 tools/check_netlab_native.py
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
    app/src/org/hecker/suite/GamingWindowBounds.java tests/GamingWindowBoundsTest.java
java -cp "$suite_test_output" org.hecker.suite.GamingWindowBoundsTest
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

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/launcher/HeckerHomeDoubleTap.java tests/InteractionPolicyTest.java
java -cp "$suite_test_output" InteractionPolicyTest

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    aosp-launcher/src/com/android/launcher3/hecker/HeckerHomeSwipe.java tests/HeckerHomeSwipeTest.java
java -cp "$suite_test_output" HeckerHomeSwipeTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    aosp-launcher/src/com/android/launcher3/hecker/StudioPolicy.java tests/StudioPolicyTest.java
java -cp "$suite_test_output" StudioPolicyTest

java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    netlab/common/org/hecker/netlab/*.java tests/NetLabPolicyTest.java
java -cp "$suite_test_output" NetLabPolicyTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    netlab/common/org/hecker/netlab/NetLabIdentity.java tests/NetLabIdentityTest.java
java -cp "$suite_test_output" NetLabIdentityTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    netlab/common/org/hecker/netlab/NetLabVendors.java netlab/common/org/hecker/netlab/NetLabIdentity.java \
    netlab/common/org/hecker/netlab/NetLabGroupPolicy.java tests/NetLabVendorsTest.java tests/NetLabGroupPolicyTest.java
java -cp "$suite_test_output" NetLabVendorsTest "$suite_root/app/assets/netlab/ieee-vendors.tsv.gz"
java -cp "$suite_test_output" NetLabGroupPolicyTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    netlab/common/org/hecker/netlab/NetLabSliderPolicy.java netlab/common/org/hecker/netlab/NetLabTrafficEvidence.java tests/NetLabSliderPolicyTest.java
java -cp "$suite_test_output" NetLabSliderPolicyTest
python3 tools/update_netlab_vendors.py --check
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    netlab/common/org/hecker/netlab/NetLabLanProtocol.java tests/NetLabLanProtocolTest.java
java -cp "$suite_test_output" NetLabLanProtocolTest
java -m jdk.compiler/com.sun.tools.javac.Main -d "$suite_test_output" \
    framework/ambient/AmbientChargingPolicy.java framework/ambient/AmbientPulsePolicy.java tests/AmbientChargingPolicyTest.java
java -cp "$suite_test_output" AmbientChargingPolicyTest
