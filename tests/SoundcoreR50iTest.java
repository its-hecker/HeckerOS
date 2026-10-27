// SPDX-License-Identifier: GPL-3.0-or-later
import me.kavishdevar.librepods.hecker.SoundcoreR50iCodec;
import java.nio.file.*;
import java.util.Arrays;

public final class SoundcoreR50iTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("R50i check "+checks);}
    static void checksum(byte[] frame){int sum=0;for(int i=0;i<frame.length-1;i++)sum+=frame[i]&255;frame[frame.length-1]=(byte)sum;}
    static byte[] packet(byte[] body){
        byte[] data=new byte[body.length+10];byte[] header={9,(byte)255,0,0,1,1,1};
        System.arraycopy(header,0,data,0,7);data[7]=(byte)data.length;data[8]=(byte)(data.length>>8);
        System.arraycopy(body,0,data,9,body.length);checksum(data);return data;
    }
    static SoundcoreR50iCodec.State parse(byte[] frame){return new SoundcoreR50iCodec().feed(frame,frame.length);}
    public static void main(String[] args)throws Exception{
        String text=Files.readString(Path.of("tests/fixtures/soundcore-r50i-state.hex"));
        String[] fields=text.lines().filter(s->!s.startsWith("#")).reduce("",(a,b)->a+" "+b).trim().split("\\s+");
        byte[] body=new byte[fields.length];for(int i=0;i<body.length;i++)body[i]=(byte)Integer.parseInt(fields[i],16);
        byte[] frame=packet(body);var state=parse(frame);
        check(state!=null&&state.left==60&&state.right==60);
        check("19.43".equals(state.leftFirmware)&&"19.43".equals(state.rightFirmware));
        check(Boolean.FALSE.equals(state.leftCharging)&&Boolean.FALSE.equals(state.rightCharging));
        check(Arrays.equals(SoundcoreR50iCodec.requestState(),new byte[]{8,(byte)238,0,0,0,1,1,10,0,2}));
        check(SoundcoreR50iCodec.matchesModel("soundcore R50i"));check(SoundcoreR50iCodec.matchesModel("SOUNDCORE P20i"));
        check(!SoundcoreR50iCodec.matchesModel("soundcore R50i NC"));check(!SoundcoreR50iCodec.matchesModel("unrelated device"));
        for(int split=1;split<frame.length;split++){
            var parser=new SoundcoreR50iCodec();check(parser.feed(Arrays.copyOf(frame,split),split)==null);
            var result=parser.feed(Arrays.copyOfRange(frame,split,frame.length),frame.length-split);check(result!=null&&result.left==60);
        }
        var streaming=new SoundcoreR50iCodec();for(int i=0;i<frame.length;i++)check((streaming.feed(new byte[]{frame[i]},1)!=null)==(i==frame.length-1));
        byte[] bad=frame.clone();bad[bad.length-1]^=1;check(parse(bad)==null);
        byte[] noise=new byte[bad.length+frame.length+2];noise[0]=1;noise[1]=2;
        System.arraycopy(bad,0,noise,2,bad.length);System.arraycopy(frame,0,noise,bad.length+2,frame.length);check(parse(noise)!=null);
        byte[] changed=body.clone();changed[2]=0;changed[3]=(byte)255;state=parse(packet(changed));
        check(state!=null&&state.left==0&&state.right==-1&&state.rightCharging==null);
        changed[2]=6;check(parse(packet(changed))==null);changed=body.clone();changed[16]='X';check(parse(packet(changed))==null);
        check(parse(packet(Arrays.copyOf(body,32)))==null);var bounded=new SoundcoreR50iCodec();
        check(bounded.feed(new byte[5000],5000)==null);check(bounded.feed(frame,frame.length)!=null);
        bad=frame.clone();bad[5]=2;checksum(bad);check(parse(bad)==null);
        System.out.println("R50i read-only status protocol: "+checks+" model, packet, checksum and fragmentation checks PASS");
    }
}
