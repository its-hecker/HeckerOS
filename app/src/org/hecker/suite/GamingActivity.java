// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GamingActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout page, library;
    private String tab="games";
    private String pendingGame;
    private String selectedGame;
    private int generation;
    private boolean pendingEnable,visible;

    @Override public void onCreate(Bundle state){
        super.onCreate(state); if(state!=null){tab=state.getString("tab","games");pendingEnable=state.getBoolean("pending_enable",false);pendingGame=state.getString("pending_game");}
        if(state!=null)selectedGame=state.getString("selected_game");
        GamingPrefs.get(this).registerOnSharedPreferenceChangeListener(this);
        render();
    }
    @Override protected void onResume(){
        super.onResume();visible=true;
        if(pendingEnable){
            pendingEnable=false;String game=pendingGame;pendingGame=null;
            if(Settings.canDrawOverlays(this)){setEnabled(true);if(game!=null){playGame(game);return;}}
            else toast(R.string.gaming_overlay_access);
        }
        render();
        if(GamingPrefs.enabled(this))GamingService.start(this);
    }
    @Override protected void onPause(){visible=false;super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("tab",tab);out.putBoolean("pending_enable",pendingEnable);out.putString("pending_game",pendingGame);out.putString("selected_game",selectedGame);}
    @Override protected void onDestroy(){
        generation++;GamingPrefs.get(this).unregisterOnSharedPreferenceChangeListener(this);worker.shutdownNow();super.onDestroy();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){
        if(visible && !isFinishing() && !isDestroyed() && !key.startsWith("session_") && !key.startsWith("hud_") && !key.equals("focus_applied"))render();
    }
    private TextView title(int string){return GamingUi.text(this,getString(string),22,GamingUi.TEXT,true);}
    private void render(){
        int request=++generation;
        LinearLayout root=GamingUi.column(this);root.setBackgroundColor(GamingUi.BG);
        int pad=GamingUi.dp(this,20);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout top=GamingUi.row(this);top.setPadding(pad,pad,pad,GamingUi.dp(this,12));
        GamingUi.Icon back=new GamingUi.Icon(this,"back",GamingUi.TEXT);back.setContentDescription(getString(R.string.back));back.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);back.setFocusable(true);
        back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(GamingUi.dp(this,40),GamingUi.dp(this,40)));
        top.addView(GamingUi.text(this,getString(R.string.gaming_title),20,GamingUi.TEXT,true));root.addView(top);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=GamingUi.column(this);page.setPadding(pad,0,pad,pad);
        if(tab.equals("games"))games(request);else if(tab.equals("tools"))tools();else style();
        scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=GamingUi.row(this);nav.setPadding(pad,GamingUi.dp(this,8),pad,GamingUi.dp(this,8));
        String[] names={"games","tools","style"};int[] labels={R.string.gaming_games,R.string.gaming_tools,R.string.gaming_style};
        for(int i=0;i<3;i++){String name=names[i];TextView button=GamingUi.button(this,getString(labels[i]),tab.equals(name),()->{tab=name;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(3,0,3,0);nav.addView(button,lp);}
        root.addView(nav);setContentView(root);
    }
    private void games(int request){
        library=GamingUi.column(this);
        library.addView(GamingUi.text(this,getString(R.string.gaming_loading),14,GamingUi.MUTED,false));
        page.addView(library);
        worker.execute(()->{List<GamingCatalog.App> apps=GamingCatalog.load(this,true);runOnUiThread(()->{
            if(request!=generation||isDestroyed())return;
            gameLibrary(apps);
        });});
    }
    private void gameLibrary(List<GamingCatalog.App> apps){
        library.removeAllViews();
        if(apps.isEmpty()){
            LinearLayout empty=GamingUi.card(this);
            empty.addView(title(R.string.gaming_library));GamingUi.gap(empty,12);
            empty.addView(GamingUi.text(this,getString(R.string.gaming_empty),15,GamingUi.MUTED,false));
            GamingUi.gap(empty,18);
            empty.addView(GamingUi.button(this,getString(R.string.gaming_add),true,()->picker(false)));
            library.addView(empty);return;
        }
        GamingCatalog.App featured=apps.get(0);
        for(GamingCatalog.App app:apps)if(app.info.packageName.equals(selectedGame))featured=app;
        selectedGame=featured.info.packageName;
        HorizontalScrollView carousel=new HorizontalScrollView(this);carousel.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=GamingUi.row(this);strip.setPadding(0,0,0,GamingUi.dp(this,14));
        for(GamingCatalog.App app:apps){
            FrameLayout slot=new FrameLayout(this);
            slot.setBackground(GamingUi.background(this,GamingUi.CARD,14,app.info.packageName.equals(selectedGame)));
            ImageView icon=new ImageView(this);icon.setImageDrawable(app.info.loadIcon(getPackageManager()));
            slot.addView(icon,new FrameLayout.LayoutParams(GamingUi.dp(this,38),GamingUi.dp(this,38),Gravity.CENTER));
            slot.setContentDescription(app.label);slot.setFocusable(true);
            slot.setSelected(app.info.packageName.equals(selectedGame));
            slot.setOnClickListener(v->{selectedGame=app.info.packageName;gameLibrary(apps);});
            LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(GamingUi.dp(this,54),GamingUi.dp(this,54));
            item.rightMargin=GamingUi.dp(this,8);strip.addView(slot,item);
        }
        FrameLayout add=new FrameLayout(this);
        add.setBackground(GamingUi.background(this,GamingUi.CARD,14,false));
        add.addView(new GamingUi.Icon(this,"plus",GamingUi.TEXT),new FrameLayout.LayoutParams(GamingUi.dp(this,22),GamingUi.dp(this,22),Gravity.CENTER));
        add.setContentDescription(getString(R.string.gaming_add));add.setFocusable(true);add.setOnClickListener(v->picker(false));
        strip.addView(add,new LinearLayout.LayoutParams(GamingUi.dp(this,54),GamingUi.dp(this,54)));
        carousel.addView(strip);library.addView(carousel);
        library.addView(gameCard(featured));GamingUi.gap(library,12);
        LinearLayout controls=GamingUi.row(this);
        hubTool(controls,"focus",R.string.gaming_focus,()->{tab="tools";render();});
        hubTool(controls,"gamepad",R.string.gaming_fps_toggle,()->{tab="tools";render();});
        library.addView(controls);GamingUi.gap(library,6);
        LinearLayout more=GamingUi.row(this);
        hubTool(more,"apps",R.string.gaming_shortcuts,()->picker(true));
        hubTool(more,"sun",R.string.gaming_style,()->{tab="style";render();});
        library.addView(more);
    }
    private void hubTool(LinearLayout row,String icon,int label,Runnable action){
        LinearLayout tile=GamingUi.column(this);tile.setGravity(Gravity.CENTER);
        tile.setBackground(GamingUi.background(this,GamingUi.CARD,14,false));
        tile.setPadding(GamingUi.dp(this,10),GamingUi.dp(this,12),GamingUi.dp(this,10),GamingUi.dp(this,12));
        tile.addView(new GamingUi.Icon(this,icon,GamingUi.TEXT),new LinearLayout.LayoutParams(GamingUi.dp(this,22),GamingUi.dp(this,22)));
        TextView copy=GamingUi.text(this,getString(label),12,GamingUi.TEXT,true);copy.setGravity(Gravity.CENTER);
        copy.setPadding(0,GamingUi.dp(this,7),0,0);tile.addView(copy);
        tile.setFocusable(true);tile.setContentDescription(getString(label));tile.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(0,-2,1);
        item.setMargins(GamingUi.dp(this,3),0,GamingUi.dp(this,3),0);row.addView(tile,item);
    }
    private View gameCard(GamingCatalog.App app){
        FrameLayout hero=new FrameLayout(this);
        hero.addView(new GamingUi.Banner(this,app.info.loadIcon(getPackageManager()),true),
                new FrameLayout.LayoutParams(-1,GamingUi.dp(this,300)));
        LinearLayout heading=GamingUi.column(this);heading.setPadding(GamingUi.dp(this,20),GamingUi.dp(this,24),GamingUi.dp(this,20),0);
        TextView name=GamingUi.text(this,app.label,26,GamingUi.TEXT,true);name.setAllCaps(true);name.setMaxLines(3);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(name);GamingUi.gap(heading,9);
        heading.addView(GamingUi.text(this,getString(GamingPrefs.enabled(this)?R.string.gaming_enabled:R.string.gaming_ready),12,GamingUi.accent(this),true));
        hero.addView(heading,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        TextView play=GamingUi.button(this,getString(R.string.gaming_launch_game),true,()->playGame(app.info.packageName));
        play.setContentDescription(getString(R.string.gaming_launch_game)+" "+app.label);
        FrameLayout.LayoutParams launch=new FrameLayout.LayoutParams(-1,GamingUi.dp(this,52),Gravity.BOTTOM);
        launch.setMargins(GamingUi.dp(this,16),0,GamingUi.dp(this,16),GamingUi.dp(this,16));hero.addView(play,launch);
        hero.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(app.label)
            .setItems(new String[]{getString(R.string.gaming_remove)},(d,i)->GamingPrefs.include(this,app.info.packageName,false)).show();return true;});
        return hero;
    }
    private interface Change{void set(boolean value);}
    private void playGame(String packageName){
        if(!Settings.canDrawOverlays(this)){pendingGame=packageName;setEnabled(true);return;}
        if(!GamingPrefs.enabled(this))setEnabled(true);else GamingService.start(this);
        Intent launch=GamingCatalog.launch(this,packageName);
        if(launch!=null)try{startActivity(launch);}catch(RuntimeException e){toast(R.string.gaming_launch_error);}
        else toast(R.string.gaming_launch_error);
    }
    private void toggle(int label,int summary,boolean checked,Change change){
        LinearLayout card=GamingUi.card(this),row=GamingUi.row(this),copy=GamingUi.column(this);
        copy.addView(GamingUi.text(this,getString(label),16,GamingUi.TEXT,true));
        if(summary!=0){GamingUi.gap(copy,6);copy.addView(GamingUi.text(this,getString(summary),13,GamingUi.MUTED,false));}
        GamingUi.weighted(row,copy);Switch control=new Switch(this);control.setContentDescription(getString(label));control.setChecked(checked);control.setThumbTintList(ColorStateList.valueOf(GamingUi.accent(this)));control.setOnCheckedChangeListener((button,value)->change.set(value));row.addView(control);card.addView(row);page.addView(card);
    }
    private void setEnabled(boolean value){
        if(value&&!Settings.canDrawOverlays(this)){
            pendingEnable=true;toast(R.string.gaming_overlay_access);
            try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){toast(R.string.gaming_unavailable);}render();return;
        }
        GamingPrefs.get(this).edit().putBoolean("enabled",value).apply();
        if(value){if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);GamingService.start(this);}
        else stopService(new Intent(this,GamingService.class));
    }
    private void tools(){
        toggle(R.string.gaming_auto,R.string.gaming_auto_summary,GamingPrefs.get(this).getBoolean("automatic",true),v->GamingPrefs.get(this).edit().putBoolean("automatic",v).apply());
        toggle(R.string.gaming_enable,0,GamingPrefs.enabled(this),this::setEnabled);
        toggle(R.string.gaming_fps_toggle,R.string.gaming_fps_summary,GamingPrefs.get(this).getBoolean("fps",true),v->GamingPrefs.get(this).edit().putBoolean("fps",v).apply());
        toggle(R.string.gaming_focus,R.string.gaming_focus_summary,GamingPrefs.get(this).getBoolean("focus",false),v->{
            if(v&&!getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted()){
                toast(R.string.gaming_focus_access);startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));render();
            }else GamingPrefs.get(this).edit().putBoolean("focus",v).apply();
        });
        LinearLayout card=GamingUi.card(this);card.addView(title(R.string.gaming_shortcuts));GamingUi.gap(card,8);
        card.addView(GamingUi.text(this,getString(R.string.gaming_shortcuts_summary),14,GamingUi.MUTED,false));GamingUi.gap(card,12);
        card.addView(GamingUi.button(this,getString(R.string.gaming_shortcuts_count,GamingPrefs.shortcuts(this).size()),false,()->picker(true)));page.addView(card);
        page.addView(GamingUi.text(this,getString(R.string.gaming_saves),14,GamingUi.MUTED,false));GamingUi.gap(page,16);
        page.addView(GamingUi.text(this,getString(R.string.gaming_record_explainer),13,GamingUi.MUTED,false));GamingUi.gap(page,12);
        page.addView(GamingUi.text(this,getString(R.string.gaming_frame_note),13,GamingUi.MUTED,false));
    }
    private void style(){
        page.addView(title(R.string.gaming_accent));GamingUi.gap(page,14);LinearLayout colors=GamingUi.row(this);
        int[] values={0xffff334b,0xff35eee3,0xffb29aff,0xffffbe65};int[] labels={R.string.gaming_red,R.string.gaming_cyan,R.string.gaming_violet,R.string.gaming_amber};
        for(int i=0;i<values.length;i++){int color=values[i];TextView chip=GamingUi.button(this,getString(labels[i]),GamingUi.accent(this)==color,()->GamingPrefs.get(this).edit().putInt("accent",color).apply());chip.setTextColor(GamingUi.accent(this)==color?GamingUi.BG:color);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(3,0,3,0);colors.addView(chip,p);}page.addView(colors);GamingUi.gap(page,24);
        toggle(R.string.gaming_edge,R.string.gaming_edge_summary,GamingPrefs.get(this).getBoolean("left",true),v->GamingPrefs.get(this).edit().putBoolean("left",v).apply());
        page.addView(title(R.string.gaming_opacity));SeekBar opacity=new SeekBar(this);opacity.setMax(40);opacity.setProgress(GamingPrefs.get(this).getInt("opacity",94)-60);opacity.setContentDescription(getString(R.string.gaming_opacity));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){}public void onStopTrackingTouch(SeekBar s){GamingPrefs.get(GamingActivity.this).edit().putInt("opacity",60+s.getProgress()).apply();}});page.addView(opacity);
    }
    private void picker(boolean shortcuts){
        LinearLayout box=GamingUi.column(this);int p=GamingUi.dp(this,16);box.setPadding(p,p,p,p);
        EditText search=new EditText(this);search.setSingleLine();search.setHint(R.string.gaming_search);box.addView(search);
        ScrollView scroll=new ScrollView(this);LinearLayout list=GamingUi.column(this);scroll.addView(list);box.addView(scroll,new LinearLayout.LayoutParams(-1,GamingUi.dp(this,360)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(shortcuts?R.string.gaming_shortcuts:R.string.gaming_add).setView(box).setPositiveButton(R.string.gaming_done,null).create();dialog.show();
        worker.execute(()->{List<GamingCatalog.App> apps=GamingCatalog.load(this,false);runOnUiThread(()->{
            if(!dialog.isShowing()||isDestroyed())return;
            Runnable[] fill=new Runnable[1];fill[0]=()->{list.removeAllViews();String query=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                for(GamingCatalog.App app:apps){if(!app.label.toLowerCase(java.util.Locale.ROOT).contains(query)&&!app.info.packageName.contains(query))continue;
                    boolean tagged=app.info.category==android.content.pm.ApplicationInfo.CATEGORY_GAME||(app.info.flags&android.content.pm.ApplicationInfo.FLAG_IS_GAME)!=0;
                    boolean selected=shortcuts?GamingPrefs.shortcuts(this).contains(app.info.packageName):GamingPolicy.isGame(app.info.packageName,tagged,GamingPrefs.get(this).getBoolean("automatic",true),GamingPrefs.added(this),GamingPrefs.excluded(this));
                    TextView row=GamingUi.button(this,(selected?"✓  ":"")+app.label,selected,()->{
                        if(shortcuts){if(!GamingPrefs.shortcuts(this).contains(app.info.packageName)&&GamingPrefs.shortcuts(this).size()>=5){toast(R.string.gaming_limit);return;}GamingPrefs.toggleShortcut(this,app.info.packageName);}
                        else {
                            GamingPrefs.include(this,app.info.packageName,!selected);
                            if(!selected&&!GamingPrefs.enabled(this)){
                                if(!Settings.canDrawOverlays(this))dialog.dismiss();
                                setEnabled(true);
                            }
                        }
                        fill[0].run();
                    });list.addView(row);GamingUi.gap(list,6);
                }};fill[0].run();search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){fill[0].run();}public void afterTextChanged(Editable e){}});
        });});
    }
    private void toast(int string){Toast.makeText(this,string,Toast.LENGTH_LONG).show();}
}
