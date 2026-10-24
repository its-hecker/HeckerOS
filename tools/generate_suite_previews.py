#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Original, offline vector Lottie illustrations for native Suite settings."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def fixed(v): return {'a':0,'k':v}
def motion(a,b):
    return {'a':1,'k':[{'t':0,'s':[a],'e':[b],'o':{'x':[.33],'y':[0]},'i':{'x':[.67],'y':[1]}},{'t':60,'s':[b],'e':[a],'o':{'x':[.33],'y':[0]},'i':{'x':[.67],'y':[1]}},{'t':120,'s':[a]}]}
cyan=[.25,.83,.98,1];violet=[.67,.53,1,1];white=[.91,.96,1,1];navy=[.08,.15,.24,1]
def fill(c):return {'ty':'fl','c':fixed(c),'o':fixed(100),'r':1}
def rect(x,y,w,h,c,r=12):return [{'ty':'rc','d':1,'s':fixed([w,h]),'p':fixed([x,y]),'r':fixed(r)},fill(c)]
def circle(x,y,w,c):return [{'ty':'el','d':1,'s':fixed([w,w]),'p':fixed([x,y])},fill(c)]
def line(points,c,width=5):return [{'ty':'sh','ks':fixed({'i':[[0,0]]*len(points),'o':[[0,0]]*len(points),'v':points,'c':False})},{'ty':'st','c':fixed(c),'o':fixed(100),'w':fixed(width),'lc':2,'lj':2}]
def group(shapes):return {'ty':'gr','it':shapes+[{'ty':'tr','p':fixed([0,0]),'a':fixed([0,0]),'s':fixed([100,100]),'r':fixed(0),'o':fixed(100)}]}
def layer(n,shapes,rotate=False,pulse=False):return {'ddd':0,'ind':n,'ty':4,'nm':'Hecker vector '+str(n),'sr':1,'ks':{'o':fixed(100),'r':{'a':1,'k':[{'t':0,'s':[0],'e':[360],'o':{'x':[0],'y':[0]},'i':{'x':[1],'y':[1]}},{'t':120,'s':[360]}]} if rotate else fixed(0),'p':fixed([160,100,0]),'a':fixed([0,0,0]),'s':{'a':1,'k':[{'t':0,'s':[96,96,100],'e':[104,104,100],'o':{'x':[.33],'y':[0]},'i':{'x':[.67],'y':[1]}},{'t':60,'s':[104,104,100],'e':[96,96,100],'o':{'x':[.33],'y':[0]},'i':{'x':[.67],'y':[1]}},{'t':120,'s':[96,96,100]}]} if pulse else fixed([100,100,100])},'ao':0,'shapes':shapes,'ip':0,'op':120,'st':0,'bm':0}
def write(name,shapes,extra=()):
    d={'v':'5.7.4','fr':30,'ip':0,'op':120,'w':320,'h':200,'nm':'Hecker '+name,'ddd':0,'assets':[],'layers':[layer(1,shapes,pulse=True),*extra]}
    (ROOT/'app/res/raw'/('suite_'+name+'.json')).write_text(json.dumps(d,separators=(',',':'))+'\n')
def generate():
    for kind in ('oneui','hecker'):
        s=[group(rect(0,0,156,176,navy,24))]
        if kind=='oneui':
            for x in (-34,34):s+=[group(rect(x,-48,59,26,cyan if x<0 else violet))]
            for y in (-8,19):
                for x in (-48,-16,16,48):s+=[group(circle(x,y,18,white if x<0 else violet))]
            for y in (48,67):s+=[group(rect(0,y,116,12,white,6)),group(rect(-24,y,68,12,cyan,6))]
        else:
            s+=[group(rect(-29,-28,66,67,violet,16)),group(rect(-29,25,66,26,cyan))]
            for x in (21,49):s+=[group(rect(x,-18,18,90,white,9)),group(rect(x,0,18,54,cyan if x==21 else violet,9))]
            for x in (-48,-16,16,48):s+=[group(circle(x,57,18,white))]
        write('qs_'+kind,s)
    lock=[group(rect(0,10,80,64,navy,20)),group(line([[-21,-12],[-21,-37],[0,-49],[21,-37],[21,-12]],cyan,7)),group(circle(0,8,12,violet)),group(rect(0,20,5,19,violet,3))]
    write('lock',lock,[layer(2,[group(circle(68,-48,7,cyan)),group(circle(-68,48,7,violet))],rotate=True)])
    status=[]
    for x,h in [(-50,16),(-32,32),(-14,48)]:status+=[group(rect(x,22-h/2,10,h,cyan,5))]
    status+=[group(rect(38,0,58,32,violet,9)),group(rect(70,0,6,15,violet,2)),group(rect(31,0,32,18,white,4))]
    write('status',status)
    headphones=[group(line([[-52,12],[-52,-21],[-35,-48],[0,-60],[35,-48],[52,-21],[52,12]],violet,9)),group(rect(-51,18,25,50,cyan)),group(rect(51,18,25,50,cyan))]
    for x,h in [(-22,18),(-7,42),(8,30),(23,16)]:headphones+=[group(rect(x,18,6,h,white,3))]
    write('headphones',headphones)
    shield=[group(line([[-47,-50],[0,-67],[47,-50],[44,16],[0,61],[-44,16],[-47,-50]],cyan,6)),group(rect(0,0,40,39,navy,10)),group(line([[-15,0],[-3,12],[21,-15]],violet,7))]
    write('sandbox',shield)
    write('integrity',shield,[layer(2,[group(circle(66,0,6,violet))],rotate=True)])
    capture=[group(rect(0,0,132,86,navy,22)),group(circle(0,0,44,violet)),group(circle(0,0,24,cyan)),group(rect(-33,-47,41,15,cyan,6))]
    for x,y in [(-78,-55),(78,-55),(-78,55),(78,55)]:capture+=[group(line([[x,y*.7],[x,y],[x*.8,y]],white,3))]
    write('capture',capture)
    bolt=group(line([[7,-39],[-16,0],[12,0],[-7,39]],white,8))
    dots=[group(circle(0,-65,9,cyan)),group(circle(65,0,7,violet)),group(circle(0,65,9,violet)),group(circle(-65,0,7,cyan))]
    ring=[{'ty':'el','d':1,'s':fixed([122,122]),'p':fixed([0,0])},{'ty':'st','c':fixed(cyan),'o':fixed(55),'w':fixed(3),'lc':2,'lj':2}]
    write('charge',[group(ring),bolt],[layer(2,dots,rotate=True)])
if __name__=='__main__':generate()
