#!/usr/bin/env python3
"""Recreate the approved compact HeckerOS geometric logo intro. Source artwork only."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import numpy as np, cv2, math, subprocess, random, os

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT / "bootanimation" / "build"
OUT.mkdir(parents=True,exist_ok=True)
W,H=960,540
FPS=24; DUR=7.5; FRAMES=int(FPS*DUR)
PALE=(236,237,222)
PINK=(247,56,111)
FAINT=(75,76,76)
GREEN=(62,185,153)
BG=(1,2,2)
font_path = Path('/usr/share/fonts/truetype/lato/Lato-Light.ttf')
if not font_path.exists():
    raise FileNotFoundError('Lato Light is required to reproduce the approved HeckerOS geometric lettering')
font=ImageFont.truetype(str(font_path),100)
text='HeckerOs'
tracking=4
advances=[font.getlength(c) for c in text]
total=sum(advances)+tracking*(len(text)-1)
x0=(W-total)/2
bbox=font.getbbox(text)
text_y=H/2-(bbox[1]+bbox[3])/2
mask=Image.new('L',(W,H),0)
char_masks=[]
x=x0
for i,c in enumerate(text):
    im=Image.new('L',(W,H),0)
    d=ImageDraw.Draw(im)
    d.text((x,text_y),c,font=font,fill=255,stroke_width=0)
    char_masks.append(np.array(im,dtype=np.uint8))
    x+=advances[i]+tracking
maska=np.maximum.reduce(char_masks)
logo=np.zeros((H,W,3),np.uint8)
for i,m in enumerate(char_masks):
    logo[m>0]=(PINK if i==6 else PALE)
segments=[]
rng=random.Random(201311)
for idx,m in enumerate(char_masks):
    ys,xs=np.where(m>10)
    if not len(xs): continue
    top,bottom=int(ys.min()),int(ys.max())+1
    left,right=int(xs.min()),int(xs.max())+1
    bands=4 if idx not in (0,6) else 5
    for b in range(bands):
        y1=top+(bottom-top)*b//bands
        y2=top+(bottom-top)*(b+1)//bands
        m2=m[y1:y2,left:right].copy()
        if m2.max()==0:continue
        dx=rng.choice([-1,1])*rng.uniform(35,140)
        dy=rng.uniform(-55,55)
        delay=rng.uniform(0,.49)
        segments.append((idx,left,y1,m2,dx,dy,delay))

def ease(x):
    x=max(0.,min(1.,x))
    return x*x*(3-2*x)

def draw_polyline(draw,points,t,color,width):
    if t<=0:return
    lens=[math.dist(points[i],points[i+1]) for i in range(len(points)-1)]
    remaining=sum(lens)*min(t,1)
    for i,l in enumerate(lens):
        if remaining<=0:break
        p0=points[i];p1=points[i+1]
        amt=min(1,remaining/l)
        q=(p0[0]+(p1[0]-p0[0])*amt, p0[1]+(p1[1]-p0[1])*amt)
        draw.line([p0,q],fill=color,width=width)
        remaining-=l

def overlay_glitch(arr,t,amount,seed):
    if amount<=.001:return
    r=random.Random(seed)
    n=int(17*amount)
    ybase=H/2
    for i in range(n):
        x=int(W/2+r.uniform(-255,255))
        y=int(ybase+r.uniform(-85,85))
        ln=r.randint(3,48)
        col=[PALE,PINK,GREEN][r.choices(range(3),weights=[6,3,1])[0]]
        val=.4+.6*r.random()
        cv2.line(arr,(x,y),(x+ln,y),tuple(int(z*val) for z in col),r.choice([1,2,2]),cv2.LINE_AA)
    if r.random()<amount*.64:
        x=int(W/2+r.uniform(-205,200)); y=int(H/2+r.uniform(-40,38))
        cv2.line(arr,(x,y),(x+r.randrange(45,130),y),tuple(int(c*.8) for c in PINK),2,cv2.LINE_AA)

writer=cv2.VideoWriter(str(OUT/'heckeros_squid_style_preview_tmp.mp4'),cv2.VideoWriter_fourcc(*'mp4v'),FPS,(W,H))
if not writer.isOpened():
    raise RuntimeError('OpenCV mp4v video writer unavailable')
for fi in range(FRAMES):
    t=fi/FPS
    arr=np.zeros((H,W,3),np.uint8)
    if t<2.10:
        fade=ease((t-.05)/.38)*(1-ease((t-1.62)/.40))
        if fade>0:
            icon=Image.new('RGB',(W,H),(0,0,0)); d=ImageDraw.Draw(icon)
            xx,yy=W//2,H//2
            ivory=tuple(int(c*fade) for c in PALE); pink=tuple(int(c*fade) for c in PINK)
            draw_polyline(d,[(xx-40,yy-67),(xx-40,yy+67)],ease((t-.10)/.50),ivory,6)
            draw_polyline(d,[(xx+40,yy+67),(xx+40,yy-67)],ease((t-.30)/.55),ivory,6)
            draw_polyline(d,[(xx-39,yy),(xx+39,yy)],ease((t-.52)/.46),pink,6)
            if t>.75:
                q=ease((t-.75)/.31)
                d.line([(xx-59,yy-83),(xx-59+int(30*q),yy-83)],fill=pink,width=2)
                d.line([(xx+40,yy+82),(xx+40+int(30*q),yy+82)],fill=ivory,width=2)
            icon=np.asarray(icon)
            if t>1.55:
                intensity=ease((t-1.55)/.40)
                temp=icon.copy()
                for iy in range(H//2-79,H//2+79,11):
                    h=rng.randint(2,6)
                    off=int(math.sin(iy*.25+t*17)*31*intensity)
                    temp[iy:iy+h]=np.roll(icon[iy:iy+h],off,axis=1)
                icon=temp
            arr=np.maximum(arr,icon)
    if t>1.80:
        if t>=3.68:
            out=logo.copy()
        else:
            out=np.zeros_like(arr)
            for i,left,top,sub,dx,dy,delay in segments:
                progress=ease((t-(1.84+delay))/(1.05))
                if progress<=0:continue
                x=int(left+dx*(1-progress)); y=int(top+dy*(1-progress))
                sh,sw=sub.shape
                cx0=max(x,0);cy0=max(y,0);cx1=min(x+sw,W);cy1=min(y+sh,H)
                if cx0>=cx1 or cy0>=cy1: continue
                s=sub[cy0-y:cy1-y,cx0-x:cx1-x].astype(np.float32)/255
                opacity=(.2+.8*ease(progress*1.5))
                col=np.array(PINK if i==6 else PALE)*opacity
                pixels=(s[:,:,None]*col).astype(np.uint8)
                out[cy0:cy1,cx0:cx1]=np.maximum(out[cy0:cy1,cx0:cx1],pixels)
        if t>=3.35:
            strength=ease((t-3.35)/.28)
            out=cv2.addWeighted(out,1-strength,logo,strength,0)
        outro=1-ease((t-6.18)/.83)
        if t>6.18:
            intensity=ease((t-6.18)/.55)
            out=(out.astype(np.float32)*outro).astype(np.uint8)
            for iy in range(H//2-70,H//2+70,9):
                off=int(math.sin((iy*31+fi*7)*.145)*65*intensity)
                out[iy:iy+3]=np.roll(out[iy:iy+3],off,axis=1)
        arr=np.maximum(arr,out)
    glitch=(ease((t-1.50)/.24)*(1-ease((t-3.35)/.30)))*1.0
    glitch+=ease((t-6.13)/.3)*(1-ease((t-7.05)/.15))*.57
    overlay_glitch(arr,t,min(glitch,1.0),fi*13+80)
    if 2.12<t<3.25 and fi%13 in (1,2):
        base=H//2+int(36*math.sin(fi*2.3))
        arr[base:base+2]=np.roll(arr[base:base+2],7 if fi%2 else -8,axis=1)
    writer.write(cv2.cvtColor(arr,cv2.COLOR_RGB2BGR))
    if abs(t-4.5)<1/FPS/2:
        cv2.imwrite(str(OUT/'heckeros_squid_style_still.png'),cv2.cvtColor(arr,cv2.COLOR_RGB2BGR))
writer.release()
subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(OUT/'heckeros_squid_style_preview_tmp.mp4'),'-an','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(OUT/'heckeros_squid_style_preview.mp4')],check=True)
os.remove(OUT/'heckeros_squid_style_preview_tmp.mp4')
print('Generated original HeckerOS geometric logo preview')
