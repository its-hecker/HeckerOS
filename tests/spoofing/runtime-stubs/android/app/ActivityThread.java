/* SPDX-License-Identifier: Apache-2.0 */
package android.app;
public class ActivityThread {
    public static android.content.pm.IPackageManager packages;
    public static android.content.pm.IPackageManager getPackageManager(){return packages;}
}
