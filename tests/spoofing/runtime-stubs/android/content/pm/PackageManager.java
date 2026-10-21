/* SPDX-License-Identifier: Apache-2.0 */
package android.content.pm;
public class PackageManager {public static final int GET_SIGNING_CERTIFICATES=0x08000000;
public final java.util.Map<Integer,String[]> names=new java.util.HashMap<>();public String[] getPackagesForUid(int uid){return names.get(uid);}
public PackageInfo getPackageInfo(String name,int flags) throws NameNotFoundException{return null;}
public static class NameNotFoundException extends Exception {}}
