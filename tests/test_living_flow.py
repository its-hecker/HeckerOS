# SPDX-License-Identifier: Apache-2.0
"""Exercise the production clock geometry, locale, AOD palette and reduced-motion policy."""
import pathlib
import subprocess
import tempfile
import unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]

class LivingClockTests(unittest.TestCase):
    def test_production_clock_frames(self):
        sources = {
            "android/graphics/Typeface.java": """package android.graphics;
public class Typeface { public static final int NORMAL=0,BOLD=1; public static Typeface create(String n,int s){return new Typeface();} }""",
            "android/graphics/Color.java": "package android.graphics; public class Color { public static final int WHITE=0xffffffff; }",
            "android/graphics/Paint.java": """package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1; public enum Align{CENTER}
 public float size=12,scale=1; public int color,alpha=255;
 public Paint(int f){} public void reset(){scale=1;alpha=255;}
 public void setAntiAlias(boolean b){} public void setColor(int c){color=c;} public void setAlpha(int a){alpha=a;}
 public void setTypeface(Typeface t){} public void setTextSize(float s){size=s;} public void setTextAlign(Align a){}
 public float measureText(String t){return t.length()*size*.55f*scale;} public void setTextScaleX(float s){scale=s;}
 public static class FontMetrics{public float ascent,descent;}
 public FontMetrics getFontMetrics(){FontMetrics f=new FontMetrics();f.ascent=-size*.8f;f.descent=size*.2f;return f;}
}""",
            "android/graphics/Canvas.java": """package android.graphics;
public class Canvas {
 public int draws;public float width,height; public boolean ambient; public String text="";
 public void drawText(String t,float x,float y,Paint p){
  if(p.alpha==0)return;
  float w=p.measureText(t),top=y-p.size*.8f,bottom=y+p.size*.2f;
  if(!Float.isFinite(x+y+w+top+bottom)||x-w/2 < -1 || x+w/2>width+1 || top < -1 || bottom>height+1)
    throw new AssertionError("Glyph outside native clock region: "+t+" "+width+"x"+height+" @"+x+","+top+".."+bottom);
  if(ambient&&p.color!=Color.WHITE)throw new AssertionError("Coloured AOD");
  draws++;text+=t+" ";
 }
}""",
            "ClockProbe.java": """import org.hecker.motion.FlowClockRenderer; import android.graphics.Canvas; import java.util.Locale;
public class ClockProbe{
 public static void main(String[]args){
 FlowClockRenderer r=new FlowClockRenderer(); int frames=0;
 float last=-1;
 for(int i=0;i<=1000;i++){float t=FlowClockRenderer.settle(i/1000f);
  if(t<last||t<0||t>1.00001)throw new AssertionError("Discontinuous settle");last=t;}
 if(FlowClockRenderer.settle(0)!=0||FlowClockRenderer.settle(1)!=1)throw new AssertionError("Endpoints");
 for(Locale locale:new Locale[]{Locale.US,Locale.forLanguageTag("ur-PK"),Locale.forLanguageTag("ar-EG")})
 for(int style=3;style<=5;style++)for(int width:new int[]{240,360,480})for(int height:new int[]{96,184,480})
 for(boolean small:new boolean[]{true,false})for(boolean home:new boolean[]{true,false})
 for(float awake:new float[]{0,.001f,.25f,.5f,.75f,1})
 for(int scale:new int[]{70,100,110})for(int position:new int[]{-100,0,100}){
  Canvas c=new Canvas();c.width=width;c.height=height;c.ambient=awake==0&&!home;
  r.draw(c,0,57,false,"Thu, 29 Oct",locale,width,height,style,awake,small,home,.76f,4,scale,position,position);
  if(c.draws<2)throw new AssertionError("Clock/date disappeared");
  if(locale.equals(Locale.US)&&!c.text.contains("12"))throw new AssertionError("12-hour midnight");
  frames++;
 }
 System.out.println("Production living clock: "+frames+" bounded locale/AOD/wake/Home frames");
 }
}"""
        }
        with tempfile.TemporaryDirectory(prefix="hecker-living-clock-") as temp:
            work = pathlib.Path(temp)
            for name, text in sources.items():
                p = work / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text)
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(work / "classes"),
                *map(str, work.rglob("*.java")), str(ROOT / "aosp-launcher/src/org/hecker/motion/FlowClockRenderer.java")], check=True)
            subprocess.run(["java", "-cp", str(work / "classes"), "ClockProbe"], check=True, timeout=30)

if __name__ == "__main__": unittest.main()
