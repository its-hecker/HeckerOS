#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile the retained Google weather client and Hecker adapter; not a full launcher build."""
import argparse
import json
import os
from pathlib import Path
import tempfile
from compile_sdk import fetch, run

ROOT = Path(__file__).resolve().parents[1]
COMPILER = {'kotlin-compiler-embeddable.jar', 'kotlin-stdlib.jar', 'kotlin-script-runtime.jar',
            'kotlin-reflect.jar', 'kotlin-daemon-embeddable.jar', 'trove4j.jar',
            'kotlinx-coroutines-core-jvm.jar', 'annotations.jar'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-cache', type=Path, required=True)
    parser.add_argument('--cache', type=Path, default=ROOT/'generated/launcher-check')
    args = parser.parse_args()
    cache, sdk = args.cache.resolve(), args.sdk_cache.resolve()
    lock = json.loads((ROOT/'tests/compose-inputs.lock.json').read_text())
    jars = [fetch(cache, name, lock[name]) for name in sorted(COMPILER)]
    with tempfile.TemporaryDirectory(prefix='hecker-launcher-check-') as temporary:
        out = Path(temporary)
        classes = out/'classes'
        classes.mkdir()
        run('java', '-cp', os.pathsep.join(map(str, jars)),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17',
            '-no-stdlib', '-no-reflect', '-classpath',
            os.pathsep.join(map(str, [sdk/'android-system.jar', cache/'kotlin-stdlib.jar'])),
            '-d', classes, *sorted((ROOT/'launcher/compat/googleweather/src').rglob('*.kt')))
        run('java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp',
            os.pathsep.join(map(str, [sdk/'android-system.jar', cache/'kotlin-stdlib.jar', classes])),
            '-d', classes, ROOT/'launcher/src/HeckerWeather.java')
        run(sdk/'aapt2', 'compile', '--dir', ROOT/'launcher/res', '-o', out/'launcher-res.zip')
    print('Google Smartspace client Kotlin, Hecker weather Java and launcher artwork compilation: PASS')


if __name__ == '__main__': main()
