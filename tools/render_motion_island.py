#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Host preview of production Canvas commands; requires Pillow and CairoSVG. Not a device screenshot."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
work=root/'generated/motion-preview'; work.mkdir(parents=True,exist_ok=True)
for stale in (work/'frames').glob('[0-9][0-9]-[0-9][0-9]*.svg'): stale.unlink()
sources={
'android/content/Context.java': 'package android.content;public class Context{public android.content.res.Resources getResources(){return new android.content.res.Resources();}}',
'android/content/res/Resources.java': 'package android.content.res;public class Resources{public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}public int getIdentifier(String a,String b,String c){return 1;}public int getDimensionPixelSize(int id){return 72;}}',
'android/util/DisplayMetrics.java':'package android.util;public class DisplayMetrics{public float density=3,scaledDensity=3;}',
'android/view/View.java':'package android.view;public class View{protected final android.content.Context context;public static final int IMPORTANT_FOR_ACCESSIBILITY_YES=1,ACCESSIBILITY_LIVE_REGION_POLITE=1;public View(android.content.Context c){context=c;}public android.content.res.Resources getResources(){return context.getResources();}public int getWidth(){return 672;}public void setImportantForAccessibility(int a){}public void setAccessibilityLiveRegion(int a){}public void setContentDescription(String a){}public void invalidate(){}protected void onDraw(android.graphics.Canvas c){}protected void onDetachedFromWindow(){}}',
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long elapsedRealtime(){return 10000;}}',
'android/view/animation/DecelerateInterpolator.java':'package android.view.animation;public class DecelerateInterpolator{}',
'android/animation/ValueAnimator.java':'package android.animation;public class ValueAnimator{public interface AnimatorUpdateListener{void update(ValueAnimator v);}public static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}public void setDuration(long a){}public void setInterpolator(Object a){}public void addUpdateListener(AnimatorUpdateListener l){}public Object getAnimatedValue(){return 1f;}public void start(){}public void cancel(){}}',
'android/graphics/Color.java':'package android.graphics;public class Color{public static int argb(int a,int r,int g,int b){return a<<24|r<<16|g<<8|b;}}',
'android/graphics/Typeface.java':'package android.graphics;public class Typeface{public static final int NORMAL=0;public static Typeface create(String s,int a){return new Typeface();}}',
'android/graphics/Paint.java':r'''package android.graphics;public class Paint{
public static final int ANTI_ALIAS_FLAG=1;public enum Style{FILL,STROKE}public enum Cap{ROUND}public enum Join{ROUND}
public Style style=Style.FILL;public int color;public float stroke=1,size=36;
public Paint(int f){}public void setStyle(Style a){style=a;}public void setColor(int c){color=c;}public void setStrokeWidth(float s){stroke=s;}public void setStrokeCap(Cap c){}public void setStrokeJoin(Join j){}public void setTextSize(float s){size=s;}public void setTypeface(Typeface t){}public float measureText(String s){return s.length()*size*.54f;}public static class FontMetrics{public float ascent,descent;}public FontMetrics getFontMetrics(){FontMetrics f=new FontMetrics();f.ascent=-size*.8f;f.descent=size*.2f;return f;}}''',
'android/text/TextPaint.java':'package android.text;public class TextPaint extends android.graphics.Paint{public TextPaint(int f){super(f);}}',
'android/text/TextUtils.java':'package android.text;public class TextUtils{public enum TruncateAt{END}public static CharSequence ellipsize(String s,TextPaint p,float width,TruncateAt t){return s;}}',
'android/graphics/RectF.java':'package android.graphics;public class RectF{public float left,top,right,bottom;public void set(float a,float b,float c,float d){left=a;top=b;right=c;bottom=d;}public float centerY(){return(top+bottom)/2;}}',
'android/graphics/Path.java':'package android.graphics;public class Path{public StringBuilder s=new StringBuilder();public void reset(){s.setLength(0);}public void moveTo(float x,float y){s.append("M"+x+","+y+" ");}public void lineTo(float x,float y){s.append("L"+x+","+y+" ");}public void quadTo(float x,float y,float a,float b){s.append("Q"+x+","+y+" "+a+","+b+" ");}public void close(){s.append("Z ");}}',
'android/graphics/Canvas.java':r'''package android.graphics;public class Canvas{
public StringBuilder s=new StringBuilder();int depth,clips;
public int save(){return depth;}public void restoreToCount(int count){while(depth>count){s.append("</g>");depth--;}}
private void group(String transform){s.append("<g transform=\""+transform+"\">");depth++;}
public void translate(float x,float y){group("translate("+x+","+y+")");}public void scale(float x,float y){group("scale("+x+","+y+")");}public void rotate(float a){group("rotate("+a+")");}
public void clipRect(float x,float y,float r,float b){String id="c"+(clips++);s.append("<defs><clipPath id=\""+id+"\"><rect x=\""+x+"\" y=\""+y+"\" width=\""+(r-x)+"\" height=\""+(b-y)+"\"/></clipPath></defs><g clip-path=\"url(#"+id+")\">");depth++;}
private String style(Paint p,boolean line){String color=String.format("#%06x",p.color&0xffffff);float alpha=((p.color>>>24)&255)/255f;return p.style==Paint.Style.STROKE||line?"fill=\"none\" stroke=\""+color+"\" stroke-opacity=\""+alpha+"\" stroke-width=\""+p.stroke+"\" stroke-linejoin=\"round\" stroke-linecap=\"round\"":"fill=\""+color+"\" fill-opacity=\""+alpha+"\"";}
public void drawPath(Path p,Paint color){s.append("<path d=\""+p.s+"\" "+style(color,false)+"/>");}
public void drawLine(float x,float y,float a,float b,Paint p){s.append("<path d=\"M"+x+","+y+" L"+a+","+b+"\" "+style(p,true)+"/>");}
public void drawRoundRect(RectF r,float x,float y,Paint p){s.append("<rect x=\""+r.left+"\" y=\""+r.top+"\" width=\""+(r.right-r.left)+"\" height=\""+(r.bottom-r.top)+"\" rx=\""+x+"\" ry=\""+y+"\" "+style(p,false)+"/>");}
public void drawCircle(float x,float y,float radius,Paint p){s.append("<circle cx=\""+x+"\" cy=\""+y+"\" r=\""+radius+"\" "+style(p,false)+"/>");}
public void drawText(String text,float x,float y,Paint p){s.append("<text x=\""+x+"\" y=\""+y+"\" font-family=\"sans-serif\" font-weight=\"500\" font-size=\""+p.size+"\" "+style(p,false)+">"+text.replace("&","&amp;").replace("<","&lt;")+"</text>");}
}''',

}

