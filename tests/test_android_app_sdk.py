#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the SDK/Gradle process boundary without downloads or an Android build."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import android_app_sdk
import build_librepods


class AndroidAppSdkTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.suite = self.root / 'suite'
        self.sdk = self.root / 'sdk cache'
        self.log = self.root / 'sdk.jsonl'
        self.old_java = self.root / 'system-jdk11'
        self.java = self.suite / 'prebuilts/jdk/jdk21/linux-x86'
        for home, version in ((self.old_java, 11), (self.java, 21)):
            self.executable(home / 'bin/java',
                            f'#!{sys.executable}\nprint(\'openjdk version "{version}.0.1"\')\n')
        self.executable(self.sdk / 'cmdline-tools/19.0/bin/sdkmanager',
                        f'#!{sys.executable}\n' + textwrap.dedent('''
            import json, os
            from pathlib import Path
            import subprocess, sys
            home = os.environ.get('JAVA_HOME', '')
            java = subprocess.run([str(Path(home) / 'bin/java'), '-version'],
                                  capture_output=True, text=True, check=True)
            if '"21.' not in java.stdout + java.stderr:
                print('SDK manager requires the selected Java 21', flush=True)
                sys.exit(1)
            args = sys.argv[1:]
            phase = 'licenses' if '--licenses' in args else 'install'
            with open(os.environ['SDK_TEST_LOG'], 'a') as stream:
                stream.write(json.dumps({'phase': phase, 'java': home,
                                         'path': os.environ['PATH']}) + '\\n')
            if os.environ.get('SDK_TEST_FAIL') == phase:
                print('SDK_TEST_DIAGNOSTIC: ' + phase + ' failed', flush=True)
                sys.exit(73)
            if phase == 'licenses':
                if not sys.stdin.read().startswith('y\\n'):
                    sys.exit('Missing license answers')
            else:
                root = Path(next(arg.split('=', 1)[1] for arg in args
                                 if arg.startswith('--sdk_root=')))
                for package in args:
                    if not package.startswith('--'):
                        target = root / package.replace(';', '/') / 'source.properties'
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_text('Pkg.Revision=1\\n')
        '''))
        self.env = dict(os.environ, JAVA_HOME=str(self.old_java),
                        SDK_TEST_LOG=str(self.log), SDK_TEST_FAIL='',
                        SDK_TEST_PYTHON=sys.executable)

    def executable(self, path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        path.chmod(0o755)

    def events(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_selected_java_reaches_sdk_and_gradle_and_cached_build_is_reused(self):
        folder = self.suite / 'prebuilts/librepods'
        (folder / 'hecker').mkdir(parents=True)
        (self.suite / 'framework/earbuds').mkdir(parents=True)
        (self.suite / 'framework/earbuds/HeckerEarbudsView.java').write_text('// fixture\n')
        (self.suite / 'tools').mkdir()
        for name in ('build_librepods.py', 'android_app_sdk.py'):
            shutil.copyfile(ROOT / 'tools' / name, self.suite / 'tools' / name)
        upstream = self.suite / 'out/librepods-upstream'
        upstream.mkdir(parents=True)
        self.executable(upstream / 'android/gradlew', textwrap.dedent('''\
            #!/usr/bin/env bash
            set -eu
            "$SDK_TEST_PYTHON" - <<'PY'
            import json, os, subprocess
            from pathlib import Path
            import zipfile
            java = subprocess.run([os.environ['JAVA_HOME'] + '/bin/java', '-version'],
                                  capture_output=True, text=True, check=True)
            assert '"21.' in java.stdout + java.stderr
            assert os.environ['ANDROID_HOME'] == os.environ['ANDROID_SDK_ROOT']
            target = Path('app/build/outputs/apk/foss/release/fixture.apk')
            target.parent.mkdir(parents=True)
            with zipfile.ZipFile(target, 'w') as apk:
                for name in ('AndroidManifest.xml', 'classes.dex',
                             'lib/arm64-v8a/libbluetooth_socket.so'):
                    apk.writestr(name, b'fixture')
            Path(os.environ['SDK_TEST_LOG'] + '.gradle').write_text(
                json.dumps({'java': os.environ['JAVA_HOME'], 'path': os.environ['PATH']}))
            PY
        '''))
        def git(*args):
            return subprocess.check_output(['git', '-C', str(upstream), *args],
                                           stderr=subprocess.STDOUT, text=True).strip()
        git('init', '-q')
        git('remote', 'add', 'origin', 'https://example.invalid/librepods')
        git('add', 'android/gradlew')
        git('-c', 'user.name=SDK Test', '-c', 'user.email=sdk-test@localhost',
            'commit', '-qm', 'Local SDK process fixture')
        (folder / 'source.json').write_text(json.dumps({
            'source_commit': git('rev-parse', 'HEAD'),
            'repository': 'https://example.invalid/librepods',
            'package': 'org.hecker.librepods',
        }))
        with mock.patch.object(build_librepods, 'ROOT', self.suite), \
             mock.patch.object(build_librepods, 'FOLDER', folder), \
             mock.patch.object(build_librepods, 'patch_source'), \
             mock.patch.dict(os.environ, self.env, clear=True):
            apk = build_librepods.build(sdk_cache=self.sdk)
            self.assertTrue(apk.is_file())
            self.assertEqual(build_librepods.build(sdk_cache=self.sdk), apk)
            self.assertEqual(os.environ['JAVA_HOME'], str(self.old_java))
        self.assertEqual([item['phase'] for item in self.events()], ['licenses', 'install'])
        for event in self.events() + [json.loads(Path(str(self.log) + '.gradle').read_text())]:
            self.assertEqual(event['java'], str(self.java))
            self.assertEqual(event['path'].split(os.pathsep)[0], str(self.java / 'bin'))
        chosen = dict(self.env, JAVA_HOME=str(self.java))
        self.assertEqual(android_app_sdk.prepare_sdk(self.sdk, env=chosen), self.sdk)
        self.assertEqual(len(self.events()), 2, 'Complete SDK cache should need no SDK manager calls')

    def test_license_failure_keeps_sdkmanager_diagnostics(self):
        self.check_failure('licenses', ['licenses'])

    def test_install_failure_keeps_sdkmanager_diagnostics(self):
        self.check_failure('install', ['licenses', 'install'])

    def check_failure(self, phase, phases):
        env = dict(self.env, JAVA_HOME=str(self.java), SDK_TEST_FAIL=phase)
        result = subprocess.run([sys.executable, str(ROOT / 'tools/android_app_sdk.py'),
                                 str(self.sdk)], env=env, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('SDK_TEST_DIAGNOSTIC: ' + phase + ' failed',
                      result.stdout + result.stderr)
        self.assertEqual([item['phase'] for item in self.events()], phases)


if __name__ == '__main__':
    unittest.main()
