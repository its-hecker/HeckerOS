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
    # Lottie paints shape groups in reverse array order. Author back-to-front,
    # then reverse once so opaque cards cannot cover their own controls.
    layers=[layer(1,list(reversed(shapes)),pulse=True),*extra]
    d={'v':'5.7.4','fr':30,'ip':0,'op':120,'w':320,'h':200,'nm':'Hecker '+name,'ddd':0,'assets':[],'layers':layers}
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
            s += [group(rect(-34,-39,59,60,navy,14)),group(rect(34,-39,59,60,violet,14))]
            for x,y,c in [(-48,-52,cyan),(-20,-52,white),(-48,-24,cyan),(-20,-24,violet)]:s += [group(circle(x,y,19,c))]
            s += [group(rect(28,-51,24,20,cyan,5)),group(line([[23,-20],[35,-20]],white,3))]
            for x in (22,52):s += [group(rect(x,23,21,62,white,10)),group(rect(x,7,21,30,cyan if x==22 else violet,10))]
            s += [group(circle(-49,8,24,white)),group(circle(-17,8,24,cyan)),group(rect(-34,40,57,23,violet,11))]
            for x in (-48,-16,16,48):s += [group(circle(x,69,18,white))]
        write('qs_'+kind,s)
    game = [group(rect(0,0,252,166,navy,22)),group(rect(-53,-57,108,25,[.13,.25,.36,1],12))]
    # FPS pill, live-looking chart, quick actions and two matching sliders.
    for points in [[[-94,-51],[-94,-64],[-85,-64]],[[-94,-58],[-87,-58]],
                   [[-79,-51],[-79,-64],[-70,-64],[-70,-57],[-79,-57]],
                   [[-55,-64],[-63,-64],[-63,-58],[-55,-58],[-55,-51],[-63,-51]]]:
        game += [group(line(points,white,2))]
    game += [group(rect(-25,-57,17,10,cyan,3)),group(rect(70,-57,63,9,violet,4))]
    game += [group(rect(-25,-10,174,50,[.05,.10,.17,1],12)),group(line([[-99,3],[-77,-6],[-60,-2],[-42,-18],[-20,-9],[0,-25],[20,-14],[47,-23]],cyan,3))]
    for x,c in [(-89,cyan),(-47,violet),(-5,white),(37,cyan)]:game += [group(rect(x,43,29,27,c,9))]
    for x,c in [(84,cyan),(108,violet)]:
        game += [group(rect(x,15,14,88,[.15,.23,.33,1],7)),group(rect(x,34,14,50,c,7))]
    # Recognizable game controller attached to the dashboard, instead of bubbles.
    game += [group(rect(-8,77,92,35,[.19,.28,.42,1],15)),group(line([[-39,77],[-19,77]],white,4)),group(line([[-29,67],[-29,87]],white,4)),group(circle(17,72,7,violet)),group(circle(28,82,7,cyan))]
    scanning=group(rect(-24,-10,3,34,white,1));scanning['it'][-1]['p']={'a':1,'k':[{'t':0,'s':[-65,0],'e':[65,0],'o':{'x':[.33],'y':[0]},'i':{'x':[.67],'y':[1]}},{'t':60,'s':[65,0],'e':[-65,0],'o':{'x':[.33],'y':[0]},'i':{'x':[.67],'y':[1]}},{'t':120,'s':[-65,0]}]}
    write('gaming',game+[scanning])
    lyrics=[group(rect(0,0,118,178,navy,24)),group(rect(0,-65,31,6,white,3)),group(rect(0,-25,57,51,violet,12)),group(line([[-8,-13],[-8,-37],[13,-41],[13,-18]],white,4)),group(circle(-13,-12,11,white)),group(circle(8,-17,11,white))]
    for y,w,c in [(18,74,white),(36,88,cyan),(54,68,white),(70,41,violet)]:lyrics += [group(rect(0,y,w,5,c,3))]
    highlight=group(rect(0,36,92,14,cyan,5));highlight['it'][1]['o']=motion(8,28)
    write('lyrics',lyrics+[highlight])
    scanner=[group(rect(0,0,146,152,navy,34)),group(line([[-41,-43],[0,-61],[41,-43],[37,20],[0,55],[-37,20],[-41,-43]],cyan,4)),group(circle(0,-4,40,violet)),group(circle(0,-4,20,navy)),group(circle(0,-4,7,white))]
    beam=group(rect(0,-4,58,3,white,1));beam['it'][-1]['p']={'a':1,'k':[{'t':0,'s':[0,-32],'e':[0,34],'o':{'x':[.33],'y':[0]},'i':{'x':[.67],'y':[1]}},{'t':60,'s':[0,34],'e':[0,-32],'o':{'x':[.33],'y':[0]},'i':{'x':[.67],'y':[1]}},{'t':120,'s':[0,-32]}]}
    write('applock',scanner+[beam],[layer(2,[group(circle(86,-42,7,cyan)),group(circle(-86,42,7,violet))],rotate=True)])

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
