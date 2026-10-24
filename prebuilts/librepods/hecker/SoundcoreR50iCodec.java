// SPDX-License-Identifier: GPL-3.0-or-later
// A3949 wire format and captured fixture: Oppzippy/OpenSCQ30, GPL-3.0-or-later.
package me.kavishdevar.librepods.hecker;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Bounded read-only status parser. No setting or firmware write commands. */
public final class SoundcoreR50iCodec {
    private static final byte[] HEADER = {9, (byte)255, 0, 0, 1};
    private byte[] pending = new byte[0];
    public static byte[] requestState() { return new byte[]{8,(byte)238,0,0,0,1,1,10,0,2}; }
    public static boolean matchesModel(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return n.equals("soundcorer50i") || n.equals("soundcorep20i") || n.equals("soundcorep25i")
                || n.equals("r50i") || n.equals("p20i") || n.equals("p25i") || n.equals("a3949");
    }
    public static final class State {
        public final int left, right;
        public final Boolean leftCharging, rightCharging;
        public final String leftFirmware, rightFirmware;
        State(int l,int r,Boolean lc,Boolean rc,String lf,String rf) {
            left=l;right=r;leftCharging=lc;rightCharging=rc;leftFirmware=lf;rightFirmware=rf;
        }
    }
    public State feed(byte[] input, int count) {
        if (count<0 || count>input.length) throw new IllegalArgumentException("Invalid byte count");
        if (pending.length+count>4096) pending=new byte[0];
        if (count>4096) return null;
        byte[] data=Arrays.copyOf(pending,pending.length+count);
        System.arraycopy(input,0,data,pending.length,count);
        int offset=0;
        while (data.length-offset>=9) {
            boolean header=true;
            for (int i=0;i<5;i++) if (data[offset+i]!=HEADER[i]) header=false;
            if (!header) {offset++;continue;}
            int length=(data[offset+7]&255)|((data[offset+8]&255)<<8);
            if (length<10 || length>1024) {offset++;continue;}
            if (data.length-offset<length) break;
            int sum=0; for(int i=0;i<length-1;i++) sum+=data[offset+i]&255;
            if ((sum&255)!=(data[offset+length-1]&255)) {offset++;continue;}
            State state=null;
            if (length>=83 && data[offset+5]==1 && data[offset+6]==1)
                state=body(Arrays.copyOfRange(data,offset+9,offset+length-1));
            offset+=length;
            if (state!=null) {pending=Arrays.copyOfRange(data,offset,data.length);return state;}
        }
        pending=Arrays.copyOfRange(data,offset,data.length);return null;
    }
    private static State body(byte[] b) {
        if (b.length<73 || (b[0]&255)>1 || (b[1]&255)>1) return null;
        // Validate the response model; R50i NC/A3959 uses a different layout.
        if (!new String(b,16,16,StandardCharsets.US_ASCII).matches("3949[A-Z0-9]{12}")) return null;
        int left=level(b[2]),right=level(b[3]);
        String lf=firmware(b,6),rf=firmware(b,11);
        if (left==-2 || right==-2 || lf==null && rf==null) return null;
        return new State(left,right,charging(b[4],left),charging(b[5],right),lf,rf);
    }
    private static int level(byte v) {int n=v&255;return n==255?-1:n<=5?n*20:-2;}
    private static Boolean charging(byte v,int battery) {return battery<0 || (v&255)>1?null:v==1;}
    private static String firmware(byte[] b,int offset) {
        String s=new String(b,offset,5,StandardCharsets.US_ASCII);
        return s.matches("[0-9]{2}\\.[0-9]{2}")?s:null;
    }
}
