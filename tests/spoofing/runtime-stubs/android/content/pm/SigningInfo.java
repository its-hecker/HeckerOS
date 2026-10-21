/* SPDX-License-Identifier: Apache-2.0 */
package android.content.pm;
public class SigningInfo {
    private final Signature[] data;
    public SigningInfo(Signature value){data=new Signature[]{value};}
    public boolean hasMultipleSigners(){return false;}
    public Signature[] getApkContentsSigners(){return data.clone();}
    public Signature[] getSigningCertificateHistory(){return data.clone();}
}
