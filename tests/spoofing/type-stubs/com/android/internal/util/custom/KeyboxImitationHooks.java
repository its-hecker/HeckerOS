package com.android.internal.util.custom;
public class KeyboxImitationHooks {
 public static void setSuccessFlag(boolean value){}
 public static android.system.keystore2.KeyEntryResponse onGetKeyEntry(android.system.keystore2.KeyDescriptor d){return null;}
 public static android.system.keystore2.KeyMetadata generateKey(android.system.keystore2.IKeystoreSecurityLevel s,android.system.keystore2.KeyDescriptor d,java.util.Collection<android.hardware.security.keymint.KeyParameter> a){return null;}
}
