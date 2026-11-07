// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

final class VaultEngine {
    interface Status{void text(String value);}
    static final class Prepared {
        final String id=UUID.randomUUID().toString();final long opened=android.os.SystemClock.elapsedRealtime();final File root;final JSONObject snapshot;
        Prepared(File root,JSONObject snapshot){this.root=root;this.snapshot=snapshot;}
    }
    static volatile Prepared prepared;
    static volatile Uri needsPassword;
    private final Context context;private final Status status;private final List<String> report=new ArrayList<>();
    VaultEngine(Context c,Status status){context=c;this.status=status;}
    static void owner(Context c)throws IOException{
        if(android.os.Process.myUid()/100000!=0||c.getSystemService(UserManager.class).isManagedProfile())throw new IOException("HeckerVault v1 supports the owner profile");
        if(!c.getSystemService(UserManager.class).isUserUnlocked())throw new IOException("Unlock the phone before using HeckerVault");
    }
    static boolean sharedStorageAvailable(Context context) {
        return context.checkSelfPermission(VaultExternal.PERMISSION) == PackageManager.PERMISSION_GRANTED
                && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }
    String run(VaultRequest request)throws Exception{
        owner(context);try{
            switch(request.operation){case "backup":backup(request);break;case "inspect":inspect(request);break;case "restore":restore(request);break;
                case "schedule":VaultStorage.folder(context);VaultJobs.configure(context,true);report.add("Scheduled backups enabled. Keep your password for recovery after a clean flash.");break;
                default:throw new IOException("Unknown backup operation");}
            return String.join("\n",report);
        }finally{VaultSession session=VaultSession.current;if(session!=null)session.cancelled=true;VaultSession.current=null;}
    }
    private void backup(VaultRequest request)throws Exception{
        File root=VaultStorage.staging(context);Uri destination=null;boolean complete=false;long token=new SecureRandom().nextLong()&Long.MAX_VALUE;if(token==0)token=1;
        String id=UUID.randomUUID().toString();JSONObject snapshot=new JSONObject().put("format",1).put("id",id).put("token",token)
                .put("created",System.currentTimeMillis()).put("sdk",Build.VERSION.SDK_INT).put("device",Build.DEVICE).put("deviceTransfer",true);
        JSONArray apps=new JSONArray(),categories=new JSONArray(),notes=new JSONArray();List<String> selected=new ArrayList<>(new LinkedHashSet<>(request.packages));
        if(request.categories.contains("launcher")&&!selected.contains("com.android.launcher3"))selected.add("com.android.launcher3");
        try{
            VaultSession session=new VaultSession(root,token,false,selected);VaultSession.current=session;
            int position=0;for(String pkg:selected){session.check();status.text("Saving APKs ("+(++position)+"/"+selected.size()+"): "+pkg);
                try{apps.put(VaultPackages.capture(context,pkg,root));}
                catch(PackageManager.NameNotFoundException unavailable){notes.put(pkg+": app is not installed");}
            }
            if(apps.length()==0&&request.categories.isEmpty())throw new IOException("Select apps or personal data to back up");
            for(String category:request.categories){session.check();if(category.equals("launcher"))continue;status.text("Backing up "+category);
                try{VaultSystemData.capture(context,category,root);categories.put(category);
                    if(category.equals("wifi"))try{List<VaultWifi.Network> saved=VaultWifi.read(new File(root,"system/wifi.bin"));
                        snapshot.put("wifiCount",saved.size());for(VaultWifi.Network network:saved)network.clear();
                    }catch(Exception unsupported){/* Preserve Android's restore payload if its detail format is newer. */}}
                catch(SecurityException e){notes.put(category+": permission is unavailable");}
                catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;notes.put(category+": backup failed ("+e.getClass().getSimpleName()+")");}
            }
            Set<String> dataPackages=new LinkedHashSet<>();for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);if(app.getBoolean("allowsData"))dataPackages.add(app.getString("package"));}
            if(!request.mode.equals("apk")&&!dataPackages.isEmpty()){
                VaultSession data=new VaultSession(root,token,false,dataPackages);VaultSession.current=data;session=data;
                boolean nativeComplete=false;
                try(VaultNative nativeBackup=new VaultNative(context,status::text)){nativeBackup.backup(data);nativeComplete=true;}
                catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;notes.put("App data: "+safeNativeError(e));}
                boolean metadata=nativeComplete&&"kv".equals(data.kinds.get("@pm@"));
                for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");String kind=data.kinds.get(pkg);
                    if(metadata&&data.results.getOrDefault(pkg,-1)==0&&kind!=null){app.put("data",kind);app.put("result","APK + app data");}
                    else{app.put("data","");app.put("result",app.getBoolean("allowsData")?"APK only: Android did not produce a complete data backup":"APK only: app does not allow Android backup");}}
            }else for(int i=0;i<apps.length();i++)apps.getJSONObject(i).put("data","").put("result","APK only");
            Path shared = request.externalParts.isEmpty() || !sharedStorageAvailable(context)
                    ? null : Environment.getExternalStorageDirectory().toPath();
            if (shared == null && !request.externalParts.isEmpty()) {
                notes.put("Selected shared storage folders were unavailable on this build or volume.");
            }
            for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");
                session.check();
                VaultPackages.checkInstalled(context,app);PackageInfo now=context.getPackageManager().getPackageInfo(pkg,0);
                if(now.getLongVersionCode()!=app.getLong("version"))throw new IOException("An app updated during backup; run the backup again");
                if (shared != null && request.packages.contains(pkg)) {
                    captureExternal(shared, root.toPath(), app, request.externalParts, session, notes);
                    now = VaultPackages.checkInstalled(context, app);
                    if (now.getLongVersionCode() != app.getLong("version")) {
                        throw new IOException("An app updated during backup; run the backup again");
                    }
                }
                if(request.categories.contains("launcher")&&pkg.equals("com.android.launcher3")&&!app.optString("data").isEmpty())categories.put("launcher");
            }
            if(request.categories.contains("launcher")&&!contains(categories,"launcher"))notes.put("Launcher layout: native launcher backup was unavailable");
            // Drop unsuccessful native payloads instead of exporting unusable private data.
            File dataRoot=new File(root,"data");if(dataRoot.isDirectory()){
                Set<String> keep=new HashSet<>();for(int i=0;i<apps.length();i++)if(!apps.getJSONObject(i).optString("data").isEmpty())keep.add(apps.getJSONObject(i).getString("package"));
                if(!keep.isEmpty())keep.add("@pm@");File[] dirs=dataRoot.listFiles();if(dirs!=null)for(File dir:dirs)if(!keep.contains(dir.getName()))cleanup(dir);
            }
            snapshot.put("apps",apps).put("categories",categories).put("notes",notes).put("groups",VaultGroups.load(context));VaultStorage.json(new File(root,"snapshot.json"),snapshot);
            VaultPackages.validate(snapshot,root);VaultSystemData.validate(root,categories);
            String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date());
            destination=VaultStorage.create(context,"HV-"+stamp+"-"+id.substring(0,8)+".hvault");status.text("Preparing encrypted export");
            final long total=VaultArchive.size(root),started=SystemClock.elapsedRealtime();final long[] last={0};
            try(OutputStream out=context.getContentResolver().openOutputStream(destination,"w")){
                if(out==null)throw new IOException("Backup folder is unavailable");final VaultSession export=session;
                VaultArchive.write(root,out,request.password,VaultStorage.prefs(context).getInt("compression",VaultArchive.FASTEST),bytes->{export.check();
                    long now=SystemClock.elapsedRealtime();if(now-last[0]<500&&bytes<total)return;last[0]=now;
                    String done=android.text.format.Formatter.formatFileSize(context,bytes),size=android.text.format.Formatter.formatFileSize(context,total);
                    long speed=bytes*1000/Math.max(1,now-started);int percent=total==0?100:(int)Math.min(100,bytes*100/total);
                    status.text("Encrypting and exporting · "+percent+"% · "+done+" / "+size+" · "+android.text.format.Formatter.formatFileSize(context,speed)+"/s");});}
            status.text("Finishing encrypted snapshot");
            complete=true;int retention=request.scheduled?VaultStorage.prefs(context).getInt("retention",3):0;
            VaultStorage.committed(context,destination,retention);VaultHistory.record(context,destination,snapshot);int withData=0;for(int i=0;i<apps.length();i++)if(!apps.getJSONObject(i).optString("data").isEmpty())withData++;
            report.add("Snapshot saved: "+apps.length()+" apps ("+withData+" with app data), "+categories.length()+" personal-data categories.");
            for(int i=0;i<apps.length();i++)report.add(apps.getJSONObject(i).optString("label")+": "+apps.getJSONObject(i).getString("result"));
            for(int i=0;i<notes.length();i++)report.add(notes.getString(i));
            report.add("Copy the HeckerVault folder to PC or USB before formatting data. Keep the archive password.");
        }finally{
            if(!complete&&destination!=null)try{android.provider.DocumentsContract.deleteDocument(context.getContentResolver(),destination);}catch(Exception ignored){}
            cleanup(root);
        }
    }
    private void captureExternal(Path shared, Path root, JSONObject app, Set<VaultExternal.Part> parts,
            VaultSession session, JSONArray notes) throws Exception {
        String pkg = app.getString("package");
        JSONArray captured = new JSONArray();
        JSONObject results=new JSONObject();app.put("externalStatus",results);
        for (VaultExternal.Part part : VaultExternal.Part.values()) {
            if (!parts.contains(part)) continue;
            session.check();
            String label = pkg + " · Android/" + part.directory;
            if (part == VaultExternal.Part.DATA && !app.getBoolean("allowsData")) {
                results.put(part.directory,new JSONObject().put("state","disallowed"));
                notes.put(label + ": app does not allow backup");
                continue;
            }
            status.text("Backing up " + label);
            try {
                int count = VaultExternal.capture(shared, root, pkg, part, bytes -> session.check());
                if (count > 0) captured.put(part.directory);
                results.put(part.directory,new JSONObject().put("state",count>0?"saved":"empty").put("files",count));
                notes.put(label + ": " + (count == 0 ? "no files" : count + " files backed up"));
            } catch (IOException | SecurityException failure) {
                session.check();
                results.put(part.directory,new JSONObject().put("state","failed").put("reason",failure.getClass().getSimpleName()));
                notes.put(label + ": backup failed (" + failure.getClass().getSimpleName() + ")");
            }
        }
        if (captured.length() > 0) app.put("externalParts", captured);
    }
    private void inspect(VaultRequest request)throws Exception{
        if(request.archive==null)throw new IOException("Choose an archive");
        boolean found=false;for(VaultStorage.Archive a:VaultStorage.list(context))if(a.uri.equals(request.archive))found=true;
        if(!found)throw new IOException("Archive is outside the selected folder");
        Prepared old=prepared;prepared=null;if(old!=null)cleanup(old.root);File root=VaultStorage.staging(context);boolean ready=false;
        try{status.text("Decrypting and checking the complete archive");
            List<char[]> passwords=VaultKeys.candidates(context);passwords.add(0,request.password.clone());
            char[] matching=null;
            try{
                for(char[] candidate:passwords){
                    VaultSystemData.check();
                    try(InputStream source=context.getContentResolver().openInputStream(request.archive)){
                        if(source==null)throw new IOException("Archive is unavailable");
                        // Authenticate the first chunk before extracting anything. Old local passwords
                        // remain usable following a phone-credential password reset.
                        try(InputStream decoded=VaultCipher.decrypt(source,candidate)){decoded.read();matching=candidate.clone();break;}
                        catch(IOException failure){if(!(failure.getCause() instanceof javax.crypto.AEADBadTagException))throw failure;}
                    }
                }
            }finally{for(char[] candidate:passwords)Arrays.fill(candidate,'\0');}
            if(matching==null){needsPassword=request.archive;throw new IOException("Archive needs its original password or is damaged");}
            try{
            try(InputStream in=context.getContentResolver().openInputStream(request.archive)){
                if(in==null)throw new IOException("Archive is unavailable");
                VaultArchive.read(in,root,matching,Math.max(0,root.getUsableSpace()-128L*1024*1024),bytes->VaultSystemData.check());}
            JSONObject snapshot=VaultStorage.json(new File(root,"snapshot.json"));VaultPackages.validate(snapshot,root);
            validateApks(snapshot,root);VaultSystemData.validate(root,snapshot.getJSONArray("categories"));
            VaultHistory.record(context,request.archive,snapshot);
            if(request.rememberPassword)VaultKeys.rememberImported(context,matching);
            needsPassword=null;prepared=new Prepared(root,snapshot);ready=true;report.add("Archive verified. Review the apps, personal data and restore mode, then tap Restore selected.");
            }finally{Arrays.fill(matching,'\0');}
        }finally{if(!ready)cleanup(root);}
    }
    private void validateApks(JSONObject snapshot,File root)throws Exception{
        JSONArray apps=snapshot.getJSONArray("apps");for(int i=0;i<apps.length();i++){VaultSystemData.check();JSONObject app=apps.getJSONObject(i);
            PackageInfo apk=context.getPackageManager().getPackageArchiveInfo(new File(root,"apps/"+app.getString("package")+"/base.apk").getPath(),PackageManager.GET_SIGNING_CERTIFICATES);
            if(apk==null||!apk.packageName.equals(app.getString("package"))||apk.getLongVersionCode()!=app.getLong("version"))throw new IOException("APK package/version does not match archive metadata");
            List<String> expected=new ArrayList<>();JSONArray signers=app.getJSONArray("signers");for(int n=0;n<signers.length();n++)expected.add(signers.getString(n));Collections.sort(expected);
            if(!expected.equals(VaultPackages.signers(apk)))throw new IOException("APK signing certificate does not match archive metadata");}
    }
    private void restore(VaultRequest request)throws Exception{
        Prepared p=prepared;if(p==null||!p.id.equals(request.preparedId))throw new IOException("Inspect the archive again before restoring");
        if(android.os.SystemClock.elapsedRealtime()-p.opened>30*60*1000L){prepared=null;cleanup(p.root);throw new IOException("Restore preview expired; inspect the archive again");}
        if(!Arrays.asList("both","apk","data").contains(request.mode))throw new IOException("Invalid restore mode");
        VaultPackages.validate(p.snapshot,p.root);validateApks(p.snapshot,p.root);VaultSystemData.validate(p.root,p.snapshot.getJSONArray("categories"));
        Set<String> chosen=new HashSet<>(request.packages);if(request.categories.contains("launcher"))chosen.add("com.android.launcher3");
        JSONArray apps=p.snapshot.getJSONArray("apps");List<String> data=new ArrayList<>();
        List<JSONObject> externalApps = new ArrayList<>();
        VaultSession session=new VaultSession(p.root,p.snapshot.getLong("token"),true,chosen);
        session.deviceTransfer=p.snapshot.optBoolean("deviceTransfer",false);VaultSession.current=session;
        try{for(int i=0;i<apps.length();i++){session.check();JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");if(!chosen.contains(pkg))continue;
                status.text("Preparing restore: "+pkg);
                try{if(!request.mode.equals("data"))VaultInstaller.install(context,app,p.root);
                    VaultPackages.checkInstalled(context,app);
                    if (!request.mode.equals("apk")) {
                        if (!app.optString("data").isEmpty()) data.add(pkg);
                        if (request.packages.contains(pkg)
                                && !Collections.disjoint(request.externalParts, VaultPackages.externalParts(app))) {
                            externalApps.add(app);
                        } else if (request.mode.equals("data") && app.optString("data").isEmpty()) {
                            report.add(pkg + ": skipped, no selected app data in this snapshot");
                        }
                    }
                    if (!request.mode.equals("data") && app.optString("data").isEmpty()) report.add(pkg + ": APK ready");
                }catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;report.add(pkg+": restore preparation failed ("+e.getClass().getSimpleName()+")");}
            }
            if(!data.isEmpty())try(VaultNative nativeRestore=new VaultNative(context,status::text)){
                nativeRestore.restore(session,data);for(String pkg:data)report.add(pkg+": Android app-data restore pass completed");
            }catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;report.add("App data: "+safeNativeError(e));}
            restoreExternal(p.root.toPath(), externalApps, request.externalParts, session);
            JSONArray available=p.snapshot.getJSONArray("categories");for(String category:request.categories){session.check();if(!contains(available,category)||category.equals("launcher"))continue;
                status.text("Restoring "+category);try{VaultSystemData.restore(context,category,p.root,p.snapshot.getString("id"));if(category.equals("settings"))VaultGroups.merge(context,p.snapshot.optJSONObject("groups"));report.add(category+": restored");}
                catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;report.add(category+": restore failed ("+e.getClass().getSimpleName()+")");}}
            if(report.isEmpty())report.add("Nothing was selected to restore.");
            report.add("Check the restored apps. Hardware-bound keys and existing login sessions may need to be set up again.");
        }finally{prepared=null;cleanup(p.root);}
    }
    private void restoreExternal(Path root, List<JSONObject> apps, Set<VaultExternal.Part> parts,
            VaultSession session) throws Exception {
        if (apps.isEmpty()) return;
        if (!sharedStorageAvailable(context)) {
            report.add("Selected shared storage folders could not be restored. Writable shared storage was unavailable.");
            return;
        }
        Path shared = Environment.getExternalStorageDirectory().toPath();
        for (JSONObject app : apps) {
            String pkg = app.getString("package");
            for (VaultExternal.Part part : VaultPackages.externalParts(app)) {
                if (!parts.contains(part)) continue;
                session.check();
                String label = pkg + " · Android/" + part.directory;
                status.text("Restoring " + label);
                try {
                    PackageInfo installed = VaultPackages.checkInstalled(context, app);
                    if (part == VaultExternal.Part.DATA
                            && (installed.applicationInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP) == 0) {
                        report.add(label + ": installed app does not allow backup data");
                        continue;
                    }
                    int count = VaultExternal.restore(shared, root, pkg, part, bytes -> session.check());
                    report.add(label + ": " + count + " files restored");
                } catch (VaultExternal.PartialRestore failure) {
                    session.check();
                    report.add(label + ": " + failure.restored + "/" + failure.total + " files restored before failure");
                } catch (Exception failure) {
                    session.check();
                    report.add(label + ": restore failed (" + failure.getClass().getSimpleName() + ")");
                }
            }
        }
    }
    static boolean contains(JSONArray array,String value){for(int i=0;i<array.length();i++)if(value.equals(array.optString(i)))return true;return false;}
    static void cleanup(File root){try{VaultArchive.delete(root);}catch(IOException ignored){/* Private leftovers are cleaned when the next operation starts. */}}
    private String safeNativeError(Exception e){String text=e.getMessage();return text!=null&&(text.startsWith("Enable Android backup")||text.startsWith("Another Android restore")||text.startsWith("HeckerVault transport"))?text:"Android backup/restore failed ("+e.getClass().getSimpleName()+")";}
}
