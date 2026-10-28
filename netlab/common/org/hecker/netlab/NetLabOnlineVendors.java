// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import javax.net.ssl.HttpsURLConnection;

/** Prefix-only public registry enrichment. A vendor is not evidence of a device model or OS. */
public final class NetLabOnlineVendors implements AutoCloseable {
    private static final long DAY=86_400_000L,RETRY=15*60_000L;
    private static final int LIMIT=512;
    public static final String ENDPOINT="https://api.maclookup.app/v2/macs/";
    public interface Transport {
        Reply get(String prefix)throws IOException;
        default void cancel(){}
    }
    public static final class Reply {
        public final int status;public final String body;public final long retryMillis;
        public Reply(int status,String body,long retryMillis){this.status=status;this.body=body;this.retryMillis=retryMillis;}
    }
    private static final class Entry {
        final String vendor;final long expires;
        Entry(String vendor,long expires){this.vendor=vendor;this.expires=expires;}
    }
    private final Map<String,Entry> cache=new LinkedHashMap<>();
    private final Consumer<String> persist;private final Transport transport;private final LongSupplier clock;
    private long retryAt;private volatile boolean closed;
    public NetLabOnlineVendors(String encoded,Consumer<String> persist,Transport transport,LongSupplier clock){
        this.persist=persist;this.transport=transport;this.clock=clock;
        if(encoded==null||encoded.length()>1024*1024)return;
        Properties values=new Properties();try{values.load(new StringReader(encoded));}catch(IOException|IllegalArgumentException invalid){return;}
        long now=clock.getAsLong();
        try{retryAt=Math.min(now+6*60*60_000L,Long.parseLong(values.getProperty("retry","0")));}catch(NumberFormatException ignored){}
        for(String key:values.stringPropertyNames())if(key.startsWith("vendor.")&&key.substring(7).matches("[0-9A-F]{6}")&&cache.size()<LIMIT){
            String prefix=key.substring(7);try{
                long expiry=Long.parseLong(values.getProperty("expires."+prefix,"0"));String vendor=company(values.getProperty(key,""));
                if(expiry>now&&expiry<=now+31*DAY&&vendor!=null)cache.put(prefix,new Entry(vendor,expiry));
            }catch(NumberFormatException ignored){}
        }
    }
    public static String prefix(String mac){
        String value=NetLabSavedDevices.mac(mac);
        return value.isEmpty()||(Integer.parseInt(value.substring(0,2),16)&2)!=0?"":value.substring(0,6);
    }
    public synchronized String cached(String mac){Entry e=cache.get(prefix(mac));return e!=null&&e.expires>clock.getAsLong()?e.vendor:"";}
    public synchronized boolean due(String mac){
        if(closed||prefix(mac).isEmpty()||clock.getAsLong()<retryAt)return false;
        Entry e=cache.get(prefix(mac));return e==null||e.expires<=clock.getAsLong();
    }
    public String lookup(String mac,BooleanSupplier cancelled){
        if(cancelled.getAsBoolean()||!due(mac))return cached(mac);
        String prefix=prefix(mac);Reply reply;
        try{reply=transport.get(prefix);}catch(IOException|RuntimeException unavailable){
            if(!closed&&!cancelled.getAsBoolean())backoff(RETRY);return cached(mac);
        }
        if(closed||cancelled.getAsBoolean())return cached(mac);
        if(reply.status==200){
            String vendor=reply.body==null||reply.body.trim().isEmpty()?null:company(reply.body);
            if(vendor!=null){synchronized(this){
                if(closed||cancelled.getAsBoolean())return cached(mac);
                put(prefix,new Entry(vendor,clock.getAsLong()+(vendor.isEmpty()?DAY:30*DAY)));save();
            }return vendor;}
        }
        if(reply.status==404){synchronized(this){if(closed||cancelled.getAsBoolean())return cached(mac);put(prefix,new Entry("",clock.getAsLong()+DAY));save();}return "";}
        backoff(Math.max(RETRY,Math.min(6*60*60_000L,reply.retryMillis)));return cached(mac);
    }
    private void put(String prefix,Entry entry){cache.remove(prefix);cache.put(prefix,entry);while(cache.size()>LIMIT)cache.remove(cache.keySet().iterator().next());}
    private synchronized void backoff(long delay){if(!closed){retryAt=clock.getAsLong()+delay;save();}}
    private static String company(String body){
        if(body==null||body.length()>1024)return null;
        String value=body.trim();if(value.equals("*NO COMPANY*")||value.equals("*PRIVATE*")||value.equalsIgnoreCase("PRIVATE")||value.toUpperCase(Locale.ROOT).contains("IEEE REGISTRATION AUTHORITY")||value.isEmpty())return "";
        if(value.length()>160||value.startsWith("{")||value.startsWith("[")||value.contains("<")||value.indexOf('\ufffd')>=0
                ||value.matches("(?s).*[\\p{Cntrl}\\p{Cf}].*"))return null;
        return value;
    }
    private synchronized void save(){
        Properties values=new Properties();values.setProperty("retry",Long.toString(retryAt));
        for(Map.Entry<String,Entry> item:cache.entrySet()){
            values.setProperty("vendor."+item.getKey(),item.getValue().vendor);values.setProperty("expires."+item.getKey(),Long.toString(item.getValue().expires));}
        StringWriter text=new StringWriter();try{values.store(text,null);}catch(IOException impossible){throw new IllegalStateException(impossible);}
        persist.accept(text.toString());
    }
    public void cancel(){transport.cancel();}
    @Override public void close(){synchronized(this){closed=true;}cancel();}
    public static Transport https(){return new Transport(){
        private volatile HttpsURLConnection active;
        public Reply get(String prefix)throws IOException{
            if(!prefix.matches("[0-9A-F]{6}"))throw new IOException("Invalid manufacturer prefix");
            // Default routing retains the user's VPN; never bind an Internet lookup to the scanned LAN.
            HttpsURLConnection connection=(HttpsURLConnection)new URL(ENDPOINT+prefix+"/company/name").openConnection();active=connection;
            connection.setConnectTimeout(1500);connection.setReadTimeout(1500);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept","text/plain");connection.setRequestProperty("User-Agent","HeckerOS-NetLab/1");
            try{
                int status=connection.getResponseCode();long retry=0;
                try{retry=Math.min(21600,Math.max(0,Long.parseLong(connection.getHeaderField("Retry-After"))))*1000;}catch(NumberFormatException ignored){}
                if(status!=200)return new Reply(status,"",retry);
                ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream input=connection.getInputStream()){
                    byte[] bytes=new byte[256];int count;while((count=input.read(bytes))!=-1){if(out.size()+count>1024)throw new IOException("Registry response too large");out.write(bytes,0,count);}}
                return new Reply(status,new String(out.toByteArray(),StandardCharsets.UTF_8),retry);
            }finally{connection.disconnect();if(active==connection)active=null;}
        }
        public void cancel(){HttpsURLConnection current=active;if(current!=null)current.disconnect();}
    };}
}
