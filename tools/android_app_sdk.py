#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Versioned Android SDK bootstrap for the source-built LibrePods variant."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

# AGP 9.2.1 uses build-tools 36.0.0 with compileSdk 37.
PACKAGES=('platforms;android-37.0','build-tools;36.0.0','ndk;30.0.14904198','cmake;3.22.1')

def prepare_sdk(destination):
    sdk=Path(destination).resolve()
    manager=next(iter(sorted(sdk.glob('cmdline-tools/*/bin/sdkmanager'))),None)
    if manager is None:
        with urllib.request.urlopen('https://dl.google.com/android/repository/repository2-3.xml',timeout=60) as response:
            document=ET.fromstring(response.read(8*1024*1024))
        for node in document.iter():node.tag=node.tag.split('}')[-1]
        package=next(p for p in document.findall('remotePackage') if p.get('path')=='cmdline-tools;19.0')
        archive=next(a for a in package.findall('archives/archive') if a.findtext('host-os')=='linux').find('complete')
        filename=archive.findtext('url');checksum=archive.find('checksum');algorithm=checksum.get('type','sha1')
        if '/' in filename or not filename.endswith('.zip') or algorithm not in {'sha1','sha256'}:raise ValueError('Unexpected SDK tools metadata')
        size=int(archive.findtext('size'));sdk.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='tools-',dir=sdk) as temporary:
            root=Path(temporary);download=root/filename
            with urllib.request.urlopen('https://dl.google.com/android/repository/'+filename,timeout=90) as response,download.open('wb') as output:
                while chunk:=response.read(1024*1024):
                    output.write(chunk)
                    if output.tell()>size:raise ValueError('SDK tools exceed the published size')
            if download.stat().st_size!=size or hashlib.new(algorithm,download.read_bytes()).hexdigest()!=checksum.text.strip():raise ValueError('SDK tools checksum differs from official metadata')
            with zipfile.ZipFile(download) as zipped:
                if any(Path(i.filename).is_absolute() or '..' in Path(i.filename).parts for i in zipped.infolist()):raise ValueError('Unsafe SDK archive')
                zipped.extractall(root/'extracted')
            target=sdk/'cmdline-tools/19.0';target.parent.mkdir(parents=True,exist_ok=True)
            shutil.move(str(root/'extracted/cmdline-tools'),str(target))
            for executable in (target/'bin').iterdir():
                if executable.is_file():executable.chmod(0o755)
        manager=target/'bin/sdkmanager'
    missing=[p for p in PACKAGES if not (sdk/p.replace(';','/')/'source.properties').is_file()]
    if missing:
        print('Preparing Android SDK/NDK for LibrePods (cached after the first build)',flush=True)
        subprocess.run([str(manager),'--sdk_root='+str(sdk),'--licenses'],input='y\n'*200,text=True,stdout=subprocess.DEVNULL,check=True)
        subprocess.run([str(manager),'--sdk_root='+str(sdk),*missing],check=True)
    return sdk

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('destination',type=Path)
    print(prepare_sdk(parser.parse_args().destination))
