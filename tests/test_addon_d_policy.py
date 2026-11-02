# SPDX-License-Identifier: Apache-2.0
"""Execute the actual product Make filter and installed-image guard."""
import ast
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class AddonDPolicyTests(unittest.TestCase):
    def test_make_filters_destinations_and_preserves_bundled_packages(self):
        with tempfile.TemporaryDirectory() as folder:
            makefile = Path(folder) / 'Makefile'
            makefile.write_text('''TARGET_COPY_OUT_SYSTEM := system
TARGET_COPY_OUT_SYSTEM_EXT := system_ext
TARGET_COPY_OUT_PRODUCT := product
TARGET_COPY_OUT_VENDOR := vendor
PRODUCT_COPY_FILES := vendor/a.sh:system/addon.d/50-lineage.sh vendor/b.sh:product/addon.d/test.sh vendor/c:system_ext/addon.d vendor/d.sh:vendor/addon.d/sub/path:root vendor/addon.d/a:system/etc/retained.xml vendor/safe:system/addon.dummy
PRODUCT_PACKAGES := Velvet PixelLauncher BasicCallRecording backuptool 50-lineage.sh addon.d HeckerSuite
include ''' + str(ROOT / 'rom/no_addon_d.mk') + '''
$(info copies=$(strip $(PRODUCT_COPY_FILES)))
$(info packages=$(strip $(PRODUCT_PACKAGES)))
all: ; @:
''')
            result = subprocess.check_output(['make', '-s', '-f', str(makefile)], text=True)
            self.assertIn('copies=vendor/addon.d/a:system/etc/retained.xml vendor/safe:system/addon.dummy', result)
            self.assertIn('packages=Velvet PixelLauncher BasicCallRecording backuptool HeckerSuite', result)
            self.assertNotIn('50-lineage.sh', result)

    def test_output_guard_rejects_files_directories_and_broken_symlinks(self):
        tree = ast.parse((ROOT / 'integration/tools/check_built_output.py').read_text())
        function = next(node for node in tree.body if isinstance(node, ast.FunctionDef)
                        and node.name == 'check_no_addon_d')
        namespace = {}
        exec(compile(ast.Module(body=[function], type_ignores=[]), '<installed-output-guard>', 'exec'), namespace)
        check = namespace['check_no_addon_d']
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            check(output)
            for name in ('system/addon.d', 'system/system/addon.d', 'system_ext/addon.d',
                         'product/addon.d', 'vendor/addon.d'):
                path = output / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.mkdir()
                with self.assertRaisesRegex(ValueError, 'addon.d'):
                    check(output)
                path.rmdir()
            path = output / 'system/addon.d'
            path.symlink_to(output / 'missing')
            with self.assertRaisesRegex(ValueError, 'addon.d'):
                check(output)


if __name__ == '__main__':
    unittest.main()
