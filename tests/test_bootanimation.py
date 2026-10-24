# SPDX-License-Identifier: Apache-2.0
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("bootanimation", ROOT / "tools/build_bootanimation.py")
bootanimation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootanimation)


class BootAnimationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.folder = self.root / "bootanimation"
        (self.folder / "source").mkdir(parents=True)
        root_patch = patch.object(bootanimation, "ROOT", self.root)
        root_patch.start()
        self.addCleanup(root_patch.stop)
        self.target = self.folder / "bootanimation.zip"

    def stage(self, compression=zipfile.ZIP_STORED, frame_size=(2, 2)):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w", compression=compression) as archive:
            archive.writestr("desc.txt", "2 2 30\nc 1 0 part0\np 0 0 part1\n")
            png = io.BytesIO()
            Image.new("RGB", frame_size).save(png, format="PNG")
            for part in ("part0", "part1"):
                archive.writestr(part + "/00000.png", png.getvalue())
        payload = stream.getvalue()
        chunks = []
        for n, content in enumerate((payload[:100], payload[100:])):
            name = f"source/terminal-boot.zip.{n:03d}"
            (self.folder / name).write_bytes(content)
            chunks.append({"file": name, "size": len(content),
                           "sha256": hashlib.sha256(content).hexdigest()})
        metadata = {"size": len(payload), "sha256": hashlib.sha256(payload).hexdigest(),
                    "resolution": [2, 2], "fps": 30, "intro_frames": 1, "loop_frames": 1,
                    "parts": [{"type": "c", "repeat": 1, "pause": 0, "path": "part0", "frames": 1},
                              {"type": "p", "repeat": 0, "pause": 0, "path": "part1", "frames": 1}],
                    "chunks": chunks}
        (self.folder / "payload.json").write_text(json.dumps(metadata))
        return payload

    def test_reconstructs_the_exact_zip_and_leaves_valid_cached_output_untouched(self):
        payload = self.stage()
        bootanimation.assemble(self.target)
        self.assertEqual(self.target.read_bytes(), payload)
        bootanimation.check(self.target)
        stamp = self.target.stat().st_mtime_ns
        bootanimation.assemble(self.target)
        self.assertEqual(self.target.stat().st_mtime_ns, stamp)

    def test_damaged_source_never_replaces_an_existing_output(self):
        self.stage()
        self.target.write_bytes(b"previous output")
        (self.folder / "source/terminal-boot.zip.001").write_bytes(b"corrupt")
        with self.assertRaisesRegex(ValueError, "source chunk differs"):
            bootanimation.assemble(self.target)
        self.assertEqual(self.target.read_bytes(), b"previous output")

    def test_checksum_alone_cannot_accept_compression_or_wrong_dimensions(self):
        for compression, size, error in ((zipfile.ZIP_DEFLATED, (2, 2), "STORE compression"),
                                          (zipfile.ZIP_STORED, (3, 2), "Invalid boot animation frame")):
            with self.subTest(compression=compression, size=size):
                self.stage(compression, size)
                bootanimation.assemble(self.target)
                with self.assertRaisesRegex(ValueError, error):
                    bootanimation.check(self.target)


if __name__ == "__main__":
    unittest.main()
