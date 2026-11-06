// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.*;
import android.content.pm.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

final class VaultPackages {
    static List<PackageInfo> installed(Context c,boolean system){
        List<PackageInfo> apps=new ArrayList<>();for(PackageInfo p:c.getPackageManager().getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES)){
            if(p.applicationInfo==null||p.packageName.equals(c.getPackageName())||p.applicationInfo.uid<10000)continue;
            if((p.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0&&!system)continue;
            apps.add(p);}
        apps.sort(Comparator.comparing(p->p.packageName));return apps;
    }
    static List<String> userApps(Context c){List<String> result=new ArrayList<>();for(PackageInfo p:installed(c,false))result.add(p.packageName);return result;}
    static List<String> signers(PackageInfo p)throws Exception{
        if(p.signingInfo==null)throw new IOException("App signing information is unavailable");
        List<String> out=new ArrayList<>();for(Signature signature:p.signingInfo.getApkContentsSigners()){
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());StringBuilder s=new StringBuilder();
            for(byte b:hash)s.append(String.format(Locale.ROOT,"%02x",b&255));out.add(s.toString());}
        Collections.sort(out);if(out.isEmpty())throw new IOException("App has no signer");return out;
    }
    static JSONObject capture(Context c,String pkg,File root)throws Exception{
        if(!VaultSession.validPackage(pkg)||pkg.equals("@pm@"))throw new IOException("Invalid app selection");
        PackageInfo p=c.getPackageManager().getPackageInfo(pkg,PackageManager.GET_SIGNING_CERTIFICATES);
        if(p.applicationInfo==null||p.applicationInfo.uid<10000||pkg.equals(c.getPackageName()))throw new IOException("Protected system package cannot be restored");
        JSONObject result=new JSONObject().put("package",pkg).put("label",p.applicationInfo.loadLabel(c.getPackageManager()).toString())
                .put("version",p.getLongVersionCode()).put("signers",new JSONArray(signers(p)))
                .put("system",(p.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)
                .put("allowsData",(p.applicationInfo.flags&ApplicationInfo.FLAG_ALLOW_BACKUP)!=0);
        List<String> paths=new ArrayList<>();paths.add(p.applicationInfo.sourceDir);
        if(p.applicationInfo.splitSourceDirs!=null)paths.addAll(Arrays.asList(p.applicationInfo.splitSourceDirs));
        if(paths.size()>1000)throw new IOException("Too many APK splits");JSONArray apks=new JSONArray();
        File dir=new File(root,"apps/"+pkg);if(!dir.mkdirs())throw new IOException("Cannot stage app APK");
        for(int i=0;i<paths.size();i++){
            File source=new File(paths.get(i));VaultStorage.checkSpace(root,source.length());String name=i==0?"base.apk":"split-"+i+".apk";
            File target=new File(dir,name);try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(target)){
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");out.write(b,0,n);}out.getFD().sync();}
            apks.put("apps/"+pkg+"/"+name);}
        result.put("apks",apks);return result;
    }
    static void validate(JSONObject snapshot,File root)throws Exception{
        if(snapshot.getInt("format")!=1||snapshot.getInt("sdk")>android.os.Build.VERSION.SDK_INT)throw new IOException("This snapshot needs the same or newer Android version");
        VaultGroups.validate(snapshot.optJSONObject("groups"));
        UUID.fromString(snapshot.getString("id"));if(snapshot.getLong("token")<=0)throw new IOException("Invalid restore token");
        JSONArray apps=snapshot.getJSONArray("apps");if(apps.length()>10000)throw new IOException("Too many apps");Set<String> names=new HashSet<>();
        for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");
            if(!VaultSession.validPackage(pkg)||pkg.equals("@pm@")||!names.add(pkg))throw new IOException("Invalid app metadata");
            if(app.getLong("version")<0||app.getString("label").length()>1024)throw new IOException("Invalid app version or label");
            JSONArray signatures=app.getJSONArray("signers");if(signatures.length()==0||signatures.length()>32)throw new IOException("Invalid signing information");
            for(int j=0;j<signatures.length();j++)if(!signatures.getString(j).matches("[0-9a-f]{64}"))throw new IOException("Invalid signer digest");
            JSONArray apks=app.getJSONArray("apks");if(apks.length()==0||apks.length()>1000)throw new IOException("Invalid APK set");Set<String> paths=new HashSet<>();
            for(int j=0;j<apks.length();j++){String path=VaultArchive.safe(apks.getString(j));
                if(!path.matches("apps/"+java.util.regex.Pattern.quote(pkg)+"/(base|split-[0-9]+)\\.apk")||!paths.add(path)
                        ||!new File(root,path).isFile())throw new IOException("Missing or invalid APK split");}
            if(!paths.contains("apps/"+pkg+"/base.apk"))throw new IOException("Missing base APK");
            String kind=app.optString("data","");if(!kind.isEmpty()&&!kind.equals("kv")&&!kind.equals("full"))throw new IOException("Invalid app-data type");
            if(!kind.isEmpty()&&(!new File(root,"data/"+pkg+"/"+kind).isFile()||!new File(root,"data/@pm@/kv").isFile()))throw new IOException("App data is missing native signing metadata");
        }
        JSONArray categories=snapshot.getJSONArray("categories");Set<String> allowed=VaultSystemData.CATEGORIES;
        if(categories.length()>allowed.size())throw new IOException("Too many system-data categories");Set<String> seen=new HashSet<>();
        for(int i=0;i<categories.length();i++)if(!allowed.contains(categories.getString(i))||!seen.add(categories.getString(i)))throw new IOException("Unknown or duplicate system-data category");
    }
    static void checkInstalled(Context c,JSONObject app)throws Exception{
        PackageInfo p=c.getPackageManager().getPackageInfo(app.getString("package"),PackageManager.GET_SIGNING_CERTIFICATES);
        if(p.applicationInfo==null||p.applicationInfo.uid<10000)throw new IOException("Protected package cannot be restored");
        List<String> expected=new ArrayList<>();JSONArray a=app.getJSONArray("signers");for(int i=0;i<a.length();i++)expected.add(a.getString(i));Collections.sort(expected);
        if(!expected.equals(signers(p)))throw new IOException("Installed app signing certificate does not match this backup");
        if(p.getLongVersionCode()<app.getLong("version"))throw new IOException("Install the backed-up app version or newer before restoring data");
    }
}
