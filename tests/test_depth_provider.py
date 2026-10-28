# SPDX-License-Identifier: Apache-2.0
"""Execute the production read-only provider against identity/path/user boundary cases."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]

class DepthProviderTests(unittest.TestCase):
    def test_active_pair_and_reader_boundaries(self):
        sources={
            'android/content/ContentProvider.java':'''package android.content;
public abstract class ContentProvider { public Context context=new Context(); public Context getContext(){return context;}
 public abstract boolean onCreate(); public abstract android.os.ParcelFileDescriptor openFile(android.net.Uri uri,String mode) throws java.io.FileNotFoundException;
 public abstract String getType(android.net.Uri uri); public abstract android.database.Cursor query(android.net.Uri u,String[]p,String s,String[]a,String sort);
 public abstract android.net.Uri insert(android.net.Uri u,ContentValues v); public abstract int update(android.net.Uri u,ContentValues v,String s,String[]a);
 public abstract int delete(android.net.Uri u,String s,String[]a); }''',
            'android/content/ContentValues.java':'package android.content;public class ContentValues{}',
            'android/database/Cursor.java':'package android.database;public interface Cursor{}',
            'android/content/ContentResolver.java':'package android.content;public class ContentResolver {public String active;}',
            'android/content/Context.java':'''package android.content;
public class Context {public java.io.File directory;public final ContentResolver resolver=new ContentResolver();
 public android.content.pm.ApplicationInfo getApplicationInfo(){return new android.content.pm.ApplicationInfo();}
 public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
 public ContentResolver getContentResolver(){return resolver;}public java.io.File getFilesDir(){return directory;} }''',
            'android/content/pm/ApplicationInfo.java':'package android.content.pm;public class ApplicationInfo{public int uid=100;}',
            'android/content/pm/PackageManager.java':'''package android.content.pm;
public class PackageManager{public static final int SIGNATURE_MATCH=0;public static boolean signed=true;
 public String[] getPackagesForUid(int uid){return uid==200?new String[]{"com.android.systemui"}:new String[]{"third.party"};}
 public int checkSignatures(String a,String b){return signed?0:-1;} }''',
            'android/os/Binder.java':'package android.os;public class Binder {public static int uid=100;public static int getCallingUid(){return uid;}}',
            'android/os/ParcelFileDescriptor.java':'''package android.os;public class ParcelFileDescriptor{
 public static final int MODE_READ_ONLY=1; public java.io.File file;
 public static ParcelFileDescriptor open(java.io.File file,int mode)throws java.io.FileNotFoundException{if(!file.isFile())throw new java.io.FileNotFoundException();
 if(mode!=MODE_READ_ONLY)throw new AssertionError();ParcelFileDescriptor p=new ParcelFileDescriptor();p.file=file;return p;} }''',
            'android/net/Uri.java':'''package android.net;public class Uri {private final String raw;private Uri(String s){raw=s;}
 public static Uri parse(String s){return new Uri(s);}public java.util.List<String> getPathSegments(){return java.util.Arrays.asList(java.net.URI.create(raw).getPath().substring(1).split("/"));}}''',
            'android/provider/Settings.java':'''package android.provider;public class Settings{public static class Secure{
 public static String getString(android.content.ContentResolver r,String key){return r.active;} } }''',
            'DepthReaderProbe.java':'''import org.hecker.suite.DepthWallpaperProvider;
import android.os.Binder;import android.net.Uri;import java.io.File;
public class DepthReaderProbe {
 interface Attempt {void run()throws Exception;}
 static void denied(Attempt a)throws Exception{try{a.run();throw new AssertionError("Access accepted");}
 catch(SecurityException|java.io.FileNotFoundException expected){}}
 public static void main(String[]args)throws Exception{
  DepthWallpaperProvider p=new DepthWallpaperProvider();p.context.directory=new File(args[0],"user0");
  String id="0123456789abcdef0123456789abcdef";p.context.resolver.active=id;
  File layer=new File(p.context.directory,"depth/"+id+"/foreground.png");layer.getParentFile().mkdirs();layer.createNewFile();
  Uri uri=Uri.parse("content://org.hecker.suite.depth/"+id+"/foreground");
  if(!p.openFile(uri,"r").file.equals(layer))throw new AssertionError("Owner read");
  Binder.uid=300;denied(()->p.openFile(uri,"r"));denied(()->p.getType(uri));denied(()->p.query(uri,null,null,null,null));
  Binder.uid=200;android.content.pm.PackageManager.signed=false;denied(()->p.openFile(uri,"r"));
  android.content.pm.PackageManager.signed=true;
  if(!p.openFile(uri,"r").file.equals(layer))throw new AssertionError("SystemUI read");
  denied(()->p.openFile(uri,"rw"));denied(()->p.openFile(Uri.parse("content://org.hecker.suite.depth/../foreground"),"r"));
  denied(()->p.openFile(Uri.parse("content://org.hecker.suite.depth/"+id+"/unknown"),"r"));
  p.context.resolver.active="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";denied(()->p.openFile(uri,"r"));
  p.context.directory=new File(args[0],"user10");p.context.resolver.active=id;denied(()->p.openFile(uri,"r"));
  System.out.println("Production depth provider: owner/SystemUI signatures, active identity, read-only paths and per-user storage PASS");
 }
}''',
        }
        with tempfile.TemporaryDirectory(prefix='hecker-depth-provider-') as temporary:
            work=Path(temporary);files=[]
            for name,source in sources.items():
                p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);files.append(str(p))
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(work/'classes'),*files,
                str(ROOT/'app/src/org/hecker/suite/DepthWallpaperProvider.java')],check=True)
            subprocess.run(['java','-cp',str(work/'classes'),'DepthReaderProbe',str(work)],check=True)

if __name__=='__main__':unittest.main()
