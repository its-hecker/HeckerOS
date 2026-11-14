# SPDX-License-Identifier: Apache-2.0
"""Guard per-app Hecker test selection and remove the old global toggle."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SUITE = ROOT / "app/src/org/hecker/suite"
KEY = "hecker_signature_allowed_packages"


class SignatureSelectionContracts(unittest.TestCase):
    def test_per_app_entry_is_registered_without_global_toggle(self):
        manifest = (ROOT / "app/AndroidManifest.xml").read_text()
        activity = (SUITE / "CaptureSettingsActivity.java").read_text()
        picker = (SUITE / "SignatureTestAppsActivity.java").read_text()
        self.assertIn('android:name=".SignatureTestAppsActivity"', manifest)
        self.assertIn("new Intent(this, SignatureTestAppsActivity.class)", activity)
        self.assertNotIn("hecker_package_signature_test_mode", activity + picker)
        self.assertIn(KEY, picker)
        self.assertIn("Settings.Global.putString", picker)
        self.assertIn("Settings.Global.getString", picker)
        self.assertIn("readBack.optBoolean(packageName, false)", picker)
        self.assertIn("new JSONObject(value)", picker)

    def test_only_user_installed_apps_are_listed(self):
        source = (SUITE / "SignatureTestAppsActivity.java").read_text()
        for contract in (
                "FLAG_SYSTEM", "FLAG_UPDATED_SYSTEM_APP",
                "getPackagesForUid(app.uid)", "shared.length == 1",
                "app.uid < 10000", "packageName, true",
                "config.remove(packageName)", "AlertDialog.Builder(this)",
                "getInstalledApplications(0)", "search",
                "app.loadIcon(pm)", "new Switch(this)"):
            self.assertIn(contract, source)

    def test_english_and_chinese_labels_are_consistent(self):
        defaults = {item.get("name"): item.text for item in
                    ET.parse(ROOT / "app/res/values/suite_polish.xml").getroot()}
        translated = {item.get("name"): item.text for item in
                      ET.parse(ROOT / "app/res/values-zh-rCN/suite_polish.xml").getroot()}
        for key in defaults:
            self.assertIn(key, translated)
        self.assertIn("%1$s", defaults["capture_signature_warning"])
        self.assertIn("%1$s", translated["capture_signature_warning"])

    def test_framework_when_checked_out_uses_same_allowlist(self):
        # On ServerHive, source checkout gives us both repositories.
        base = ROOT.parents[3] / "frameworks/base/services/core/java/com/android/server/pm"
        policy = base / "HeckerSignatureTestPolicy.java"
        if not policy.exists():
            self.skipTest("Android framework checkout not available in standalone Suite CI")
        text = policy.read_text()
        self.assertIn(KEY, text)
        self.assertIn("new JSONObject(value).optBoolean(incoming.getPackageName(), false)", text)
        self.assertNotIn('"hecker_package_signature_test_mode"', text)
        self.assertIn("previousPackage.isSystem()", text)
        self.assertIn("sharedUser != null", text)
        self.assertIn("incoming.isCoreApp()", text)

    def test_own_permission_redeclaration_is_scoped_to_enabled_user_app(self):
        base = ROOT.parents[3] / "frameworks/base/services/core/java/com/android/server/pm"
        policy = base / "HeckerSignatureTestPolicy.java"
        installer = base / "InstallPackageHelper.java"
        if not (policy.exists() and installer.exists()):
            self.skipTest("Framework source checkout not available")
        policy_source = policy.read_text()
        install_source = installer.read_text()
        self.assertIn("allowOwnPermissionRedeclaration(", policy_source)
        self.assertIn("permissionOwner.equals(incoming.getPackageName())",
                      policy_source)
        self.assertIn("&& allowMismatch(previousPackage, sharedUser, null, incoming,",
                      policy_source)
        self.assertEqual(3, install_source.count(
            "HeckerSignatureTestPolicy.allowOwnPermissionRedeclaration("))
        self.assertIn("INSTALL_FAILED_DUPLICATE_PERMISSION_GROUP", install_source)
        self.assertIn("INSTALL_FAILED_DUPLICATE_PERMISSION", install_source)
        self.assertIn("INSTALL_FAILED_BAD_PERMISSION_GROUP", install_source)
        self.assertIn("signaturePermissionSharedUser", install_source)

    def test_fastbootd_uses_rebranded_hecker_logo(self):
        source = (ROOT / "tools/recovery_phase1.py").read_text()
        self.assertIn('fastbootd_logo_ = LoadBitmap("fastbootd");', source)
        self.assertIn('fastbootd_logo_ = LoadBitmap("logo_image");', source)


if __name__ == "__main__":
    unittest.main()
