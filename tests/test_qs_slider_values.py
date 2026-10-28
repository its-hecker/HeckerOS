# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]


class QsSliderValuesTests(unittest.TestCase):
    def test_production_percentages_and_locales(self):
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',directory,
                str(ROOT/'framework/qs/HeckerSliderValues.java'),str(ROOT/'tests/HeckerSliderValuesTest.java')],check=True)
            subprocess.run(['java','-cp',directory,'HeckerSliderValuesTest'],check=True)


if __name__=='__main__':unittest.main()
