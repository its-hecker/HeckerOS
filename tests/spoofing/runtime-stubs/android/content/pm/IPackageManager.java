/* SPDX-License-Identifier: Apache-2.0 */
package android.content.pm;
public interface IPackageManager {
    String[] getPackagesForUid(int uid) throws android.os.RemoteException;
    PackageInfo getPackageInfo(String name,long flags,int user) throws android.os.RemoteException;
}
