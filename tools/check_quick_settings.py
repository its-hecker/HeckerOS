#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile QS Kotlin/Java and resources, using locked Compose/SDK dependencies.

Only the small SystemUI/hidden-API declarations are facades, reviewed against
frameworks/base@7367a79dcb02c6388ab80b1cdf9ed7d223f8fcea. They are never installed.
This catches adapter/API/Compose errors; a full Soong build is still required.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]
JAVA = {
    # SystemUIDialog and ComposeInitializer signatures reviewed at framework pin
    # 140b06a482a6ecbbdbe33190a149228c50220fb1; never packaged in the ROM.
    'com/android/systemui/statusbar/phone/SystemUIDialog.java': '''package com.android.systemui.statusbar.phone;
public class SystemUIDialog extends android.app.AlertDialog {
 public static final int DIALOG_WINDOW_TYPE = 2017;
 public SystemUIDialog(android.content.Context context) { super(context); }
}''',
    'com/android/systemui/hecker/HeckerSystemUiControl.java': '''package com.android.systemui.hecker;
public final class HeckerSystemUiControl { public static void start(android.content.Context c) {} public static void boost(android.content.Context c) {} }''',

    'android/util/PathParser.java': '''package android.util;
public class PathParser {
 public static android.graphics.Path createPathFromPathData(String data) { return null; }
}''',
    'android/content/res/ThemeEngine.java': '''package android.content.res;
public final class ThemeEngine {
 public interface ThemeChangeListener { void onThemeChanged(String category); }
 public static ThemeEngine getInstance(android.content.Context context) { return null; }
 public void addThemeChangeListener(ThemeChangeListener listener) {}
 public String getEnabledPackage(String category) { return null; }
}''',
    'android/content/ContentResolver.java': '''package android.content;
public abstract class ContentResolver {
 public final java.io.InputStream openInputStream(android.net.Uri uri) throws java.io.FileNotFoundException { return null; }
 public final void registerContentObserver(android.net.Uri uri, boolean descendants, android.database.ContentObserver o, int user) {}
 public final void unregisterContentObserver(android.database.ContentObserver o) {}
}''',
    'android/provider/Settings.java': '''package android.provider;
public final class Settings {
 public static final String ACTION_CAST_SETTINGS="", ACTION_ZEN_MODE_SETTINGS="", ACTION_BLUETOOTH_SETTINGS="", ACTION_DISPLAY_SETTINGS="";
 public static final class Secure {
  public static android.net.Uri getUriFor(String name) { return null; }
  public static int getIntForUser(android.content.ContentResolver r,String n,int d,int u) { return d; }
  public static String getStringForUser(android.content.ContentResolver r,String n,int u) { return null; }
  public static boolean putIntForUser(android.content.ContentResolver r,String n,int v,int u) { return true; }
  public static boolean putStringForUser(android.content.ContentResolver r,String n,String v,int u) { return true; }
 }
 public static final class System {
  public static android.net.Uri getUriFor(String name) { return null; }
  public static int getIntForUser(android.content.ContentResolver r,String n,int d,int u) { return d; }
  public static boolean putIntForUser(android.content.ContentResolver r,String n,int v,int u) { return true; }
 }
}''',
    'android/os/UserHandle.java': '''package android.os;
public final class UserHandle implements Parcelable {
 public static final int USER_ALL=-1, USER_CURRENT=-2;
 public static final UserHandle ALL=null, SYSTEM=null;
 public static UserHandle of(int user) { return null; }
 public static boolean isApp(int uid) { return false; }
 public int describeContents() { return 0; }
 public void writeToParcel(Parcel out,int flags) {}
}''',
    'android/app/UiModeManager.java': '''package android.app;
public class UiModeManager { public boolean setNightModeActivated(boolean active) { return true; } }
''',
    'android/media/session/MediaSessionManager.java': '''package android.media.session;
public class MediaSessionManager {
 public interface OnActiveSessionsChangedListener { void onActiveSessionsChanged(java.util.List<MediaController> sessions); }
 public void addOnActiveSessionsChangedListener(android.content.ComponentName component,android.os.UserHandle user,
  java.util.concurrent.Executor executor,OnActiveSessionsChangedListener listener) {}
 public void removeOnActiveSessionsChangedListener(OnActiveSessionsChangedListener listener) {}
 public void addOnActiveSessionsChangedListener(OnActiveSessionsChangedListener listener, android.content.ComponentName component,android.os.Handler handler) {}
 public java.util.List<MediaController> getActiveSessions(android.content.ComponentName component) { return null; }
 public java.util.List<MediaController> getActiveSessionsForUser(android.content.ComponentName component, android.os.UserHandle user) { return null; }
}''',
    'com/android/systemui/plugins/statusbar/StatusBarStateController.java': '''package com.android.systemui.plugins.statusbar;
public interface StatusBarStateController {
 boolean isDozing(); int getState(); void addCallback(StateListener listener); void removeCallback(StateListener listener);
 interface StateListener { default void onDozingChanged(boolean dozing) {} default void onStateChanged(int state) {} }
}''',
    'com/android/systemui/plugins/ActivityStarter.java': '''package com.android.systemui.plugins;
public interface ActivityStarter { void postStartActivityDismissingKeyguard(android.content.Intent intent,int delay); }
''',
    'javax/inject/Inject.java': '''package javax.inject;
@java.lang.annotation.Target({java.lang.annotation.ElementType.CONSTRUCTOR}) public @interface Inject {}
''',
    'com/android/systemui/dagger/SysUISingleton.java': '''package com.android.systemui.dagger;
@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE}) public @interface SysUISingleton {}
''',
    'com/android/systemui/dagger/qualifiers/Application.java': '''package com.android.systemui.dagger.qualifiers;
@java.lang.annotation.Target({java.lang.annotation.ElementType.PARAMETER}) public @interface Application {}
''',
}
KOTLIN = {
    'Controls.kt': '''package com.android.systemui.controls.dagger
class ControlsComponent {
 fun getControlsUiController(): java.util.Optional<com.android.systemui.controls.ui.ControlsUiController> = java.util.Optional.empty()
}
''',
    'ControlsUi.kt': '''package com.android.systemui.controls.ui
interface ControlsUiController {
 fun resolveActivity(): Class<*>
 companion object { const val EXTRA_ANIMATE = "extra_animate" }
}
''',
    'BatteryModels.kt': '''package com.android.systemui.statusbar.pipeline.battery.shared.ui
import androidx.compose.ui.graphics.Color
sealed interface BatteryGlyph { data object Defend: BatteryGlyph; data object Question: BatteryGlyph; data object Plus: BatteryGlyph }
class BatteryColors(val fill: Color)
''',
    'BatteryViewModel.kt': '''package com.android.systemui.statusbar.pipeline.battery.ui.viewmodel
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryGlyph
class BatteryViewModel(val level: Int?,val attribution: BatteryGlyph?,val isCharging: Boolean,val glyphList: List<BatteryGlyph>)
''',
    'SceneKey.kt': '''package com.android.compose.animation.scene
class ElementKey(val debugName: String)
''',
    'CoreStartable.kt': '''package com.android.systemui
interface CoreStartable { fun start() }
''',
    'Expandable.kt': '''package com.android.systemui.animation
interface Expandable
''',
    'Tiles.kt': '''package com.android.systemui.qs.panels.ui.viewmodel
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import com.android.systemui.animation.Expandable
import kotlinx.coroutines.flow.Flow
class TileSpec(val spec: String)
class State
class TileViewModel(val spec: TileSpec, val state: Flow<State>, val currentState: State) {
 fun mainClick(expandable: Expandable?) {}
 fun settingsClick(expandable: Expandable?) {}
 fun toggleClick() {}
}
class AccessibilityUiState(val contentDescription: String,val stateDescription: String,
 val accessibilityRole: Role,val toggleableState: ToggleableState?)
class TileUiState(val label: String,val secondaryLabel: String,val visualState: Int,
 val handlesMainClick: Boolean,val handlesToggleClick: Boolean,val handlesSettingsClick: Boolean,
 val accessibilityUiState: AccessibilityUiState)
fun State.toUiState(resources: Resources): TileUiState = error("compile only")
class Icon { fun getDrawable(context: Context): Drawable = error("compile only") }
class IconProvider(val icon: Icon?)
fun State.toIconProvider(): IconProvider = error("compile only")
''',
    'TileListener.kt': '''package com.android.systemui.qs.panels.ui.compose
import androidx.compose.runtime.Composable
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
@Composable fun TileListener(tiles: List<TileViewModel>,listening: () -> Boolean) {}
''',
    'Models.kt': '''package com.android.systemui.common.shared.model
sealed interface ContentDescription {
 class Loaded(val description: String): ContentDescription
 class Resource(val res: Int): ContentDescription
}
sealed interface Icon {
 val contentDescription: ContentDescription?
 class Loaded(val drawable: android.graphics.drawable.Drawable,override val contentDescription: ContentDescription?): Icon
 class Resource(val resId: Int,override val contentDescription: ContentDescription?): Icon
}
''',
    'Footer.kt': '''package com.android.systemui.qs.footer.ui.viewmodel
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.Icon
import kotlinx.coroutines.flow.Flow
interface FooterActionsButtonViewModel { val icon: Icon; val onClick: (Expandable) -> Unit }
class FooterActionsViewModel(val settings: Flow<FooterActionsButtonViewModel?>,val power: FooterActionsButtonViewModel?)
''',
    'ExpandableCompose.kt': '''package com.android.compose.animation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.android.systemui.animation.Expandable
@Composable fun Expandable(color: Color,shape: Shape,onClick: (Expandable) -> Unit,modifier: Modifier,
 useModifierBasedImplementation: Boolean,content: @Composable () -> Unit) {}
''',
}


