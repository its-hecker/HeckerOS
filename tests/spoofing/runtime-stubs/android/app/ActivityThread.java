/* SPDX-License-Identifier: Apache-2.0 */
package android.app;
public class ActivityThread {
    public static android.content.pm.IPackageManager packages;
    public static android.content.pm.IPackageManager getPackageManager(){return packages;}
    public static android.content.Context currentApplication(){
        return new android.content.Context(){
            @Override public android.content.pm.PackageManager getPackageManager(){
                return new android.content.pm.PackageManager(){
                    @Override public String[] getPackagesForUid(int uid){
                        try{return ActivityThread.packages.getPackagesForUid(uid);}catch(Exception e){return null;}
                    }
                    @Override public android.content.pm.PackageInfo getPackageInfo(String name,int flags) throws NameNotFoundException{
                        try{return ActivityThread.packages.getPackageInfo(name,flags,0);}catch(Exception e){throw new NameNotFoundException();}
                    }
                };
            }
        };
    }
}
