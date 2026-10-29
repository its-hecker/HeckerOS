#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise actual pinned WM inset calculation and compile the native status-bar hook methods."""
from pathlib import Path
import re
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
WINDOW="frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/window/StatusBarWindowControllerImpl.java"
UTILS="frameworks/base/core/java/com/android/internal/policy/SystemBarUtils.java"
PHONE="frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/phone/PhoneStatusBarView.java"

def check_native_insets(folder):
    source=(folder/WINDOW).read_text()
    height=re.search(r"    private void applyHeight\(State state\) \{.*?\n    \}",source,re.S).group()
    util=(folder/UTILS).read_text()
    offset=re.search(r"    public static int getQuickQsOffsetHeight\(Context context\) \{.*?\n    \}",util,re.S).group()
    probe=r'''public class InsetsProbe {
static class ViewGroup {static class LayoutParams {static final int MATCH_PARENT=-1;int height;LayoutParams[] paramsForRotation;InsetsFrameProvider[] providedInsets;}}
static class Surface {static final int ROTATION_0=0,ROTATION_270=3;}
static class Insets {int top;static Insets of(int l,int t,int r,int b){Insets x=new Insets();x.top=t;return x;}}
static class InsetsFrameProvider {Insets value;void setInsetsSize(Insets i){value=i;}}
static class SystemBarUtils {static int[] heights=new int[4];static int getStatusBarHeightForRotation(Object c,int r){return heights[r];}}
static class State {boolean mIsLaunchAnimationRunning;}
static class Resources {int qs;int getDimensionPixelSize(int id){return qs;}}
static class Context {Resources resources=new Resources();int height;Resources getResources(){return resources;}}
static class R {static class dimen {static int quick_qs_offset_height;}}
static int getStatusBarHeight(Context c){return c.height;}
ViewGroup.LayoutParams mLpChanged=new ViewGroup.LayoutParams();int mBarHeight;Object mContext;
private Insets getInsets(int h){return Insets.of(0,h,0,0);}
''' + height + "\n" + offset + r'''
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[]args){
 InsetsProbe p=new InsetsProbe();State s=new State();int cases=0;
 for(int dp:new int[]{24,44,52,60})for(float density:new float[]{1,1.5f,2.5f,3,3.5f})
 for(boolean launch:new boolean[]{false,true})for(boolean cutout:new boolean[]{false,true}){
  p.mBarHeight=Math.round(dp*density);s.mIsLaunchAnimationRunning=launch;
  p.mLpChanged.paramsForRotation=new ViewGroup.LayoutParams[4];
  for(int r=0;r<4;r++){
   SystemBarUtils.heights[r]=Math.max(p.mBarHeight,cutout?(int)((32+r*3)*density):0);
   ViewGroup.LayoutParams lp=new ViewGroup.LayoutParams();lp.providedInsets=new InsetsFrameProvider[]{
      new InsetsFrameProvider(),new InsetsFrameProvider(),new InsetsFrameProvider()};
   p.mLpChanged.paramsForRotation[r]=lp;
  }
  p.applyHeight(s);check(p.mLpChanged.height==(launch?-1:p.mBarHeight));
  for(int r=0;r<4;r++){var lp=p.mLpChanged.paramsForRotation[r];
   check(lp.height==(launch?-1:SystemBarUtils.heights[r]));
   for(var provider:lp.providedInsets)check(provider.value.top==SystemBarUtils.heights[r]);
   Context context=new Context();context.height=SystemBarUtils.heights[r];context.resources.qs=Math.round(32*density);
   check(getQuickQsOffsetHeight(context)==Math.max(context.height,context.resources.qs));cases++;
  }
 }
 System.out.println("Actual pinned WM/app/status-bar insets and QQS offsets: "+cases+" height/density/rotation/cutout/launch cases PASS");
}
}'''
    with tempfile.TemporaryDirectory(prefix="hecker-status-insets-") as temp:
        output=Path(temp);java=output/"InsetsProbe.java";java.write_text(probe)
        subprocess.run(["java","-m","jdk.compiler/com.sun.tools.javac.Main","-d",str(output),str(java)],check=True)
        subprocess.run(["java","-cp",str(output),"InsetsProbe"],check=True)

def hook_probe(source):
    """Three new override methods, taken from the actual prepared native PhoneStatusBarView."""
    methods=[]
    for name in ["onMeasure","onLayout","onVisibilityAggregated"]:
        found=re.findall(r"    @Override\n    (?:public|protected) void "+name+r"\([^)]*\) \{.*?\n    \}",source,re.S)
        if len(found)!=1:raise AssertionError("Invalid native hook count: "+name)
        methods.append(found[0])
    assert source.count("HeckerStatusBarRows.attach(this)")==1
    assert source.count("HeckerStatusBarRows.detach(this)")==1
    return """package com.android.systemui.statusbar.phone;
public class HeckerRowsNativeProbe extends android.widget.FrameLayout {
 public HeckerRowsNativeProbe(android.content.Context c){super(c);}
"""+"\n".join(methods)+"\n}\n"

def check_overlays(sdk):
    with tempfile.TemporaryDirectory(prefix="hecker-status-rro-") as temp:
        for name in ["Compact","Balanced","Tall"]:
            source=ROOT/"rom/overlays/StatusBarHeight"/name
            output=Path(temp)/name;output.mkdir()
            subprocess.run([str(sdk/"aapt2"),"compile","--dir",str(source/"res"),"-o",str(output/"res.zip")],check=True)
            subprocess.run([str(sdk/"aapt2"),"link","--auto-add-overlay","-I",str(sdk/"android.jar"),
                "--manifest",str(source/"AndroidManifest.xml"),"--min-sdk-version","31","-o",str(output/"height.apk"),
                str(output/"res.zip")],check=True)
    print("All three native framework height RRO manifests and dimension resources compile: PASS")
