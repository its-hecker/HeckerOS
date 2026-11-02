# SPDX-License-Identifier: Apache-2.0
"""Execute the prepared legacy QS callbacks and transition with narrow native facades."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from check_launcher import COMPILER
from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]


def check_qs_panel_bridge(folder, cache):
    native = folder / 'frameworks/base/packages/SystemUI/src/com/android/systemui/qs/composefragment'
    source = (native / 'QSFragmentCompose.kt').read_text()
    methods = '\n'.join(re.search(r'(?ms)^    override fun ' + name + r'\(.*?^    }', source).group(0)
                        for name in ['setQsVisible', 'setQsExpansion'])
    files = {
        'NativePanel.kt': '''package probe
import com.android.systemui.hecker.HeckerQsPanelState
interface Panel {
 fun setQsVisible(qsVisible:Boolean)
 fun setQsExpansion(qsExpansionFraction:Float,panelExpansionFraction:Float,headerTranslation:Float,squishinessFraction:Float)
}
class Container { var qsVisible=false }
class Model {
 var isQsVisible=false;var panelExpansionFraction=0f;var squishinessFraction=0f;var proposedTranslation=0f;var qs=0f
 fun setQsExpansionValue(value:Float){qs=value}
}
class NativePanel:Panel {
 val containerView:Container?=Container();val viewModel=Model()
''' + methods + '\n}',
        'PanelState.kt': '''package com.android.systemui.hecker
object HeckerQsPanelState {
 var shown=false;var fraction=0f
 fun setVisible(value:Boolean){shown=value}
 fun setExpansion(value:Float){fraction=value}
 fun alpha()=MediaCardGeometry.shadeAlpha(true,shown,fraction,false,false,false,0f)
}''',
        'Scene.kt': '''package com.android.compose.animation.scene
open class ContentKey
interface ElementMatcher { fun matches(key:ElementKey,content:ContentKey):Boolean }
class ElementKey(val debugName:String):ElementMatcher {
 override fun matches(key:ElementKey,content:ContentKey)=this===key
}
class TransitionBuilder {
 data class Fade(val key:Any,val start:Float,val end:Float)
 val fades=mutableListOf<Fade>();val anchors=mutableListOf<Any>();val shared=mutableListOf<Any>()
 var start=0f;var end=1f
 fun fractionRange(start:Float=0f,end:Float=1f,block:TransitionBuilder.()->Unit){
  val a=this.start;val b=this.end;this.start=start;this.end=end;block();this.start=a;this.end=b
 }
 fun fade(key:Any){fades.add(Fade(key,start,end))}
 fun anchoredTranslate(key:Any,anchor:Any){anchors.add(key)}
 fun sharedElement(key:Any,enabled:Boolean){if(enabled)shared.add(key)}
}''',
        'Elements.kt': '''package com.android.systemui.qs.shared.ui
object QuickSettings { object Elements {
 val QuickSettingsContent=Any();val FooterActions=Any();val GridAnchor=Any();val TileElementMatcher=Any()
} }''',
        'SceneKeys.kt': '''package com.android.systemui.qs.composefragment
object SceneKeys { val QqsTileElementMatcher=Any() }''',
        'Header.kt': '''package com.android.systemui.shade.ui.composable
object ShadeHeader { object Elements {
 val Clock=Any();val CollapsedContentStart=Any();val CollapsedContentEnd=Any()
} }''',
        'Transition.kt': (native / 'ui/FromQuickQuickSettingsToQuickSettings.kt').read_text(),
        'Main.kt': '''import probe.NativePanel
import com.android.systemui.hecker.HeckerQsPanelState
import com.android.systemui.hecker.HeckerQsScene
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.composefragment.ui.quickQuickSettingsToQuickSettings
fun main(){
 val panel=NativePanel();panel.setQsVisible(true)
 for(step in 0..100){
  panel.setQsExpansion(step/100f,1f,3f,.5f)
  check(HeckerQsPanelState.alpha()==1f)
  check(panel.viewModel.qs==step/100f && panel.viewModel.panelExpansionFraction==1f)
  check(panel.viewModel.squishinessFraction==.5f && panel.viewModel.proposedTranslation==3f)
 }
 for(step in 100 downTo 0){
  panel.setQsExpansion(1f,step/100f,0f,1f)
  check((HeckerQsPanelState.alpha()>0)==(step>0))
 }
 panel.setQsExpansion(1f,1f,0f,1f);panel.setQsVisible(false)
 check(HeckerQsPanelState.alpha()==0f && panel.containerView?.qsVisible==false)
 panel.setQsVisible(true);panel.setQsExpansion(0f,0f,0f,1f);check(HeckerQsPanelState.alpha()==0f)
 val transition=TransitionBuilder();transition.quickQuickSettingsToQuickSettings()
 check(transition.fades.none{it.key===Elements.QuickSettingsContent || it.key===HeckerQsScene.Media})
 check(transition.anchors.none{it===Elements.QuickSettingsContent})
 check(HeckerQsScene.Media in transition.shared)
 check(transition.fades.any{it.key===HeckerQsScene.Controls && it.start==.12f && it.end==.70f})
 check(transition.fades.any{it.key===HeckerQsScene.ExpandedHeader})
 for(key in listOf(com.android.systemui.shade.ui.composable.ShadeHeader.Elements.Clock,
   com.android.systemui.shade.ui.composable.ShadeHeader.Elements.CollapsedContentStart,
   com.android.systemui.shade.ui.composable.ShadeHeader.Elements.CollapsedContentEnd))
  check(transition.fades.any{it.key===key && it.end==.12f})
 println("Prepared legacy QS: immediate player, quick close/reopen, real panel callbacks and isolated shared-media transition PASS")
}''',
    }
    lock = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    jars = [fetch(cache, name, lock[name]) for name in sorted(COMPILER)]
    with tempfile.TemporaryDirectory(prefix='hecker-qs-bridge-') as temporary:
        out = Path(temporary)
        for name, text in files.items():
            (out / name).write_text(text)
        classes = out / 'classes'
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(classes),
                        str(ROOT / 'framework/qs/MediaCardGeometry.java')], check=True)
        classpath = os.pathsep.join([str(classes), str(cache / 'kotlin-stdlib.jar')])
        subprocess.run(['java', '-cp', os.pathsep.join(map(str, jars)),
                        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17',
                        '-no-stdlib', '-no-reflect', '-classpath', classpath, '-d', str(classes),
                        *map(str, out.glob('*.kt')), str(ROOT / 'framework/qs/HeckerQsScene.kt')], check=True)
        subprocess.run(['java', '-cp', classpath, 'MainKt'], check=True)
