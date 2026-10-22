# SPDX-License-Identifier: Apache-2.0
import base64
import hashlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from compile_sdk import fetch


class LockedDownloadTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.cache = Path(self.temp.name)
        self.payload = b'locked dependency bytes'
        self.item = {'url': 'https://example.invalid/dependency.jar',
                     'sha256': hashlib.sha256(self.payload).hexdigest()}

    def server_error(self, code):
        return urllib.error.HTTPError(self.item['url'], code, 'server response', {}, None)

    def test_temporary_server_error_recovers_and_reuses_verified_cache(self):
        with patch('compile_sdk.urllib.request.urlopen', side_effect=[
                self.server_error(503), io.BytesIO(self.payload)]) as download, \
                patch('compile_sdk.time.sleep'):
            target = fetch(self.cache, 'dependency.jar', self.item)
            self.assertEqual(self.payload, target.read_bytes())
            self.assertEqual(target, fetch(self.cache, 'dependency.jar', self.item))
            self.assertEqual(2, download.call_count)

    def test_transport_timeout_can_recover_with_base64_source(self):
        item = {**self.item, 'base64': True}
        with patch('compile_sdk.urllib.request.urlopen', side_effect=[
                TimeoutError(), io.BytesIO(base64.b64encode(self.payload))]), \
                patch('compile_sdk.time.sleep'):
            self.assertEqual(self.payload, fetch(self.cache, 'dependency.jar', item).read_bytes())

    def test_persistent_server_error_stops_without_creating_cache(self):
        with patch('compile_sdk.urllib.request.urlopen', side_effect=self.server_error(503)) as download, \
                patch('compile_sdk.time.sleep'), self.assertRaises(urllib.error.HTTPError):
            fetch(self.cache, 'dependency.jar', self.item)
        self.assertLessEqual(download.call_count, 4)
        self.assertFalse((self.cache / 'dependency.jar').exists())

    def test_missing_file_is_not_retried(self):
        with patch('compile_sdk.urllib.request.urlopen', side_effect=self.server_error(404)) as download, \
                patch('compile_sdk.time.sleep') as sleep, self.assertRaises(urllib.error.HTTPError):
            fetch(self.cache, 'dependency.jar', self.item)
        download.assert_called_once()
        sleep.assert_not_called()

    def test_bad_checksum_is_not_retried_or_cached(self):
        with patch('compile_sdk.urllib.request.urlopen', return_value=io.BytesIO(b'changed bytes')) as download, \
                patch('compile_sdk.time.sleep') as sleep, self.assertRaises(ValueError):
            fetch(self.cache, 'dependency.jar', self.item)
        download.assert_called_once()
        sleep.assert_not_called()
        self.assertFalse((self.cache / 'dependency.jar').exists())

    def test_unrecognized_cache_is_preserved_and_refused(self):
        target = self.cache / 'dependency.jar'
        target.write_bytes(b'unrecognized cached bytes')
        with patch('compile_sdk.urllib.request.urlopen') as download, self.assertRaises(ValueError):
            fetch(self.cache, 'dependency.jar', self.item)
        download.assert_not_called()
        self.assertEqual(b'unrecognized cached bytes', target.read_bytes())
