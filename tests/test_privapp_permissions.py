# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from privapp_permissions import check_privapp_permissions, check_feature_privapp_permissions


class PrivappPermissionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.platform = self.root / "platform.xml"
        # Protection levels from the pinned Android 17 framework. The app and
        # allowlist below are the actual Suite files, including its sandbox request.
        self.platform.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <permission android:name="android.permission.FORCE_STOP_PACKAGES"
                android:protectionLevel="signature|privileged" />
            <permission android:name="android.permission.CONTROL_DISPLAY_BRIGHTNESS"
                android:protectionLevel="signature" />
            <permission android:name="android.permission.QUERY_ALL_PACKAGES"
                android:protectionLevel="normal" />
            <permission android:name="android.permission.READ_WALLPAPER_INTERNAL"
                android:protectionLevel="signature|privileged" />
            <permission android:name="android.permission.TETHER_PRIVILEGED"
                android:protectionLevel="signature|privileged" />
            <permission android:name="android.permission.SCHEDULE_EXACT_ALARM"
                android:protectionLevel="signature|privileged|appop" />
        </manifest>''')
        for relative in ("app/AndroidManifest.xml", "configs/privapp-permissions-hecker-suite.xml",
                         "netlab/AndroidManifest.xml", "configs/privapp-permissions-hecker-netlab.xml",
                         "configs/privapp-permissions-hecker-launcher.xml", "Android.bp"):
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / relative).read_bytes())
        self.allowlist = self.root / "configs/privapp-permissions-hecker-suite.xml"

    def test_actual_suite_and_allowlist_cover_sandbox_permission(self):
        self.assertEqual({"android.permission.FORCE_STOP_PACKAGES"},
                         check_privapp_permissions(self.platform, self.root))

    def test_reported_bootloop_is_rejected_before_source_changes(self):
        tree = ET.parse(self.allowlist)
        block = tree.getroot().find("privapp-permissions")
        block.remove(block.find("permission[@name='android.permission.FORCE_STOP_PACKAGES']"))
        tree.write(self.allowlist)
        with self.assertRaisesRegex(ValueError, "prevent Android from booting.*FORCE_STOP_PACKAGES"):
            check_privapp_permissions(self.platform, self.root)

    def test_framework_protection_level_changes_are_checked_without_a_static_list(self):
        self.platform.write_text(self.platform.read_text().replace(
            'android:protectionLevel="normal"', 'android:protectionLevel="signature|privileged"'))
        with self.assertRaisesRegex(ValueError, "QUERY_ALL_PACKAGES"):
            check_privapp_permissions(self.platform, self.root)

    def test_explicit_denial_is_a_decision_without_granting_permission(self):
        tree = ET.parse(self.allowlist)
        node = tree.getroot().find("privapp-permissions/permission[@name='android.permission.FORCE_STOP_PACKAGES']")
        node.tag = "deny-permission"
        tree.write(self.allowlist)
        check_privapp_permissions(self.platform, self.root)

    def test_all_owned_features_cover_the_bootloop_permissions(self):
        decisions = check_feature_privapp_permissions(self.platform, self.root)
        self.assertEqual({'android.permission.READ_WALLPAPER_INTERNAL'}, decisions['com.android.launcher3'])
        self.assertEqual({'android.permission.SCHEDULE_EXACT_ALARM', 'android.permission.TETHER_PRIVILEGED'}, decisions['org.hecker.netlab'])

    def test_missing_wallpaper_allowlist_reproduces_reported_boot_failure(self):
        path = self.root / 'configs/privapp-permissions-hecker-launcher.xml'
        path.write_text('<permissions><privapp-permissions package="com.android.launcher3" /></permissions>')
        with self.assertRaisesRegex(ValueError, 'HeckerLauncher.*READ_WALLPAPER_INTERNAL'):
            check_feature_privapp_permissions(self.platform, self.root)

    def test_exact_alarm_appop_permission_still_needs_privileged_decision(self):
        path = self.root / 'configs/privapp-permissions-hecker-netlab.xml'
        tree = ET.parse(path)
        block = tree.getroot().find('privapp-permissions')
        block.remove(block.find("permission[@name='android.permission.SCHEDULE_EXACT_ALARM']"))
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'HeckerNetLabController.*SCHEDULE_EXACT_ALARM'):
            check_feature_privapp_permissions(self.platform, self.root)

    def test_decisions_on_the_wrong_partition_are_rejected(self):
        path = self.root / 'Android.bp'
        path.write_text(path.read_text().replace('system_ext_specific: true', 'system_ext_specific: false'))
        with self.assertRaisesRegex(ValueError, 'system_ext/etc/permissions'):
            check_feature_privapp_permissions(self.platform, self.root)


if __name__ == "__main__":
    unittest.main()
