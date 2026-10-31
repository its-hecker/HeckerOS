#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Render the current About XML/vectors and compiled glow spec; not phone footage."""
from pathlib import Path
from functools import lru_cache
from io import BytesIO
import json, math, re, subprocess, sys, xml.etree.ElementTree as ET
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from fontTools.pens.basePen import BasePen
from fontTools.svgLib.path import parse_path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'generated/about-phone-preview';OUT.mkdir(parents=True,exist_ok=True)
S=2;W=360*S;H=690*S;FPS=60;COUNT=432
A='{http://schemas.android.com/apk/res/android}'
REG='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
BOLD='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
CONFIG={'message':'Reference About home with the shared media glow.', 'style':'Supplied native HeckerOS reference',
 'fps':FPS,'duration_seconds':COUNT/FPS,'palette':'settings/res/values[-night]/hecker_about_colors.xml',
 'layout':'settings/res/layout/hecker_about_phone.xml','glow':'framework/qs/MediaGlowSpec.java',
 'font_note':'DejaVu host labels approximate Android sans-serif; not a phone capture.',
 'sample_data':{'name':'Google Pixel 4 XL','Android':'17','version':'1.0','processor':'Snapdragon 855'},
 'motion':'One 7.2-second absolute-time cycle; controls and text remain still.'}
(OUT/'style.json').write_text(json.dumps(CONFIG,indent=2)+'\n')
STRINGS={};STYLES={}
for p in (ROOT/'settings/res/values').glob('*.xml'):
 for n in ET.parse(p).getroot():
  if n.tag=='string':STRINGS[n.get('name')]=''.join(n.itertext())
  if n.tag=='style':STYLES[n.get('name')]={i.get('name').replace('android:',''):i.text for i in n}

def colors(light=False):
 d={n.get('name'):n.text for n in ET.parse(ROOT/'settings/res/values/hecker_about_colors.xml').getroot()}
 if not light:d.update({n.get('name'):n.text for n in ET.parse(ROOT/'settings/res/values-night/hecker_about_colors.xml').getroot()})
 return d

def color(value,palette):
 if not value or value.startswith('?'):return None
 if value.startswith('@color/'):return palette[value.split('/')[-1]]
 if 'transparent' in value:return None
 return value

@lru_cache(maxsize=80)
def font(size,bold=False):return ImageFont.truetype(BOLD if bold else REG,max(1,round(size)))
def unit(value,default=0):
 if value is None:return default
 return float(re.sub(r'(dp|sp|px)$','',str(value)))*S

def wrap(text,width,f):
 lines=[]
 for paragraph in text.split('\n'):
  line=''
  words=[]
  for word in paragraph.split():
   while f.getlength(word)>width and len(word)>1:
    cut=1
    while cut<len(word) and f.getlength(word[:cut+1])<=width:cut+=1
    words.append(word[:cut]);word=word[cut:]
   if word:words.append(word)
  for word in words:
   test=(line+' '+word).strip()
   if line and f.getlength(test)>width:lines.append(line);line=word
   else:line=test
  lines.append(line)
 return lines

class NativePathPen(BasePen):
 def __init__(self):super().__init__(None);self.paths=[];self.current=[]
 def _moveTo(self,p):
  if self.current:self.paths.append(self.current)
  self.current=[p]
 def _lineTo(self,p):self.current.append(p)
 def _curveToOne(self,a,b,c):
  p=self.current[-1]
  for t in np.linspace(0,1,25)[1:]:self.current.append(tuple((1-t)**3*p[k]+3*(1-t)**2*t*a[k]+3*(1-t)*t*t*b[k]+t**3*c[k] for k in range(2)))
 def _qCurveToOne(self,a,b):
  p=self.current[-1]
  for t in np.linspace(0,1,20)[1:]:self.current.append(tuple((1-t)**2*p[k]+2*(1-t)*t*a[k]+t*t*b[k] for k in range(2)))
 def _closePath(self):
  if self.current:self.current.append(self.current[0]);self.paths.append(self.current);self.current=[]
 def _endPath(self):
  if self.current:self.paths.append(self.current);self.current=[]