def scene_probe():
    """Compile the actual new native routing block, including its optional Compose lambda."""
    patches = json.loads((ROOT / 'framework/customization-patches.json').read_text())
    path = 'frameworks/base/packages/SystemUI/compose/features/src/com/android/systemui/qs/ui/composable/QuickSettingsContent.kt'
    entry = next(p for p in patches[path] if p['old'] == '    QuickSettingsPanelLayout(\n')
    body = entry['new'].rsplit('    QuickSettingsPanelLayout(', 1)[0]
    return r'''package com.android.systemui.hecker.compilecheck
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import com.android.systemui.res.R
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import kotlinx.coroutines.flow.filterNotNull
class Key { fun currentAlpha(): Float? = 1f }
object Elements { val QuickSettingsContent=Key(); val QuickSettingsTiles=Key(); val BrightnessSlider=Key() }
class TileGrid(val tileViewModels: List<TileViewModel>)
class Container(val tileGridViewModel: TileGrid,val isBrightnessSliderVisible: Boolean,val brightnessSliderViewModel: Any)
class ContainerColors(val background: Color,val mirrorBackground: Color)
fun Modifier.element(key: Key): Modifier = this
fun Modifier.sysuiResTag(tag: String): Modifier = this
fun Modifier.gesturesDisabled(): Modifier = this
fun Modifier.thenIf(condition: Boolean,other: () -> Modifier): Modifier = this
class StartScope { fun onStopOrDispose(stop: () -> Unit) {} }
@Composable fun LifecycleStartEffect(key: Any,effect: StartScope.() -> Unit) {}
@Composable fun Element(modifier: Modifier,key: Key,content: @Composable () -> Unit) { content() }
@Composable fun GridAnchor() {}
@Composable fun BrightnessSliderContainer(model: Any,containerColors: ContainerColors,modifier: Modifier) {}
@Composable fun NativeReferenceRoute(viewModel: Container,modifier: Modifier) {
''' + body + '\n}\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-cache', type=Path, required=True)
    parser.add_argument('--cache', type=Path, default=ROOT / 'generated/compose-check')
    args = parser.parse_args()
    sdk, cache = args.sdk_cache.resolve(), args.cache.resolve()
    lock = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    for name, item in lock.items():
        archive = fetch(cache, name, item)
        if name.endswith('.aar'):
            with zipfile.ZipFile(archive) as zipped:
                archive.with_suffix('.jar').write_bytes(zipped.read('classes.jar'))
    def run(*cmd): subprocess.run([str(v) for v in cmd], check=True)
    with tempfile.TemporaryDirectory(prefix='hecker-qs-check-') as temp:
        out = Path(temp)
        for folder, resource in [('values', 'strings.xml'), ('values-zh-rCN', 'strings-zh.xml')]:
            dest = out / 'res' / folder / 'hecker_strings.xml'
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / 'framework/qs' / resource, dest)
        (out / 'res/values/native.xml').write_text('<resources><item name="keyguard_carrier_text" type="id"/><string name="quick_settings_tiles_default">wifi,bt</string><dimen name="qs_horizontal_margin">16dp</dimen></resources>')
        shutil.copytree(ROOT / 'framework/qs/icons', out / 'res/drawable')
        manifest = out / 'AndroidManifest.xml'
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            'package="com.android.systemui.res"><application/></manifest>')
        run(sdk / 'aapt2', 'compile', '--dir', out / 'res', '-o', out / 'res.zip')
        run(sdk / 'aapt2', 'link', '-I', sdk / 'android.jar', '--manifest', manifest,
            '--java', out / 'java', '-o', out / 'res.apk', out / 'res.zip')
        for name, content in JAVA.items():
            target = out / 'java' / name
            target.parent.mkdir(parents=True, exist_ok=True); target.write_text(content)
        compose_initializer = '''package com.android.systemui.compose
object ComposeInitializer {
 fun onAttachedToWindow(root: android.view.View) {}
 fun onDetachedFromWindow(root: android.view.View) {}
}'''
        for name, content in {**KOTLIN, 'ReferenceRoute.kt': scene_probe(),
                              'ComposeInitializer.kt': compose_initializer}.items():
            target = out / 'kotlin' / name
            target.parent.mkdir(parents=True, exist_ok=True); target.write_text(content)
        java_sources = list((out / 'java').rglob('*.java')) + [p for p in (ROOT / 'framework/qs').glob('*.java') if p.name != 'HeckerSystemUiControl.java']
        java_sources += sorted((ROOT / 'framework/lyrics').glob('*.java'))
        java_sources += sorted((ROOT / 'framework/ambient').glob('*.java'))
        java_sources += sorted((ROOT / 'framework/lockscreen').glob('*.java'))
        java_sources += [ROOT / 'framework/themes/ThemeIconNames.java', ROOT / 'framework/applock/HeckerAppLockPrompt.java']
        run('java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', sdk / 'android-system.jar',
            '-d', out / 'classes', *java_sources)
        jars = sorted(cache.glob('*.jar'))
        classpath = os.pathsep.join(map(str, [out / 'classes', sdk / 'android-system.jar', *jars]))
        run('java', '-cp', os.pathsep.join(map(str, jars)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', classpath,
            '-Xplugin=' + str(cache / 'kotlin-compose-compiler-plugin-embeddable.jar'),
            '-d', out / 'classes-kotlin', *sorted((out / 'kotlin').glob('*.kt')),
            *sorted((ROOT / 'framework/qs').glob('*.kt')),
            *sorted((ROOT / 'framework/ambient').glob('*.kt')),
            *sorted((ROOT / 'framework/lockscreen').glob('*.kt')),
            ROOT / 'framework/motionisland/HeckerMotionMediaDialog.kt',
            ROOT / 'framework/lyrics/HeckerLockscreenLyrics.kt',
            ROOT / 'framework/themes/HeckerThemeAssets.kt',
            ROOT / 'framework/themes/HeckerConnectivityIcons.kt',
            ROOT / 'framework/themes/HeckerBattery.kt',
            ROOT / 'framework/themes/HeckerChargingAnimation.kt',
            ROOT / 'framework/themes/HeckerBatteryDrawable.java')
        run('java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp',
            os.pathsep.join([str(out / 'classes-kotlin'), classpath]),
            '-d', out / 'classes', ROOT / 'framework/themes/HeckerBatteryDrawable.java')
    print('QS Kotlin/Java, Compose and drawable/string resource compilation: PASS')
    print('Full SystemUI Soong compilation and visual/device testing remain required.')


if __name__ == '__main__': main()
