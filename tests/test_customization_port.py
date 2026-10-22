# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import json
import sys
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from customization_port import customization_plan
from prepare import replace_guarded

class CustomizationPortTests(unittest.TestCase):
    def stage(self, folder):
        patches=json.loads((ROOT/'framework/customization-patches.json').read_text())
        base=folder/'frameworks/base/core/java/android/content/res/Resources.java'
        base.parent.mkdir(parents=True,exist_ok=True);base.write_text('// fixture')
        for relative,pairs in patches.items():
            path=folder/relative;path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text('\n// next anchor\n'.join(pair['old'] for pair in pairs))
    def write(self, changes):
        for path,source in changes.items():
            if source is None:path.unlink()
            else:
                path.parent.mkdir(parents=True,exist_ok=True)
                if isinstance(source,bytes):path.write_bytes(source)
                else:path.write_text(source)
    def snapshot(self, folder):
        return {str(p.relative_to(folder)):p.read_bytes() for p in folder.rglob('*') if p.is_file()}
    def test_apply_idempotent_and_revert_preserves_original(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder=Path(temporary);self.stage(folder);before=self.snapshot(folder)
            self.write(customization_plan(folder,replace_guarded))
            self.assertEqual({},customization_plan(folder,replace_guarded))
            self.write(customization_plan(folder,replace_guarded,True))
            self.assertEqual(before,self.snapshot(folder))
    def test_drift_refuses_before_any_writes(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder=Path(temporary);self.stage(folder)
            path=folder/'vendor/custom/build/tasks/pixelos.mk';path.write_text('// unfamiliar maintainer changes')
            before=self.snapshot(folder)
            with self.assertRaises(ValueError):customization_plan(folder,replace_guarded)
            self.assertEqual(before,self.snapshot(folder))
    def test_foreign_copy_is_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder=Path(temporary);self.stage(folder)
            copies=json.loads((ROOT/'framework/customization-copies.json').read_text())
            path=folder/next(iter(copies));path.parent.mkdir(parents=True,exist_ok=True);path.write_text('// other port')
            before=self.snapshot(folder)
            with self.assertRaises(ValueError):customization_plan(folder,replace_guarded)
            self.assertEqual(before,self.snapshot(folder))

    def test_existing_prepared_adapters_upgrade_and_revert_without_losing_edits(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder=Path(temporary);self.stage(folder)
            original=self.snapshot(folder)
            self.write(customization_plan(folder,replace_guarded))
            patches=json.loads((ROOT/'framework/customization-patches.json').read_text())
            upgraded=[]
            for relative,pairs in patches.items():
                for pair in pairs:
                    if not pair.get('previous'):continue
                    path=folder/relative
                    self.assertEqual(1,path.read_text().count(pair['new']))
                    path.write_text(path.read_text().replace(pair['new'],pair['previous'][0])
                                    +'\n// unrelated maintainer edit\n')
                    original[relative]+='\n// unrelated maintainer edit\n'.encode()
                    upgraded.append(path)
            self.assertGreaterEqual(len(upgraded),2)
            changes=customization_plan(folder,replace_guarded)
            self.assertEqual(set(upgraded),set(changes))
            self.write(changes)
            self.assertEqual({},customization_plan(folder,replace_guarded))
            self.write(customization_plan(folder,replace_guarded,True))
            self.assertEqual(original,self.snapshot(folder))

    def test_unreviewed_previous_adapter_is_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder=Path(temporary);self.stage(folder)
            self.write(customization_plan(folder,replace_guarded))
            patches=json.loads((ROOT/'framework/customization-patches.json').read_text())
            for relative,pairs in patches.items():
                candidates=[pair for pair in pairs if pair.get('previous')]
                if not candidates:continue
                pair=candidates[0];path=folder/relative
                path.write_text(path.read_text().replace(pair['new'],'// unreviewed local integration'))
                break
            before=self.snapshot(folder)
            with self.assertRaises(ValueError):customization_plan(folder,replace_guarded)
            self.assertEqual(before,self.snapshot(folder))
