# SPDX-License-Identifier: Apache-2.0
"""Exercise the shipped Java picker-copy implementation using real host IO."""
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[1]
class DepthPickerTests(unittest.TestCase):
    def test_copy_limits_and_provider_errors(self):
        java='''package org.hecker.suite;
import java.io.*;import java.nio.file.*;import java.util.Arrays;
class Probe {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static void fail(InputStream in,File f,long n)throws Exception {
  try {DepthPickerCopy.copy(in,f,n);throw new AssertionError("Invalid accepted");}
  catch(IOException ok){check(!f.exists());}
 }
 public static void main(String[]x)throws Exception {
  File f=new File(x[0],"picker.bin");
  byte[] b=new byte[1024];Arrays.fill(b,(byte)37);
  check(DepthPickerCopy.copy(new ByteArrayInputStream(b),f,1024)==1024);
  check(Arrays.equals(b,Files.readAllBytes(f.toPath())));f.delete();
  fail(null,f,1024);fail(new ByteArrayInputStream(new byte[0]),f,1024);
  fail(new ByteArrayInputStream(b),f,1023);
  fail(new InputStream(){public int read()throws IOException{throw new IOException("unavailable");}},f,1024);
  System.out.println("Depth URI private-copy bounds and cleanup PASS");
 }
}'''
        with tempfile.TemporaryDirectory() as temp:
            probe=Path(temp)/'Probe.java'
            probe.write_text(java)
            subprocess.run(['javac','-d',temp,str(ROOT/'app/src/org/hecker/suite/DepthPickerCopy.java'),str(probe)],check=True)
            subprocess.run(['java','-cp',temp,'org.hecker.suite.Probe',temp],check=True)
