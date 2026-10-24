#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile and exercise the prepared PIN guards and real bouncer input lifecycle.

Sources come from check_native_ports' immutable framework fixture. The platform
credential service and settings storage are fakes; no device or real PIN is used.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]
SYSTEMUI = 'frameworks/base/packages/SystemUI/src/com/android/systemui'
PIN_INPUTS = {
    SYSTEMUI + '/bouncer/ui/viewmodel/' + name
    for name in ('PinBouncerViewModel.kt', 'AuthMethodBouncerViewModel.kt', 'PinInputViewModel.kt')
}


def between(source, start, end):
    begin = source.index(start)
    return source[begin:source.index(end, begin)]


def check_pin_bouncer(folder, cache):
    source = folder / SYSTEMUI
    repository = (source / 'authentication/data/repository/AuthenticationRepository.kt').read_text()
    interactor = (source / 'authentication/domain/interactor/AuthenticationInteractor.kt').read_text()
    pin = (source / 'bouncer/ui/viewmodel/PinBouncerViewModel.kt').read_text()
    inputs = {
        'SettingsGate.kt': between(repository, '    override val isAutoConfirmFeatureEnabled:',
                                  '\n    override val authenticationMethod:'),
        'AutoConfirm.kt': between(interactor, '    val isAutoConfirmEnabled:',
                                 '    /** The length of the hinted PIN'),
        'AuthenticationGuards.kt': between(interactor, '    private suspend fun shouldSkipAuthenticationAttempt(',
                                          '    /**\n     * @return Whether the current user'),
        'PinClick.kt': between(pin, '    fun onPinButtonClicked(input: Int)',
                              '    /** Notifies that the user clicked the backspace button.'),
        'ConfirmClick.kt': between(pin, '    fun onAuthenticateButtonClicked()',
                                  '    fun onDisableEsimButtonClicked()'),
        'ConfirmAppearance.kt': between(pin, '                interactor.isAutoConfirmEnabled\n',
                                       '\n            }\n            launch {'),
    }
    dependencies = ('kotlin-compiler-embeddable', 'kotlin-stdlib', 'kotlin-script-runtime',
                    'kotlin-reflect', 'trove4j', 'kotlinx-coroutines-core-jvm', 'annotations')
    lock = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    jars = [fetch(cache, name + '.jar', lock[name + '.jar']) for name in dependencies]
    classpath = os.pathsep.join(map(str, jars))
    with tempfile.TemporaryDirectory(prefix='hecker-pin-check-') as temporary:
        out = Path(temporary)
        for template in (ROOT / 'tests/pin-bouncer').glob('*.kt'):
            content = template.read_text()
            for name, native in inputs.items():
                content = content.replace('/* NATIVE:' + name + ' */', native)
            (out / template.name).write_text(content)
        for name in ('AuthMethodBouncerViewModel.kt', 'PinInputViewModel.kt'):
            shutil.copyfile(source / 'bouncer/ui/viewmodel' / name, out / name)
        subprocess.run(['java', '-cp', classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                        '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', classpath,
                        '-d', str(out / 'classes'), *map(str, sorted(out.glob('*.kt')))], check=True)
        subprocess.run(['java', '-cp', str(out / 'classes') + os.pathsep + classpath,
                        'com.android.systemui.bouncer.ui.viewmodel.PinUnlockTestKt'], check=True)
    print('Prepared PIN guards, confirm-button flow and actual bouncer input lifecycle: PASS')
