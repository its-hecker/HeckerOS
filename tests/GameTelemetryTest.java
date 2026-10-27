// SPDX-License-Identifier: Apache-2.0
package com.android.server.hecker;
public final class GameTelemetryTest {
    private static void eq(int wanted,int actual){if(wanted!=actual)throw new AssertionError(wanted+" != "+actual);}
    public static void main(String[] args){
        GameTelemetry sample=new GameTelemetry();
        eq(-1,sample.cpu("cpu 100 0 50 850 0 0 0 0 1000 1000"));
        eq(50,sample.cpu("cpu 125 0 75 900 0 0 0 0 3000 3000"));
        eq(-1,sample.cpu("cpu 125 0 75 900 0 0 0 0"));
        eq(-1,sample.cpu("permission denied"));
        eq(-1,sample.cpu("cpu 1 0 0 1 0 0 0 0"));
        eq(0,sample.cpu("cpu 1 0 0 10 0 0 0 0"));
        sample.reset();eq(-1,sample.cpu("cpu 1 2 3 4"));
        eq(61,GameTelemetry.gpu("61000 100000"));eq(0,GameTelemetry.gpu("0 100"));
        eq(-1,GameTelemetry.gpu("0 0"));eq(-1,GameTelemetry.gpu("101 100"));eq(-1,GameTelemetry.gpu("unavailable"));
        eq(72,GameTelemetry.ram(100,28));eq(0,GameTelemetry.ram(100,100));
        eq(-1,GameTelemetry.ram(0,0));eq(-1,GameTelemetry.ram(100,101));
        System.out.println("Game telemetry: real CPU deltas, counter resets, guest exclusion, GPU windows and RAM PASS");
    }
}
