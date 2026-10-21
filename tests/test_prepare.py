# SPDX-License-Identifier: Apache-2.0
import importlib.util
from pathlib import Path
import tempfile
import sys
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
spec = importlib.util.spec_from_file_location("suite_prepare", ROOT / "tools/prepare.py")
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class PreparationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.rom = Path(self.temp.name)
        base = self.rom / prepare.FRAMEWORK
        for relative, markers in prepare.CAPABILITIES.items():
            file = base / relative
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text("\n".join(markers))
        for relative, (old, _) in prepare.PATCHES.items():
            file = base / relative
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(old + "\n")
        (base / "core/res/AndroidManifest.xml").write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
''' + prepare.MANIFEST_OLD + '''
    <permission android:name="android.permission.LOCK_APPS" android:protectionLevel="internal|role|recents" />
</manifest>''')

    def contents(self):
        return {str(p.relative_to(self.rom)): p.read_bytes()
                for root in (prepare.FRAMEWORK, prepare.MOTION.parent)
                for p in (self.rom / root).rglob("*") if p.is_file()}

    def stage_motion(self):
        settings = self.rom / prepare.MOTION
        settings.mkdir(parents=True)
        (settings.parent / "motionsense.mk").write_text(prepare.MOTION_PRODUCT_OLD)
        (settings / "Android.bp.in").write_text(prepare.MOTION_RESOURCES_BP_OLD + '''
    resource_dirs: ["common-res"],
}

