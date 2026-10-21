/* SPDX-License-Identifier: Apache-2.0 */
package android.content.pm;
public class Signature {
    private final byte[] data;
    public Signature(byte[] value){data=value.clone();}
    public byte[] toByteArray(){return data.clone();}
}