sources['android/content/Context.java']='package android.content;public class Context{public android.content.res.Resources getResources(){return new android.content.res.Resources();}public String getString(int id){return "Open music player";}}'
sources['android/os/SystemClock.java']='package android.os;public class SystemClock{public static long now=10000;public static long elapsedRealtime(){return now;}}'
sources['com/android/systemui/res/R.java']='package com.android.systemui.res;public class R{public static class drawable{public static int hecker_motion_astronaut=1;}public static class string{public static int hecker_motion_open_player=1;}}'
sources['com/android/systemui/hecker/HeckerQsMedia.java']='package com.android.systemui.hecker;public class HeckerQsMedia{public static class Snapshot{public android.graphics.Bitmap artwork;public boolean playing=true;}}'
sources['android/graphics/Bitmap.java']='package android.graphics;public class Bitmap{public final boolean cover;public Bitmap(){this(false);}public Bitmap(boolean cover){this.cover=cover;}public boolean isRecycled(){return false;}public int getWidth(){return cover?512:2172;}public int getHeight(){return cover?512:724;}}'
sources['android/graphics/BitmapFactory.java']='package android.graphics;public class BitmapFactory{public static class Options{public boolean inScaled;}public static Bitmap decodeResource(android.content.res.Resources r,int id,Options o){return new Bitmap();}}'
sources['android/graphics/Rect.java']='package android.graphics;public class Rect{public int left,top,right,bottom;public Rect(int a,int b,int c,int d){left=a;top=b;right=c;bottom=d;}}'
sources['android/graphics/RectF.java']=sources['android/graphics/RectF.java'].replace('public void set(', 'public RectF(){}public RectF(float a,float b,float c,float d){set(a,b,c,d);}public void set(')
sources['android/graphics/Paint.java']=sources['android/graphics/Paint.java'].replace('ANTI_ALIAS_FLAG=1;', 'ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;').replace('public void setStyle(', 'public void setAlpha(int a){color=(color&0xffffff)|(a<<24);}public void setStyle(')
sources['android/graphics/Path.java']=sources['android/graphics/Path.java'].replace('public void reset()', 'public enum Direction{CW}public RectF roundRect;public float radius;public void addRoundRect(RectF r,float x,float y,Direction d){roundRect=r;radius=x;}public void reset()')
sources['android/view/View.java']=sources['android/view/View.java'].replace('getWidth(){return 672;}', 'getWidth(){return width;}public int getHeight(){return height;}public int width=672,height=450;public android.content.Context getContext(){return context;}public void setClickable(boolean v){}public void setFocusable(boolean v){}public boolean post(Runnable r){return true;}public boolean postDelayed(Runnable r,long d){return true;}public boolean removeCallbacks(Runnable r){return true;}').replace('IMPORTANT_FOR_ACCESSIBILITY_YES=1,', 'IMPORTANT_FOR_ACCESSIBILITY_YES=1,IMPORTANT_FOR_ACCESSIBILITY_NO=2,')
sources['android/text/TextUtils.java']=sources['android/text/TextUtils.java'].replace('return s;', 'int n=Math.max(1,(int)(width/(p.size*.54f)));return s.length()>n?s.substring(0,Math.max(0,n-1))+"…":s;')
canvas=sources['android/graphics/Canvas.java']
canvas=canvas[:-1]+r'''
public void clipPath(Path path){
 RectF r=path.roundRect;String id="b"+(clips++);
 s.append("<defs><clipPath id=\""+id+"\"><rect x=\""+r.left+"\" y=\""+r.top+"\" width=\""+(r.right-r.left)+"\" height=\""+(r.bottom-r.top)+"\" rx=\""+path.radius+"\"/></clipPath></defs><g clip-path=\"url(#"+id+")\">");depth++;
}
public void drawBitmap(Bitmap bitmap,Rect source,RectF target,Paint p){
 s.append("<svg x=\""+target.left+"\" y=\""+target.top+"\" width=\""+(target.right-target.left)+"\" height=\""+(target.bottom-target.top)+"\" viewBox=\""+source.left+" "+source.top+" "+(source.right-source.left)+" "+(source.bottom-source.top)+"\" opacity=\""+((p.color>>>24)/255f)+"\"><image width=\""+bitmap.getWidth()+"\" height=\""+bitmap.getHeight()+"\" href=\""+System.getProperty(bitmap.cover?"coverData":"atlasData")+"\"/></svg>");
}
}'''
sources['android/graphics/Canvas.java']=canvas
sources['com/android/systemui/hecker/Preview.java']=r'''package com.android.systemui.hecker;
public class Preview {public static void main(String[] args)throws Exception{
java.nio.file.Path out=java.nio.file.Path.of(args[0]);java.nio.file.Files.createDirectories(out);
String data=java.util.Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[1])));System.setProperty("atlasData","data:image/png;base64,"+data);
String cover=java.util.Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[2])));System.setProperty("coverData","data:image/svg+xml;base64,"+cover);
String[] labels={"Midnight orbit","Playing","Previous track","Track skipped","Paused"};int[] actions={0,1,4,3,2};
String[] captions={"Music playing · capsule stays visible","PLAY · Welcome: bounce, open arm and sparkles","LEFT · Reach into the swing","RIGHT · Balance with a playful wiggle","PAUSE · Bye-bye wave and smooth retraction"};
for(int scene=0;scene<actions.length;scene++){
 MotionIslandState state=new MotionIslandState();state.environment(true,true,true,true,0);
 if(actions[scene]!=0){state.accept(actions[scene],0,10000,10000);state.direction=scene==2?-1:scene==3?1:0;}
 MotionIslandView view=new MotionIslandView(new android.content.Context());view.width=444;view.height=96;
 MotionAstronautView person=new MotionAstronautView(new android.content.Context());person.width=672;person.height=450;
 HeckerQsMedia.Snapshot music=new HeckerQsMedia.Snapshot();music.playing=scene!=4;music.artwork=new android.graphics.Bitmap(true);
 for(int frame=0;frame<60;frame++){
 android.os.SystemClock.now=10000+frame*50;
 state.environment(true,true,true,music.playing,0);
 view.update(music,state.current(android.os.SystemClock.now)==0?labels[0]:labels[scene],true);person.update(state,true);
 android.graphics.Canvas canvas=new android.graphics.Canvas();
 int save;
 if(state.visible(android.os.SystemClock.now)){save=canvas.save();canvas.translate(114,0);view.onDraw(canvas);canvas.restoreToCount(save);}
 String capsuleOnly=canvas.s.toString();
 if(state.creatureVisible(android.os.SystemClock.now)){save=canvas.save();canvas.translate(0,87);person.onDraw(canvas);canvas.restoreToCount(save);}
 String base="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"672\" height=\"500\"><defs><linearGradient id=\"bg\" x2=\"0\" y2=\"1\"><stop stop-color=\"#2d6076\"/><stop offset=\"1\" stop-color=\"#0c1624\"/></linearGradient></defs><rect width=\"100%\" height=\"100%\" rx=\"24\" fill=\"url(#bg)\"/>";
 String caption="<text x=\"336\" y=\"475\" text-anchor=\"middle\" font-family=\"sans-serif\" font-size=\"18\" fill=\"#e2eef7\">"+captions[scene]+"</text>";
 java.nio.file.Files.writeString(out.resolve(String.format("%02d-%02d.svg",scene,frame)),base+canvas.s+caption+"</svg>");
 java.nio.file.Files.writeString(out.resolve(String.format("%02d-%02d.capsule.svg",scene,frame)),base+capsuleOnly+caption+"</svg>");
 }
}
}}'''
files=[]
for name,code in sources.items():
 p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code);files.append(str(p))
