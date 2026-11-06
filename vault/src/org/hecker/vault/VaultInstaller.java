// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.PendingIntent;
import android.content.*;
import android.content.pm.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

final class VaultInstaller {
    static final Map<Integer,CompletableFuture<Integer>> results=new ConcurrentHashMap<>();
    static void install(Context c,JSONObject app,File root)throws Exception{
        String pkg=app.getString("package");
        try{VaultPackages.checkInstalled(c,app);return;}catch(PackageManager.NameNotFoundException missing){}
        catch(IOException mismatch){
            // PackageInstaller enforces signing and prevents downgrades. A matching older version may be updated.
            PackageInfo current=c.getPackageManager().getPackageInfo(pkg,PackageManager.GET_SIGNING_CERTIFICATES);
            List<String> expected=new ArrayList<>();JSONArray signers=app.getJSONArray("signers");for(int i=0;i<signers.length();i++)expected.add(signers.getString(i));Collections.sort(expected);
            if(!expected.equals(VaultPackages.signers(current)))throw mismatch;
        }
        PackageInstaller installer=c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);params.setInstallReason(PackageManager.INSTALL_REASON_DEVICE_RESTORE);
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id=installer.createSession(params);boolean committed=false;CompletableFuture<Integer> result=new CompletableFuture<>();results.put(id,result);
        try(PackageInstaller.Session session=installer.openSession(id)){
            JSONArray apks=app.getJSONArray("apks");for(int i=0;i<apks.length();i++){VaultSystemData.check();File apk=new File(root,apks.getString(i));
                try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite(i==0?"base.apk":"split-"+i+".apk",0,apk.length())){
                    byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){VaultSystemData.check();out.write(b,0,n);}session.fsync(out);}}
            Intent intent=new Intent(c,VaultInstallReceiver.class).setAction("org.hecker.vault.INSTALL."+id).putExtra("vault_session",id);
            PendingIntent callback=PendingIntent.getBroadcast(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
            session.commit(callback.getIntentSender());committed=true;
            int status=result.get(10,TimeUnit.MINUTES);if(status!=PackageInstaller.STATUS_SUCCESS)throw new IOException(status==PackageInstaller.STATUS_PENDING_USER_ACTION?
                    "Android requires manual installation for this app":"Android rejected this APK installation");
            VaultPackages.checkInstalled(c,app);
        }finally{results.remove(id);if(!committed)installer.abandonSession(id);}
    }
}