@lru_cache(maxsize=96)
def icon(name,width,height,light=False):
 palette=colors(light);root=ET.parse(ROOT/'settings/res/drawable'/f'{name}.xml').getroot();scale=4
 im=Image.new('RGBA',(width*scale,height*scale));d=ImageDraw.Draw(im)
 vw=float(root.get(A+'viewportWidth','24'));vh=float(root.get(A+'viewportHeight','24'))
 for path in root.iter('path'):
  pen=NativePathPen();parse_path(path.get(A+'pathData'),pen);pen.endPath()
  stroke=color(path.get(A+'strokeColor'),palette);fill=color(path.get(A+'fillColor'),palette)
  for points in pen.paths:
   mapped=[(x/vw*width*scale,y/vh*height*scale) for x,y in points]
   if fill:d.polygon(mapped,fill=fill)
   if stroke and len(mapped)>1:d.line(mapped,fill=stroke,width=max(1,round(float(path.get(A+'strokeWidth','1'))/vw*width*scale)),joint='curve')
 return im.resize((width,height),Image.Resampling.LANCZOS)

class Node:
 def __init__(self,e,scale=1):
  self.tag=e.tag;self.a={**STYLES.get(e.get('style','').split('/')[-1],{}),**{k.replace(A,''):v for k,v in e.attrib.items() if k.startswith(A)}}
  self.children=[Node(c,scale) for c in e];self.factor=scale;self.id=self.a.get('id','').split('/')[-1]
  self.x=self.y=self.w=self.h=0;self.lines=[]
 def pad(self):
  a=self.a;p=unit(a.get('padding'));h=unit(a.get('paddingHorizontal'),p);v=unit(a.get('paddingVertical'),p)
  return [unit(a.get('paddingLeft',a.get('paddingStart')),h),unit(a.get('paddingTop'),v),unit(a.get('paddingRight',a.get('paddingEnd')),h),unit(a.get('paddingBottom'),v)]
 def margins(self):
  a=self.a;h=unit(a.get('layout_marginHorizontal'));v=unit(a.get('layout_marginVertical'))
  return [unit(a.get('layout_marginStart'),h),unit(a.get('layout_marginTop'),v),unit(a.get('layout_marginEnd'),h),unit(a.get('layout_marginBottom'),v)]
 def text(self):
  values={'hecker_about_model':'Google Pixel 4 XL','hecker_about_version_line':'Version 1.0  ·  Android 17','hecker_about_android':'17','hecker_about_processor':'Snapdragon 855','hecker_about_community':'Made by Hecker · Credits & legal'}
  if self.id in values:return values[self.id]
  text=self.a.get('text','');return STRINGS.get(text.split('/')[-1],text)
 def measure(self,width):
  a=self.a;ww=a.get('layout_width','match_parent')
  self.w=unit(ww) if ww.endswith(('dp','px')) and unit(ww)>0 else width
  l,t,r,b=self.pad();aw=max(1,self.w-l-r)
  if self.tag=='TextView':
   self.f=font(unit(a.get('textSize','14sp'))*self.factor,False)
   self.compound=self.id=='hecker_about_community';cw=52*S if self.compound else 0
   self.lines=wrap(self.text(),max(1,aw-cw),self.f);self.lineh=sum(self.f.getmetrics());self.h=t+b+len(self.lines)*self.lineh
  elif self.tag=='ImageView':self.h=unit(a.get('layout_height','16dp'))
  elif self.tag=='View':self.h=unit(a.get('layout_height','1dp'))
  elif self.tag.endswith('GlowLayout'):
   for c in self.children:c.measure(aw);c.x=l;c.y=t
   self.h=t+b+max((c.h for c in self.children),default=0)
  elif a.get('orientation','vertical')=='horizontal':
   fixed=0;weight=0
   for c in self.children:
    ml,mt,mr,mb=c.margins();weight+=float(c.a.get('layout_weight',0));fixed+=ml+mr
    if not float(c.a.get('layout_weight',0)):fixed+=unit(c.a.get('layout_width','0dp'))
   remaining=max(0,aw-fixed);x=l;maxh=0
   for c in self.children:
    ml,mt,mr,mb=c.margins();cw=remaining*float(c.a.get('layout_weight',0))/weight if float(c.a.get('layout_weight',0)) else unit(c.a.get('layout_width','0dp'))
    c.measure(cw);c.x=x+ml;c.y=t+mt;x+=ml+c.w+mr;maxh=max(maxh,mt+c.h+mb)
   self.h=t+b+maxh
   for c in self.children:
    ml,mt,mr,mb=c.margins()
    if c.a.get('layout_height')=='match_parent':c.h=maxh-mt-mb
    if 'center_vertical' in a.get('gravity',''):c.y=t+mt+(maxh-mt-mb-c.h)/2
  else:
   y=t
   for c in self.children:
    ml,mt,mr,mb=c.margins();c.measure(aw-ml-mr);c.x=l+ml;c.y=y+mt;y+=mt+c.h+mb
   self.h=y+b
  hh=a.get('layout_height','wrap_content')
  if hh.endswith(('dp','px')):self.h=unit(hh)
  self.h=max(self.h,unit(a.get('minHeight')))
  return self
 def paint(self,im,x=0,y=0,light=False,skip_glow_text=False):
  x+=self.x;y+=self.y;a=self.a;palette=colors(light);d=ImageDraw.Draw(im)
  bg=a.get('background');fill=color(bg,palette)
  if bg=='@drawable/hecker_about_surface':d.rounded_rectangle((x,y,x+self.w,y+self.h),20*S,fill=palette['hecker_about_surface'])
  elif bg=='@drawable/hecker_about_hero':
   c0=np.array(ImageColorRGB(palette['hecker_about_hero_start']),float);c1=np.array(ImageColorRGB(palette['hecker_about_hero_end']),float)
   gradient=np.tile(np.linspace(0,1,max(1,round(self.w)))[None,:,None],(max(1,round(self.h)),1,1));data=np.uint8(c0+(c1-c0)*gradient)
   layer=Image.fromarray(data,'RGB').convert('RGBA');mask=Image.new('L',layer.size);ImageDraw.Draw(mask).rounded_rectangle((0,0,layer.width-1,layer.height-1),26*S,fill=255);layer.putalpha(mask);im.alpha_composite(layer,(round(x),round(y)))
  elif fill:d.rectangle((x,y,x+self.w,y+self.h),fill=fill)
  if self.tag=='TextView':
   l,t,r,b=self.pad();center='center' in a.get('gravity','');yy=y+t
   for n,line in enumerate(self.lines):
    extra=(24*S if n==0 else 0)+(24*S if n==len(self.lines)-1 else 0) if self.compound else 0
    start=x+(self.w-self.f.getlength(line)-extra)/2 if center else x+l
    xx=start+(24*S if self.compound and n==0 else 0)
    d.text((round(xx),round(yy)),line,font=self.f,fill=color(a.get('textColor','@color/hecker_about_text'),palette),anchor='lt')
    if self.compound and n==0:im.alpha_composite(icon('hecker_about_heart',16*S,16*S,light),(round(start),round(yy)))
    if self.compound and n==len(self.lines)-1:im.alpha_composite(icon('hecker_about_external',14*S,14*S,light),(round(xx+self.f.getlength(line)+8*S),round(yy)))
    yy+=self.lineh
  elif self.tag=='ImageView':im.alpha_composite(icon(a['src'].split('/')[-1],round(self.w),round(self.h),light),(round(x),round(y)))
  if skip_glow_text and self.tag.endswith('GlowLayout'):return
  for c in self.children:c.paint(im,x,y,light,skip_glow_text)

