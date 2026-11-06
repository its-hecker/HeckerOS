// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Provider-based, merge-only personal data. Never copies system databases or hardware keys. */
final class VaultSystemData {
    static final Set<String> CATEGORIES=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "wifi","contacts","messages","calls","wallpapers","settings","launcher")));
    private static final String[] SMS={"address","date","date_sent","read","seen","type","body","status","reply_path_present","subject","service_center","locked","error_code"};
    private static final String[] CALLS={"number","date","duration","type","new","is_read","name","numbertype","numberlabel","presentation","features","via_number"};
    private static final String[] MMS={"_id","date","date_sent","msg_box","read","seen","sub","sub_cs","ct_t","ct_l","exp","m_cls","m_type","v","m_size","pri","rr","rpt_a","resp_st","st","tr_id","retr_st","retr_txt","retr_txt_cs","read_status","ct_cls","resp_txt","d_tm","m_id","locked"};
    private static final String[] PART={"_id","seq","ct","name","chset","cd","fn","cid","cl","ctt_s","ctt_t","text","_data"};
    private static final String MARKER="vnd.android.cursor.item/vnd.org.hecker.vault.snapshot";
    private static final int MAX_ROWS=1_000_000;
    private VaultSystemData(){}
    private static File file(File root,String name)throws IOException{File parent=new File(root,"system");if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot stage personal data");return new File(parent,name);}
    interface RowAction{void row(JSONObject row)throws Exception;}
    static void check()throws IOException{if(Thread.currentThread().isInterrupted())throw new IOException("Operation cancelled");VaultSession s=VaultSession.current;if(s!=null)s.check();}
    private static JSONObject row(Cursor cursor)throws Exception{
        JSONObject result=new JSONObject();for(int i=0;i<cursor.getColumnCount();i++){if(cursor.isNull(i))continue;
            Object value;switch(cursor.getType(i)){case Cursor.FIELD_TYPE_INTEGER:value=cursor.getLong(i);break;
                case Cursor.FIELD_TYPE_FLOAT:value=cursor.getDouble(i);break;
                case Cursor.FIELD_TYPE_BLOB:byte[] blob=cursor.getBlob(i);if(blob.length>2*1024*1024)throw new IOException("Contact photo is too large");value=new JSONObject().put("blob",Base64.encodeToString(blob,Base64.NO_WRAP));break;
                default:value=cursor.getString(i);if(((String)value).length()>8*1024*1024)throw new IOException("Personal-data record is too large");}
            result.put(cursor.getColumnName(i),value);}return result;
    }
    private static ContentValues values(JSONObject row,String...allowed)throws Exception{
        ContentValues result=new ContentValues();for(String key:allowed){if(!row.has(key)||row.isNull(key))continue;Object v=row.get(key);
            if(v instanceof JSONObject){byte[] bytes=Base64.decode(((JSONObject)v).getString("blob"),Base64.NO_WRAP);if(bytes.length>2*1024*1024)throw new IOException("Invalid contact photo");result.put(key,bytes);}
            else if(v instanceof Number){if(v instanceof Float||v instanceof Double)result.put(key,((Number)v).doubleValue());else result.put(key,((Number)v).longValue());}
            else if(v instanceof String){if(((String)v).length()>8*1024*1024)throw new IOException("Record is too large");result.put(key,(String)v);}
            else throw new IOException("Invalid personal-data field");}return result;
    }
    private static void lines(File path,RowAction action)throws Exception{
        try(Reader reader=new InputStreamReader(new FileInputStream(path),StandardCharsets.UTF_8)){
            // Bound line length while reading, before allocating an attacker-controlled line.
            StringBuilder line=new StringBuilder();char[] buffer=new char[8192];int size,count=0;
            while((size=reader.read(buffer))!=-1){check();for(int i=0;i<size;i++){char ch=buffer[i];
                if(ch=='\n'){if(++count>MAX_ROWS)throw new IOException("Too many personal-data records");
                    if(line.length()>0)action.row(new JSONObject(line.toString()));line.setLength(0);
                }else{if(line.length()>=16*1024*1024)throw new IOException("Personal-data record is too large");line.append(ch);}}}
            if(line.length()!=0)throw new IOException("Incomplete personal-data record");}
    }
    private static void table(Context c,File target,Uri uri,String[] projection)throws Exception{
        try(Cursor cursor=c.getContentResolver().query(uri,projection,null,null,null);
                BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target),StandardCharsets.UTF_8))){
            if(cursor==null)throw new IOException("Personal-data provider unavailable");int count=0;
            while(cursor.moveToNext()){check();if(++count>MAX_ROWS)throw new IOException("Too many records");out.write(row(cursor).toString());out.newLine();}}
    }
    static void capture(Context c,String category,File root)throws Exception{
        check();switch(category){
            case "wifi":{byte[] data=c.getSystemService(WifiManager.class).retrieveBackupData();if(data==null||data.length==0||data.length>16*1024*1024)throw new IOException("Wi-Fi backup unavailable");
                java.nio.file.Files.write(file(root,"wifi.bin").toPath(),data);break;}
            case "calls":table(c,file(root,"calls.jsonl"),CallLog.Calls.CONTENT_URI,CALLS);break;
            case "messages":table(c,file(root,"sms.jsonl"),Telephony.Sms.CONTENT_URI,SMS);captureMms(c,root);break;
            case "contacts":captureContacts(c,root);break;
            case "wallpapers":{WallpaperManager wm=c.getSystemService(WallpaperManager.class);boolean any=false;
                for(int flag:new int[]{WallpaperManager.FLAG_SYSTEM,WallpaperManager.FLAG_LOCK}){
                    try(ParcelFileDescriptor fd=wm.getWallpaperFile(flag)){if(fd==null)continue;
                        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.getFileDescriptor()));OutputStream out=new FileOutputStream(file(root,"wallpaper-"+flag))){copy(in,out,512L*1024*1024);}any=true;}}
                if(!any)throw new IOException("No static wallpaper is available");break;}
            case "settings":{JSONObject data=new JSONObject();for(String which:new String[]{"secure","system"}){
                Uri uri=which.equals("secure")?Settings.Secure.CONTENT_URI:Settings.System.CONTENT_URI;JSONObject items=new JSONObject();
                try(Cursor cursor=c.getContentResolver().query(uri,new String[]{"name","value"},null,null,null)){
                    if(cursor==null)throw new IOException("Settings provider unavailable");while(cursor.moveToNext()){
                        String key=cursor.getString(0);if(settingAllowed(key))items.put(key,cursor.isNull(1)?JSONObject.NULL:cursor.getString(1));}}
                data.put(which,items);}VaultStorage.json(file(root,"settings.json"),data);break;}
            case "launcher":break; // Captured/restored through the launcher's native backup agent by VaultEngine.
            default:throw new IOException("Unsupported personal-data category");
        }
    }
    static boolean settingAllowed(String key){return key!=null&&key.matches("hecker_[a-z0-9_]{1,120}")
            &&!key.matches(".*(keybox|password|secret|token|credential|trusted_signer|private_key).*");}
    static void validate(File root,JSONArray categories)throws Exception{
        for(int i=0;i<categories.length();i++){String category=categories.getString(i);
            switch(category){
                case "wifi":{File f=file(root,"wifi.bin");if(!f.isFile()||f.length()==0||f.length()>16*1024*1024)throw new IOException("Invalid Wi-Fi backup");break;}
                case "calls":lines(file(root,"calls.jsonl"),r->{values(r,CALLS);if(!r.has("date")||!r.has("type"))throw new IOException("Invalid call log");});break;
                case "messages":lines(file(root,"sms.jsonl"),r->{values(r,SMS);if(!r.has("date")||!r.has("type"))throw new IOException("Invalid SMS record");});
                    lines(file(root,"mms.jsonl"),r->{values(r,MMS);long id=r.getLong("_id");if(id<0)throw new IOException("Invalid MMS identifier");
                        JSONArray addresses=r.getJSONArray("addresses"),parts=r.getJSONArray("parts");if(addresses.length()>10000||parts.length()>10000)throw new IOException("Invalid MMS record");
                        for(int j=0;j<addresses.length();j++)values(addresses.getJSONObject(j),"address","type","charset");
                        for(int j=0;j<parts.length();j++){JSONObject p=parts.getJSONObject(j);values(p,PART);if(p.has("binary")){
                            String name=p.getString("binary");if(!name.matches("mms-"+id+"-[0-9]+\\.bin")||!file(root,name).isFile())throw new IOException("Missing or invalid MMS attachment");}}});break;
                case "contacts":lines(file(root,"contacts.jsonl"),r->{if(r.getLong("source")<0)throw new IOException("Invalid contact identifier");JSONArray rows=r.getJSONArray("rows");
                    if(rows.length()>1000)throw new IOException("Too many contact fields");for(int j=0;j<rows.length();j++)values(rows.getJSONObject(j),contactColumns());});break;
                case "settings":{JSONObject data=VaultStorage.json(file(root,"settings.json"));for(String which:new String[]{"secure","system"}){
                    JSONObject items=data.getJSONObject(which);Iterator<String> keys=items.keys();while(keys.hasNext()){String key=keys.next();if(!settingAllowed(key)||!items.isNull(key)&&items.getString(key).length()>65536)throw new IOException("Invalid Hecker setting");}}break;}
                case "wallpapers":{boolean any=false;for(int flag:new int[]{WallpaperManager.FLAG_SYSTEM,WallpaperManager.FLAG_LOCK}){
                    File f=file(root,"wallpaper-"+flag);if(f.isFile()){if(f.length()==0||f.length()>512L*1024*1024)throw new IOException("Invalid wallpaper");
                        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                        android.graphics.BitmapFactory.decodeFile(f.getPath(),bounds);
                        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>64_000_000L)throw new IOException("Unsupported wallpaper dimensions");any=true;}}
                    if(!any)throw new IOException("Missing wallpaper");break;}
                case "launcher":break;
                default:throw new IOException("Unknown personal-data category");
            }
        }
    }
    static void restore(Context c,String category,File root,String snapshot)throws Exception{
        check();switch(category){
            case "wifi":{File f=file(root,"wifi.bin");if(f.length()>16*1024*1024)throw new IOException("Invalid Wi-Fi backup");
                c.getSystemService(WifiManager.class).restoreBackupData(java.nio.file.Files.readAllBytes(f.toPath()));break;}
            case "contacts":restoreContacts(c,root,snapshot);break;
            case "calls":lines(file(root,"calls.jsonl"),r->{ContentValues v=values(r,CALLS);
                if(!exists(c,CallLog.Calls.CONTENT_URI,"number IS ? AND date=? AND type=? AND duration=?",new String[]{v.getAsString("number"),v.getAsString("date"),v.getAsString("type"),v.getAsString("duration")}))
                    if(c.getContentResolver().insert(CallLog.Calls.CONTENT_URI,v)==null)throw new IOException("Call log restore failed");});break;
            case "messages":withSmsWrite(c,()->{lines(file(root,"sms.jsonl"),r->{ContentValues v=values(r,SMS);
                    // Never resurrect a queued message in a form that might send automatically.
                    Integer type=v.getAsInteger("type");if(type==null||type<1||type>3){v.put("type",3);v.put("status",-1);}
                    if(!exists(c,Telephony.Sms.CONTENT_URI,"address IS ? AND date=? AND type=? AND body IS ?",new String[]{v.getAsString("address"),v.getAsString("date"),v.getAsString("type"),v.getAsString("body")}))
                        if(c.getContentResolver().insert(Telephony.Sms.CONTENT_URI,v)==null)throw new IOException("SMS restore failed");});restoreMms(c,root,snapshot);});break;
            case "wallpapers":{WallpaperManager wm=c.getSystemService(WallpaperManager.class);for(int flag:new int[]{WallpaperManager.FLAG_SYSTEM,WallpaperManager.FLAG_LOCK}){
                File f=file(root,"wallpaper-"+flag);if(f.isFile())try(InputStream in=new FileInputStream(f)){wm.setStream(in,null,true,flag);}}break;}
            case "settings":{JSONObject data=VaultStorage.json(file(root,"settings.json"));for(String which:new String[]{"secure","system"}){
                JSONObject items=data.getJSONObject(which);Iterator<String> keys=items.keys();while(keys.hasNext()){String key=keys.next();if(!settingAllowed(key))throw new IOException("Invalid Hecker setting");
                    String value=items.isNull(key)?null:items.getString(key);if(value!=null&&value.length()>65536)throw new IOException("Setting is too large");
                    boolean ok=which.equals("secure")?Settings.Secure.putString(c.getContentResolver(),key,value):Settings.System.putString(c.getContentResolver(),key,value);
                    if(!ok)throw new IOException("Cannot restore Hecker setting");}}break;}
            case "launcher":break;
            default:throw new IOException("Unsupported personal-data category");
        }
    }
    private static boolean exists(Context c,Uri uri,String where,String[] args)throws IOException{
        try(Cursor cursor=c.getContentResolver().query(uri,new String[]{"_id"},where,args,null)){if(cursor==null)throw new IOException("Provider unavailable");return cursor.moveToFirst();}
    }
    interface Action{void run()throws Exception;}
    private static void withSmsWrite(Context c,Action action)throws Exception{
        AppOpsManager ops=c.getSystemService(AppOpsManager.class);int uid=android.os.Process.myUid();
        int before=ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_WRITE_SMS,uid,c.getPackageName());
        VaultStorage.prefs(c).edit().putInt("sms_write_before",before).commit();
        try{ops.setUidMode(AppOpsManager.OPSTR_WRITE_SMS,uid,AppOpsManager.MODE_ALLOWED);action.run();}
        finally{ops.setUidMode(AppOpsManager.OPSTR_WRITE_SMS,uid,before);VaultStorage.prefs(c).edit().remove("sms_write_before").commit();}
    }
    static void recoverSmsWrite(Context c){
        if(VaultStorage.prefs(c).contains("sms_write_before")){
            c.getSystemService(AppOpsManager.class).setUidMode(AppOpsManager.OPSTR_WRITE_SMS,android.os.Process.myUid(),VaultStorage.prefs(c).getInt("sms_write_before",AppOpsManager.MODE_DEFAULT));
            VaultStorage.prefs(c).edit().remove("sms_write_before").commit();
        }
    }
    private static void copy(InputStream in,OutputStream out,long limit)throws IOException{
        byte[] b=new byte[65536];long total=0;int n;while((n=in.read(b))!=-1){check();total+=n;if(total>limit)throw new IOException("Personal-data item too large");out.write(b,0,n);}
    }
    private static String[] contactColumns(){String[] fields=new String[16];fields[0]="mimetype";for(int i=1;i<=15;i++)fields[i]="data"+i;return fields;}
    private static void captureContacts(Context c,File root)throws Exception{
        try(Cursor raw=c.getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI,new String[]{"_id"},"account_type IS NULL AND deleted=0",null,null);
                BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file(root,"contacts.jsonl")),StandardCharsets.UTF_8))){
            if(raw==null)throw new IOException("Contacts provider unavailable");int total=0;
            while(raw.moveToNext()){check();if(++total>100000)throw new IOException("Too many contacts");long id=raw.getLong(0);JSONArray rows=new JSONArray();
                try(Cursor data=c.getContentResolver().query(ContactsContract.Data.CONTENT_URI,contactColumns(),"raw_contact_id=?",new String[]{Long.toString(id)},null)){
                    if(data==null)throw new IOException("Contact data unavailable");while(data.moveToNext()){
                        JSONObject item=row(data);String mime=item.optString("mimetype");if(mime.equals(MARKER)||mime.equals(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE))continue;
                        if(rows.length()>=1000)throw new IOException("Contact has too many fields");rows.put(item);}}
                out.write(new JSONObject().put("source",id).put("rows",rows).toString());out.newLine();}}
    }
    private static void restoreContacts(Context c,File root,String snapshot)throws Exception{
        lines(file(root,"contacts.jsonl"),r->{String source=snapshot+":"+r.getLong("source");
            if(exists(c,ContactsContract.Data.CONTENT_URI,"mimetype=? AND data1=?",new String[]{MARKER,source}))return;
            ArrayList<ContentProviderOperation> batch=new ArrayList<>();batch.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue("account_type",null).withValue("account_name",null).build());
            batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference("raw_contact_id",0)
                    .withValue("mimetype",MARKER).withValue("data1",source).build());JSONArray rows=r.getJSONArray("rows");
            if(rows.length()>1000)throw new IOException("Contact has too many fields");
            for(int i=0;i<rows.length();i++){ContentValues v=values(rows.getJSONObject(i),contactColumns());String mime=v.getAsString("mimetype");
                if(mime==null||mime.equals(MARKER)||mime.equals(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE))throw new IOException("Invalid contact data");
                batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference("raw_contact_id",0).withValues(v).build());}
            c.getContentResolver().applyBatch(ContactsContract.AUTHORITY,batch);});
    }
    private static void captureMms(Context c,File root)throws Exception{
        Uri uri=Uri.parse("content://mms");try(Cursor cursor=c.getContentResolver().query(uri,MMS,null,null,null);
                BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file(root,"mms.jsonl")),StandardCharsets.UTF_8))){
            if(cursor==null)throw new IOException("MMS provider unavailable");int count=0;
            while(cursor.moveToNext()){check();if(++count>MAX_ROWS)throw new IOException("Too many MMS messages");JSONObject item=row(cursor);long id=item.getLong("_id");JSONArray addresses=new JSONArray(),parts=new JSONArray();
                try(Cursor addr=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/addr"),new String[]{"address","type","charset"},null,null,null)){
                    if(addr==null)throw new IOException("MMS addresses unavailable");while(addr.moveToNext()){if(addresses.length()>=10000)throw new IOException("Too many MMS recipients");addresses.put(row(addr));}}
                try(Cursor part=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/part"),PART,null,null,null)){
                    if(part==null)throw new IOException("MMS attachments unavailable");while(part.moveToNext()){JSONObject p=row(part);long partId=p.getLong("_id");
                        if(parts.length()>=10000)throw new IOException("Too many MMS parts");
                        if(p.has("_data")){File target=file(root,"mms-"+id+"-"+partId+".bin");try(InputStream in=c.getContentResolver().openInputStream(Uri.parse("content://mms/part/"+partId));OutputStream data=new FileOutputStream(target)){
                            if(in==null)throw new IOException("MMS attachment unavailable");copy(in,data,512L*1024*1024);}p.put("binary",target.getName());}
                        p.remove("_data");p.remove("_id");parts.put(p);}}
                item.put("addresses",addresses).put("parts",parts);out.write(item.toString());out.newLine();}}
    }
    private static void restoreMms(Context c,File root,String snapshot)throws Exception{
        try(VaultReceipts receipts=new VaultReceipts(c)){
        lines(file(root,"mms.jsonl"),r->{long source=r.getLong("_id");String receipt="mms_"+snapshot+"_"+source;
            long previous=receipts.target(receipt);if(previous>=0&&exists(c,Uri.parse("content://mms/"+previous),null,null))return;
            ContentValues header=values(r,MMS);header.remove("_id");Integer box=header.getAsInteger("msg_box");if(box==null||box<1||box>3)header.put("msg_box",3);
            JSONArray addresses=r.getJSONArray("addresses"),parts=r.getJSONArray("parts");if(addresses.length()>10000||parts.length()>10000)throw new IOException("MMS record is too large");
            Set<String> recipients=new HashSet<>();for(int i=0;i<addresses.length();i++){String a=addresses.getJSONObject(i).optString("address");if(!a.isEmpty()&&!a.equals("insert-address-token"))recipients.add(a);}
            if(!recipients.isEmpty())header.put("thread_id",Telephony.Threads.getOrCreateThreadId(c,recipients));
            // A stable message identifier avoids duplicates when the same archive is imported again after a reset.
            String messageId=header.getAsString("m_id");if(messageId!=null&&!messageId.isEmpty()&&exists(c,Uri.parse("content://mms"),"m_id=? AND date=? AND msg_box=?",new String[]{messageId,header.getAsString("date"),header.getAsString("msg_box")}))return;
            Uri created=c.getContentResolver().insert(Uri.parse("content://mms"),header);if(created==null)throw new IOException("MMS restore failed");long id=ContentUris.parseId(created);boolean complete=false;
            try{for(int i=0;i<addresses.length();i++)if(c.getContentResolver().insert(Uri.parse("content://mms/"+id+"/addr"),values(addresses.getJSONObject(i),"address","type","charset"))==null)throw new IOException("MMS address restore failed");
                for(int i=0;i<parts.length();i++){JSONObject p=parts.getJSONObject(i);ContentValues v=values(p,PART);v.remove("_id");v.remove("_data");v.put("mid",id);
                    Uri part=c.getContentResolver().insert(Uri.parse("content://mms/"+id+"/part"),v);if(part==null)throw new IOException("MMS attachment restore failed");
                    if(p.has("binary")){String name=p.getString("binary");if(!name.matches("mms-"+source+"-[0-9]+\\.bin"))throw new IOException("Invalid MMS attachment path");
                        try(InputStream in=new FileInputStream(file(root,name));OutputStream data=c.getContentResolver().openOutputStream(part,"w")){if(data==null)throw new IOException("MMS attachment unavailable");copy(in,data,512L*1024*1024);}}}
                receipts.put(receipt,id);complete=true;
            }finally{if(!complete)c.getContentResolver().delete(created,null,null);}});
        }
    }
}
