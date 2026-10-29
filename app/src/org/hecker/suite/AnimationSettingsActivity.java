// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;
import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;
public final class AnimationSettingsActivity extends Activity {
    private static final String[] KEYS={Settings.Global.WINDOW_ANIMATION_SCALE,Settings.Global.TRANSITION_ANIMATION_SCALE,Settings.Global.ANIMATOR_DURATION_SCALE};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);LinearLayout page=Ui.page(this,R.string.animations_title);
        Switch flow=new Switch(this);flow.setText(R.string.living_flow_title);
        flow.setChecked(Settings.Secure.getInt(getContentResolver(),"hecker_living_flow",1)!=0);
        flow.setOnClickListener(v->{
            try { if(!Settings.Secure.putInt(getContentResolver(),"hecker_living_flow",flow.isChecked()?1:0))
                flow.setChecked(!flow.isChecked()); }
            catch(SecurityException unavailable){flow.setChecked(!flow.isChecked());}
        });page.addView(flow);
        page.addView(Ui.text(this,getString(R.string.living_flow_summary),14,false));
        Switch lottie=new Switch(this);lottie.setText(R.string.animations_lottie);
        lottie.setChecked(Settings.Secure.getInt(getContentResolver(),"hecker_lottie_enabled",1)==1);
        lottie.setOnCheckedChangeListener((button,checked)->Settings.Secure.putInt(getContentResolver(),"hecker_lottie_enabled",checked?1:0));page.addView(lottie);
        page.addView(Ui.text(this,getString(R.string.animations_scale_summary),15,false));
        RadioGroup group=new RadioGroup(this);String[] names=getResources().getStringArray(R.array.animation_speeds);float[] scales={0f,.5f,1f,1.5f};
        float current=Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f);
        for(int n=0;n<names.length;n++){RadioButton radio=new RadioButton(this);radio.setText(names[n]);radio.setId(n+1);group.addView(radio);if(current==scales[n])group.check(n+1);}
        group.setOnCheckedChangeListener((g,id)->{
            if(id<1 || id>scales.length)return;
            float[] previous=new float[KEYS.length];for(int n=0;n<KEYS.length;n++)previous[n]=Settings.Global.getFloat(getContentResolver(),KEYS[n],1f);
            try { for(String key:KEYS)if(!Settings.Global.putFloat(getContentResolver(),key,scales[id-1]))throw new SecurityException(); }
            catch(SecurityException unavailable){for(int n=0;n<KEYS.length;n++){try{Settings.Global.putFloat(getContentResolver(),KEYS[n],previous[n]);}catch(SecurityException ignored){}}Toast.makeText(this,R.string.native_page_unavailable,Toast.LENGTH_LONG).show();}
        });page.addView(group);Ui.scroll(this,page);
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);
    }
}
