#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Type-check headphone/sandbox/face/Motion Island Java with the locked system SDK.

The small declarations below model hidden platform APIs absent from the
system SDK. They are compile-only facades, never installed or used by Soong.
This does not compile the full SystemUI/services modules.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
from check_quick_settings import JAVA as QS_JAVA

ROOT = Path(__file__).resolve().parents[1]
# Declarations reviewed against frameworks/base@7367a79dcb02c6388ab80b1cdf9ed7d223f8fcea.
FACADES = {
    'com/android/systemui/plugins/statusbar/StatusBarStateController.java': r'''package com.android.systemui.plugins.statusbar;
public interface StatusBarStateController {
 boolean isDozing(); boolean isPulsing();
 void addCallback(StateListener listener); void removeCallback(StateListener listener);
 interface StateListener {default void onDozingChanged(boolean dozing) {} default void onPulsingChanged(boolean pulsing) {}}
}''',
    'com/android/systemui/plugins/OverlayPlugin.java': '''package com.android.systemui.plugins;
public interface OverlayPlugin {
 boolean holdStatusBarOpen();
 interface Callback { void onHoldStatusBarOpenChange(); }
}''',
    'com/android/systemui/statusbar/policy/KeyguardStateController.java': '''package com.android.systemui.statusbar.policy;
public interface KeyguardStateController {
 boolean isShowing();
 void addCallback(Callback callback);
 void removeCallback(Callback callback);
 interface Callback { default void onKeyguardShowingChanged() {} }
}''',
    'android/hardware/biometrics/BiometricSourceType.java': '''
package android.hardware.biometrics;
public enum BiometricSourceType { FINGERPRINT, FACE, IRIS }
''',
    'com/android/keyguard/KeyguardUpdateMonitor.java': '''
package com.android.keyguard;
public class KeyguardUpdateMonitor {
    public boolean isKeyguardVisible() { return false; }
    public boolean isDeviceInteractive() { return false; }
    public boolean isFaceAuthOrDetectionRunning() { return false; }
    public boolean isUnlockingWithBiometricAllowed(boolean strong) { return false; }
    public void registerCallback(KeyguardUpdateMonitorCallback callback) {}
}''',
    'com/android/keyguard/KeyguardUpdateMonitorCallback.java': '''
package com.android.keyguard;
import android.hardware.biometrics.BiometricSourceType;
public class KeyguardUpdateMonitorCallback {
    public void onKeyguardVisibilityChanged(boolean visible) {}
    public void onStartedWakingUp() {}
    public void onStartedGoingToSleep(int why) {}
    public void onUserSwitchComplete(int userId) {}
    public void onStrongAuthStateChanged(int userId) {}
    public void onBiometricRunningStateChanged(boolean running, BiometricSourceType type) {}
    public void onBiometricAuthenticated(int userId, BiometricSourceType type, boolean strong) {}
    public void onBiometricAuthFailed(BiometricSourceType type) {}
    public void onBiometricError(int code, String message, BiometricSourceType type) {}
}''',
    'com/android/systemui/statusbar/phone/SystemUIDialog.java': '''
package com.android.systemui.statusbar.phone;
public class SystemUIDialog extends android.app.AlertDialog {
    public SystemUIDialog(android.content.Context context) { super(context); }
}''',
    'android/content/ContentResolver.java': '''
package android.content;
public abstract class ContentResolver {
    public final void registerContentObserver(android.net.Uri uri, boolean descendants,
            android.database.ContentObserver observer, int userId) {}
    public final void unregisterContentObserver(android.database.ContentObserver observer) {}
}''',
    'android/os/UserHandle.java': '''
package android.os;
public final class UserHandle {
    public static final int USER_ALL = -1;
    public static UserHandle of(int userId) { return null; }
    public static boolean isApp(int uid) { return false; }
}''',
    'android/provider/Settings.java': '''
package android.provider;
public final class Settings {
    public static final String ACTION_SOUND_SETTINGS = "android.settings.SOUND_SETTINGS";
    public static final String ACTION_BLUETOOTH_SETTINGS = "android.settings.BLUETOOTH_SETTINGS";
    public static final class Secure {
        public static android.net.Uri getUriFor(String name) { return null; }
        public static int getIntForUser(android.content.ContentResolver cr, String name, int def, int userId) { return def; }
        public static String getStringForUser(android.content.ContentResolver cr, String name, int userId) { return null; }
    }
}''',
    'android/util/Slog.java': '''
package android.util;
public final class Slog {
    public static int w(String tag, String msg) { return 0; }
}''',
}
for name in ('android/os/UserHandle.java', 'android/media/session/MediaSessionManager.java'):
    FACADES[name] = QS_JAVA[name]
