#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile homepage adapters with the pinned native preference/helper and AndroidX APIs.

This is an adapter/resource check, not a full Settings APK or phone layout test.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile
from prepare import replace_guarded

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-cache', type=Path, required=True)
    args = parser.parse_args()
    sdk = args.sdk_cache.resolve()
    origin = json.loads((ROOT/'integration/source-revisions.json').read_text())['pixelos_inputs']['packages/apps/Settings']
    native = ROOT/'out/native-source-cache'/origin['revision']/'packages/apps/Settings'
    with tempfile.TemporaryDirectory(prefix='hecker-settings-home-') as temporary:
        work = Path(temporary)
        subprocess.run([str(sdk/'aapt2'),'compile','--dir',str(ROOT/'settings/res'),'-o',str(work/'res.zip')],check=True)
        sources = [ROOT/'settings/src/com/android/settings/hecker'/name for name in (
            'HeckerHomeDecoration.java','HeckerHomepageStyle.java','HeckerFeaturePreference.java','HeckerSettingsVoiceSearch.java')]
        # The decoration also recognizes the existing branding preference. Compile its real source.
        sources.append(ROOT/'settings/src/com/android/settings/hecker/HeckerBrandPreference.java')
        helper = native/'src/com/android/settings/widget/HomepagePreferenceLayoutHelper.java'
        text = helper.read_text()
        patches = json.loads((ROOT/'settings/patches.json').read_text())
        for pair in patches['packages/apps/Settings/src/com/android/settings/widget/HomepagePreferenceLayoutHelper.java']:
            text = replace_guarded(text,pair['old'],pair['new'],str(helper),False)
        generated = {
            'com/android/settings/widget/HomepagePreferenceLayoutHelper.java':text,
            'com/android/settings/widget/HomepagePreference.java':(native/'src/com/android/settings/widget/HomepagePreference.java').read_text(),
            'com/android/settings/flags/Flags.java':'package com.android.settings.flags; public class Flags { public static boolean homepageTileAlert() { return true; } }',
            'com/android/settingslib/widget/SettingsThemeHelper.java':'package com.android.settingslib.widget; public class SettingsThemeHelper {}',
            'com/android/settingslib/drawer/EntriesProvider.java':'package com.android.settingslib.drawer; public class EntriesProvider { public static final String EXTRA_ALERT_VALUE="alert"; }',
            'android/app/settings/SettingsEnums.java':'package android.app.settings; public class SettingsEnums { public static final int SETTINGS_HOMEPAGE=1502; }',
            'com/android/settings/overlay/FeatureFactory.java':'''package com.android.settings.overlay;
public class FeatureFactory { public static FeatureFactory getFeatureFactory() { return null; }
 public Search getSearchFeatureProvider() { return null; }
 public interface Search { android.content.Intent buildSearchIntent(android.content.Context context,int pageId); }
}''',
        }
        references = '\n'.join([p.read_text() for p in sources]+list(generated.values()))
        fields = {}
        for kind,name in re.findall(r'(?<!android\.)R\.(\w+)\.(\w+)',references):fields.setdefault(kind,set()).add(name)
        generated['com/android/settings/R.java'] = 'package com.android.settings; public class R {\n'+''.join(
            'public static class '+kind+' { '+''.join('public static final int '+name+'=1;' for name in sorted(names))+' }\n'
            for kind,names in fields.items())+'}\n'
        for name,content in generated.items():
            path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content);sources.append(path)
        jars=[sdk/'android-system.jar']
        for name in ('core','preference','recyclerview'):
            target=work/(name+'.jar')
            with zipfile.ZipFile(sdk/(name+'.aar')) as archive:target.write_bytes(archive.read('classes.jar'))
            jars.append(target)
        subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',os.pathsep.join(map(str,jars)),
            '-d',str(work/'classes'),*map(str,sources)],check=True)
    print('Settings homepage resources and adapters with pinned native preference/helper: PASS')

if __name__=='__main__':main()
