// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.hecker;
import android.Manifest;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads Android's connected, bonded audio devices; no Apple protocol on other brands. */
final class ConnectedHeadsets {
    static List<BluetoothDevice> devices(Context context) {
        List<BluetoothDevice> result=new ArrayList<>();
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) return result;
        try {
            BluetoothManager manager=context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null || !adapter.isEnabled()) return result;
            for(BluetoothDevice d:adapter.getBondedDevices()) {
                if(!Boolean.TRUE.equals(BluetoothDevice.class.getMethod("isConnected").invoke(d))) continue;
                String name=d.getName();
                boolean apple=name!=null && name.toLowerCase(Locale.ROOT).contains("airpods");
                if(d.getUuids()!=null) for(var uuid:d.getUuids())
                    if(uuid.getUuid().toString().equals("74ec2172-0bad-4d01-8f77-997b2be0722a")) apple=true;
                BluetoothClass c=d.getBluetoothClass();
                int kind=c==null?-1:c.getDeviceClass();
                boolean audio=kind==1024 || kind==1028 || kind==1032 || kind==1048 || kind==1052;
                if(!apple && (audio || SoundcoreR50iCodec.matchesModel(name))) result.add(d);
            }
        } catch(ReflectiveOperationException | SecurityException unavailable) { result.clear(); }
        result.sort(Comparator.comparing(ConnectedHeadsets::name));
        return result;
    }
    static String name(BluetoothDevice device) {
        try {String value=device.getName();return value==null?"Bluetooth audio":value;}
        catch(SecurityException unavailable){return "Bluetooth audio";}
    }
    static int battery(BluetoothDevice device) {
        try {return valid((Integer)BluetoothDevice.class.getMethod("getBatteryLevel").invoke(device));}
        catch(ReflectiveOperationException | SecurityException unavailable){return -1;}
    }
    static int metadata(BluetoothDevice device,String field) {
        try {
            int key=BluetoothDevice.class.getField(field).getInt(null);
            byte[] bytes=(byte[])BluetoothDevice.class.getMethod("getMetadata",int.class).invoke(device,key);
            return bytes==null?-1:valid(Integer.parseInt(new String(bytes,StandardCharsets.US_ASCII).trim()));
        } catch(ReflectiveOperationException | NumberFormatException | SecurityException unavailable){return -1;}
    }
    private static int valid(int value){return value>=0 && value<=100?value:-1;}
}
