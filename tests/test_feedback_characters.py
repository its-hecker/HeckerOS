# SPDX-License-Identifier: Apache-2.0
"""Bounded character choreography and real supplied image payloads."""
from pathlib import Path
import unittest
from PIL import Image
from test_motion_island import java_run
ROOT=Path(__file__).resolve().parents[1]
class CharacterTests(unittest.TestCase):
    def test_three_reference_cutouts_have_real_alpha(self):
        for name in ('blue_monster','goblin','ninja'):
            with Image.open(ROOT/f'framework/motionisland/{name}.png') as image:
                self.assertIn('A',image.getbands())
                self.assertEqual(image.getchannel('A').getextrema(),(0,255))
                self.assertGreater(image.width,300)
    def test_all_choreography_is_finite_bounded_and_distinct(self):
        sources={'com/android/systemui/hecker/PoseProbe.java':'''package com.android.systemui.hecker;
public class PoseProbe{public static void main(String[]args){
 double[] signatures=new double[3];int checks=0;
 for(int character=1;character<=3;character++)for(int action=1;action<=11;action++)for(int side=-1;side<=1;side++)for(long age=0;age<=2400;age+=8){
 float[] value=new float[5];MotionCharacterPose.sample(value,character,action,side,age);
 for(float v:value)if(!Float.isFinite(v))throw new AssertionError("Non-finite pose");
 if(Math.abs(value[0])>.4||Math.abs(value[1])>.2||Math.abs(value[2])>30||value[3]<.9||value[3]>1.1||value[4]<.9||value[4]>1.1)throw new AssertionError("Clipped or inverted character");
 signatures[character-1]+=Math.abs(value[0])+Math.abs(value[1])+Math.abs(value[2]);checks++;
 }
 if(signatures[0]==signatures[1]||signatures[1]==signatures[2]||signatures[0]==signatures[2])throw new AssertionError("Identical characters");
 System.out.println(checks+" finite, bounded character/action/direction frames PASS");}}
'''}
        java_run(sources,[ROOT/'framework/motionisland/MotionIslandState.java',ROOT/'framework/motionisland/MotionCharacterPose.java'],'com.android.systemui.hecker.PoseProbe')
if __name__=='__main__':unittest.main()
