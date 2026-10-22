# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from privapp_permissions import check_privapp_permissions


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
        </manifest>''')
        for relative in ("app/AndroidManifest.xml", "configs/privapp-permissions-hecker-suite.xml"):
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


if __name__ == "__main__":
    unittest.main()
