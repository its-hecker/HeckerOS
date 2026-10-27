#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Static metadata audit of a user-supplied APK. Never installs, executes or extracts its code."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

def strings(data):
    if not data.startswith(b'dex\n') or len(data)<112:raise ValueError('Not a DEX file')
    count,offset=struct.unpack_from('<II',data,56)
    if count>1_000_000 or offset+4*count>len(data):raise ValueError('Invalid DEX strings')
    for i in range(count):
        pos=struct.unpack_from('<I',data,offset+4*i)[0]
        for _ in range(5):
            if pos>=len(data):raise ValueError('Invalid DEX offset')
            byte=data[pos];pos+=1
            if byte<128:break
        else:raise ValueError('Invalid DEX length')
        end=data.find(b'\0',pos)
        if end<0:raise ValueError('Unterminated DEX string')
        yield data[pos:end].decode('utf-8','replace')

def audit(path,aapt):
    path=Path(path)
    if path.stat().st_size>40*1024*1024:raise ValueError('APK exceeds audit limit')
    report={'input_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'execution':'none','code_redistributed':False}
    badging=subprocess.run([str(aapt),'dump','badging',str(path)],capture_output=True,text=True,check=True,timeout=30).stdout
    for key,pattern in [('package',r"package: name='([^']+)'"),('version',r"versionName='([^']+)'"),
                        ('version_code',r"versionCode='([^']+)'"),('minimum_sdk',r"(?:minSdkVersion|sdkVersion):'(\d+)'"),('target_sdk',r"targetSdkVersion:'(\d+)'")]:
        match=re.search(pattern,badging);report[key]=match.group(1) if match else None
    report['requested_permissions']=sorted(set(re.findall(r"uses-permission: name='([^']+)'",badging)))
    with zipfile.ZipFile(path) as apk:
        names=apk.namelist()
        if len(names)>4096 or sum(i.file_size for i in apk.infolist())>128*1024*1024:raise ValueError('APK expansion exceeds audit limit')
        report['native_libraries']=[{'path':n,'bytes':apk.getinfo(n).file_size} for n in names if re.fullmatch(r'lib/[^/]+/[^/]+\.so',n)]
        report['abis']=sorted({n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')})
        all_strings=set()
        for name in names:
            if re.fullmatch(r'classes\d*\.dex',name):all_strings.update(strings(apk.read(name)))
        report['feature_evidence']={
            'jail_ui':any('JNetCutBlackListFragment' in s for s in all_strings),
            'offline_schedule_ui':any('Schedule_Offline_dlg' in s for s in all_strings),
            'speed_controls':any(s=='IPCMESSAGE_ID_MAC_INT_SETSPEED' for s in all_strings),
            'netlock_command':any(s=='IPCMESSAGE_ID_INT_NETLOCK' for s in all_strings),
            'defender_command':any(s=='IPCMESSAGE_ID_INT_SETDEFENDER' for s in all_strings),
            'driver_socket_threads':any('JNetCutDriver$SocketWriterThread' in s for s in all_strings),
            'app_cloner_instrumentation':any('Lcom/applisto/appcloner/' in s for s in all_strings),
            'opaque_native_asset':'assets/natives_sec_blob.dat' in names}
    report['limits']='Metadata and symbol evidence only; no runtime behavior, security or license verdict.'
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('apk',type=Path);parser.add_argument('--aapt2',type=Path,required=True)
    args=parser.parse_args();print(json.dumps(audit(args.apk,args.aapt2),indent=2,ensure_ascii=False))
