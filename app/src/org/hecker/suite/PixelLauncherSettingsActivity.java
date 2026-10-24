// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;
import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

public final class PixelLauncherSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout page=Ui.page(this,R.string.pixel_launcher_title);
        page.addView(Ui.text(this,getString(R.string.pixel_launcher_description),15,false));
        toggle(page,R.string.pixel_double_tap,"double_tap",1);
        page.addView(Ui.text(this,getString(R.string.pixel_glass_mode),17,true));
        Spinner mode=new Spinner(this);
        String[] choices={getString(R.string.pixel_glass_off),getString(R.string.pixel_glass_frosted),getString(R.string.pixel_glass_liquid)};
        mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));
        mode.setSelection(Math.max(0,Math.min(2,read("glass",1))));
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){write("glass",pos);}});page.addView(mode);
        int[] titles={R.string.pixel_glass_dock,R.string.pixel_glass_search,R.string.pixel_glass_folders,R.string.pixel_glass_widgets};
        for(int i=0;i<4;i++){final int bit=1<<i;Switch control=new Switch(this);control.setText(titles[i]);control.setTextColor(getColor(R.color.hecker_foreground));
            control.setChecked((read("glass_targets",15)&bit)!=0);control.setOnCheckedChangeListener((v,enabled)->{int value=read("glass_targets",15);write("glass_targets",enabled?value|bit:value&~bit);});page.addView(control);}
        String[] corners={"corner_tl","corner_tr","corner_br","corner_bl"};int[] labels={R.string.pixel_corner_tl,R.string.pixel_corner_tr,R.string.pixel_corner_br,R.string.pixel_corner_bl};
        for(int i=0;i<4;i++){final String key=corners[i];TextView label=Ui.text(this,getString(labels[i]),14,false);page.addView(label);
            SeekBar slider=new SeekBar(this);slider.setContentDescription(label.getText());slider.setMax(48);slider.setProgress(Math.max(0,Math.min(48,read(key,28))));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){write(key,v.getProgress());}public void onProgressChanged(SeekBar v,int n,boolean user){}});page.addView(slider);}
        page.addView(Ui.text(this,getString(R.string.pixel_launcher_apply),13,false));Ui.scroll(this,page);
    }
    private int read(String key,int fallback){return Settings.Secure.getInt(getContentResolver(),"hecker_pixel_"+key,fallback);}
    private void write(String key,int value){if(!Settings.Secure.putInt(getContentResolver(),"hecker_pixel_"+key,value))Toast.makeText(this,R.string.memory_save_failed,Toast.LENGTH_LONG).show();}
    private void toggle(LinearLayout page,int label,String key,int fallback){Switch control=new Switch(this);control.setText(label);control.setTextColor(getColor(R.color.hecker_foreground));control.setChecked(read(key,fallback)!=0);control.setOnCheckedChangeListener((v,enabled)->write(key,enabled?1:0));page.addView(control);}
}
