#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Validate the real native adapters against immutable Android 17 inputs and boundary fakes."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from compile_sdk import fetch
from native_rom_compat import native_rom_compat_plan
from prepare import replace_guarded

ROOT = Path(__file__).resolve().parents[1]


def write(changes):
    for path, content in changes.items():
        if content is None:
            path.unlink()
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content if isinstance(content, bytes) else content.encode())


def native_checks(rom, out):
    source = rom / 'bionic/libc/bionic'
    tests = ROOT / 'native-compat/tests'
    preamble = tests / 'preamble.h'
    include = out / 'include/private'
    include.mkdir(parents=True)
    (include / 'bionic_fdtrack.h').write_text('#define FDTRACK_CREATE(v) (v)\n#define FDTRACK_CREATE_NAME(n,v) (v)\n')
    (include / 'bionic_fortify.h').write_text('extern "C" void __fortify_fatal(const char*);\n')
    compiler = ['c++', '-std=c++20', '-Wall', '-Wextra', '-Werror', '-Wno-nonnull',
                '-Wno-nonnull-compare', '-include', str(preamble), '-I' + str(out / 'include'), '-I' + str(source)]
    helper = out / 'policy.o'
    # Match libc's frame budget and optimized compilation. The boundary harness
    # itself may use larger test buffers; enforce this on the production helper.
    libc_flags = ['-O2', '-fno-builtin', '-fstack-protector-strong',
                  '-ftrivial-auto-var-init=zero', '-Wframe-larger-than=2048']
    allocator = tests / 'allocator.cpp'
    subprocess.run([*compiler, *libc_flags, '-include', str(tests / 'allocation.h'),
                    '-Dgetuid=probe_uid', '-Dsyscall=probe_syscall', '-c',
                    str(source / 'custom_rom_hide.cpp'), '-o', str(helper)], check=True)
    wrappers = ['open.cpp', 'faccessat.cpp', 'fstatat_compat.cpp', 'readlinkat_compat.cpp', 'statx_compat.cpp']
    names = ['open', 'openat', 'creat', 'faccessat', 'fstatat', 'statx', 'readlinkat']
    sample = out / 'sample-maps'
    sample.write_text('1000-2000 r-xp /system/lib64/libc.so\n2000-3000 r-xp /system/lineage/custom.so\n'
                      '3000-4000 r-xp /data/app/com.heckerboard/lib/example.so\n')
    vfs = out / 'vfs-test'
    subprocess.run([*compiler, *['-D' + name + '=probe_' + name for name in names],
                    '-D__open_2=probe_open_2', '-D__openat_2=probe_openat_2',
                    *[str(source / name) for name in wrappers], str(tests / 'vfs.cpp'), str(allocator), str(helper),
                    '-o', str(vfs)], check=True)
    subprocess.run([str(vfs), str(sample)], check=True)
    properties = (source / 'system_property_api.cpp').read_text()
    a = properties.index('__BIONIC_WEAK_FOR_NATIVE_BRIDGE\nconst prop_info* __system_property_find(')
    b = properties.index('__BIONIC_WEAK_FOR_NATIVE_BRIDGE\nint __system_property_update(', a)
    c = properties.index('struct RomForeach {')
    d = properties.index('\n__BIONIC_WEAK_FOR_NATIVE_BRIDGE', properties.index('const prop_info* __system_property_find_nth(', c))
    # The final nth function is the last function in this block; include its complete body.
    block = properties[c:d] if d > c else properties[c:]
    prop_test = out / 'properties.cpp'
    prop_test.write_text((tests / 'properties.cpp').read_text().replace('// PREPARED_PROPERTY_API',
                        properties[a:b] + '\n' + block))
    binary = out / 'properties-test'
    subprocess.run([*compiler, str(prop_test), str(allocator), str(helper), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
    policy = out / 'services.cpp'
    policy.write_text('''#include <cassert>
#include "HeckerRomServiceVisibility.h"
int main(){
 for(auto name:{"lineagehardware","vendor.lineage.foo.IService/default","profile"}) {
  for(auto uid:{10001u,110001u}) {
   assert(android::hecker::hideRomService(uid,"u:r:untrusted_app:s0:c1",name));
   assert(android::hecker::hideRomService(uid,"u:r:untrusted_app_32:s0:c1",name));
   assert(!android::hecker::hideRomService(uid,"u:r:platform_app:s0",name));
  }
  for(auto uid:{0u,1000u,2000u,1001000u}) assert(!android::hecker::hideRomService(uid,"u:r:untrusted_app:s0",name));
 }
 for(auto name:{"activity","package","hecker_game","sensorservice","media.camera"})
  assert(!android::hecker::hideRomService(10001,"u:r:untrusted_app:s0",name));
 assert(!android::hecker::hideRomService(10001,"u:r:fake_untrusted_app:s0","profile"));
}
''')
    binary = out / 'service-policy-test'
    subprocess.run(['c++', '-std=c++20', '-Wall', '-Wextra', '-Werror',
                    '-I' + str(rom / 'frameworks/native/cmds/servicemanager'), str(policy), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
    print('Lineage service visibility: app/secondary-user SIDs, system callers and unrelated services PASS')
    generator = rom / 'bionic/libc/tools/gensyscalls.py'
    for arch in ('arm', 'arm64', 'x86', 'x86_64', 'riscv64'):
        assembly = subprocess.check_output(['python3', str(generator), arch,
                         str(rom / 'bionic/libc/SYSCALLS.TXT')], text=True)
        for name in ('__fstatat64', '__readlinkat', '__statx'):
            assert 'ENTRY(' + name + ')' in assembly, (arch, name)
        for name in ('fstatat', 'fstatat64', 'readlinkat', 'statx'):
            assert 'ENTRY(' + name + ')' not in assembly, (arch, name)
    print('Bionic syscall generator: private stubs and public wrapper ownership across all five ABIs PASS')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=ROOT / 'out/rom-compat-sources')
    parser.add_argument('--source-root', type=Path)
    parser.add_argument('--android-compile', action='store_true',
                        help='Also compile ARM/ARM64 platform and APEX variants with real Bionic headers')
    parser.add_argument('--bionic-source', type=Path, help='Reuse an unmodified pinned Bionic checkout')
    parser.add_argument('--clang', default='clang++', help='Clang executable for --android-compile')
    args = parser.parse_args()
    lock = json.loads((ROOT / 'native-compat/source-lock.json').read_text())
    inputs = []
    previous = lock['regression_inputs']['published_custom_rom_hide']
    old_source = fetch(args.cache / previous['revision'], previous['path'], {
        'url': 'https://raw.githubusercontent.com/' + previous['repository'] + '/'
               + previous['revision'] + '/' + previous['path'], 'sha256': previous['sha256']}).read_bytes()
    assert hashlib.sha1(b'blob ' + str(len(old_source)).encode() + b'\0' + old_source).hexdigest() == previous['blob_sha']
    def read(project, origin, name, digest):
        relative = project + '/' + name
        if args.source_root:
            content = (args.source_root / relative).read_bytes()
        else:
            item = {'url': 'https://raw.githubusercontent.com/' + origin['repository'] + '/'
                    + origin['revision'] + '/' + name, 'sha256': digest}
            content = fetch(args.cache / origin['revision'], name, item).read_bytes()
        if hashlib.sha256(content).hexdigest() != digest:
            raise ValueError('Native source input differs: ' + relative)
        return relative, content
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        jobs = [pool.submit(read, project, origin, name, digest)
                for project, origin in lock['projects'].items() for name, digest in origin['files'].items()]
        inputs = [job.result() for job in jobs]
    with tempfile.TemporaryDirectory(prefix='hecker-rom-compat-') as folder:
        out = Path(folder)
        rom = out / 'rom'
        for relative, content in inputs:
            path = rom / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        before = {str(path.relative_to(rom)): path.read_bytes() for path in rom.rglob('*') if path.is_file()}
        write(native_rom_compat_plan(rom, replace_guarded))
        assert native_rom_compat_plan(rom, replace_guarded) == {}
        native_checks(rom, out)
        if args.android_compile:
            from check_bionic_rom_compile import bionic_headers, android_compile_checks
            headers = bionic_headers(lock['projects']['bionic'], args.cache, args.bionic_source)
            android_compile_checks(rom, headers, out, args.clang, old_source)
        owned = rom / 'bionic/libc/bionic/custom_rom_hide.cpp'
        expected = owned.read_bytes()
        prepared = {str(path.relative_to(rom)): path.read_bytes() for path in rom.rglob('*') if path.is_file()}
        owned.write_bytes(old_source)
        upgrade = native_rom_compat_plan(rom, replace_guarded)
        assert upgrade == {owned: expected}, 'Upgrade must replace exactly the reviewed published copy'
        write(upgrade)
        assert native_rom_compat_plan(rom, replace_guarded) == {}
        assert prepared == {str(path.relative_to(rom)): path.read_bytes() for path in rom.rglob('*') if path.is_file()}
        edited = expected + b'\n// local edit to retain\n'
        owned.write_bytes(edited)
        try:
            native_rom_compat_plan(rom, replace_guarded)
        except ValueError as rejected:
            assert 'unfamiliar native ROM adapter' in str(rejected)
        else:
            raise AssertionError('An unfamiliar local adapter must not be overwritten')
        assert owned.read_bytes() == edited
        owned.write_bytes(expected)
        write(native_rom_compat_plan(rom, replace_guarded, revert=True))
        after = {str(path.relative_to(rom)): path.read_bytes() for path in rom.rglob('*') if path.is_file()}
        assert before == after, 'Native revert must restore immutable inputs exactly'
    print('Native ROM adapters: fresh apply, published-copy upgrade, unfamiliar-edit refusal, repeat and exact revert PASS')
    print('Full Android/APEX/SELinux build and device acceptance remain pending.')


if __name__ == '__main__':
    main()
