#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile the connected-headphone page and actual artwork using the locked SDK."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--sdk-cache',type=Path,required=True)
    sdk=parser.parse_args().sdk_cache.resolve()
    def run(*args):subprocess.run(list(map(str,args)),check=True)
    with tempfile.TemporaryDirectory(prefix='hecker-earbuds-') as temporary:
        out=Path(temporary)
        for directory,name in [('values','strings.xml'),('values-zh-rCN','strings-zh.xml')]:
            target=out/'res'/directory/'strings.xml';target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(ROOT/'prebuilts/librepods/hecker'/name,target)
        manifest=out/'AndroidManifest.xml'
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="me.kavishdevar.librepods"><application/></manifest>')
        run(sdk/'aapt2','compile','--dir',out/'res','-o',out/'res.zip')
        run(sdk/'aapt2','link','-I',sdk/'android.jar','--manifest',manifest,'--java',out/'java','-o',out/'res.apk',out/'res.zip')
        sources=list((ROOT/'prebuilts/librepods/hecker').glob('*.java'))+[ROOT/'framework/earbuds/HeckerEarbudsView.java']
        run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',sdk/'android-system.jar','-d',out/'classes',out/'java/me/kavishdevar/librepods/R.java',*sources)
    print('Connected-headphone UI, artwork, Bluetooth reader and translated resources compile: PASS')

if __name__=='__main__':main()