FACADES['com/android/systemui/hecker/HeckerMotionMediaDialog.java'] = '''
package com.android.systemui.hecker;
public final class HeckerMotionMediaDialog {
 public static android.app.Dialog show(android.content.Context context,
  android.media.session.MediaSession.Token token, Runnable dismissed) { return null; }
}'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-cache', type=Path, required=True)
    sdk = parser.parse_args().sdk_cache.resolve()
    def run(*args): subprocess.run(list(map(str, args)), check=True)
    with tempfile.TemporaryDirectory(prefix='hecker-device-features-') as temporary:
        out = Path(temporary)
        for directory, name in [('values', 'strings.xml'), ('values-zh-rCN', 'strings-zh.xml'),
                                ('drawable', 'hecker_headphones_art.xml')]:
            target = out / 'res' / directory / ('strings.xml' if directory.startswith('values') else name)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / 'framework/headphones' / name, target)
        manifest = out / 'AndroidManifest.xml'
        for directory, name in [('values', 'strings.xml'), ('values-zh-rCN', 'strings-zh.xml')]:
            shutil.copyfile(ROOT / 'framework/faceisland' / name,
                            out / 'res' / directory / 'hecker_face_island.xml')
            shutil.copyfile(ROOT / 'framework/motionisland' / name,
                            out / 'res' / directory / 'hecker_motion_island.xml')
        (out / 'res/drawable-nodpi').mkdir()
        shutil.copyfile(ROOT / 'framework/motionisland/astronaut-wave.png',
                        out / 'res/drawable-nodpi/hecker_motion_astronaut.png')
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            'package="com.android.systemui.res"><application/></manifest>')
        run(sdk / 'aapt2', 'compile', '--dir', out / 'res', '-o', out / 'res.zip')
        run(sdk / 'aapt2', 'link', '-I', sdk / 'android.jar', '--manifest', manifest,
            '--java', out / 'java', '-o', out / 'res.apk', out / 'res.zip')
        for name, content in FACADES.items():
            target = out / 'facades' / name
            target.parent.mkdir(parents=True, exist_ok=True); target.write_text(content)
        sources = sorted((ROOT / 'framework/headphones').glob('*.java'))
        sources += sorted((ROOT / 'framework/earbuds').glob('*.java'))
        sources += sorted((ROOT / 'framework/glow').glob('*.java'))
        sources += sorted((ROOT / 'framework/motionisland').glob('*.java'))
        sources += [ROOT / 'framework/qs/HeckerQsMedia.java', ROOT / 'framework/qs/MusicSessionPolicy.java']
        sources += sorted((ROOT / 'framework/sandbox').glob('*.java'))
        sources += sorted((ROOT / 'framework/faceisland').glob('*.java'))
        run('java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', sdk / 'android-system.jar',
            '-d', out / 'classes', *sorted((out / 'facades').rglob('*.java')),
            out / 'java/com/android/systemui/res/R.java', *sources)
    print('Headphone, sandbox, face and Motion Island Java with system SDK / reviewed hidden declarations: PASS')
    print('Headphone, face and Motion Island resources, including Chinese translations: PASS')
    print('Full SystemUI/services Soong compilation and device testing remain required.')


if __name__ == '__main__': main()
