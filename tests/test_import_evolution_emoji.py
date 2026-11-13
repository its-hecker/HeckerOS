# SPDX-License-Identifier: Apache-2.0
"""Offline tests for the opt-in importer's file and integrity checks."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest import mock

MODULE = Path(__file__).resolve().parents[1] / "tools/import_evolution_emoji.py"
spec = importlib.util.spec_from_file_location("import_evolution_emoji", MODULE)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class ImportEvolutionEmojiTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.data = b"example font fixture"
        self.sha = hashlib.sha1(b"blob 20\0" + self.data).hexdigest()
        self.expected = (20, self.sha)

    def test_pinned_identity(self):
        file = self.root / "fixture.ttf"
        file.write_bytes(self.data)
        self.assertTrue(mod.verify(file, self.expected))
        self.assertFalse(mod.verify(file, (21, self.sha)))

    def test_fetch_verified_and_reuse(self):
        class FakeResponse:
            def __init__(self, payload): self.payload = payload
            def __enter__(self): return self
            def __exit__(self, *args): return None
            def read(self, n):
                data, self.payload = self.payload[:n], self.payload[n:]
                return data
        font = self.root / "fixture.ttf"
        with mock.patch.object(mod, "urlopen", return_value=FakeResponse(self.data)) as fetch:
            mod.fetch_asset("https://example.invalid/test", font, self.expected)
            mod.fetch_asset("https://example.invalid/test", font, self.expected)
            self.assertEqual(fetch.call_count, 1)
        self.assertEqual(font.read_bytes(), self.data)

    def test_corrupt_download_never_installed(self):
        font = self.root / "fixture.ttf"
        class Corrupt:
            def __enter__(self): return self
            def __exit__(self, *args): return None
            def read(self, n): return b"bad" if not hasattr(self, "done") else b""
        with mock.patch.object(mod, "urlopen") as downloader:
            bad = Corrupt()
            downloader.return_value = bad
            original = bad.read
            def once(n):
                if hasattr(bad, "done"): return b""
                bad.done = True
                return original(n)
            bad.read = once
            with self.assertRaises(ValueError):
                mod.fetch_asset("https://example.invalid/test", font, self.expected)
        self.assertFalse(font.exists())


if __name__ == "__main__":
    unittest.main()
