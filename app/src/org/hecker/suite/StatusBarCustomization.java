// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Two independent opt-ins, with no implicit change to the lock-screen lyric switch. */
final class StatusBarCustomization {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final Switch dual;
    private final Spinner height;
    private boolean busy;
    private StatusBarCustomization(Activity activity,LinearLayout page){
        this.activity=activity;
        LinearLayout layout=Ui.card(activity);
        layout.addView(Ui.text(activity,activity.getString(R.string.sb_layout_title),18,true));
        dual=new Switch(activity);dual.setText(R.string.sb_dual_title);
        dual.setTextColor(activity.getColor(R.color.hecker_foreground));dual.setMinHeight(Ui.dp(activity,48));
        dual.setChecked(value(StatusBarHeightController.ENABLED,0)!=0);layout.addView(dual);
        layout.addView(Ui.text(activity,activity.getString(R.string.sb_dual_summary),14,false));
        layout.addView(Ui.text(activity,activity.getString(R.string.sb_height_title),16,true));
        height=spinner(layout,new int[]{R.string.sb_height_compact,R.string.sb_height_balanced,R.string.sb_height_tall},
                value(StatusBarHeightController.HEIGHT,0));
        height.setContentDescription(activity.getString(R.string.sb_height_title));
        boolean available=StatusBarHeightController.available(activity);
        dual.setEnabled(available);height.setEnabled(available);
        if(!available)layout.addView(Ui.text(activity,activity.getString(R.string.sb_height_unavailable),14,false));
        dual.setOnClickListener(v->applyHeight());
        select(height,index->{if(index!=value(StatusBarHeightController.HEIGHT,0))applyHeight();});
        choice(layout,R.string.sb_second_row,"hecker_dual_statusbar_layout",0,
                new int[]{R.string.sb_row_notifications,R.string.sb_row_lyrics});
        range(layout,R.string.sb_row_gap,"hecker_statusbar_row_gap",2,0,8,"dp");
        range(layout,R.string.sb_icon_size,"hecker_statusbar_icon_size",16,12,20,"dp");
        page.addView(layout);
        LinearLayout lyrics=Ui.card(activity);
        lyrics.addView(Ui.text(activity,activity.getString(R.string.sb_lyrics_title),18,true));
        toggle(lyrics,R.string.sb_lyrics_title,"hecker_statusbar_lyrics");
        lyrics.addView(Ui.text(activity,activity.getString(R.string.sb_lyrics_summary),14,false));
        lyrics.addView(Ui.text(activity,activity.getString(R.string.lyrics_source),14,false));
        choice(lyrics,R.string.sb_lyric_position,"hecker_statusbar_lyric_position",2,
                new int[]{R.string.sb_left,R.string.sb_center,R.string.sb_right});
        range(lyrics,R.string.sb_lyric_width,"hecker_statusbar_lyric_width",160,80,360,"dp");
        range(lyrics,R.string.sb_lyric_font,"hecker_statusbar_lyric_size",11,9,18,"sp");
        choice(lyrics,R.string.sb_lyric_transition,"hecker_statusbar_lyric_transition",0,
                new int[]{R.string.sb_fade,R.string.sb_scroll,R.string.sb_no_transition});
        int[] colors={0,Color.WHITE,0xffb9f5d4,0xffa8d5ff,0xffffd493,0xfff6b5da};
        int chosen=value("hecker_statusbar_lyric_color",0),current=colors.length;
        for(int i=0;i<colors.length;i++)if(chosen==colors[i])current=i;
        lyrics.addView(Ui.text(activity,activity.getString(R.string.sb_lyric_colour),16,true));
        Spinner palette=spinner(lyrics,new int[]{R.string.sb_adaptive,R.string.flow_white,R.string.flow_mint,
            R.string.flow_blue,R.string.flow_sand,R.string.sb_pink,R.string.sb_custom},current);
        select(palette,index->{if(index<colors.length&&value("hecker_statusbar_lyric_color",0)!=colors[index])
            put("hecker_statusbar_lyric_color",colors[index]);});
        Button custom=new Button(activity);custom.setText(R.string.sb_custom_colour);
        custom.setOnClickListener(v->customColor());lyrics.addView(custom);
        page.addView(lyrics);
    }
    static void add(Activity activity,LinearLayout page){new StatusBarCustomization(activity,page);}
    int value(String key,int fallback){return Settings.Secure.getInt(activity.getContentResolver(),key,fallback);}
    boolean put(String key,int value){
        try{if(Settings.Secure.putInt(activity.getContentResolver(),key,value))return true;}catch(SecurityException ignored){}
        Toast.makeText(activity,R.string.native_page_unavailable,Toast.LENGTH_LONG).show();return false;
    }
    void applyHeight(){
        if(busy)return;busy=true;dual.setEnabled(false);height.setEnabled(false);
        boolean enabled=dual.isChecked();int preset=height.getSelectedItemPosition();
        WORK.execute(()->{
            boolean saved=StatusBarHeightController.apply(activity,enabled,preset);
            activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed())return;
                busy=false;dual.setChecked(value(StatusBarHeightController.ENABLED,0)!=0);
                height.setSelection(Math.max(0,Math.min(2,value(StatusBarHeightController.HEIGHT,0))));
                dual.setEnabled(true);height.setEnabled(true);
                if(!saved)Toast.makeText(activity,R.string.native_page_unavailable,Toast.LENGTH_LONG).show();
            });
        });
    }
    void toggle(LinearLayout card,int title,String key){
        Switch view=new Switch(activity);view.setText(title);view.setTextColor(activity.getColor(R.color.hecker_foreground));
        view.setMinHeight(Ui.dp(activity,48));view.setChecked(value(key,0)!=0);
        view.setOnClickListener(v->{if(!put(key,view.isChecked()?1:0))view.setChecked(!view.isChecked());});card.addView(view);
    }
    Spinner spinner(LinearLayout card,int[] names,int selected){
        String[] labels=new String[names.length];for(int i=0;i<names.length;i++)labels[i]=activity.getString(names[i]);
        Spinner spinner=new Spinner(activity);
        spinner.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels));
        spinner.setSelection(Math.max(0,Math.min(names.length-1,selected)));card.addView(spinner);return spinner;
    }
    interface Selection{void selected(int index);}
    void select(Spinner spinner,Selection listener){spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
        public void onNothingSelected(AdapterView<?> parent){}
        public void onItemSelected(AdapterView<?> parent,View view,int position,long id){listener.selected(position);}
    });}
    void choice(LinearLayout card,int title,String key,int fallback,int[] names){
        card.addView(Ui.text(activity,activity.getString(title),16,true));
        Spinner spinner=spinner(card,names,value(key,fallback));spinner.setContentDescription(activity.getString(title));
        select(spinner,index->{if(value(key,fallback)!=index&&!put(key,index))spinner.setSelection(value(key,fallback));});
    }
    void range(LinearLayout card,int title,String key,int fallback,int min,int max,String unit){
        int current=Math.max(min,Math.min(max,value(key,fallback)));
        TextView label=Ui.text(activity,activity.getString(title)+" · "+current+" "+unit,14,false);card.addView(label);
        SeekBar slider=new SeekBar(activity);slider.setMax(max-min);slider.setProgress(current-min);
        slider.setContentDescription(activity.getString(title));card.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar view){}
            public void onStopTrackingTouch(SeekBar view){}
            public void onProgressChanged(SeekBar view,int n,boolean user){
                int chosen=n+min;label.setText(activity.getString(title)+" · "+chosen+" "+unit);
                if(user&&!put(key,chosen))view.setProgress(Math.max(min,Math.min(max,value(key,fallback)))-min);
            }
        });
    }
    void customColor(){
        EditText value=new EditText(activity);value.setSingleLine(true);value.setHint("#RRGGBB");
        value.setText(String.format(Locale.US,"#%06X",value("hecker_statusbar_lyric_color",Color.WHITE)&0xffffff));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.sb_custom_colour).setView(value)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String text=value.getText().toString().trim();
            if(!text.matches("#[0-9a-fA-F]{6}")){value.setError(activity.getString(R.string.sb_invalid_colour));return;}
            if(put("hecker_statusbar_lyric_color",Color.parseColor(text)))dialog.dismiss();
        }));dialog.show();
    }
}
