/* SPDX-License-Identifier: Apache-2.0 */
package android.provider;
public final class Settings {
 public static final class Global {
  public static final java.util.Map<String,String> values = new java.util.HashMap<>();
  public static boolean unavailable; public static int reads;
  public static String getString(android.content.ContentResolver resolver,String name){
   reads++;if(unavailable)throw new IllegalStateException("provider unavailable");return values.get(name);
  }
  public static int getInt(android.content.ContentResolver resolver,String name,int fallback){
   String value=getString(resolver,name);return value==null?fallback:Integer.parseInt(value);
  }
 }
 public static final class Secure {
  public static final java.util.Map<String,String> values = new java.util.HashMap<>();
  public static String getString(android.content.ContentResolver resolver,String name){return values.get(name);}
  public static int getInt(android.content.ContentResolver resolver,String name,int fallback){
   String value=values.get(name);return value==null?fallback:Integer.parseInt(value);
  }
 }
}
