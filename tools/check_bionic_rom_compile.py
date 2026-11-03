#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Cross-compile prepared ROM hooks with real pinned Bionic headers and libc's stack budget."""
from pathlib import Path
import shutil
import subprocess

HEADERS = ('libc/include', 'libc/kernel', 'libc/private', 'libc/platform', 'libstdc++/include')


def bionic_headers(origin, cache, supplied=None):
    folder = (supplied or cache / 'bionic-headers' / origin['revision']).resolve()
    if supplied is None and not (folder / '.git').exists():
        folder.mkdir(parents=True, exist_ok=True)
        commands = [
            ['init', '-q'],
            ['remote', 'add', 'origin', 'https://github.com/' + origin['repository'] + '.git'],
            ['config', 'remote.origin.promisor', 'true'],
            ['config', 'remote.origin.partialclonefilter', 'blob:none'],
            ['fetch', '-q', '--depth=1', '--filter=blob:none', 'origin', origin['revision']],
            ['sparse-checkout', 'set', '--no-cone', *('/' + path + '/' for path in HEADERS)],
            ['checkout', '-q', '--detach', origin['revision']],
        ]
        for command in commands:
            subprocess.run(['git', '-C', str(folder), *command], check=True)
    revision = subprocess.check_output(['git', '-C', str(folder), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != origin['revision']:
        raise ValueError('Bionic headers are not at the locked revision: ' + str(folder))
    status = subprocess.check_output(['git', '-C', str(folder), 'status', '--porcelain',
                                      '--untracked-files=all', '--', *HEADERS], text=True)
    if status:
        raise ValueError('Preserving modified Bionic headers: ' + str(folder))
    for name in HEADERS:
        if not (folder / name).is_dir():
            raise ValueError('Missing pinned Bionic headers: ' + name)
    return folder


def android_compile_checks(rom, headers, out, clang, previous):
    executable = shutil.which(str(clang))
    if not executable:
        raise ValueError('Android compile checks need clang++; install it or pass --clang PATH')
    source = rom / 'bionic/libc/bionic'
    common = [executable, '-c', '-nostdlibinc', '-D_LIBC=1', '-DANDROID', '-DNDEBUG',
              '-D_FORTIFY_SOURCE=3', '-std=gnu++20', '-fPIC', '-fno-builtin', '-fno-exceptions',
              '-fno-rtti', '-fstack-protector-strong', '-ftrivial-auto-var-init=zero',
              '-Wall', '-Wextra', '-Werror', '-Wold-style-cast', '-Wexit-time-destructors',
              '-Wframe-larger-than=2048', '-fstack-usage',
              '-I' + str(source), '-I' + str(headers / 'libstdc++/include'),
              '-I' + str(headers / 'libc'), '-I' + str(headers / 'libc/platform'),
              '-isystem', str(headers / 'libc/include'),
              '-isystem', str(headers / 'libc/kernel/uapi'),
              '-isystem', str(headers / 'libc/kernel/android/uapi')]
    files = ('custom_rom_hide.cpp', 'fstatat_compat.cpp', 'statx_compat.cpp', 'readlinkat_compat.cpp')
    for arch, target in (('arm64', 'aarch64-linux-android10000'),
                         ('arm', 'armv7a-linux-androideabi10000')):
        target_flags = ['-target', target, '-isystem', str(headers / 'libc/kernel/uapi' / ('asm-' + arch))]
        sizes = []
        for optimization in ('-O2', '-Os'):
            for apex in (False, True):
                mode = arch + optimization + ('-apex' if apex else '-platform')
                options = [optimization, *target_flags, *(['-D__ANDROID_APEX__'] if apex else [])]
                for name in files:
                    output = out / (mode + '-' + name + '.o')
                    subprocess.run([*common, *options, str(source / name), '-o', str(output)], check=True)
                    for line in output.with_suffix('.su').read_text().splitlines():
                        fields = line.split('\t')
                        if len(fields) >= 2:
                            sizes.append(int(fields[1]))
        assert sizes and max(sizes) <= 2048, (arch, max(sizes))
        print(f'Android {arch}: four prepared libc sources, O2/Os, platform/APEX compile; '
              f'maximum static frame {max(sizes)} <= 2048 bytes PASS')
        # Negative control: this check must reject the exact published source
        # which produced the attached build error, using the same headers/flags.
        old = out / ('published-' + arch + '.cpp')
        old.write_bytes(previous)
        rejected = subprocess.run([*common, '-O2', *target_flags, str(old),
                                   '-o', str(out / ('rejected-' + arch + '.o'))],
                                  capture_output=True, text=True)
        assert rejected.returncode and '-Wframe-larger-than' in rejected.stderr, rejected.stderr
    print('The same Android frame checks reject the published oversized helper on both ABIs PASS')
