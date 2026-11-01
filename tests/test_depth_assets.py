"""Verify the actual packaged interpreter, model bytes, ABI and Android 16 KB alignment."""
from pathlib import Path
import hashlib
import json
import struct
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class DepthAssetsTests(unittest.TestCase):
    def test_packaged_inference_inputs(self):
        lock = json.loads((ROOT / 'prebuilts/depth/inputs.lock.json').read_text())
        for relative, expected in lock['files'].items():
            self.assertEqual(hashlib.sha256((ROOT / relative).read_bytes()).hexdigest(), expected, relative)
        self.assertEqual((ROOT / 'app/assets/depth/subject.tflite').read_bytes()[4:8], b'TFL3')
        library = (ROOT / 'prebuilts/depth/libtensorflowlite_jni.so').read_bytes()
        self.assertEqual(library[:5], b'\x7fELF\x02')
        self.assertEqual(struct.unpack_from('<H', library, 18)[0], 183)  # arm64
        offset = struct.unpack_from('<Q', library, 32)[0]
        size, count = struct.unpack_from('<HH', library, 54)
        loads = 0
        for index in range(count):
            entry = offset + index * size
            if struct.unpack_from('<I', library, entry)[0] == 1:
                loads += 1
                self.assertGreaterEqual(struct.unpack_from('<Q', library, entry + 48)[0], 16384)
        self.assertGreater(loads, 0)
        with zipfile.ZipFile(ROOT / 'prebuilts/depth/litert.jar') as jar:
            self.assertIn('org/tensorflow/lite/Interpreter.class', jar.namelist())
        with zipfile.ZipFile(ROOT / 'prebuilts/depth/litert-api.jar') as jar:
            self.assertIn('org/tensorflow/lite/Tensor.class', jar.namelist())


if __name__ == '__main__':
    unittest.main()
