// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

/** Settings for the Coral-only native companion; the existing Motion Sense page remains intact. */
public final class MotionFeedbackSettingsActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!"coral".equals(Build.DEVICE)) { finish(); return; }
        LinearLayout page = Ui.page(this, R.string.motion_feedback_title);
        LinearLayout intro = Ui.card(this);
        intro.addView(Ui.text(this, getString(R.string.motion_feedback_summary), 14, false));
        page.addView(intro);
        LinearLayout options = Ui.card(this);
        toggle(options, R.string.motion_island_title, "hecker_motion_island");
        options.addView(Ui.text(this, getString(R.string.motion_island_summary), 13, false));
        toggle(options, R.string.motion_creature_title, "hecker_motion_creature");
        options.addView(Ui.text(this, getString(R.string.motion_creature_summary), 13, false));
        options.addView(Ui.text(this,getString(R.string.motion_character),16,true));
        android.widget.RadioGroup characters=new android.widget.RadioGroup(this);
        int[] labels={R.string.motion_astronaut,R.string.motion_blue_monster,R.string.motion_goblin,R.string.motion_ninja};
        int current=Math.max(0,Math.min(3,Settings.Secure.getInt(getContentResolver(),"hecker_motion_character",0)));
        for(int i=0;i<labels.length;i++){
            android.widget.RadioButton choice=new android.widget.RadioButton(this);choice.setId(android.view.View.generateViewId());
            choice.setTag(i);choice.setText(labels[i]);choice.setTextColor(getColor(R.color.hecker_foreground));
            characters.addView(choice);if(i==current)characters.check(choice.getId());
        }
        characters.setOnCheckedChangeListener((group,id)->{android.view.View choice=group.findViewById(id);
            if(choice!=null&&!Settings.Secure.putInt(getContentResolver(),"hecker_motion_character",(Integer)choice.getTag()))
                Toast.makeText(this,R.string.motion_feedback_save_failed,Toast.LENGTH_SHORT).show();});
        options.addView(characters);
        int height=Math.max(26,Math.min(56,Settings.Secure.getInt(getContentResolver(),"hecker_motion_bar_height",32)));
        android.widget.TextView size=Ui.text(this,getString(R.string.motion_bar_height,height),16,true);options.addView(size);
        android.widget.SeekBar bar=new android.widget.SeekBar(this);bar.setMax(30);bar.setProgress(height-26);
        bar.setContentDescription(getString(R.string.motion_bar_height,height));
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar view,int progress,boolean user){
                size.setText(getString(R.string.motion_bar_height,progress+26));
                if(user)Settings.Secure.putInt(getContentResolver(),"hecker_motion_bar_height",progress+26);
            }
            public void onStartTrackingTouch(android.widget.SeekBar view){}
            public void onStopTrackingTouch(android.widget.SeekBar view){}
        });options.addView(bar);
        int width=Math.max(92,Math.min(220,Settings.Secure.getInt(getContentResolver(),"hecker_motion_bar_width",124)));
        android.widget.TextView widthLabel=Ui.text(this,getString(R.string.motion_bar_width,width),16,true);
        options.addView(widthLabel);
        android.widget.SeekBar widthBar=new android.widget.SeekBar(this);
        widthBar.setMax(128);widthBar.setProgress(width-92);
        widthBar.setContentDescription(getString(R.string.motion_bar_width,width));
        widthBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar view,int progress,boolean user){
                int newWidth=progress+92;
                widthLabel.setText(getString(R.string.motion_bar_width,newWidth));
                if(user&&!Settings.Secure.putInt(getContentResolver(),"hecker_motion_bar_width",newWidth))
                    Toast.makeText(MotionFeedbackSettingsActivity.this,R.string.motion_feedback_save_failed,
                            Toast.LENGTH_SHORT).show();
            }
            public void onStartTrackingTouch(android.widget.SeekBar view){}
            public void onStopTrackingTouch(android.widget.SeekBar view){}
        });options.addView(widthBar);
        position(options, R.string.motion_bar_horizontal, "hecker_motion_bar_x", -160, 160);
        position(options, R.string.motion_bar_vertical, "hecker_motion_bar_y", 0, 160);
        toggle(options, R.string.motion_media_events, "hecker_motion_media_events");
        page.addView(options);
        LinearLayout behavior = Ui.card(this);
        behavior.addView(Ui.text(this, getString(R.string.motion_feedback_behavior), 14, false));
        page.addView(behavior);
        Ui.scroll(this, page);
    }

    private void position(LinearLayout parent, int title, String key, int min, int max) {
        int value = Math.max(min, Math.min(max, Settings.Secure.getInt(getContentResolver(), key, 0)));
        android.widget.TextView label = Ui.text(this, getString(title) + " · " + value + " dp", 16, true); parent.addView(label);
        android.widget.SeekBar slider = new android.widget.SeekBar(this); slider.setMax(max-min); slider.setProgress(value-min);
        slider.setContentDescription(getString(title));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean user) {
                label.setText(getString(title) + " · " + (progress+min) + " dp");
                if(user) Settings.Secure.putInt(getContentResolver(), key, progress+min);
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        }); parent.addView(slider);
    }
    private void toggle(LinearLayout parent, int title, String key) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setTextColor(getColor(R.color.hecker_foreground));
        control.setMinHeight(Ui.dp(this, 56));
        control.setChecked(Settings.Secure.getInt(getContentResolver(), key, 1) != 0);
        control.setOnCheckedChangeListener((view, enabled) -> {
            if (!Settings.Secure.putInt(getContentResolver(), key, enabled ? 1 : 0)) {
                control.setOnCheckedChangeListener(null);
                control.setChecked(!enabled);
                Toast.makeText(this, R.string.motion_feedback_save_failed, Toast.LENGTH_LONG).show();
                recreate();
            }
        });
        parent.addView(control);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
