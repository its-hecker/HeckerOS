// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;
import android.os.Bundle;
import org.hecker.netlab.NetLabSavedDevices;

/** Owner metadata in credential-encrypted private app storage; discovery history is opt-in. */
final class NetLabPreferences {
    private final SharedPreferences preferences;
    private JSONObject metadata;
    private NetLabSavedDevices saved;
    private final Map<String,String> sessionNames=new HashMap<>();
    private final Map<String,Boolean> sessionTrust=new HashMap<>();
    NetLabPreferences(Context context) {
        preferences = context.getSharedPreferences("netlab_private", Context.MODE_PRIVATE);
        try { String raw=preferences.getString("metadata", "{}"); metadata=raw.length()>96*1024?new JSONObject():new JSONObject(raw); }
        catch (JSONException | ClassCastException invalid) { metadata=new JSONObject(); }
        saved=new NetLabSavedDevices(read("saved_devices_v1"));
    }
    private String read(String key){try{return preferences.getString(key,"");}catch(ClassCastException invalid){return "";}}
    String lookupCache(){return read("online_vendor_cache_v1");}
    void lookupCache(String encoded){preferences.edit().putString("online_vendor_cache_v1",encoded).apply();}
    boolean onlineIdentity(){return preferences.getBoolean("online_identity",true);}
    void onlineIdentity(boolean enabled){preferences.edit().putBoolean("online_identity",enabled).apply();}
    private void migrateHotspot(Bundle device){
        String mac=NetLabSavedDevices.mac(device.getString("mac","")),id=device.getString("id","");
        if(mac.isEmpty()||saved.get(mac)!=null||!id.equalsIgnoreCase("hotspot:"+device.getString("mac","")))return;
        JSONObject old=metadata.optJSONObject(id);if(old==null)return;
        String name=old.optString("name","");boolean trust=old.optBoolean("trusted");
        if((!name.isEmpty()||trust)&&saved.save(mac,name,trust)){
            old.remove("name");old.remove("trusted");persist();persistNames();
        }
    }
    String customName(Bundle device){
        migrateHotspot(device);String mac=NetLabSavedDevices.mac(device.getString("mac",""));
        if(!mac.isEmpty())return saved.label(mac,"");
        String id=device.getString("id","");return sessionNames.containsKey(id)?sessionNames.get(id):label(id,"");
    }
    String label(Bundle device,String fallback){String custom=customName(device);return custom.isEmpty()?fallback:custom;}
    boolean trusted(Bundle device){
        migrateHotspot(device);NetLabSavedDevices.Device entry=saved.get(device.getString("mac",""));
        return entry!=null?entry.trusted:sessionTrust.getOrDefault(device.getString("id",""),trusted(device.getString("id","")));
    }
    boolean save(Bundle device,String name,boolean trusted){
        String mac=NetLabSavedDevices.mac(device.getString("mac","")),id=device.getString("id","");
        if(!mac.isEmpty()){
            if(!saved.save(mac,name,trusted))return false;persistNames();
        }else{sessionNames.put(id,NetLabSavedDevices.name(name));sessionTrust.put(id,trusted);}
        JSONObject old=metadata.optJSONObject(id);if(old!=null){old.remove("name");old.remove("trusted");persist();}
        return true;
    }
    List<NetLabSavedDevices.Device> savedNames(){return saved.names();}
    void clearSession(){sessionNames.clear();sessionTrust.clear();}
    private void persistNames(){preferences.edit().putString("saved_devices_v1",saved.encode()).apply();}
    String label(String id, String fallback) { JSONObject item=metadata.optJSONObject(id);return item==null?fallback:item.optString("name",fallback); }
    boolean trusted(String id) { JSONObject item=metadata.optJSONObject(id);return item!=null&&item.optBoolean("trusted"); }
    boolean history() { return preferences.getBoolean("history_enabled",false); }
    void history(boolean enabled) { preferences.edit().putBoolean("history_enabled",enabled).apply();if(!enabled)clearHistory(); }
    void save(String id,String name,boolean trusted) {
        try { JSONObject item=metadata.optJSONObject(id);if(item==null)item=new JSONObject();
            item.put("name",name.trim().substring(0,Math.min(name.trim().length(),64)));item.put("trusted",trusted);
            metadata.put(id,item);trim();persist();
        } catch(JSONException ignored) { /* Only locally bounded strings/booleans are written. */ }
    }
    void seen(String id,String address,long when) {
        if(!history())return;
        try {JSONObject item=metadata.optJSONObject(id);if(item==null)item=new JSONObject();
            if(when-item.optLong("last")<30_000&&address.equals(item.optString("address")))return;
            item.put("last",when);item.put("address",address);metadata.put(id,item);trim();persist();
        }catch(JSONException ignored){}
    }
    void seen(Bundle device,String address,long when){
        if(!history())return;String id=device.getString("id","");seen(id,address,when);
        JSONObject item=metadata.optJSONObject(id);String mac=NetLabSavedDevices.mac(device.getString("mac",""));
        if(item!=null&&!mac.isEmpty()&&!mac.equals(item.optString("mac")))try{item.put("mac",mac);persist();}catch(JSONException ignored){}
    }
    List<android.os.Bundle> remembered() {
        List<android.os.Bundle> result=new ArrayList<>();if(!history())return result;
        long cutoff=System.currentTimeMillis()-7*24*60*60*1000L;
        for(Iterator<String> it=metadata.keys();it.hasNext();){String id=it.next();JSONObject item=metadata.optJSONObject(id);
            if(item==null||item.optLong("last")<cutoff)continue;
            android.os.Bundle device=new android.os.Bundle();device.putString("id",id);device.putBoolean("online",false);
            device.putBoolean("managed",id.startsWith("hotspot:"));device.putString("mac",item.optString("mac",id.startsWith("hotspot:")?id.substring(8):""));
            device.putString("state","history");device.putLong("last",item.optLong("last"));
            device.putStringArrayList("addresses",new ArrayList<>(Collections.singletonList(item.optString("address",""))));result.add(device);
        }return result;
    }
    void clearHistory(){
        for(Iterator<String> it=metadata.keys();it.hasNext();){JSONObject item=metadata.optJSONObject(it.next());if(item!=null){item.remove("last");item.remove("address");}}
        trim();persist();
    }
    void clearAll(){metadata=new JSONObject();saved=new NetLabSavedDevices("");clearSession();preferences.edit().remove("metadata").remove("saved_devices_v1").remove("online_vendor_cache_v1").putBoolean("history_enabled",false).apply();}
    private void trim(){
        List<String> remove=new ArrayList<>();long cutoff=System.currentTimeMillis()-7*24*60*60*1000L;
        for(Iterator<String> it=metadata.keys();it.hasNext();){String key=it.next();JSONObject item=metadata.optJSONObject(key);
            if(item==null)remove.add(key);else{if(item.optLong("last")<cutoff){item.remove("last");item.remove("address");}
                if(item.optString("name").isEmpty()&&!item.optBoolean("trusted")&&!item.has("last"))remove.add(key);}}
        for(String key:remove)metadata.remove(key);
        if(metadata.length()>128){List<String> keys=new ArrayList<>();for(Iterator<String> it=metadata.keys();it.hasNext();)keys.add(it.next());
            keys.sort(Comparator.<String>comparingInt(k->{JSONObject item=metadata.optJSONObject(k);return item.optBoolean("trusted")||!item.optString("name").isEmpty()?1:0;})
                    .thenComparingLong(k->metadata.optJSONObject(k).optLong("last")));
            for(int i=0;i<keys.size()-128;i++)metadata.remove(keys.get(i));}
    }
    private void persist(){preferences.edit().putString("metadata",metadata.toString()).apply();}
    int download(){return preferences.getInt("download_kbps",2048);}int upload(){return preferences.getInt("upload_kbps",512);}
    void preset(int down,int up){preferences.edit().putInt("download_kbps",down).putInt("upload_kbps",up).apply();}
}
