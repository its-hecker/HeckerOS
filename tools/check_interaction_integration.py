# SPDX-License-Identifier: Apache-2.0
"""Exercise prepared native dispatch and Java-service registry integration.

The dispatch method and binding checker are pinned platform sources. View/popup
collaborators are fakes; this is not a full SystemUI or SELinux/Soong build.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from check_launcher import COMPILER
from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]
INPUTS = {'system/sepolicy/tools/fuzzer_bindings_check.py'}
STATUS_VIEW = ('frameworks/base/packages/SystemUI/src/com/android/systemui/'
               'statusbar/phone/PhoneStatusBarView.java')


def context_declarations(path):
    declarations = []
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        # Exercise both release-flag branches without requiring generated M4.
        if line == "')" or re.fullmatch(r'is_flag_enabled\(RELEASE_[A-Z0-9_]+,\s*`', line):
            continue
        assert re.fullmatch(r'\S+\s+u:object_r:[A-Za-z0-9_]+:s0', line), line
        declarations.append(line)
    return declarations


def check_service_bindings(folder):
    policy = folder / 'system/sepolicy'
    source = (policy / 'build/soong/service_fuzzer_bindings.go').read_text()
    body = source.split('ServiceFuzzerBindings = map[string][]string{', 1)[1].rsplit('\n\t}', 1)[0]
    bindings = {}
    for line in body.splitlines():
        if not line.strip() or line.lstrip().startswith('//'):
            continue
        match = re.fullmatch(r'\s*"([^"]+)"\s*:\s*(EXCEPTION_NO_FUZZER|\[\]string\{[^}]*\})'
                             r'\s*,\s*(?://.*)?', line)
        assert match, 'Review changed native binding syntax: ' + line
        name, value = match.groups()
        assert name not in bindings, 'Duplicate service binding: ' + name
        bindings[name] = [] if value == 'EXCEPTION_NO_FUZZER' else json.loads(
            '[' + value[len('[]string{'):-1].strip().rstrip(',') + ']')
    declarations = context_declarations(policy / 'private/service_contexts')
    system_ext = context_declarations(ROOT / 'rom/sepolicy/private/service_contexts')
    services = [line.split()[0] for line in declarations + system_ext]
    required = ('hecker_memory', 'hecker_game')
    for name in required:
        assert services.count(name) == 1, 'Missing or duplicate service context: ' + name
    game = [line for line in system_ext if line.split()[0] == 'hecker_game']
    assert len(game) == 1 and game[0].split()[1] == 'u:object_r:hecker_game_service:s0', \
        'Gaming must retain its dedicated system_ext label'
    with tempfile.TemporaryDirectory(prefix='hecker-service-bindings-') as temporary:
        registry = Path(temporary) / 'bindings.json'
        registry.write_text(json.dumps(bindings))
        contexts = Path(temporary) / 'service_contexts'
        # Exercise the three new Java services with the real checker, including
        # gaming's system_ext declaration. Other Suite services are outside this probe.
        contexts.write_text('\n'.join(declarations + game) + '\n')
        command = [sys.executable, str(policy / 'tools/fuzzer_bindings_check.py'),
                   '-s', str(contexts), '-b', str(registry)]
        subprocess.run(command, check=True)
        for name in required:
            assert bindings[name] == [], 'Missing Java service exception: ' + name
        # Verify each new registration is necessary, not just the first reported one.
        for name in required:
            registry.write_text(json.dumps({k: v for k, v in bindings.items() if k != name}))
            missing = subprocess.run(command, capture_output=True, text=True)
            assert missing.returncode != 0 and "Service '" + name + "'" in missing.stderr, name
    print('Merged gaming label is unique; native fuzzer checker accepts both Java registrations: PASS')


def check_statusbar_dispatch(folder, cache):
    source = (folder / STATUS_VIEW).read_text()
    methods = re.findall(r'(?ms)^    @Override\n    public boolean dispatchTouchEvent'
                         r'\(MotionEvent event\) \{.*?^    \}', source)
    assert methods, 'Missing native touch dispatch method'
    lock = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    jars = [fetch(cache, name, lock[name]) for name in sorted(COMPILER)]
    with tempfile.TemporaryDirectory(prefix='hecker-status-dispatch-') as temporary:
        out = Path(temporary)
        sources = {
            'MotionEvent.java': 'package android.view; public class MotionEvent {}',
            'HeckerStatusBarPopup.java': '''package com.android.systemui.hecker;
public class HeckerStatusBarPopup {
 public static int calls; public static boolean consume;
 public static boolean onTouch(Object view, android.view.MotionEvent event) {calls++; return consume;}
}''',
            'PhoneStatusBarView.java': '''package com.android.systemui.statusbar.phone;
import android.view.MotionEvent;
import java.util.function.BooleanSupplier;
public class PhoneStatusBarView extends TouchParent {
 public BooleanSupplier mIsStatusBarInteractiveSupplier;
''' + '\n'.join(methods) + '''
}
class TouchParent {
 public int nativeCalls; public boolean nativeResult;
 public boolean dispatchTouchEvent(MotionEvent event) {nativeCalls++; return nativeResult;}
}''',
            'ControllerProbe.kt': '''package com.android.systemui.statusbar.phone
import android.view.MotionEvent
fun dispatchFromController(view: PhoneStatusBarView, event: MotionEvent): Boolean =
    view.dispatchTouchEvent(event)
''',
            'DispatchProbe.java': '''package com.android.systemui.statusbar.phone;
import android.view.MotionEvent;
import com.android.systemui.hecker.HeckerStatusBarPopup;
public class DispatchProbe {
 static void check(boolean value) {if(!value) throw new AssertionError();}
 static boolean dispatch(PhoneStatusBarView view) {
  return ControllerProbeKt.dispatchFromController(view,new MotionEvent());
 }
 public static void main(String[] args) {
  PhoneStatusBarView view=new PhoneStatusBarView();
  view.mIsStatusBarInteractiveSupplier=()->false;
  check(dispatch(view) && HeckerStatusBarPopup.calls==0 && view.nativeCalls==0);
  view.mIsStatusBarInteractiveSupplier=null;
  check(!dispatch(view) && HeckerStatusBarPopup.calls==0 && view.nativeCalls==1);
  view.mIsStatusBarInteractiveSupplier=()->true;
  HeckerStatusBarPopup.consume=true;
  check(!dispatch(view) && HeckerStatusBarPopup.calls==0 && view.nativeCalls==2);
  HeckerStatusBarPopup.consume=false; view.nativeResult=true;
  check(dispatch(view) && HeckerStatusBarPopup.calls==0 && view.nativeCalls==3);
  view.nativeResult=false;
  check(!dispatch(view) && HeckerStatusBarPopup.calls==0 && view.nativeCalls==4);
 }
}''',
        }
        for name, content in sources.items():
            (out / name).write_text(content)
        javac = ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17']
        classes = out / 'classes'
        subprocess.run(javac + ['-d', str(classes)] + [str(out / name) for name in (
            'MotionEvent.java', 'HeckerStatusBarPopup.java', 'PhoneStatusBarView.java')], check=True)
        assert len(methods) == 1, 'Duplicate native dispatch methods'
        classpath = os.pathsep.join([str(classes), str(cache / 'kotlin-stdlib.jar')])
        subprocess.run(['java', '-cp', os.pathsep.join(map(str, jars)),
                        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17',
                        '-no-stdlib', '-no-reflect', '-classpath', classpath,
                        '-d', str(classes), str(out / 'ControllerProbe.kt')], check=True)
        subprocess.run(javac + ['-cp', classpath, '-d', str(classes),
                               str(out / 'DispatchProbe.java')], check=True)
        subprocess.run(['java', '-cp', classpath,
                        'com.android.systemui.statusbar.phone.DispatchProbe'], check=True)
    print('Native status-bar dispatch and Kotlin caller compile; interactivity and native touch dispatch without popup interception: PASS')
