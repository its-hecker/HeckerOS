# SPDX-License-Identifier: Apache-2.0
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('migration', ROOT/'tools/migrate_platform.py')
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)


class FakeGitHub:
    def __init__(self, missing=None): self.missing=missing
    def head(self, repo, branch): return None if repo==self.missing else 'b'*40
    def contains(self, repo, ancestor, head): return ancestor=='a'*40


class PlatformMigrationTests(unittest.TestCase):
    def entries(self):
        return [dict(path='build/make',name='PixelOS-AOSP/android_build',
                     destination='its-hecker/heckeros_build',destination_branch='heckeros-17',
                     source_revision='a'*40)]

    def test_missing_repository_blocks_activation_before_file_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            marker=Path(temp)/'default.xml';marker.write_text('untouched')
            with self.assertRaisesRegex(ValueError,'Missing owned branch'):
                migration.verified_revisions(self.entries(),FakeGitHub('its-hecker/heckeros_build'),{})
            self.assertEqual('untouched',marker.read_text())

    def test_wrong_history_is_rejected(self):
        entries=self.entries();entries[0]['destination_revision']='c'*40
        with self.assertRaisesRegex(ValueError,'reviewed revision'):
            migration.verified_revisions(entries,FakeGitHub(),{})

    def test_rewrite_retains_linkfiles_and_pins_reviewed_content(self):
        original='''<manifest><!-- keep attribution --><project name="PixelOS-AOSP/android_build" path="build/make" remote="github"><linkfile src="core" dest="build/core" /></project></manifest>'''
        entries=self.entries()
        revisions=migration.verified_revisions(entries,FakeGitHub(),{})
        result=migration.rewrite_snippet(original,entries,revisions)
        project=ET.fromstring(result).find('project')
        self.assertEqual('its-hecker/heckeros_build',project.get('name'))
        self.assertEqual('heckeros',project.get('remote'))
        self.assertEqual('a'*40,project.get('revision'))
        self.assertEqual('build/core',project.find('linkfile').get('dest'))
        self.assertIn('keep attribution',result)
        self.assertEqual(result,migration.rewrite_snippet(result,entries,revisions))

    def test_all_platform_entries_have_unique_owned_destinations(self):
        entries=migration.inventory(ROOT/'migration/platform-projects.json')
        self.assertEqual(92,len(entries))
        self.assertEqual(92,len({x['destination'] for x in entries}))
        self.assertEqual(7,sum(x['remote']=='gitlab' for x in entries))


if __name__=='__main__': unittest.main()
