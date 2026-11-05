#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Type-check imported visibility APIs against the locked real Android 17 classes."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
from check_platform_features import source
from compile_sdk import fetch

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--framework-source',type=Path)
    parser.add_argument('--cache',type=Path,default=ROOT/'out/spoof-deps')
    args=parser.parse_args()
    lock=json.loads((ROOT/'integration/platform-features.json').read_text())['framework']
    framework=args.framework_source or source(lock,ROOT/'out/platform-feature-sources')
    dependencies=json.loads((ROOT/'tests/spoof-dependencies.lock.json').read_text())
    name,spec=next(iter(dependencies.items()));jar=fetch(args.cache,name,spec)
    native=(framework/'services/core/java/com/android/server/pm/AppsFilterBase.java').read_text()
    assert 'import android.util.Pair;' in native
    begin=native.index('    private static final String PACKAGE_SYSTEMUI')
    end=native.index('    /**\n     * See\n',begin)
    engine=(framework/'services/core/java/com/android/server/pm/ComputerEngine.java').read_text()
    begin_home=engine.index('    @GuardedBy("mDefaultHomeCache")')
    end_home=engine.index('    public final ComponentName getHomeActivitiesAsUser(',begin_home)
    # Use the actual imported cache/getter source and real DefaultAppProvider,
    # Computer, PackageStateInternal and SharedUserApi APIs. No policy mocks here.
    imports='''package com.android.server.pm;
import android.annotation.NonNull;import android.annotation.Nullable;import android.annotation.UserIdInt;
import android.os.Process;import android.os.UserHandle;import android.util.Pair;import android.util.SparseArray;
import com.android.internal.annotations.GuardedBy;
import com.android.server.pm.pkg.PackageStateInternal;import com.android.server.pm.pkg.SharedUserApi;
'''
    with tempfile.TemporaryDirectory(prefix='hecker-penguin-abi-') as directory:
        out=Path(directory)
        (out/'AppsFilterBase.java').write_text(imports+'abstract class AppsFilterBase {\n'+native[begin:end]+'\n}')
        (out/'ComputerEngine.java').write_text(imports+
            'abstract class ComputerEngine implements Computer {DefaultAppProvider mDefaultAppProvider;\n'+
            engine[begin_home:end_home]+'\n}')
        subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',str(jar),
            '-d',str(out/'classes'),str(out/'AppsFilterBase.java'),str(out/'ComputerEngine.java'),
            str(framework/'services/core/java/com/android/server/pm/HeckerRootVisibility.java')],check=True)
    print('Penguin visibility, real Android 17 package/UID APIs and cached default-Home getter: PASS')


if __name__=='__main__':main()
