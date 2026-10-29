#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Artwork QA from the production renderer; host fonts approximate Android's fonts."""
from pathlib import Path
import subprocess
import cairosvg
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
base = ROOT / "tools/render_lockscreen_clocks.py"
namespace = {"__file__": str(base)}
# Reuse the reviewed Canvas-to-SVG adapter without executing its old preview exports.
exec(compile(base.read_text().split("files=[]\n")[0], str(base), "exec"), namespace)
sources = namespace["sources"]
sources.pop("ClockPreview.java")
paint = sources["android/graphics/Paint.java"].replace("public enum Cap{ROUND}", "public enum Cap{ROUND} public enum Align{CENTER}")
paint = paint.replace("public float measureText", "public void setTextAlign(Align a){} public float measureText")
sources["android/graphics/Paint.java"] = paint
sources["android/graphics/Typeface.java"] = sources["android/graphics/Typeface.java"].replace("NORMAL=0;", "NORMAL=0,BOLD=1;").replace("t.name=name;", 't.name=style==1?name+"-black":name;')
canvas = sources["android/graphics/Canvas.java"]
start = canvas.index('public void drawText(')
canvas = canvas[:start] + r'''public void drawText(String text,float x,float y,Paint p){String weight=p.typeface!=null&&p.typeface.name.contains("black")?"900":p.typeface!=null&&p.typeface.name.contains("thin")?"200":"500";
 s.append("<g transform='translate("+x+" 0) scale("+p.scale+" 1)'><text text-anchor='middle' x='0' y='"+y+"' font-family='DejaVu Sans' font-weight='"+weight+"' font-size='"+p.size+"' "+style(p,false)+">"+text.replace("&","&amp;").replace("<","&lt;")+"</text></g>");}
}'''
sources["android/graphics/Canvas.java"] = canvas
sources["FlowPreview.java"] = '''import android.graphics.Canvas;import org.hecker.motion.FlowClockRenderer;
public class FlowPreview{public static void main(String[]args)throws Exception{
 FlowClockRenderer renderer=new FlowClockRenderer();
 for(int style=3;style<=5;style++)for(int mode=0;mode<3;mode++){
  Canvas c=new Canvas();renderer.draw(c,22,30,true,"Thu, 29 Oct",java.util.Locale.US,360,mode==2?184:480,style,mode==0?0:1,false,mode==2,.76f,1,100,0,0);
  java.nio.file.Files.writeString(java.nio.file.Path.of(args[0],style+"-"+mode+".svg"),
   "<svg xmlns='http://www.w3.org/2000/svg' width='360' height='480'><rect width='360' height='480' fill='#121019'/><g transform='translate(0,"+(mode==2?110:0)+")'>"+c.s+"</g></svg>");
 }
 for(int i=0;i<=30;i++){Canvas c=new Canvas();renderer.draw(c,22,30,true,"Thu, 29 Oct",java.util.Locale.US,360,480,3,i/30f,false,false,.76f,1,100,0,0);
 java.nio.file.Files.writeString(java.nio.file.Path.of(args[0],String.format("wake-%02d.svg",i)),"<svg xmlns='http://www.w3.org/2000/svg' width='360' height='480'><rect width='360' height='480' fill='#121019'/>"+c.s+"</svg>");}
}}'''
work = ROOT / "generated/living-flow-preview"
work.mkdir(parents=True, exist_ok=True)
files=[]
for name, source in sources.items():
    path=work/name;path.parent.mkdir(parents=True, exist_ok=True);path.write_text(source);files.append(str(path))
subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(work/"classes"),
    *files, str(ROOT/"aosp-launcher/src/org/hecker/motion/FlowClockRenderer.java")], check=True)
subprocess.run(["java", "-Djava.awt.headless=true", "-cp", str(work/"classes"), "FlowPreview", str(work)], check=True)
dest = ROOT/"docs/previews"; dest.mkdir(exist_ok=True)
grid=Image.new("RGB", (1080, 1550), "#121019")
draw=ImageDraw.Draw(grid)
draw.text((24, 12), "HECKEROS LIVING CLOCKS / Production drawing, approximate host fonts", fill="#bbb2c9")
for column, style in enumerate(range(3,6)):
    for row, title in enumerate(("AOD", "Lock screen", "Home")):
        svg=work/f"{style}-{row}.svg"
        png=svg.with_suffix(".png");cairosvg.svg2png(url=str(svg), write_to=str(png))
        grid.paste(Image.open(png).convert("RGB"), (column*360, 50+row*500))
        draw.text((column*360+20, 50+row*500), f"{('Tall','Horizontal','Bold')[column]} / {title}", fill="#bbb2c9")
grid.save(dest/"living-flow-clocks.png")
for i in range(31):
    svg=work/f"wake-{i:02d}.svg"
    cairosvg.svg2png(url=str(svg), write_to=str(svg.with_suffix(".png")))
subprocess.run(["ffmpeg", "-v", "error", "-framerate", "30", "-i", str(work/"wake-%02d.png"),
    "-filter_complex", "[0:v]split[f][r];[r]reverse[b];[f][b]concat=n=2:v=1:a=0,split[a][c];[a]palettegen[p];[c][p]paletteuse",
    "-loop", "0", "-y", str(dest/"living-flow-wake.gif")], check=True)
if (dest/"living-flow-wake.gif").stat().st_size == 0:
    raise RuntimeError("Empty clock animation export")
print("Production Living Clock artwork rendered; not a device screenshot.")
