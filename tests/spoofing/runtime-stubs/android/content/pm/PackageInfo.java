/* SPDX-License-Identifier: Apache-2.0 */
package android.content.pm;
public class PackageInfo {public String packageName;public Signature[] signatures;public static android.os.Parcelable.Creator<PackageInfo> CREATOR;
    public SigningInfo signingInfo;
    public long getLongVersionCode(){return 1L;}
}
