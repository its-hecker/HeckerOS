# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from check_build_revision import check, head, CORE

class BuildRevisionTests(unittest.TestCase):
    def test_matching_source_and_rejecting_stale_or_edited_suite(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);manifest=root/'manifest'
            def repo(path):
                path.mkdir(parents=True)
                subprocess.run(['git','init','-q',str(path)],check=True)
                (path/'source.txt').write_text('baseline')
                subprocess.run(['git','-C',str(path),'add','.'],check=True)
                subprocess.run(['git','-C',str(path),'-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','baseline'],check=True)
            repo(manifest)
            xml=ET.Element('manifest')
            for name in sorted(CORE):
                repo(root/name)
                ET.SubElement(xml,'project',path=name,revision=head(root/name))
            ET.ElementTree(xml).write(manifest/'floral.xml')
            check(root,manifest)
            suite=root/'vendor/hecker/floral/suite'
            (suite/'source.txt').write_text('edited')
            with self.assertRaisesRegex(ValueError,'local source changes'):
                check(root,manifest)
            subprocess.run(['git','-C',str(suite),'add','.'],check=True)
            subprocess.run(['git','-C',str(suite),'-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','changed'],check=True)
            with self.assertRaisesRegex(ValueError,'Revision mismatch'):
                check(root,manifest)

    def test_stamp_at_android_17_install_path_rejects_stale_output(self):
        from unittest.mock import patch
        from check_built_output import check_build_revision as check_output
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);prop=root/'system_ext/etc/build.prop'
            prop.parent.mkdir(parents=True)
            prop.write_text('ro.heckeros.suite_revision='+'1'*40+'\nro.heckeros.manifest_revision='+'1'*40+'\n')
            with patch('check_build_revision.head',return_value='1'*40):
                check_output(root,root)
                prop.write_text('ro.heckeros.suite_revision='+'0'*40+'\n')
                with self.assertRaisesRegex(ValueError,'stale or missing'):
                    check_output(root,root)
