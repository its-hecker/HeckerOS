#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Run the imported MMS length/skip guards; this is not the full Telephony suite."""
import argparse
from pathlib import Path
import subprocess
import tempfile
from check_launcher_studio import method


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--framework-root', type=Path, required=True)
    args = parser.parse_args()
    source = (args.framework_root / 'telephony/common/com/google/android/mms/pdu/PduParser.java').read_text()
    methods = '\n'.join(method(source, signature) for signature in [
        '    private static boolean isInvalidLength(', '    protected static int skipWapValue('])
    probe = '''import java.io.ByteArrayInputStream;
public class MmsProbe {
 static final String LOG_TAG="MMS";
 static class Log { static void e(String tag,String message){} }
''' + methods + '''
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  for(int length:new int[]{Integer.MIN_VALUE,-1,0,1,3,4,5,Integer.MAX_VALUE}) {
   var stream=new ByteArrayInputStream(new byte[]{1,2,3,4});
   boolean invalid=length<0||length>4;
   check(isInvalidLength(length,stream)==invalid);
   check(skipWapValue(stream,length)==(invalid?-1:length));
   check(stream.available()==(invalid?4:4-length));
  }
  for(int i=0;i<10000;i++)check(skipWapValue(new ByteArrayInputStream(new byte[0]),Integer.MAX_VALUE)==-1);
  var stream=new ByteArrayInputStream(new byte[]{1,2,3,4});
  check(skipWapValue(stream,3)==3&&skipWapValue(stream,2)==-1&&stream.read()==4);
  System.out.println("Imported MMS guards: negative, overflow, truncated, empty and valid skips; invalid input preserves stream; bounded heap PASS");
 }
}'''
    with tempfile.TemporaryDirectory(prefix='hecker-mms-') as temporary:
        folder = Path(temporary)
        file = folder / 'MmsProbe.java'
        file.write_text(probe)
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(folder), str(file)], check=True)
        subprocess.run(['java', '-Xmx16m', '-ea', '-cp', str(folder), 'MmsProbe'], check=True)


if __name__ == '__main__':
    main()
