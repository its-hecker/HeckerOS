#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise generated config through the matching release's real setupConfig action."""
import io
import hashlib
import json
from pathlib import Path
import socket
import subprocess
import tarfile


def check(archive, configuration, output):
    data = subprocess.check_output(['dpkg-deb', '--fsys-tarfile', str(archive)])
    with tarfile.open(fileobj=io.BytesIO(data)) as tar:
        core = tar.extractfile('./usr/share/FlClash/FlClashCore').read()
    assert hashlib.sha256(core).hexdigest() == 'ed62aa4fa9448e38dd42a523b506eba9946c36f326d3152243d3537e72285295'
    binary = output / 'FlClashCore'; binary.write_bytes(core); binary.chmod(0o755)
    home = output / 'release-home'; home.mkdir()
    (home / 'config.yaml').write_bytes(Path(configuration).read_bytes())
    with socket.socket(socket.AF_INET) as server:
        server.bind(('127.0.0.1', 0)); server.listen(1); server.settimeout(20)
        process = subprocess.Popen([str(binary), str(server.getsockname()[1])], stdin=subprocess.DEVNULL,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            connection, _ = server.accept(); connection.settimeout(20)
            with connection, connection.makefile('rwb') as stream:
                def action(method, data):
                    stream.write((json.dumps(dict(id='hecker', method=method, data=data))+'\n').encode()); stream.flush()
                    for _ in range(100):
                        response = json.loads(stream.readline(32769))
                        if response.get('id') != 'hecker' or response.get('method') != method: continue
                        assert response['code'] == 0, 'Release core rejected action '+method
                        return response['data']
                    raise AssertionError('Missing release core reply')
                assert action('initClash', json.dumps({'home-dir': str(home), 'version': 37})) is True
                assert action('setupConfig', json.dumps({'selected-map': {}, 'test-url': 'https://www.gstatic.com/generate_204'})) == ''
                traffic = json.loads(action('getTotalTraffic', True))
                assert 'up' in traffic and 'down' in traffic
        finally:
            process.terminate()
            try: process.wait(timeout=5)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
    print('Matching FlClash release: generated config, real setupConfig application and traffic reply shape PASS')
