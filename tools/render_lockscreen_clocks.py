#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Render production clock Canvas commands through the existing SVG host adapter.

The host font metrics are approximate; this is artwork QA, not a device screenshot.
"""
import ast
from io import BytesIO
from pathlib import Path
import subprocess
import cairosvg
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'generated/lockscreen-preview'
WORK.mkdir(parents=True,exist_ok=True)
syntax=ast.parse((ROOT/'tools/render_motion_island.py').read_text())
sources=next(ast.literal_eval(n.value) for n in syntax.body if isinstance(n,ast.Assign)
    and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets))
sources={k:v for k,v in sources.items() if k.startswith('android/graphics/')}
sources['android/graphics/Paint.java']='''package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1;public enum Style{FILL,STROKE}public enum Cap{ROUND}
 public int color=0xffffffff,alpha=255;public float size=12,stroke=1,scale=1;public Style style=Style.FILL;
 public Typeface typeface;public Shader shader;public Paint(int flags){}public void reset(){color=0xffffffff;alpha=255;size=12;stroke=1;scale=1;style=Style.FILL;shader=null;}
 public void setAntiAlias(boolean a){}public void setColor(int a){color=a;}public void setAlpha(int a){alpha=a;}
 public void setStyle(Style a){style=a;}public void setStrokeWidth(float a){stroke=a;}public void setStrokeCap(Cap a){}
 public void setTypeface(Typeface a){typeface=a;}public void setTextSize(float a){size=a;}public float getTextSize(){return size;}
 public void setTextScaleX(float a){scale=a;}public void setShader(Shader a){shader=a;}
 public float measureText(String text){return (float)new java.awt.Font("SansSerif",typeface!=null&&typeface.name.contains("black")?1:0,1)
 .deriveFont(size).getStringBounds(text,new java.awt.font.FontRenderContext(null,true,true)).getWidth()*scale;}
 public static class FontMetrics{public float ascent,descent;}public FontMetrics getFontMetrics(){FontMetrics m=new FontMetrics();m.ascent=-size*.8f;m.descent=size*.2f;return m;}
}'''
sources['android/graphics/Typeface.java']='''package android.graphics;public class Typeface{public static final int NORMAL=0;public String name;
 public static Typeface create(String name,int style){Typeface t=new Typeface();t.name=name;return t;}}'''
sources['android/graphics/Color.java']='package android.graphics;public class Color{public static final int WHITE=0xffffffff;}'
sources['android/graphics/Shader.java']='package android.graphics;public class Shader{public enum TileMode{CLAMP}}'
sources['android/graphics/LinearGradient.java']='''package android.graphics;public class LinearGradient extends Shader{public float x,y,a,b;public int[] colors;
 public LinearGradient(float x,float y,float a,float b,int[]colors,float[]stops,TileMode mode){this.x=x;this.y=y;this.a=a;this.b=b;this.colors=colors;}}'''
sources['android/graphics/Path.java']=sources['android/graphics/Path.java'].replace('public void close()',
    'public void cubicTo(float x,float y,float a,float b,float e,float f){s.append("C"+x+","+y+" "+a+","+b+" "+e+","+f+" ");}public void close()')
canvas=sources['android/graphics/Canvas.java']
canvas=canvas.replace('int depth,clips;', 'int depth,clips,gradients;java.util.Stack<Integer> stack=new java.util.Stack<>();')
canvas=canvas.replace('public int save(){return depth;}', 'public int save(){stack.push(depth);return depth;}public void restore(){restoreToCount(stack.pop());}')
canvas=canvas.replace('public void rotate(float a){', 'public void rotate(float a,float x,float y){group("rotate("+a+","+x+","+y+")");}public void rotate(float a){')
canvas=canvas.replace('float alpha=((p.color>>>24)&255)/255f;', '''float alpha=((p.color>>>24)&255)/255f*p.alpha/255f;
 if(p.shader instanceof LinearGradient){LinearGradient g=(LinearGradient)p.shader;String id="g"+(gradients++);
 s.append("<defs><linearGradient id='"+id+"' gradientUnits='userSpaceOnUse' x1='"+g.x+"' y1='"+g.y+"' x2='"+g.a+"' y2='"+g.b+"'>");
 for(int i=0;i<g.colors.length;i++)s.append("<stop offset='"+(i*100f/(g.colors.length-1))+"%' stop-color='"+String.format("#%06x",g.colors[i]&0xffffff)+"'/>");
 s.append("</linearGradient></defs>");color="url(#"+id+")";}''')
canvas=canvas.replace('public void drawPath(','''public void drawOval(float x,float y,float a,float b,Paint p){s.append("<ellipse cx='"+((x+a)/2)+"' cy='"+((y+b)/2)+"' rx='"+((a-x)/2)+"' ry='"+((b-y)/2)+"' "+style(p,false)+"/>");}
public void drawPath(''')
start=canvas.index('public void drawText(')
canvas=canvas[:start]+'''public void drawText(String text,float x,float y,Paint p){String weight=p.typeface!=null&&p.typeface.name.contains("black")?"900":p.typeface!=null&&p.typeface.name.contains("thin")?"200":"500";
 s.append("<text x='"+x+"' y='"+y+"' font-family='sans-serif' font-weight='"+weight+"' font-size='"+p.size+"' textLength='"+p.measureText(text)+"' lengthAdjust='spacingAndGlyphs' "+style(p,false)+">"+text.replace("&","&amp;").replace("<","&lt;")+"</text>");}
}'''
sources['android/graphics/Canvas.java']=canvas
sources['ClockPreview.java']='''import android.graphics.Canvas;import com.android.systemui.hecker.HeckerLockscreenClockRenderer;
public class ClockPreview{public static void main(String[]args)throws Exception{
 HeckerLockscreenClockRenderer renderer=new HeckerLockscreenClockRenderer();
 for(int i=0;i<72;i++){Canvas c=new Canvas();renderer.word(c,4,57,"10. 28. WEDNESDAY",360,310,i*120f/72,false);
 java.nio.file.Files.writeString(java.nio.file.Path.of(args[0],String.format("%03d.svg",i)),"<svg xmlns='http://www.w3.org/2000/svg' width='360' height='360'><rect width='360' height='360' fill='#09080c'/><text x='28' y='24' fill='#a6a0ad' font-family='sans-serif' font-size='11'>HECKEROS · WORD CLOCK · SAMPLE 4:57</text><g transform='translate(0,45)'>"+c.s+"</g></svg>");}
 Canvas tall=new Canvas();renderer.tall(tall,4,57,true,"WEDNESDAY, OCTOBER 28",360,480,1,false);
 java.nio.file.Files.writeString(java.nio.file.Path.of(args[0],"tall.svg"),"<svg xmlns='http://www.w3.org/2000/svg' width='360' height='540'><rect width='360' height='540' fill='#09080c'/><text x='28' y='24' fill='#a6a0ad' font-family='sans-serif' font-size='11'>HECKEROS · TALL CLOCK · SAMPLE 04:57</text><g transform='translate(0,45)'>"+tall.s+"</g></svg>");
}}'''
files=[]
for name,source in sources.items():
    p=WORK/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);files.append(str(p))
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(WORK/'classes'),*files,
    str(ROOT/'framework/lockscreen/WordClockText.java'),str(ROOT/'framework/lockscreen/HeckerLockscreenClockRenderer.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(WORK/'classes'),'ClockPreview',str(WORK)],check=True)
frames=[]
for svg in sorted(WORK.glob('[0-9][0-9][0-9].svg')):
    png=cairosvg.svg2png(url=str(svg));frames.append(Image.open(BytesIO(png)).convert('RGB'))
destination=ROOT/'docs/previews';destination.mkdir(exist_ok=True)
frames[0].save(destination/'lockscreen-word.png')
palette=frames[0].quantize(colors=256)
animation=[frame.quantize(palette=palette,dither=Image.Dither.NONE) for frame in frames]
animation[0].save(destination/'lockscreen-word.gif',save_all=True,append_images=animation[1:],duration=83,loop=0,optimize=True)
cairosvg.svg2png(url=str(WORK/'tall.svg'),write_to=str(destination/'lockscreen-tall.png'))
print('Production Canvas clock artwork rendered; host font metrics, not a phone screenshot.')
