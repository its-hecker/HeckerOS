#!/usr/bin/env python3
"""Pin the generated bootanimation into the existing checksummed ROM asset pipeline."""
from pathlib import Path
import hashlib, json, zipfile

ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'bootanimation'
SOURCE=FOLDER/'build/HeckerOS_2.0_Infinite_Bootanimation.zip'
STAGED=FOLDER/'bootanimation.zip'
NAME='heckeros2-geometric.zip'

def digest(data):
    return hashlib.sha256(data).hexdigest()

# The existing ROM build validator expects 5-digit PNG names.
# Repackaging changes only ZIP member names, never rendered frame pixels.
with zipfile.ZipFile(SOURCE) as src, zipfile.ZipFile(STAGED,'w',compression=zipfile.ZIP_STORED) as dst:
    names=src.namelist()
    assert len(names)==163 and len(names)==len(set(names))
    for entry in names:
        if entry=='desc.txt':
            dst.writestr(entry,src.read(entry))
        else:
            folder,file=entry.split('/')
            number=int(file[:-4])
            dst.writestr(f'{folder}/{number:05d}.png',src.read(entry))
raw=STAGED.read_bytes()
assert len(raw)>1000000
with zipfile.ZipFile(STAGED) as z:
    assert z.read('desc.txt')==b'1080 2280 24\np 1 0 part0\np 0 0 part1\n'
    assert all(x.compress_type==zipfile.ZIP_STORED for x in z.infolist())
for old in (FOLDER/'source').glob('terminal-boot.zip.*'):
    old.unlink()
chunks=[]
for i in range(0,len(raw),8*1024*1024):
    piece=raw[i:i+8*1024*1024]
    path=FOLDER/'source'/f'{NAME}.{i//(8*1024*1024):03d}'
    path.write_bytes(piece)
    chunks.append({'file':f'source/{path.name}','size':len(piece),'sha256':digest(piece)})
metadata={
 'source_filename':'HeckerOS_2.0_Infinite_Bootanimation.zip',
 'sha256':digest(raw),'size':len(raw),
 'resolution':[1080,2280],'fps':24,
 'intro_frames':90,'loop_frames':72,
 'parts':[
   {'type':'p','repeat':1,'pause':0,'path':'part0','frames':90},
   {'type':'p','repeat':0,'pause':0,'path':'part1','frames':72}
 ],
 'chunks':chunks
}
(FOLDER/'payload.json').write_text(json.dumps(metadata,indent=2)+'\n')
(FOLDER/'preview-coral.mp4').write_bytes((FOLDER/'build/HeckerOS_2.0_Infinite_Bar_Preview.mp4').read_bytes())
(FOLDER/'README.md').write_text("""# HeckerOS 2.0 geometric bootanimation

The selected visual is the minimal HeckerOs geometric/glitch intro (ivory + pink),
followed by a smooth, indeterminate pink loading pill moving endlessly left to right
and back. Black background, centered lettering, no fake progress percentage.

Native viewport: **1080 x 2280**, **24 fps**. `part0` has 90 one-time intro
frames (**3.75s**); `part1` has 72 frames and repeats (**3s** per smooth cycle)
until Android exits bootanimation. `p 1` allows Android to end the intro early
if boot is already ready. It never imposes a fixed boot time.

The approved frames are preserved in checksum-locked ZIP chunks in `source/`;
`python3 tools/build_bootanimation.py --check` reconstructs and checks PNG
size, frame completeness, ZIP stored compression, SHA-256, and descriptor.
Build preparation never re-renders artwork. `preview-coral.mp4` previews the
intro and two loading loops.

`tools/heckeros2_logo_source.py`, `tools/heckeros2_package.py`, and
`tools/heckeros2_lock_payload.py` are provenance/recreation tools, not run
on Android or during normal ROM build. The feature is in `product/media` via
the existing `bootanimation_heckeros` prebuilt, shared by coral and flame.
""")
print('payload SHA256:',metadata['sha256'],'bytes:',metadata['size'],'chunks:',len(chunks))
