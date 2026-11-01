// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.os.Bundle;
import android.os.IBinder;

/** The ROM broker authenticates every call. No su, shell process or writable script. */
final class SensorRecoveryClient {
    static Bundle call(boolean recover){
        try{
            IBinder binder=(IBinder)Class.forName("android.os.ServiceManager")
                    .getMethod("getService",String.class).invoke(null,"hecker_sensor_recovery");
            if(binder!=null){
                Object service=Class.forName("android.os.IHeckerSensorRecoveryService$Stub")
                        .getMethod("asInterface",IBinder.class).invoke(null,binder);
                return (Bundle)Class.forName("android.os.IHeckerSensorRecoveryService")
                        .getMethod(recover?"requestRecovery":"getState").invoke(service);
            }
        }catch(ReflectiveOperationException|RuntimeException unavailable){}
        Bundle value=new Bundle();value.putString("status","unavailable");return value;
    }
    private SensorRecoveryClient(){}
}
