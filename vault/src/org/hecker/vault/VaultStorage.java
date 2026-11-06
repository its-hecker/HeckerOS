// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

final class VaultStorage {
    static final String MIME="application/vnd.hecker.vault";
    static final class Archive {
        final Uri uri;final String name;final long size;
        Archive(Uri uri,String name,long size){this.uri=uri;this.name=name;this.size=size;}
    }
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("vault",Context.MODE_PRIVATE);}
    static Uri folder(Context c)throws IOException{
        String raw=prefs(c).getString("folder",null);if(raw==null)throw new IOException("Choose a backup folder first");
        Uri uri=Uri.parse(raw);if(!"content".equals(uri.getScheme())||!DocumentsContract.isTreeUri(uri))throw new IOException("Invalid backup folder");return uri;
    }
    static List<Archive> list(Context c)throws IOException{
        Uri tree=folder(c);Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        List<Archive> result=new ArrayList<>();try(Cursor cursor=c.getContentResolver().query(children,new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_SIZE},null,null,null)){
            if(cursor==null)throw new IOException("Backup folder is unavailable");
            while(cursor.moveToNext()){String name=cursor.getString(1);if(name!=null&&name.endsWith(".hvault")){
                if(result.size()>=10000)throw new IOException("Too many archives in this folder");
                result.add(new Archive(DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)),name,cursor.isNull(2)?0:cursor.getLong(2)));}}
        }catch(SecurityException e){throw new IOException("Backup folder permission was lost; choose it again",e);}
        result.sort(Comparator.comparing((Archive a)->a.name).reversed());return result;
    }
    static Uri create(Context c,String name)throws IOException{
        Uri tree=folder(c);Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        Uri uri=DocumentsContract.createDocument(c.getContentResolver(),parent,MIME,name);
        if(uri==null)throw new IOException("Cannot create archive in the selected folder");return uri;
    }
    static void committed(Context c,Uri uri,int retention){
        // Only archives created by this installation are eligible for automatic retention deletion.
        try{org.json.JSONArray old=new org.json.JSONArray(prefs(c).getString("owned","[]"));
            List<String> ids=new ArrayList<>();ids.add(uri.toString());for(int i=0;i<old.length();i++)ids.add(old.getString(i));
            if(retention>0)while(ids.size()>retention){String remove=ids.get(ids.size()-1);
                if(!DocumentsContract.deleteDocument(c.getContentResolver(),Uri.parse(remove)))break;ids.remove(ids.size()-1);}
            prefs(c).edit().putString("owned",new org.json.JSONArray(ids).toString()).apply();
        }catch(Exception ignored){/* A provider refusing retention must never invalidate the completed backup. */}
    }
    static void remove(Context c,Uri uri)throws IOException{
        // Restrict deletion to a currently enumerated archive in the selected tree.
        for(Archive a:list(c))if(a.uri.equals(uri)){if(!DocumentsContract.deleteDocument(c.getContentResolver(),uri))throw new IOException("Cannot delete archive");return;}
        throw new IOException("Archive no longer exists in the selected folder");
    }
    static File staging(Context c)throws IOException{
        File parent=new File(c.getNoBackupFilesDir(),"staging");if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create private staging");
        File root=new File(parent,UUID.randomUUID().toString());if(!root.mkdir())throw new IOException("Cannot prepare private staging");return root;
    }
    static void checkSpace(File root,long needed)throws IOException{
        if(needed<0||root.getUsableSpace()<needed+128L*1024*1024)throw new IOException("Not enough free storage for backup staging");
    }
    static void json(File file,org.json.JSONObject object)throws IOException{
        try(FileOutputStream out=new FileOutputStream(file)){out.write(object.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
    }
    static org.json.JSONObject json(File file)throws Exception{
        if(file.length()>4*1024*1024)throw new IOException("Snapshot metadata is too large");
        return new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
    }
}
