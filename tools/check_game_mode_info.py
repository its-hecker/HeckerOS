# SPDX-License-Identifier: Apache-2.0
"""Run the patched native GameModeInfo path with and without OEM intervention configs."""
from pathlib import Path
import subprocess
import tempfile


def check_game_mode_info(folder):
    source=(folder/'frameworks/base/services/core/java/com/android/server/app/GameManagerService.java').read_text()
    start=source.index('    public GameModeInfo getGameModeInfo(')
    end=source.index('\n    /**',start)
    method=source[start:end].replace('@NonNull ','').replace('@UserIdInt ','').replace('@GameMode ','')
    program=r'''
public class GameModeInfoProbe {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class ActivityManager{static int handleIncomingUser(int a,int b,int user,boolean c,boolean d,String e,String f){return user;}}
 static class Binder{static int getCallingPid(){return 1;}static int getCallingUid(){return 1000;}}
 static class Manifest{static class permission{static String MANAGE_GAME_MODE="manage";}}
 static class GameManager{static final int GAME_MODE_STANDARD=1,GAME_MODE_CUSTOM=4;}
 static class PackageManager{boolean fps=true,scale=true;}
 final PackageManager mPackageManager=new PackageManager();boolean game=true;GamePackageConfiguration configured;
 boolean isPackageGame(String p,int u){return game;}void checkPermission(String p){}
 int getGameModeFromSettingsUnchecked(String p,int u){return 1;}
 int[] getAvailableGameModesUnchecked(String p,int u){return new int[]{1,4};}
 GamePackageConfiguration getConfig(String p,int u){return configured;}
 static class PublicConfig{}
 static class GamePackageConfiguration{boolean mAllowFpsOverride,mAllowDownscale;
  GamePackageConfiguration(PackageManager pm,String p,int u){mAllowFpsOverride=pm.fps;mAllowDownscale=pm.scale;}
  int[] getOverriddenGameModes(){return new int[0];}int[] getAvailableGameModes(){return new int[]{1,4};}
  boolean willGamePerformOptimizations(int mode){return false;}
  GameModeConfiguration getGameModeConfiguration(int mode){return null;}
  static class GameModeConfiguration{PublicConfig toPublicGameModeConfig(){return new PublicConfig();}}}
 static class GameModeInfo {boolean fps,scale;
  static class Builder{GameModeInfo info=new GameModeInfo();Builder setActiveGameMode(int i){return this;}
   Builder setAvailableGameModes(int[] a){return this;}Builder setOverriddenGameModes(int[] a){return this;}
   Builder setDownscalingAllowed(boolean b){info.scale=b;return this;}Builder setFpsOverrideAllowed(boolean b){info.fps=b;return this;}
   Builder setGameModeConfiguration(int i,PublicConfig c){return this;}GameModeInfo build(){return info;}}}
''' + method + r'''
 public static void main(String[] args){
  GameModeInfoProbe p=new GameModeInfoProbe();
  check(p.getGameModeInfo("game",0).fps&&p.getGameModeInfo("game",0).scale);
  p.mPackageManager.fps=false;check(!p.getGameModeInfo("game",0).fps);
  p.mPackageManager.scale=false;check(!p.getGameModeInfo("game",0).scale);
  p.configured=new GamePackageConfiguration(p.mPackageManager,"game",0);
  p.mPackageManager.fps=true;check(!p.getGameModeInfo("game",0).fps);
  p.game=false;check(p.getGameModeInfo("game",0)==null);
  System.out.println("Native GameModeInfo: absent OEM tuning retains manifest defaults; FPS/downscale opt-outs and non-games remain respected PASS");
 }
}
'''
    with tempfile.TemporaryDirectory(prefix='hecker-native-game-mode-') as temporary:
        root=Path(temporary);file=root/'GameModeInfoProbe.java';file.write_text(program)
        subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',temporary,str(file)],check=True)
        subprocess.run(['java','-cp',temporary,'GameModeInfoProbe'],check=True)
