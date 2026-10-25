# SPDX-License-Identifier: Apache-2.0
"""Run the actual policy reload with unavailable profiles and corrupt saved rules."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]

class MemorySnapshotTest(unittest.TestCase):
    def test_failed_profile_does_not_turn_every_app_into_a_protected_app(self):
        source=(ROOT/'framework/memory/HeckerMemoryPolicy.java').read_text()
        method=source[source.index('    private void reload(){'):source.index('    private Entry entry(')]
        program=r'''
import java.util.*;
import com.android.internal.hecker.HeckerMemoryRules;
public class MemorySnapshotProbe {
 static final String TAG="test";static final int OOM_ADJ_REASON_RESTRICTION_CHANGE=1;
 final Context context=new Context();final Handler worker=new Handler();final AMS am=new AMS();
 final Runnable background=()->{};Map<Integer,Entry> entries;Set<Integer> loadedUsers;
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static class Entry {ApplicationInfo app;int flags;Entry(ApplicationInfo a,int f){app=a;flags=f;}}
 static class ApplicationInfo {static final int FLAG_PERSISTENT=8;int uid,flags;String packageName;
  ApplicationInfo(String p,int u){packageName=p;uid=u;}}
 static class PackageManager {static final int MATCH_DEFAULT_ONLY=1;List<ApplicationInfo> apps=new ArrayList<>();
  String[] getPackagesForUid(int uid){return uid==10125?new String[]{"shared.one","shared.two"}:apps.stream().filter(a->a.uid==uid).map(a->a.packageName).toArray(String[]::new);}
  List<ApplicationInfo> getInstalledApplications(int flags){return apps;}
  ResolveInfo resolveActivity(Intent i,int flags){return new ResolveInfo();}}
 static class ResolveInfo{ActivityInfo activityInfo=new ActivityInfo();}static class ActivityInfo{String packageName="home";}
 static class Intent {static final String ACTION_MAIN="main",CATEGORY_HOME="home";Intent(String s){}Intent addCategory(String s){return this;}}
 static class ComponentName{String getPackageName(){return "keyboard";}static ComponentName unflattenFromString(String s){return new ComponentName();}}
 static class UserHandle {int id;UserHandle(int i){id=i;}int getIdentifier(){return id;}static UserHandle of(int i){return new UserHandle(i);}static boolean isApp(int uid){return uid>=10000;}}
 static class UserManager {List<UserHandle> getUserHandles(boolean b){return List.of(new UserHandle(0),new UserHandle(10));}}
 static class ActivityManager{static int getCurrentUser(){return 0;}}
 static class Context{int user;Map<Integer,PackageManager> pm=new HashMap<>();Map<Integer,android.app.role.RoleManager> roles=new HashMap<>();
  PackageManager getPackageManager(){return pm.get(user);}Object getContentResolver(){return this;}
  Context createContextAsUser(UserHandle h,int f){Context c=new Context();c.user=h.id;c.pm=pm;c.roles=roles;return c;}
  <T>T getSystemService(Class<T> cls){return cls.cast(cls==UserManager.class?new UserManager():roles.get(user));}}
 static class Settings{static class Secure{static final String DEFAULT_INPUT_METHOD="ime";static String raw;
  static String getStringForUser(Object c,String key,int user){return key.equals(DEFAULT_INPUT_METHOD)?"keyboard/.Input":raw;}}}
 static class JSONObject{JSONObject(){}JSONObject(String s)throws org.json.JSONException{if(s.equals("broken"))throw new org.json.JSONException();}int optInt(String p){return 0;}}
 static class Handler{void removeCallbacks(Runnable r){}void postDelayed(Runnable r,int ms){}}
 static class ProcessRecord{}static class ProcessList{List<ProcessRecord> getLruProcessesLOSP(){return List.of();}}
 static class AMS{Object mGlobalLock=new Object(),mProcLock=new Object();ProcessList mProcessList=new ProcessList();void updateOomAdjLocked(int r){}}
 static class Slog{static void w(String t,String s,Throwable e){}}
 void applyResident(ProcessRecord p){}
''' + method + r'''
 public static void main(String[] args){
  MemorySnapshotProbe p=new MemorySnapshotProbe();PackageManager pm=new PackageManager();
  for(String name:new String[]{"ordinary","dialer","shared.one","home","keyboard","com.android.systemui"})
   pm.apps.add(new ApplicationInfo(name,10123+pm.apps.size()));
  p.context.pm.put(0,pm);p.context.pm.put(10,new PackageManager());
  p.context.roles.put(0,new android.app.role.RoleManager(false));p.context.roles.put(10,new android.app.role.RoleManager(true));
  p.reload();check(p.loadedUsers.equals(Set.of(0)));check(p.entries.size()==1&&p.entries.get(10123).app.packageName.equals("ordinary"));
  Settings.Secure.raw="broken";p.reload();check(p.loadedUsers.equals(Set.of(0))&&p.entries.containsKey(10123));
  p.context.roles.put(10,new android.app.role.RoleManager(false));p.reload();check(p.loadedUsers.equals(Set.of(0,10)));
  System.out.println("Production memory snapshot: ordinary apps manageable, HOME/IME/roles/shared UID excluded, failing profile isolated, corrupt rules default safely PASS");
 }
}
'''
        files={'MemorySnapshotProbe.java':program,
          'android/app/role/RoleManager.java':'''package android.app.role;import java.util.*;public class RoleManager{
 public static final String ROLE_DIALER="dialer",ROLE_SMS="sms",ROLE_ASSISTANT="assistant",ROLE_EMERGENCY="emergency";
 final boolean failed;public RoleManager(boolean f){failed=f;}public boolean isRoleAvailable(String r){if(failed)throw new SecurityException();return true;}
 public List<String> getRoleHolders(String r){return r.equals(ROLE_DIALER)?List.of("dialer"):List.of();}}''',
          'org/json/JSONException.java':'package org.json;public class JSONException extends Exception{}'}
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory);paths=[]
            for name,text in files.items():
                path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text);paths.append(str(path))
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(out/'classes'),*paths,
                            str(ROOT/'framework/memory/HeckerMemoryRules.java')],check=True)
            subprocess.run(['java','-cp',str(out/'classes'),'MemorySnapshotProbe'],check=True)
