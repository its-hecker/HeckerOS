#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile the patched, immutable Android 17 mobile icon with real Compose APIs."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

from compile_sdk import fetch
from prepare import replace_guarded

ROOT = Path(__file__).resolve().parents[1]
TARGET = 'frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/pipeline/mobile/ui/compose/MobileIcon.kt'

# Only unchanged SystemUI model/icon dependencies are facades. The complete native
# mobile layout, modifiers, measurement API and drawing functions are compiled.
STUBS = {
    'SignalIconModel.kt': '''package com.android.systemui.statusbar.pipeline.mobile.domain.model
import com.android.systemui.common.shared.model.IconResource
sealed interface SignalIconModel {
 sealed interface CellularTypeIconModel:SignalIconModel {
  val level:Int;val numberOfLevels:Int;val showExclamationMark:Boolean;val carrierNetworkChange:Boolean
  class Cellular(override val level:Int,override val numberOfLevels:Int,
   override val showExclamationMark:Boolean,override val carrierNetworkChange:Boolean):CellularTypeIconModel
 }
 class Satellite(val icon:IconResource):SignalIconModel
}''',
    'MobileIconState.kt': '''package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel
import com.android.systemui.common.shared.model.*
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
class MobileIconState(val isVisible:Boolean,val icon:SignalIconModel,val contentDescription:Description?,
 val networkTypeIcon:IconResource?,val roaming:Boolean,val activityInVisible:Boolean,
 val activityOutVisible:Boolean,val activityContainerVisible:Boolean)
''',
    'Models.kt': '''package com.android.systemui.common.shared.model
class Description {fun loadContentDescription(context:android.content.Context):String=""}
class IconResource(val resId:Int,val contentDescription:Description?)
''',
    'Icons.kt': '''package com.android.systemui.common.ui.compose
import androidx.compose.runtime.Composable
import com.android.systemui.common.shared.model.*
@Composable fun Icon(icon:IconResource){}
@Composable fun Description.load():String=""
''',
    'ActivityIndicators.kt': '''package com.android.systemui.statusbar.pipeline.shared.ui.composable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
@Composable fun ActivityIndicators(isActivityInVisible:Boolean,isActivityOutVisible:Boolean,color:Color){}
''',
    'ThemeSignal.kt': '''package com.android.systemui.hecker
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
@Composable fun HeckerSignalIcon(level:Int,numberOfLevels:Int,color:Color,modifier:Modifier):Boolean=false
''',
    'R.kt': '''package com.android.systemui.res
object R {object drawable {const val stat_sys_roaming_updated=1};object string {const val data_connection_roaming=2}}
''',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-cache', type=Path, required=True)
    parser.add_argument('--cache', type=Path, default=ROOT / 'generated/compose-check')
    parser.add_argument('--source-cache', type=Path, default=ROOT / 'out/mobile-icon-check')
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    cache, source_cache = args.cache.resolve(), args.source_cache.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    source_cache.mkdir(parents=True, exist_ok=True)

    def locked(folder, name, item):
        if args.offline and not (folder / name).exists():
            raise SystemExit('Missing locked input: ' + name)
        return fetch(folder, name, item)

    native_lock = json.loads((ROOT / 'tests/mobile-icon-inputs.lock.json').read_text())
    original = locked(source_cache, 'MobileIcon.kt', native_lock['MobileIcon.kt']).read_text()
    pairs = json.loads((ROOT / 'framework/customization-patches.json').read_text())[TARGET]

    def apply(source, adapters, revert=False):
        for pair in reversed(adapters) if revert else adapters:
            source = replace_guarded(source, pair['old'], pair['new'], TARGET, revert)
        return source

    patched = apply(original, pairs)
    assert apply(patched, pairs) == patched
    assert apply(patched, pairs, True) == original
    previous = [pair for pair in pairs if pair['old'].startswith('    Canvas(')]
    assert previous, 'The previously published signal-theme adapter must remain supported'
    assert apply(apply(original, previous), pairs) == patched

    compose = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    for name, item in compose.items():
        archive = locked(cache, name, item)
        if name.endswith('.aar'):
            with zipfile.ZipFile(archive) as zipped:
                archive.with_suffix('.jar').write_bytes(zipped.read('classes.jar'))
    jars = sorted(cache.glob('*.jar'))
    android = args.sdk_cache.resolve() / 'android-system.jar'
    if not android.is_file():
        raise SystemExit('Missing Android system SDK jar')
    with tempfile.TemporaryDirectory(prefix='hecker-mobile-icon-') as temporary:
        output = Path(temporary)
        for name, content in {**STUBS, 'MobileIcon.kt': patched}.items():
            (output / name).write_text(content)
        subprocess.run([
            'java', '-cp', os.pathsep.join(map(str, jars)),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
            '-nowarn', '-jvm-target', '17', '-classpath', os.pathsep.join(map(str, [android, *jars])),
            '-Xplugin=' + str(cache / 'kotlin-compose-compiler-plugin-embeddable.jar'),
            '-d', str(output / 'classes'), *map(str, sorted(output.glob('*.kt'))),
        ], check=True)
    print('Pinned mobile icon: native/prepared upgrade, exact revert and Compose API compilation: PASS')
    print('Full SystemUI Soong build and device visual acceptance remain required.')


if __name__ == '__main__':
    main()
