/* SPDX-License-Identifier: Apache-2.0 */
package android.security.pif;

import android.app.ActivityThread;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;

public final class PenguinPifTest {
    private static int checks;
    private static final String PROFILE="FINGERPRINT=google/shiba/shiba:17/CP21/123456:user/release-keys\n";
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("PIF check "+checks);}
    private static PlayIntegritySpoofService fresh() throws Exception {
        var constructor=PlayIntegritySpoofService.class.getDeclaredConstructor();constructor.setAccessible(true);
        return constructor.newInstance();
    }
    public static void main(String[] args) throws Exception {
        Context context=new Context();context.applicationReady=false;SystemClock.uptime=0;
        Settings.Global.values.put("hecker_pif_config",PROFILE);
        PlayIntegritySpoofService p=fresh();
        p.initialize(context,"example.other","com.android.vending");
        check(!p.isConfigLoaded()&&Settings.Global.reads==0);
        Process.isolated=true;p.initialize(context,"com.android.vending","com.android.vending");
        check(!p.isConfigLoaded()&&Settings.Global.reads==0);Process.isolated=false;
        Settings.Global.unavailable=true;
        p.initialize(context,"com.google.android.gms","com.google.android.gms.unstable");
        check(!p.isConfigLoaded());int reads=Settings.Global.reads;
        Settings.Global.unavailable=false;
        check(!p.shouldSpoof("com.google.android.gms.unstable")&&Settings.Global.reads==reads);
        SystemClock.uptime=5000;
        check(p.shouldSpoof("com.google.android.gms.unstable"));
        check(!p.shouldSpoof("com.google.android.gms.persistent"));
        check(!p.shouldSpoof("example.other"));
        Settings.Global.values.put("hecker_pif_enabled","0");
        p=fresh();p.initialize(context,"com.android.vending","com.android.vending");
        check(!p.shouldSpoof("com.android.vending"));reads=Settings.Global.reads;
        SystemClock.uptime=10000;
        check(!p.shouldSpoof("com.android.vending")&&Settings.Global.reads==reads);
        ActivityThread.packageName="com.google.android.apps.photos";
        Settings.Global.values.put("hecker_pif_photos","1");
        p=fresh();p.initialize(context,ActivityThread.packageName,ActivityThread.packageName);
        check(p.shouldSpoofPhotos(ActivityThread.packageName));
        check(!p.hasSystemFeature("com.google.android.feature.PIXEL_2026_EXPERIENCE",0));
        check(p.hasSystemFeature("com.google.android.apps.photos.NEXUS_PRELOAD",0));
        Settings.Global.values.put("hecker_pif_photos","0");
        p=fresh();p.initialize(context,ActivityThread.packageName,ActivityThread.packageName);
        check(!p.shouldSpoofPhotos(ActivityThread.packageName));
        check(p.hasSystemFeature("com.google.android.feature.PIXEL_2026_EXPERIENCE",0));
        check(!p.shouldSpoofPhotos("example.other"));
        check(p.hasSystemFeature("example.unknown",0)==null);
        System.out.println(checks+" PIF lifecycle checks: package/isolated scope, provider retry, explicit disable and independent Photos PASS");
    }
}