''' + prepare.MOTION_BP_OLD + '''
    srcs: ["src/**/*.java"],
    privileged: true,
    system_ext_specific: true,
    required: ["FloralMotionSensePrivappPermissions"],
}
''')
        (settings / "Android.bp").write_bytes((settings / "Android.bp.in").read_bytes())
        (settings / "AndroidManifest.xml").write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="org.hecker.floral.motionsense" android:sharedUserId="android.uid.system">
    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
    <application android:persistent="true">
        <activity android:name=".motionsense.MotionSenseActivity" android:exported="true">
            <intent-filter>
                <action android:name="com.android.settings.action.IA_SETTINGS" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data android:name="com.android.settings.category" android:value="com.android.settings.category.ia.system" />
            <meta-data android:name="keep.custom.metadata" android:value="keep" />
        </activity>
        <activity android:name=".motionsense.GlowActivity" android:exported="false" />
        <service android:name=".motionsense.MotionSenseTileService" android:exported="true"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE" /></intent-filter>
        </service>
    </application>
</manifest>''')
        fragment = settings / prepare.MOTION_FRAGMENT
        fragment.parent.mkdir(parents=True)
        fragment.write_text(prepare.MOTION_ABOUT_OLD)
        return settings

    def test_motion_navigation_is_reversible_and_preserves_controls_tile_and_source_manifest(self):
        settings = self.stage_motion()
        before = self.contents()
        source = (settings / "AndroidManifest.xml").read_bytes()
        prepare.apply(self.rom, "apply")
        self.assertEqual(source, (settings / "AndroidManifest.xml").read_bytes())
        manifest = ET.parse(settings / prepare.MOTION_MANIFEST).getroot()
        activity = manifest.find("application/activity")
        self.assertEqual(activity.get(prepare.ANDROID + "exported"), "true")
        self.assertEqual(activity.find("meta-data").get(prepare.ANDROID + "name"), "keep.custom.metadata")
        self.assertIsNone(activity.find("intent-filter"))
        self.assertEqual(len(manifest.findall("application/activity")), 2)
        self.assertEqual(manifest.find("application/service").get(prepare.ANDROID + "permission"),
                         "android.permission.BIND_QUICK_SETTINGS_TILE")
        self.assertEqual(manifest.get(prepare.ANDROID + "sharedUserId"), "android.uid.system")
        native = ET.parse(settings / prepare.MOTION_NATIVE_MANIFEST).getroot()
        self.assertNotIn(prepare.ANDROID + "sharedUserId", native.attrib)
        self.assertIsNone(native.find("uses-sdk"))
        self.assertEqual(native.find("application").attrib, {})
        self.assertFalse(native.findall("application/service"))
        self.assertTrue(all(node.get(prepare.ANDROID + "exported") == "false"
                            for node in native.findall("application/activity")))
        self.assertTrue(all(node.get(prepare.ANDROID + "name").startswith("org.hecker.floral.")
                            for node in native.findall("application/activity")))
        self.assertIn(prepare.MOTION_NATIVE_LIBRARY, (settings / "Android.bp").read_text())
        resources = ET.parse(settings / prepare.MOTION_RESOURCES_MANIFEST).getroot()
        self.assertEqual(resources.attrib, {"package": "org.hecker.floral.motionsense"})
        self.assertEqual(resources.find("application").attrib, {})
        self.assertFalse(list(resources.find("application")))
        self.assertEqual([node.tag for node in resources], ["application"])
        applied = self.contents()
        prepare.apply(self.rom, "verify")
        prepare.apply(self.rom, "apply")
        self.assertEqual(applied, self.contents())
        prepare.apply(self.rom, "revert")
        self.assertEqual(before, self.contents())

    def test_invalid_motion_input_refuses_before_any_framework_changes(self):
        settings = self.stage_motion()
        manifest = settings / "AndroidManifest.xml"
        manifest.write_text(manifest.read_text().replace('android:exported="true"', 'android:exported="false"', 1))
        before = self.contents()
        with self.assertRaisesRegex(ValueError, "exported settings activity"):
            prepare.apply(self.rom, "apply")
        self.assertEqual(before, self.contents())

    def test_foreign_generated_motion_manifest_is_preserved(self):
        settings = self.stage_motion()
        for name in (prepare.MOTION_MANIFEST, prepare.MOTION_NATIVE_MANIFEST,
                     prepare.MOTION_RESOURCES_MANIFEST):
            with self.subTest(manifest=name):
                generated = settings / name
                generated.parent.mkdir(parents=True, exist_ok=True)
                generated.write_text("unrelated local file")
                before = self.contents()
                with self.assertRaisesRegex(ValueError, "unfamiliar generated Motion Sense manifest"):
                    prepare.apply(self.rom, "apply")
                self.assertEqual(before, self.contents())
                generated.unlink()

    def test_upgrade_existing_motion_integration_keeps_native_and_standalone_manifests(self):
        settings = self.stage_motion()
        original = self.contents()
        prepare.apply(self.rom, "apply")
        # Reproduce the previous adapter's already-applied state in a user's checkout.
        for name in ("Android.bp.in", "Android.bp"):
            path = settings / name
            path.write_text(path.read_text().replace(prepare.MOTION_RESOURCES_BP_NEW,
                                                     prepare.MOTION_RESOURCES_BP_OLD))
        (settings / prepare.MOTION_RESOURCES_MANIFEST).unlink()
        manifests = {name: (settings / name).read_bytes()
                     for name in (prepare.MOTION_MANIFEST, prepare.MOTION_NATIVE_MANIFEST)}
        prepare.apply(self.rom, "apply")
        prepare.apply(self.rom, "verify")
        for name, content in manifests.items():
            self.assertEqual(content, (settings / name).read_bytes())
        prepare.apply(self.rom, "revert")
        self.assertEqual(original, self.contents())

    def test_foreign_resource_manifest_selection_refuses_before_writes(self):
        settings = self.stage_motion()
        blueprint = settings / "Android.bp.in"
        blueprint.write_text(blueprint.read_text().replace(prepare.MOTION_RESOURCES_BP_OLD,
            prepare.MOTION_RESOURCES_BP_OLD + '\n    manifest: "maintainer-manifest.xml",'))
        before = self.contents()
        with self.assertRaisesRegex(ValueError, "resource manifest selection"):
            prepare.apply(self.rom, "apply")
        self.assertEqual(before, self.contents())

    def test_idempotent_and_reversible_without_touching_native_guards(self):
        before = self.contents()
        prepare.apply(self.rom, "check")
        self.assertEqual(before, self.contents())
        prepare.apply(self.rom, "apply")
        applied = self.contents()
        manifest = (self.rom / prepare.FRAMEWORK / "core/res/AndroidManifest.xml").read_text()
        self.assertIn(prepare.MANIFEST_OLD + '\n    <permission android:name="android.permission.LOCK_APPS"', manifest)
        prepare.apply(self.rom, "verify")
        prepare.apply(self.rom, "apply")
        self.assertEqual(applied, self.contents())
        for relative in prepare.CAPABILITIES:
            if relative not in prepare.PATCHES:
                self.assertEqual(before[str(prepare.FRAMEWORK / relative)],
                                 applied[str(prepare.FRAMEWORK / relative)])
        prepare.apply(self.rom, "revert")
        self.assertEqual(before, self.contents())

    def test_all_inputs_validated_before_any_writes(self):
        bad = self.rom / prepare.FRAMEWORK / "services/core/java/com/android/server/pm/ComputerEngine.java"
        bad.write_text("unknown implementation")
        before = self.contents()
        with self.assertRaises(ValueError): prepare.apply(self.rom, "apply")
        self.assertEqual(before, self.contents())

    def test_refuses_broadened_native_permission(self):
        manifest = self.rom / prepare.FRAMEWORK / "core/res/AndroidManifest.xml"
        manifest.write_text(manifest.read_text().replace("internal|role|recents", "normal"))
        before = self.contents()
        with self.assertRaises(ValueError): prepare.apply(self.rom, "apply")
        self.assertEqual(before, self.contents())

    def test_preserves_unrelated_edits_on_revert(self):
        prepare.apply(self.rom, "apply")
        file = self.rom / prepare.FRAMEWORK / "services/core/java/com/android/server/pm/ComputerEngine.java"
        file.write_text(file.read_text() + "\n// unrelated maintainer change\n")
        prepare.apply(self.rom, "revert")
        self.assertTrue(file.read_text().endswith("// unrelated maintainer change\n"))

    def test_refuses_to_overwrite_foreign_helper(self):
        file = self.rom / prepare.FRAMEWORK / prepare.HELPER
        file.write_text("foreign helper")
        before = self.contents()
        with self.assertRaises(ValueError): prepare.apply(self.rom, "apply")
        self.assertEqual(before, self.contents())

    def test_verify_rejects_unapplied_source(self):
        with self.assertRaises(ValueError): prepare.apply(self.rom, "verify")


if __name__ == "__main__": unittest.main()