def ImageColorRGB(c):return tuple(int(c[i:i+2],16) for i in (1,3,5))
def prepare():
 probe=OUT/'GlowPreview.java';probe.write_text('''import org.hecker.motion.MediaGlowSpec;public class GlowPreview {public static void main(String[]args){StringBuilder b=new StringBuilder("[");for(int f=0;f<432;f++){if(f>0)b.append(',');float p=MediaGlowSpec.phase(Math.round(f*1000d/60));b.append('[');for(int i=0;i<3;i++){if(i>0)b.append(',');b.append('[').append(MediaGlowSpec.centerX(i,p)).append(',').append(MediaGlowSpec.centerY(i,p)).append(',').append(MediaGlowSpec.radius(i,p)).append(',').append(MediaGlowSpec.centerAlpha(i)).append(',').append(MediaGlowSpec.color(i)&0xffffff).append(']');}b.append(']');}System.out.println(b.append(']'));}}''')
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(OUT/'classes'),str(ROOT/'framework/qs/MediaGlowSpec.java'),str(probe)],check=True)
 return json.loads(subprocess.check_output(['java','-cp',str(OUT/'classes'),'GlowPreview']))

def setup(light=False,scale=1):
 node=Node(ET.parse(ROOT/'settings/res/layout/hecker_about_phone.xml').getroot(),scale).measure(W)
 node.y=87*S
 height=max(H,round(node.y+node.h))
 im=Image.new('RGBA',(W,height),colors(light)['hecker_about_background']);node.paint(im,light=light,skip_glow_text=True)
 hero=node.children[0];fg=Image.new('RGBA',im.size)
 for child in hero.children:child.paint(fg,hero.x+node.x,hero.y+node.y,light)
 d=ImageDraw.Draw(im);c=colors(light)['hecker_about_text']
 d.text((24*S,16*S),'9:41',font=font(11*S,True),fill=c,anchor='lt')
 d.line((26*S,66*S,34*S,66*S),fill=c,width=2);d.line((26*S,66*S,30*S,62*S),fill=c,width=2);d.line((26*S,66*S,30*S,70*S),fill=c,width=2)
 d.text((58*S,57*S),'About phone',font=font(19*S),fill=c,anchor='lt')
 d.ellipse((322*S,60*S,332*S,70*S),outline=c,width=2);d.line((330*S,68*S,334*S,72*S),fill=c,width=2)
 for i in range(4):d.rectangle((282*S+i*3*S,26*S-i*2*S,283*S+i*3*S,27*S),fill=c)
 d.arc((301*S,17*S,315*S,30*S),210,330,fill=c,width=2);d.arc((304*S,21*S,312*S,29*S),210,330,fill=c,width=2)
 d.rounded_rectangle((322*S,19*S,335*S,26*S),2,outline=c,width=2)
 return im,fg,(round(hero.x),round(node.y+hero.y),round(hero.w),round(hero.h))

