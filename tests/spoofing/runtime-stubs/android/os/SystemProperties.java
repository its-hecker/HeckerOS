/* SPDX-License-Identifier: Apache-2.0 */
package android.os;
public class SystemProperties {public static String get(String key,String def){return key.equals("ro.boot.vbmeta.digest")?"1111111111111111111111111111111111111111111111111111111111111111":def;}public static void set(String key,String value){throw new AssertionError("No global property writes expected");}}
