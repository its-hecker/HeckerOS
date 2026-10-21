/* SPDX-License-Identifier: Apache-2.0 */
package android.os;
public class Binder {public static int uid=1000; public static int getCallingUid(){return uid;}
public static long clearCallingIdentity(){int old=uid;uid=1000;return old;} public static void restoreCallingIdentity(long old){uid=(int)old;}}
