# SPDX-License-Identifier: Apache-2.0
"""Exercise production player measurement through native expansion/reversal, not a video-only tween."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class MediaFlowTests(unittest.TestCase):
    def test_reused_buffers_preserve_reviewed_media_positions(self):
        baseline = (ROOT / 'tests/fixtures/media-geometry-6963155.snapshot').read_text()
        probe = r'''import com.android.systemui.hecker.MediaCardGeometry;
import com.android.systemui.hecker.BaselineMediaGeometry;
public class MediaParity {
 public static void main(String[] args) {
  float[] reused=new float[5];int checks=0;
  for(float font:new float[]{1,1.4f,2})for(float width:new float[]{240,324,420})
  for(float height:new float[]{76,154,220})for(int step=0;step<=400;step++) {
   float square=step/400f,row=(400-step)/400f;
   for(int element=0;element<MediaCardGeometry.COUNT;element++) {
    float[] expected=BaselineMediaGeometry.bounds(element,width,height,square,row,font);
    MediaCardGeometry.boundsInto(reused,element,width,height,square,row,font);
    for(int k=0;k<5;k++)if(Math.abs(expected[k]-reused[k])>.00001f)
     throw new AssertionError("Optimized media changes reviewed placement");
    checks++;
   }
  }
  System.out.println("Reused media buffers match "+checks+" reviewed child placements PASS");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='hecker-media-parity-') as temporary:
            folder = Path(temporary)
            (folder / 'BaselineMediaGeometry.java').write_text(baseline.replace('MediaCardGeometry', 'BaselineMediaGeometry'))
            (folder / 'MediaParity.java').write_text(probe)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(folder),
                            str(folder / 'BaselineMediaGeometry.java'), str(folder / 'MediaParity.java'),
                            str(ROOT / 'framework/qs/MediaCardGeometry.java')], check=True)
            subprocess.run(['java', '-cp', str(folder), 'MediaParity'], check=True)

    def test_geometry_and_absolute_glow_phase(self):
        probe = r'''import com.android.systemui.hecker.MediaCardGeometry;
public class MediaProbe {
 public static void main(String[] args) {
  int frames=0;
  for(float font: new float[]{1,1.4f,2})
  for (int start: new int[]{240,324,420}) for(boolean row: new boolean[]{false,true})
  for(int end: row ? new int[]{240,264,324,420} : new int[]{154,176,324,420}) {
   float[][] previous=new float[MediaCardGeometry.COUNT][];
   for(int frame=0;frame<=1000;frame++) {
    float p=frame/1000f,w=start+(end-start)*p;
    float fromH=164+(font-1)*48,toH=row?80+(font-1)*32:178+(font-1)*64,h=fromH+(toH-fromH)*p;
    float[] title=MediaCardGeometry.bounds(MediaCardGeometry.TITLES,w,h,row?0:p,row?p:0,font);
    for(int i=0;i<MediaCardGeometry.COUNT;i++) {
     float[] b=MediaCardGeometry.bounds(i,w,h,row?0:p,row?p:0,font);
     for(float v:b) if(!Float.isFinite(v)) throw new AssertionError("Non-finite layout");
     if(b[0]<0||b[1]<0||b[2]<1||b[3]<1||b[0]+b[2]>w+.001||b[1]+b[3]>h+.001||b[4]<0||b[4]>1.00001)
      throw new AssertionError("Media child escapes its native card");
     if(i==MediaCardGeometry.PLAY && (b[2]<43.999f||b[3]<43.999f||Math.abs(b[4]-1)>.00001f)) throw new AssertionError("Lost playback target");
     if(i!=MediaCardGeometry.TITLES && b[4]>.5f && title[0]<b[0]+b[2]-.5f && b[0]<title[0]+title[2]-.5f &&
        title[1]<b[1]+b[3]-.5f && b[1]<title[1]+title[3]-.5f)
      throw new AssertionError("A media control crosses the title: "+i+" at "+p+" size "+w+"x"+h);
     if(previous[i]!=null) for(int k=0;k<5;k++)
      if(Math.abs(b[k]-previous[i][k])>.9f) throw new AssertionError("Player handoff snaps");
     float[] reverse=MediaCardGeometry.bounds(i,w,h,row?0:1-(1000-frame)/1000f,row?1-(1000-frame)/1000f:0,font);
     for(int k=0;k<5;k++) if(Math.abs(b[k]-reverse[k])>.0001) throw new AssertionError("Gesture reversal changes geometry");
     previous[i]=b;
    }
    frames++;
   }
  }
  if(MediaCardGeometry.bounds(MediaCardGeometry.SEEK,176,178,1,0)[4]!=0 ||
     MediaCardGeometry.bounds(MediaCardGeometry.PREVIOUS,324,80,0,1)[4]!=0 ||
     MediaCardGeometry.bounds(MediaCardGeometry.NEXT,324,80,0,1)[4]!=0)
      throw new AssertionError("Hidden controls still placed");
  // A quick close/reopen and a finishing scene animation cannot draw over Home.
  for(int i=0;i<=1000;i++) {
   float p=i/1000f;
   for(boolean opening:new boolean[]{false,true})for(boolean closing:new boolean[]{false,true}) {
    if(MediaCardGeometry.shadeAlpha(true,true,0,false,closing,opening,p)!=0 ||
       MediaCardGeometry.shadeAlpha(true,false,1,false,closing,opening,p)!=0 ||
       MediaCardGeometry.shadeAlpha(false,true,1,true,closing,opening,p)!=0)
     throw new AssertionError("Collapsed player lingers over Home");
   }
   if(MediaCardGeometry.shadeAlpha(true,true,1,false,false,false,p)!=1)
    throw new AssertionError("Visible legacy player waits for inner QS scene");
   if(p>=.25f && MediaCardGeometry.shadeAlpha(false,true,1,false,true,false,p)!=0)
    throw new AssertionError("Closing scene leaves a delayed player");
  }
  if(MediaCardGeometry.shadeAlpha(true,true,Float.NaN,false,false,false,0)!=0 ||
     MediaCardGeometry.shadeAlpha(false,true,1,false,true,false,Float.NaN)!=0)
   throw new AssertionError("Invalid visibility is not closed");
  for(long t=0;t<72000;t+=13) {
   float a=MediaCardGeometry.glowPhase(t),b=MediaCardGeometry.glowPhase(t+7200);
   if(Math.abs(a-b)>.00001) throw new AssertionError("Glow restarts across a scene handoff");
  }
  System.out.println("Production media: "+frames+" continuous forward/reverse card layouts; 44dp playback targets; absolute glow phase PASS");
 }
}'''
        with tempfile.TemporaryDirectory(prefix="hecker-media-flow-") as temp:
            folder = Path(temp)
            source = folder / "MediaProbe.java"
            source.write_text(probe)
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(folder),
                            str(source), str(ROOT / "framework/qs/MediaCardGeometry.java")], check=True)
            subprocess.run(["java", "-cp", str(folder), "MediaProbe"], check=True, timeout=30)


if __name__ == "__main__":
    unittest.main()
