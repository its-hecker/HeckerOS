// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.io.*;
import java.util.*;

/** User labels keyed by an observed unicast MAC, independently of scan history or IP addresses. */
public final class NetLabSavedDevices {
    public static final int LIMIT=512;
    public static final class Device {
        public final String mac,name; public final boolean trusted;
        Device(String mac,String name,boolean trusted){this.mac=mac;this.name=name;this.trusted=trusted;}
    }
    private final Map<String,Device> devices=new LinkedHashMap<>();
    public NetLabSavedDevices(String encoded){
        if(encoded==null||encoded.length()>1024*1024)return;
        Properties values=new Properties();
        try{values.load(new StringReader(encoded));}catch(IOException|IllegalArgumentException invalid){return;}
        for(String key:new TreeSet<>(values.stringPropertyNames()))if(key.startsWith("name.")&&devices.size()<LIMIT){
            String mac=mac(key.substring(5));if(mac.isEmpty())continue;
            String name=name(values.getProperty(key,""));boolean trusted="true".equals(values.getProperty("trusted."+mac));
            if(!name.isEmpty()||trusted)devices.put(mac,new Device(mac,name,trusted));
        }
    }
    public static String mac(String input){
        if(input==null||!input.matches("(?i)([0-9a-f]{12}|[0-9a-f]{2}(:[0-9a-f]{2}){5}|[0-9a-f]{2}(-[0-9a-f]{2}){5})"))return "";
        String value=input.replace(":","").replace("-","").toUpperCase(Locale.ROOT);
        return value.equals("000000000000")||(Integer.parseInt(value.substring(0,2),16)&1)!=0?"":value;
    }
    public static String name(String input){
        String value=input==null?"":input.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        return value.substring(0,value.offsetByCodePoints(0,Math.min(64,value.codePointCount(0,value.length()))));
    }
    public Device get(String mac){return devices.get(mac(mac));}
    public String label(String mac,String fallback){Device d=get(mac);return d==null||d.name.isEmpty()?fallback:d.name;}
    public boolean save(String address,String name,boolean trusted){
        String key=mac(address);if(key.isEmpty())return false;
        String clean=name(name);
        if(clean.isEmpty()&&!trusted){devices.remove(key);return true;}
        if(!devices.containsKey(key)&&devices.size()>=LIMIT)return false;
        devices.put(key,new Device(key,clean,trusted));return true;
    }
    public List<Device> names(){
        List<Device> result=new ArrayList<>();for(Device d:devices.values())if(!d.name.isEmpty())result.add(d);
        result.sort(Comparator.comparing((Device d)->d.name,String.CASE_INSENSITIVE_ORDER).thenComparing(d->d.mac));
        return Collections.unmodifiableList(result);
    }
    public String encode(){
        Properties values=new Properties();for(Device d:devices.values()){
            values.setProperty("name."+d.mac,d.name);values.setProperty("trusted."+d.mac,Boolean.toString(d.trusted));}
        StringWriter result=new StringWriter();try{values.store(result,null);}catch(IOException impossible){throw new IllegalStateException(impossible);}
        return result.toString();
    }
}
