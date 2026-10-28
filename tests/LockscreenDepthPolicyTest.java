// SPDX-License-Identifier: Apache-2.0
import com.android.systemui.hecker.DepthWallpaperGeometry;
import com.android.systemui.hecker.WordClockText;
import java.util.Arrays;

public final class LockscreenDepthPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(Arrays.equals(WordClockText.lines(4,57), new String[]{"IT’S","THREE","TO","FIVE"}), "Reference 4:57");
        check(Arrays.equals(WordClockText.lines(23,59), new String[]{"IT’S","ONE","TO","TWELVE"}), "Midnight rollover");
        check(Arrays.equals(WordClockText.lines(0,0), new String[]{"IT’S","TWELVE","O’CLOCK"}), "Midnight");
        check(WordClockText.lines(12,30)[1].equals("HALF"), "Half past");
        check(WordClockText.lines(12,45)[1].equals("QUARTER"), "Quarter to");
        for (int hour=0; hour<24; hour++) for (int minute=0; minute<60; minute++) {
            String[] words=WordClockText.lines(hour,minute);
            check(words.length>=3 && words.length<=4, "Bounded word layout");
            check(!String.join(" ",words).contains("null"), "Every minute has wording");
            check(Arrays.equals(words,WordClockText.lines(hour+24,minute)), "24-hour rollover");
        }
        int[][] sizes={{1080,1920,1440,3040},{1600,900,1080,2400},{900,1600,2400,1080},{800,800,720,1600}};
        int crops=0;
        for (int[] size:sizes) for (int frame=0; frame<300; frame++) for (boolean foreground:new boolean[]{false,true}) {
            float t=frame/299f;
            float[] crop=DepthWallpaperGeometry.crop(size[0],size[1],size[2],size[3],t,foreground);
            check(Arrays.equals(crop,DepthWallpaperGeometry.crop(size[0],size[1],size[2],size[3],t,!foreground)),
                    "An ordinary photo cannot have a second displaced subject edge");
            check(crop[1]<=.001f && crop[2]<=.001f, "Crop fills start edges");
            check(crop[1]+crop[0]*size[0]>=size[2]-.001f, "Crop fills width");
            check(crop[2]+crop[0]*size[1]>=size[3]-.001f, "Crop fills height");
            // A foreground pixel remains at the same screen location when drawn in the clock's local canvas.
            float global=crop[2]+size[1]*.4f*crop[0], clockTop=173.5f;
            check(Math.abs(global-((global-clockTop)+clockTop))<.001f, "Clock mask aligns with backdrop");
            crops++;
        }
        check(!DepthWallpaperGeometry.validIdentity("../foreground"), "No paths as identities");
        check(!DepthWallpaperGeometry.validIdentity("a".repeat(31)), "No truncated identity");
        check(!DepthWallpaperGeometry.validIdentity(null), "Missing identity");
        check(DepthWallpaperGeometry.validIdentity("0123456789abcdef0123456789abcdef"), "Published identity");
        try { DepthWallpaperGeometry.crop(0,100,100,100,0,true); throw new AssertionError("Empty image accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("Reference word clock: all 1440 minutes; depth crop/alignment: "+crops+" frames/aspects; invalid identities PASS");
    }
}
