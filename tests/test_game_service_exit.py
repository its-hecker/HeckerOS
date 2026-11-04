# SPDX-License-Identifier: Apache-2.0
"""Execute the production dashboard task-evaluation method and real session policy."""
from pathlib import Path
import sys,unittest
from test_motion_island import java_run
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from check_launcher_studio import method
class GameExitTests(unittest.TestCase):
    def test_exit_removes_foreground_service_while_preserving_opt_in(self):
        evaluate=method((ROOT/'app/src/org/hecker/suite/GamingService.java').read_text(),'    private void evaluate()')
        code='''package org.hecker.suite;
import java.util.*;
public class ServiceProbe{
 boolean destroyed,active,catalogReady=true;int gameTask=-1,sessionGeneration,stops,removed,pauses,starts;String gamePackage;long recordGrace;
 Set<String> games=new HashSet<>(Set.of("game")),popups=new HashSet<>();GamingPolicy.TaskObservation observation=new GamingPolicy.TaskObservation();
 ActivityManager am=new ActivityManager();Worker worker=new Worker();Session session=new Session();Overlay overlay=new Overlay();
 static final int STOP_FOREGROUND_REMOVE=1;
 static class TaskInfo{int taskId=1;boolean visible=true,focused=true;String pkg="game";boolean isVisible(){return visible;}}
 static class ActivityManager{static class RunningTaskInfo extends TaskInfo{}List<RunningTaskInfo> tasks=new ArrayList<>();List<RunningTaskInfo> getRunningTasks(int count){return tasks;}}
 static class Settings{static boolean canDrawOverlays(Object o){return true;}}
 static class SystemClock{static long elapsedRealtime(){return 1000;}}
 static class GamingPrefs{static boolean enabled(Object o){return true;}}
 static class GamingPlatform{static boolean ownDisplayTask(Object o){return true;}static boolean freeform(Object o){return false;}}
 static class Log{static void w(String a,String b,Throwable c){throw new AssertionError(c);}}
 static class NotificationManager{void notify(int id,Object o){}}
 static class Worker{void execute(Runnable r){r.run();}}
 static class Session{void end(){}}
 static class Overlay{void restyle(){}}
 static String pkg(ActivityManager.RunningTaskInfo task){return task.pkg;}
 boolean focused(ActivityManager.RunningTaskInfo task){return task.focused;}
 boolean unlocked(){return true;}void loadModes(){}void stopFps(){}void activate(){active=true;starts++;}void pause(){active=false;pauses++;}
 void stopForeground(int flags){if(flags!=STOP_FOREGROUND_REMOVE)throw new AssertionError();removed++;}void stopSelf(){stops++;}void schedule(long delay){}
 <T>T getSystemService(Class<T> c){return c.cast(new NotificationManager());}Object notification(){return this;}
 static void check(boolean v){if(!v)throw new AssertionError();}
''' + evaluate + '''
 public static void main(String[]args){ServiceProbe p=new ServiceProbe();
 p.catalogReady=false;p.evaluate();check(p.stops==0);p.catalogReady=true;
 var game=new ActivityManager.RunningTaskInfo();p.am.tasks.add(game);p.evaluate();check(p.active&&p.starts==1&&p.stops==0);
 var home=new ActivityManager.RunningTaskInfo();home.pkg="home";home.taskId=2;p.am.tasks=List.of(home);p.evaluate();
 check(!p.active&&p.pauses==1&&p.removed==1&&p.stops==1&&p.gameTask==-1&&p.gamePackage==null&&GamingPrefs.enabled(p));
 System.out.println("Production GameSpace exit: restoration, foreground removal, service stop, cleared task and retained auto-monitor preference PASS");
 }}'''
        java_run({'org/hecker/suite/ServiceProbe.java':code},[ROOT/'app/src/org/hecker/suite/GamingPolicy.java'],'org.hecker.suite.ServiceProbe')
if __name__=='__main__':unittest.main()
