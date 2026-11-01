"""Exercise clean, cached, offline and damaged dependency preparation."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("fetch_depth_assets", ROOT / "tools/fetch_depth_assets.py")
FETCH = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FETCH)


class DepthPreparationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "checkout"
        self.source = Path(self.temporary.name) / "downloads"
        self.source.mkdir()
        self.expected = {
            "prebuilts/depth/litert.jar": b"interpreter",
            "prebuilts/depth/litert-api.jar": b"api",
            "prebuilts/depth/libtensorflowlite_jni.so": b"arm64-native",
            "app/assets/depth/subject.tflite": b"model-TFL3",
        }
        lock = {"files": {name: hashlib.sha256(data).hexdigest()
                           for name, data in self.expected.items()}, "downloads": {}}
        archives = {
            "litert.aar": {"classes.jar": "prebuilts/depth/litert.jar",
                           "jni/arm64-v8a/libtensorflowlite_jni.so": "prebuilts/depth/libtensorflowlite_jni.so"},
            "litert-api.aar": {"classes.jar": "prebuilts/depth/litert-api.jar"},
            "subject.tflite": {"": "app/assets/depth/subject.tflite"},
        }
        for name, outputs in archives.items():
            source = self.source / name
            if name.endswith(".aar"):
                with zipfile.ZipFile(source, "w") as archive:
                    for member, output in outputs.items():
                        archive.writestr(member, self.expected[output])
            else:
                source.write_bytes(self.expected[next(iter(outputs.values()))])
            data = source.read_bytes()
            lock["downloads"][name] = {"url": "https://example.invalid/" + name,
                                       "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                                       "outputs": outputs}
        directory = self.root / "prebuilts/depth"
        directory.mkdir(parents=True)
        (directory / "inputs.lock.json").write_text(json.dumps(lock))
        self.addCleanup(patch.stopall)
        patch.object(FETCH, "ROOT", self.root).start()

    def test_clean_preparation_and_offline_reuse(self):
        FETCH.prepare(source=self.source)
        for name, expected in self.expected.items():
            self.assertEqual((self.root / name).read_bytes(), expected)
        with patch.object(FETCH.urllib.request, "urlopen", side_effect=AssertionError("network")):
            FETCH.prepare(offline=True)

    def test_missing_offline_inputs_fail_without_network(self):
        with patch.object(FETCH.urllib.request, "urlopen", side_effect=AssertionError("network")):
            with self.assertRaisesRegex(ValueError, "Missing or damaged"):
                FETCH.prepare(offline=True)

    def test_corrupt_download_is_rejected_before_install(self):
        (self.source / "litert.aar").write_bytes(b"wrong source")
        with self.assertRaisesRegex(ValueError, "differs from locked bytes"):
            FETCH.prepare(source=self.source)
        self.assertFalse((self.root / "prebuilts/depth/litert.jar").exists())

    def test_damaged_output_is_detected_then_repaired_from_cache(self):
        FETCH.prepare(source=self.source)
        target = self.root / "prebuilts/depth/litert.jar"
        target.write_bytes(b"damaged")
        with self.assertRaisesRegex(ValueError, "Missing or damaged"):
            FETCH.prepare(offline=True)
        FETCH.prepare(source=self.source / "unavailable")
        self.assertEqual(target.read_bytes(), self.expected["prebuilts/depth/litert.jar"])

    def test_symlink_output_is_rejected(self):
        target = self.root / "prebuilts/depth/litert.jar"
        target.symlink_to(self.source / "subject.tflite")
        with self.assertRaisesRegex(ValueError, "symlinked"):
            FETCH.prepare(source=self.source)

    def test_oversized_network_response_is_rejected(self):
        with patch.object(FETCH.urllib.request, "urlopen", return_value=io.BytesIO(b"x" * 100000)):
            with self.assertRaisesRegex(ValueError, "exceeds locked size"):
                FETCH.prepare()


if __name__ == "__main__":
    unittest.main()