production=root/'framework/motionisland'
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(work/'classes'),*files,*[str(production/n) for n in ('MotionIslandState.java','MotionIslandAnimation.java','MotionIslandView.java','MotionAstronautView.java')]],check=True)
subprocess.run(['java','-cp',str(work/'classes'),'com.android.systemui.hecker.Preview',str(work/'frames'),str(production/'astronaut-wave.png'),str(root/'docs/previews/motion-island-album.svg')],check=True)
import cairosvg,io,re
from PIL import Image, ImageChops
frames=[];negative_control=False
for svg in sorted((work/'frames').glob('[0-9][0-9]-[0-9][0-9].svg')):
 source=svg.read_bytes()
 actual=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=source))).convert('RGB')
 baseline=Image.open(io.BytesIO(cairosvg.svg2png(url=str(svg.with_suffix('.capsule.svg'))))).convert('RGB')
 bar=(0,0,672,87)
 assert ImageChops.difference(actual.crop(bar),baseline.crop(bar)).getbbox() is None, 'Character overlapped capsule: '+svg.name
 if svg.name=='01-01.svg':
  unclipped=re.sub(rb' clip-path="url\(#c\d+\)"',b'',source)
  unsafe=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=unclipped))).convert('RGB')
  negative_control=ImageChops.difference(unsafe.crop(bar),baseline.crop(bar)).getbbox() is not None
 frames.append(actual)
assert negative_control, 'Clipping regression check did not detect its negative control'
out=root/'docs/previews';out.mkdir(exist_ok=True)
frames[72].save(out/'motion-island.png')
frames[252].save(work/'wave.png')
frames[0].save(out/'motion-island.gif',save_all=True,append_images=frames[1:],duration=50,loop=0,optimize=True)
print('Rope/character pixel comparison:',len(frames),'frames leave the capsule untouched; unclipped negative control detected')
print('Native production draw-command preview:',out/'motion-island.gif')
