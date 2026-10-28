// SPDX-License-Identifier: Apache-2.0
import com.android.systemui.hecker.HeckerSliderValues;
import java.text.NumberFormat;
import java.util.Locale;
public final class HeckerSliderValuesTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        check(HeckerSliderValues.percent(-1)==0&&HeckerSliderValues.percent(2)==100);
        check(HeckerSliderValues.percent(Float.NaN)==0&&HeckerSliderValues.percent(Float.POSITIVE_INFINITY)==0);
        check(HeckerSliderValues.fraction(5,5,5)==0&&HeckerSliderValues.fraction(5,10,1)==0);
        check(HeckerSliderValues.fraction(Float.NaN,0,1)==0);
        int previous=-1;
        for(int value=0;value<=65535;value++){
            int percent=HeckerSliderValues.percent(HeckerSliderValues.fraction(value,0,65535));
            check(percent>=previous&&percent>=0&&percent<=100);previous=percent;
        }
        for(int maximum=1;maximum<=100;maximum++)for(int value=0;value<=maximum;value++){
            float fraction=HeckerSliderValues.fraction(value,0,maximum);
            check(HeckerSliderValues.percent(fraction)==Math.round(100f*value/maximum));
        }
        // A nonzero allowed minimum is not mute: 1 of 15 still displays 7%.
        check(HeckerSliderValues.percent(HeckerSliderValues.fraction(1,0,15))==7);
        for(Locale locale:new Locale[]{Locale.US,Locale.FRANCE,Locale.CHINA,Locale.forLanguageTag("ar-PK"),Locale.forLanguageTag("ur-PK")}){
            for(int percent:new int[]{0,1,49,50,99,100}){
                String label=HeckerSliderValues.label(percent/100f,locale);
                check(!label.isEmpty());double parsed=NumberFormat.getPercentInstance(locale).parse(label).doubleValue();
                check(Math.abs(parsed-percent/100.0)<.00001);
            }
        }
        check(HeckerSliderValues.verticalThickness(1)==64&&HeckerSliderValues.verticalThickness(2)==80);
        check(HeckerSliderValues.verticalThickness(Float.NaN)==64&&HeckerSliderValues.verticalThickness(10)==96);
        System.out.println("Slider labels: full native gamma range, stream indices, muted/nonzero minimum, invalid ranges, five locales and font scaling PASS");
    }
}
