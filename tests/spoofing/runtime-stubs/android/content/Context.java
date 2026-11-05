/* SPDX-License-Identifier: Apache-2.0 */
package android.content;
public class Context {
 public final android.content.pm.PackageManager packages=new android.content.pm.PackageManager();
 public android.content.pm.PackageManager getPackageManager(){return packages;}
 public android.content.ContentResolver getContentResolver(){return null;}
 public boolean applicationReady=true;
 public Context getApplicationContext(){return applicationReady?this:null;}
}
