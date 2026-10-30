# SPDX-License-Identifier: Apache-2.0
"""Execute the prepared native blur method with narrow platform scalar facades."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
from check_launcher import COMPILER
from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]


def check_shade_blur(folder, cache):
    source = (folder / 'frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/NotificationShadeDepthController.kt').read_text()
    method = source.split('    private fun computeBlurAndZoomOut()', 1)[1].split('    private fun blurRadiusToZoomOut', 1)[0]
    method = '    fun computeBlurAndZoomOut()' + method
    # Keep the production method, including all native suppression gates.
    fixture = '''import kotlin.math.max
object MathUtils { fun constrain(v:Float,a:Float,b:Float)=v.coerceIn(a,b) }
object ShadeInterpolation { fun getNotificationScrimAlpha(v:Float)=v.coerceIn(0f,1f) }
object Flags { var enabled=true; fun notificationShadeBlur()=enabled }
object Log { fun i(t:String,s:String){} }
class Animation(var radius:Float=0f,var ratio:Float=0f)
class BlurUtils {
 val minBlurRadius=0f;val maxBlurRadius=100f;var supported=true
 fun blurRadiusOfRatio(v:Float)=v*100f
 fun supportsBlursOnWindows()=supported
}
class Probe {
 val shadeAnimation=Animation();val brightnessMirrorSpring=Animation();val blurUtils=BlurUtils()
 var shadeExpansion=1f;var qsPanelExpansion=0f;var transitionToFullShadeProgress=0f
 var wakeAndUnlockBlurRadius=0f;var areBlursDisabledForAppLaunch=false;var blursDisabledForUnlock=false
 var isShadeOnDefaultDisplay=true;var scrimsVisible=false;var applyShade=true
 val INTERACTION_BLUR_FRACTION=.4f;val ANIMATION_BLUR_FRACTION=.6f;val TAG="Probe"
 fun shouldApplyShadeBlur()=applyShade
 fun blurRadiusToZoomOut(blurRadius:Float)=blurRadius/100f
''' + method + '''
}
fun main() {
 val p=Probe()
 for(animation in listOf(0f,20f,50f,100f)) {
  p.shadeAnimation.radius=animation;p.qsPanelExpansion=0f
  val quick=p.computeBlurAndZoomOut()
  for(step in 0..100) {p.qsPanelExpansion=step/100f;check(p.computeBlurAndZoomOut()==quick)}
 }
 p.brightnessMirrorSpring.ratio=1f;check(p.computeBlurAndZoomOut().first==0)
 p.brightnessMirrorSpring.ratio=0f;p.blursDisabledForUnlock=true;check(p.computeBlurAndZoomOut().first==0)
 p.blursDisabledForUnlock=false;p.areBlursDisabledForAppLaunch=true;check(p.computeBlurAndZoomOut().first==0)
 p.areBlursDisabledForAppLaunch=false;p.blurUtils.supported=false;check(p.computeBlurAndZoomOut().first==0)
 p.blurUtils.supported=true;p.scrimsVisible=true;Flags.enabled=false;check(p.computeBlurAndZoomOut().first==0)
 Flags.enabled=true;p.isShadeOnDefaultDisplay=false;check(p.computeBlurAndZoomOut().second==0f)
 p.isShadeOnDefaultDisplay=true;p.applyShade=false;p.shadeExpansion=0f;p.qsPanelExpansion=0f
 p.shadeAnimation.radius=0f;check(p.computeBlurAndZoomOut().first==0)
 println("Native QS blur: 404 expansion handoffs match; brightness mirror, unlock, app launch, blur support, scrim and display gates retained PASS")
}
'''
    lock = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    jars = [fetch(cache, name, lock[name]) for name in sorted(COMPILER)]
    with tempfile.TemporaryDirectory(prefix='hecker-shade-blur-') as directory:
        out = Path(directory)
        file = out / 'Probe.kt'
        file.write_text(fixture)
        subprocess.run(['java', '-cp', os.pathsep.join(map(str, jars)),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17',
            '-no-stdlib', '-no-reflect', '-classpath', str(cache / 'kotlin-stdlib.jar'),
            '-d', str(out / 'classes'), str(file)], check=True)
        subprocess.run(['java', '-cp', os.pathsep.join([str(out / 'classes'), str(cache / 'kotlin-stdlib.jar')]), 'ProbeKt'], check=True)
