#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Audit NetLab's real resource, UID and privilege boundaries before a ROM build."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

A='{http://schemas.android.com/apk/res/android}'

def check(root):
    root=Path(root)
    backend=ET.parse(root/'netlab/AndroidManifest.xml').getroot()
    assert backend.get('package')=='org.hecker.netlab' and A+'sharedUserId' not in backend.attrib
    permission=backend.find('permission')
    assert permission.get(A+'name')=='org.hecker.netlab.permission.CONTROL'
    assert permission.get(A+'protectionLevel')=='signature'
    permissions={p.get(A+'name') for p in backend.findall('uses-permission')}
    assert permissions=={'android.permission.ACCESS_WIFI_STATE','android.permission.CHANGE_WIFI_STATE',
        'android.permission.ACCESS_NETWORK_STATE','android.permission.NETWORK_SETTINGS',
        'android.permission.TETHER_PRIVILEGED','android.permission.SCHEDULE_EXACT_ALARM',
        'android.permission.POST_NOTIFICATIONS','android.permission.RECEIVE_BOOT_COMPLETED'}
    app=backend.find('application')
    assert app.get(A+'allowBackup')=='false' and app.get(A+'directBootAware')=='true'
    assert app.get(A+'persistent')=='true' and app.get(A+'allowClearUserData')=='false'
    service=app.find('service')
    assert service.get(A+'name')=='.NetLabService' and service.get(A+'exported')=='true'
    assert service.get(A+'permission')==permission.get(A+'name')
    assert len(app.findall('service'))==1 and not app.findall('activity')
    assert all(r.get(A+'exported')=='false' for r in app.findall('receiver'))
    allow=ET.parse(root/'configs/privapp-permissions-hecker-netlab.xml').getroot()
    assert allow.find('privapp-permissions').get('package')=='org.hecker.netlab'
    assert {p.get('name') for p in allow.findall('.//permission')}=={
        'android.permission.NETWORK_SETTINGS','android.permission.TETHER_PRIVILEGED'}
    defaults=ET.parse(root/'configs/default-permissions-hecker-netlab.xml').getroot()
    assert defaults.tag=='exceptions' and len(defaults.findall('exception'))==1
    exception=defaults.find('exception')
    assert exception.get('package')=='org.hecker.netlab'
    assert [(p.get('name'),p.get('fixed')) for p in exception.findall('permission')]==[('android.permission.POST_NOTIFICATIONS','false')]
    ui=ET.parse(root/'app/AndroidManifest.xml').getroot()
    ui_permissions={p.get(A+'name') for p in ui.findall('uses-permission')}
    assert {'android.permission.INTERNET','android.permission.ACCESS_LOCAL_NETWORK',permission.get(A+'name')}<=ui_permissions
    assert not {'android.permission.NETWORK_SETTINGS','android.permission.TETHER_PRIVILEGED','android.permission.NETWORK_STACK'}&ui_permissions
    activity=ui.find("application/activity[@"+A+"name='.NetworkLabActivity']")
    assert activity is not None and activity.get(A+'exported')=='false'
    tile=ui.find("application/service[@"+A+"name='.NetworkLabTileService']")
    assert tile is not None and tile.get(A+'permission')=='android.permission.BIND_QUICK_SETTINGS_TILE'
    for folder,filename in [('app','netlab.xml'),('netlab','strings.xml')]:
        keys=[]
        for qualifier in ['values','values-zh-rCN']:
            document=ET.parse(root/folder/'res'/qualifier/filename).getroot()
            names=[n.get('name') for n in document]
            assert len(names)==len(set(names)), 'Duplicate NetLab resource'
            keys.append(set(names))
        assert keys[0]==keys[1], 'NetLab translation keys differ'
    animation=json.loads((root/'app/res/raw/suite_netlab.json').read_text())
    assert animation['assets']==[] and animation['fr']==30 and animation['op']==120
    assert animation['layers'] and animation['nm']=='Hecker netlab'
    print('NetLab independent UID, signature endpoint, minimal grants, local permission, translations and Lottie: PASS')

if __name__=='__main__':check(Path(__file__).resolve().parents[1])
