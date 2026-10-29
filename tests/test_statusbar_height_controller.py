# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
class StatusBarHeightTests(unittest.TestCase):
    def test_atomic_builtin_overlays_and_user_state(self):
        sources={
"android/content/Context.java":"""package android.content;public class Context{public android.content.om.OverlayManager manager=new android.content.om.OverlayManager();public java.util.Map<String,Integer> values=new java.util.HashMap<>();public <T>T getSystemService(Class<T> c){return c.cast(manager);}public Object getContentResolver(){return this;}}""",
"android/os/UserHandle.java":"package android.os;public class UserHandle{public static final int USER_SYSTEM=0;public static UserHandle of(int id){UserHandle h=new UserHandle();h.id=id;return h;}public int id;public int getIdentifier(){return id;}}",
"android/os/Process.java":"package android.os;public class Process{public static int user;public static UserHandle myUserHandle(){UserHandle h=new UserHandle();h.id=user;return h;}}",
"android/content/om/OverlayIdentifier.java":"package android.content.om;public class OverlayIdentifier{public String name;public OverlayIdentifier(String n){name=n;}}",
"android/content/om/OverlayInfo.java":"package android.content.om;public class OverlayInfo{public boolean enabled;public boolean isEnabled(){return enabled;}public String getTargetPackageName(){return \"android\";}}",
"android/content/om/OverlayManagerTransaction.java":"""package android.content.om;public class OverlayManagerTransaction{
public java.util.List<String> names=new java.util.ArrayList<>();public java.util.List<Boolean> enabled=new java.util.ArrayList<>();public java.util.List<Integer> users=new java.util.ArrayList<>();
public static class Builder{OverlayManagerTransaction tx=new OverlayManagerTransaction();public Builder setEnabled(OverlayIdentifier id,boolean v,int user){tx.names.add(id.name);tx.enabled.add(v);tx.users.add(user);return this;}public OverlayManagerTransaction build(){return tx;}}
}""",
"android/content/om/OverlayManager.java":"""package android.content.om;public class OverlayManager{
public java.util.Map<String,Boolean> enabled=new java.util.HashMap<>();public boolean failOnce,missing;
public OverlayInfo getOverlayInfo(String n,android.os.UserHandle u){if(missing)return null;OverlayInfo i=new OverlayInfo();i.enabled=enabled.getOrDefault(u.id+":"+n,false);return i;}
public void commit(OverlayManagerTransaction t){if(failOnce){failOnce=false;throw new IllegalStateException();}
for(int i=0;i<t.names.size();i++)enabled.put(t.users.get(i)+":"+t.names.get(i),t.enabled.get(i));}
}""",
"android/provider/Settings.java":"""package android.provider;public class Settings{public static class Secure{public static boolean blocked;
static String key(String k){return android.os.Process.user+":"+k;}
public static int getInt(Object c,String k,int d){return ((android.content.Context)c).values.getOrDefault(key(k),d);}
public static int getIntForUser(Object c,String k,int d,int user){return ((android.content.Context)c).values.getOrDefault(user+":"+k,d);}
public static boolean putInt(Object c,String k,int v){if(blocked)return false;((android.content.Context)c).values.put(key(k),v);return true;}
}}""",
"org/hecker/suite/HeightProbe.java":"""package org.hecker.suite;import android.content.Context;
public class HeightProbe{
static void check(boolean b){if(!b)throw new AssertionError();}
static boolean on(Context c,int u,String name){return c.manager.enabled.getOrDefault(u+":org.hecker.statusbar.height."+name,false);}
public static void main(String[]args){
 Context c=new Context();c.manager.enabled.put("0:other.overlay",true);
 check(StatusBarHeightController.apply(c,true,1));check(on(c,0,"balanced")&&!on(c,0,"compact")&&!on(c,0,"tall"));
 check(c.manager.enabled.get("0:other.overlay"));check(c.values.get("0:hecker_dual_statusbar")==1);
 c.manager.failOnce=true;check(!StatusBarHeightController.apply(c,true,2));
 check(on(c,0,"balanced")&&!on(c,0,"tall")&&c.values.get("0:hecker_dual_statusbar_height")==1);
 android.provider.Settings.Secure.blocked=true;check(!StatusBarHeightController.apply(c,true,2));check(on(c,0,"balanced"));
 android.provider.Settings.Secure.blocked=false;
 check(StatusBarHeightController.apply(c,false,2));check(!on(c,0,"balanced")&&!on(c,0,"tall"));
 check(c.values.get("0:hecker_dual_statusbar")==0);
 android.os.Process.user=10;check(StatusBarHeightController.apply(c,true,2));check(on(c,10,"tall")&&!on(c,0,"tall"));
 c.manager.missing=true;check(!StatusBarHeightController.apply(c,false,0));check(on(c,10,"tall"));
 c.manager.missing=false;check(on(c,10,"tall"));
 System.out.println("Height controller: built-in-only atomic overlays, preference/commit rollback, user isolation, opt-out PASS");
}}"""
        }
        sources["android/util/Log.java"]="package android.util;public class Log{public static void w(String tag,String msg,Throwable error){}}"
        sources["com/android/server/theme/SyncProbe.java"]="""package com.android.server.theme;
import android.content.Context;
public class SyncProbe {
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    static boolean on(Context c, int user, String name) {
        return c.manager.enabled.getOrDefault(user+":org.hecker.statusbar.height."+name,false);
    }
    public static void main(String[] args) {
        Context c=new Context(); StatusBarHeightSync sync=new StatusBarHeightSync(c);
        c.manager.enabled.put("0:other.overlay",true);
        c.values.put("0:hecker_dual_statusbar",1); c.values.put("0:hecker_dual_statusbar_height",1);
        sync.sync(0); check(on(c,0,"balanced")&&!on(c,0,"tall"));
        c.values.put("10:hecker_dual_statusbar",1); c.values.put("10:hecker_dual_statusbar_height",2);
        sync.sync(10); check(on(c,0,"tall")&&on(c,10,"tall")&&!on(c,0,"balanced"));
        check(c.values.get("0:hecker_dual_statusbar_height")==1&&c.manager.enabled.get("0:other.overlay"));
        sync.sync(11); check(!on(c,0,"tall")&&!on(c,11,"tall"));
        sync.sync(0); check(on(c,0,"balanced")&&!on(c,0,"tall"));
        c.manager.failOnce=true; sync.sync(10); check(on(c,0,"balanced"));
        sync.sync(10); check(on(c,0,"tall"));
        c.manager.enabled.put("0:org.hecker.statusbar.height.tall",false);
        sync.sync(10); check(on(c,0,"tall"));
        c.values.put("10:hecker_dual_statusbar",0); sync.sync(10);
        check(!on(c,0,"tall")&&!on(c,10,"tall"));
        c.manager.missing=true; c.values.put("10:hecker_dual_statusbar",1);
        sync.sync(10); check(!on(c,0,"tall"));
        c.manager.missing=false; sync.sync(10); check(on(c,0,"tall"));
        System.out.println("Native height synchronization: foreground/system resources, switch-back, disabled user, failure retry, overlay repair and preference isolation PASS");
    }
}
"""
        with tempfile.TemporaryDirectory(prefix="hecker-height-controller-") as temp:
            out=Path(temp)
            for name,source in sources.items():
                p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
            subprocess.run(["java","-m","jdk.compiler/com.sun.tools.javac.Main","-d",str(out/"classes"),*map(str,out.rglob("*.java")),
                str(ROOT/"app/src/org/hecker/suite/StatusBarHeightController.java"),
                str(ROOT/"framework/themes/StatusBarHeightSync.java")],check=True)
            subprocess.run(["java","-cp",str(out/"classes"),"org.hecker.suite.HeightProbe"],check=True)
            subprocess.run(["java","-cp",str(out/"classes"),"com.android.server.theme.SyncProbe"],check=True)