def glow(base,fg,box,spec):
 x,y,w,h=box;rgb=np.array(base.crop((x,y,x+w,y+h)).convert('RGB'),dtype=np.float32)/255
 yy,xx=np.mgrid[0:h,0:w].astype(np.float32)
 def overlay(color,alpha):
  nonlocal rgb
  rgb=rgb*(1-alpha[...,None])+np.array(color)[None,None,:]/255*alpha[...,None]
 overlay((19,19,34),np.broadcast_to(np.linspace(0,.28,h)[:,None],(h,w)))
 for cx,cy,r,a,c in spec:
  distance=np.sqrt((xx-w*cx)**2+(yy-h*cy)**2)/max(1,w*r)
  alpha=np.where(distance<.5,a+(.10-a)*distance*2,.10*(2-2*distance));alpha=np.clip(alpha,0,1)
  overlay(((c>>16)&255,(c>>8)&255,c&255),alpha)
 overlay((179,222,255),np.broadcast_to(np.linspace(0,.10,h)[:,None],(h,w)))
 layer=Image.fromarray(np.uint8(np.clip(rgb,0,1)*255),'RGB').convert('RGBA');mask=Image.new('L',(w,h));ImageDraw.Draw(mask).rounded_rectangle((0,0,w-1,h-1),26*S,fill=255);layer.putalpha(mask)
 im=base.copy();im.alpha_composite(layer,(x,y));im.alpha_composite(fg);return im

def main():
 spec=prepare();base,fg,box=setup();normal=glow(base,fg,box,spec[0]);normal.save(OUT/'HeckerOS-About-Phone.png')
 for light,scale,name in [(True,1,'qa-light'),(False,2,'qa-font-2')]:
  b,f,bounds=setup(light,scale);glow(b,f,bounds,spec[120]).save(OUT/(name+'.png'))
 if '--stills' in sys.argv:return
 output=OUT/'HeckerOS-About-Phone-Glow.mp4';temporary=OUT/'about-rendering.mp4'
 proc=subprocess.Popen(['ffmpeg','-y','-v','error','-f','rawvideo','-pix_fmt','rgb24','-s',f'{W}x{H+44}','-r',str(FPS),'-i','-','-an','-c:v','libx264','-threads','4','-crf','18','-preset','fast','-pix_fmt','yuv420p','-movflags','+faststart',str(temporary)],stdin=subprocess.PIPE)
 try:
  for index in range(COUNT):
   canvas=Image.new('RGBA',(W,H+44),'#111115');im=glow(base,fg,box,spec[index]);canvas.alpha_composite(im.crop((0,0,W,H)))
   ImageDraw.Draw(canvas).text((W/2,H+22),'SOURCE PREVIEW · FONTS APPROXIMATE',font=font(13),fill='#a49bad',anchor='mm')
   proc.stdin.write(canvas.convert('RGB').tobytes())
  proc.stdin.close()
  if proc.wait():raise RuntimeError('Encoder failed')
 except BaseException:
  proc.terminate();proc.wait();raise
 temporary.replace(output);print(output)

if __name__=='__main__':main()
