# SPDX-License-Identifier: Apache-2.0
"""Regression checks for applying the weather port without losing local work."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from omnijaws_port import OMNIJAWS, omnijaws_plan
from prepare import replace_guarded


class OmniJawsPortTests(unittest.TestCase):
    def stage(self, folder):
        for relative, pairs in json.loads((ROOT / "omnijaws/patches.json").read_text()).items():
            path = folder / OMNIJAWS / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("\n// next anchor\n".join(pair["old"] for pair in pairs))

    def snapshot(self, folder):
        return {str(p.relative_to(folder)): p.read_bytes() for p in folder.rglob("*") if p.is_file()}

    def write(self, changes):
        for path, source in changes.items():
            if source is None:
                path.unlink()
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)

    def test_apply_reapply_and_exact_revert(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            self.stage(folder)
            before = self.snapshot(folder)
            self.write(omnijaws_plan(folder, replace_guarded))
            self.assertEqual({}, omnijaws_plan(folder, replace_guarded))
            self.write(omnijaws_plan(folder, replace_guarded, True))
            self.assertEqual(before, self.snapshot(folder))

    def test_changed_dependency_contract_refuses_without_writes(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            self.stage(folder)
            (folder / OMNIJAWS / "Android.bp").write_text('// maintainer replacement')
            before = self.snapshot(folder)
            with self.assertRaises(ValueError):
                omnijaws_plan(folder, replace_guarded)
            self.assertEqual(before, self.snapshot(folder))

    def test_edited_helper_is_preserved_on_apply_and_revert(self):
        for revert in (False, True):
            with self.subTest(revert=revert), tempfile.TemporaryDirectory() as temporary:
                folder = Path(temporary)
                self.stage(folder)
                self.write(omnijaws_plan(folder, replace_guarded))
                helper = folder / OMNIJAWS / "app/src/main/java/org/omnirom/omnijaws/hecker/HeckerWeatherUi.kt"
                helper.write_text('// local weather UI changes')
                before = self.snapshot(folder)
                with self.assertRaises(ValueError):
                    omnijaws_plan(folder, replace_guarded, revert)
                self.assertEqual(before, self.snapshot(folder))

    def test_standalone_suite_without_weather_is_supported(self):
        with tempfile.TemporaryDirectory() as temporary:
            self.assertEqual({}, omnijaws_plan(Path(temporary), replace_guarded))
