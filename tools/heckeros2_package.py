#!/usr/bin/env python3
"""Package the chosen HeckerOS intro and a continuously oscillating loading pill."""
from pathlib import Path
import cv2, numpy as np, math, zipfile, subprocess, hashlib

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'bootanimation'/'build'
OUT.mkdir(parents=True,exist_ok=True)
SOURCE=OUT/'heckeros_squid_style_preview.mp4'
OUTPUT=OUT/'HeckerOS_2.0_Infinite_Bootanimation.zip'
PREVIEW=OUT/'HeckerOS_2.0_Infinite_Bar_Preview.mp4'
W,H=1080,2280
FPS=24
INTRO_N=90
LOOP_N=72
PX,PY=60,870
BAR_Y=1270
BAR_X0=435; BAR_X1=645
BAR_LENGTH=46

cap=cv2.VideoCapture(str(SOURCE))
if not cap.isOpened():
    raise RuntimeError('Missing geometric intro preview')
assert int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))==960 and int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))==540
base_frames=[]
for fi in range(108):
    ok,frame=cap.read()
    if not ok:break
    base_frames.append(frame)
cap.release()
assert len(base_frames)>=108, f'Only {len(base_frames)} input frames'
logo_frame=base_frames[107].copy()

def bar(frame,loc,alpha=1.0):
    if alpha<=0:return
    a=np.clip(alpha,0,1)
    col=tuple(int(c*a) for c in (31,31,32))
    cv2.line(frame,(BAR_X0,BAR_Y),(BAR_X1,BAR_Y),col,2,cv2.LINE_AA)
    cv2.circle(frame,(BAR_X0,BAR_Y),1,col,-1,cv2.LINE_AA)
    cv2.circle(frame,(BAR_X1,BAR_Y),1,col,-1,cv2.LINE_AA)
    x0=BAR_X0+round(loc*(BAR_X1-BAR_X0-BAR_LENGTH))
    x1=x0+BAR_LENGTH
    glow=tuple(int(c*a) for c in (28,39,71))
    cv2.line(frame,(x0,BAR_Y),(x1,BAR_Y),glow,8,cv2.LINE_AA)
    pink=tuple(int(c*a) for c in (106,60,246))
    cv2.line(frame,(x0,BAR_Y),(x1,BAR_Y),pink,4,cv2.LINE_AA)
    for x in (x0,x1):
        cv2.circle(frame,(x,BAR_Y),2,pink,-1,cv2.LINE_AA)
    highlight=tuple(int(c*a) for c in (174,171,253))
    cv2.line(frame,(x0+7,BAR_Y),(x1-7,BAR_Y),highlight,1,cv2.LINE_AA)

def to_full(crop):
    canvas=np.zeros((H,W,3),dtype=np.uint8)
    canvas[PY:PY+540,PX:PX+960,:]=crop
    return canvas

preview_frames=INTRO_N+LOOP_N*2
command=['ffmpeg','-y','-hide_banner','-loglevel','error','-f','rawvideo',
         '-pix_fmt','bgr24','-s','540x1140','-r',str(FPS),'-i','-',
         '-an','-c:v','libx264','-preset','veryfast','-crf','19',
         '-pix_fmt','yuv420p','-movflags','+faststart',str(PREVIEW)]
proc=subprocess.Popen(command,stdin=subprocess.PIPE,stderr=subprocess.PIPE)
with zipfile.ZipFile(OUTPUT,'w',compression=zipfile.ZIP_STORED,allowZip64=True) as z:
    z.writestr('desc.txt',f'{W} {H} {FPS}\np 1 0 part0\np 0 0 part1\n')
    for j in range(preview_frames):
        if j<INTRO_N:
            frame=to_full(base_frames[j])
            fade=min(1,max(0,(j-75)/14))
            bar(frame,0,fade)
        else:
            frame=to_full(logo_frame)
            phase=((j-INTRO_N)%LOOP_N)/LOOP_N
            loc=0.5-0.5*math.cos(2*math.pi*phase)
            bar(frame,loc)
        if j<INTRO_N+LOOP_N:
            part='part0' if j<INTRO_N else 'part1'
            local=j if j<INTRO_N else j-INTRO_N
            ok,png=cv2.imencode('.png',frame,[cv2.IMWRITE_PNG_COMPRESSION,3])
            if not ok:raise ValueError('PNG encode failure')
            z.writestr(f'{part}/{local:04d}.png',png.tobytes())
        reduced=cv2.resize(frame,(540,1140),interpolation=cv2.INTER_AREA)
        proc.stdin.write(reduced.tobytes())
proc.stdin.close()
err=proc.stderr.read().decode('utf-8','replace')
if proc.wait()!=0:raise RuntimeError('FFmpeg encoding failed: '+err)
print('HeckerOS 2.0 bootanimation generated:',OUTPUT.stat().st_size,'bytes')
