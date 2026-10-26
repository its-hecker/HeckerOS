#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile/test the real L2 core and helper protocol without opening a raw socket."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile

def check():
    root = Path(__file__).resolve().parents[1]
    compiler = os.environ.get('CXX', 'c++')
    if not shutil.which(compiler):
        raise RuntimeError('A C++17 compiler is required for NetLab native checks')
    with tempfile.TemporaryDirectory(prefix='hecker-netlab-') as directory:
        out = Path(directory)
        module = re.search(r'(?ms)^cc_binary\s*\{\s*name:\s*"hecker-netlab-lan",(.*?)^\}',
            (root / 'Android.bp').read_text())
        if module is None:
            raise RuntimeError('Cannot find the NetLab native Soong module')
        configured = re.search(r'\bcppflags:\s*(\[[^\]]*\])', module.group(1))
        cppflags = json.loads(configured.group(1)) if configured else []
        # Android defaults to disabled C++ exceptions/RTTI. Exercise the module's
        # actual override instead of silently relying on the host compiler defaults.
        flags = [compiler, '-std=c++17', '-fno-exceptions', '-fno-rtti',
            '-Wall', '-Wextra', '-Werror', '-O1', '-g', '-I', str(root / 'netlab/native'), *cppflags]
        # glibc casts NLMSG_OK's length to unsigned, while Bionic's kernel UAPI
        # macro compares it directly. Reproduce the latter on either host libc.
        netlink = out / 'bionic-netlink.h'
        netlink.write_text('''#include <linux/netlink.h>
#undef NLMSG_OK
#define NLMSG_OK(nlh,len) ((len) >= (int) sizeof(struct nlmsghdr) && (nlh)->nlmsg_len >= sizeof(struct nlmsghdr) && (nlh)->nlmsg_len <= (len))
''')
        helper_flags = flags + ['-include', str(netlink)]
        core = str(root / 'netlab/native/lan_core.cpp')
        guard = str(root / 'netlab/native/forwarding_guard.cpp')
        binary = out / 'core-test'
        subprocess.run(flags + ['-fsanitize=address,undefined', '-fno-omit-frame-pointer', core,
            str(root / 'tests/NetLabLanCoreTest.cpp'), '-o', str(binary)], check=True)
        environment = os.environ.copy()
        # Work Mode does not expose /proc/<pid>/task to LeakSanitizer. Retain
        # address/undefined-behavior instrumentation; omit only the exit leak walk.
        environment['ASAN_OPTIONS'] = environment.get('ASAN_OPTIONS', '') + ':detect_leaks=0'
        environment['UBSAN_OPTIONS'] = 'halt_on_error=1:print_stacktrace=1'
        subprocess.run([str(binary)], check=True, env=environment)
        helper = out / 'hecker-netlab-lan'
        subprocess.run(helper_flags + [core, guard, str(root / 'netlab/native/lan_main.cpp'), '-o', str(helper)], check=True)
        guard_test = out / 'forwarding-guard-test'
        subprocess.run(flags + [guard, str(root / 'tests/NetLabForwardingGuardTest.cpp'), '-o', str(guard_test)], check=True)
        subprocess.run([str(guard_test)], check=True, timeout=30)
        route_test = out / 'route-parser-test'
        wrappers = ['socket', 'sendto', 'recv', 'poll', '__recv_chk', '__poll_chk',
            'close', 'clock_gettime']
        subprocess.run(helper_flags + ['-fsanitize=address,undefined', '-fno-omit-frame-pointer',
            core, guard, str(root / 'tests/NetLabRouteParserTest.cpp'),
            *['-Wl,--wrap=' + name for name in wrappers], '-o', str(route_test)], check=True)
        subprocess.run([str(route_test)], check=True, env=environment, timeout=10)
        # Every input below exits before interface/raw-socket access, even as UID 0.
        cases = [(b'INFO\n', b'READY 1\n'), (b'not_a_command\n', b'ERROR invalid_command\n'),
            (b'SCAN eth0 192.168.10.2 24 192.168.10.1\n', b'ERROR invalid_link\n'),
            (b'SCAN wlan0 8.8.8.2 24 8.8.8.1\n', b'ERROR invalid_link\n'),
            (b'SCAN wlan0 192.168.10.2 0 192.168.10.1\n', b'ERROR invalid_link\n'),
            (b'X' * 1025 + b'\n', b'ERROR invalid_command\n'), (b'', b'ERROR controller_lost\n')]
        for data, expected in cases:
            result = subprocess.run([str(helper)], input=data, capture_output=True, timeout=3)
            assert result.stdout == (expected if os.geteuid() == 0 else b'ERROR root_required\n'), result.stdout
        # Exercise the real entry-point rejection without requiring CAP_SETUID
        # from the host. Only geteuid() is substituted in this test executable.
        shim = out / 'uid.cpp'
        shim.write_text('#include <unistd.h>\nextern "C" uid_t __wrap_geteuid() { return 65534; }\n')
        unprivileged = out / 'unprivileged-helper'
        subprocess.run(helper_flags + [core, guard, str(root / 'netlab/native/lan_main.cpp'), str(shim),
            '-Wl,--wrap=geteuid', '-o', str(unprivileged)], check=True)
        result = subprocess.run([str(unprivileged)], input=b'INFO\n', capture_output=True, timeout=3)
        assert result.stdout == b'ERROR root_required\n', result.stdout
        print('NetLab real helper build, bounded stdin preflight and non-root rejection: PASS (no LAN packets sent)')

if __name__ == '__main__':
    check()
