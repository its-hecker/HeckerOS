// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.*;
import android.app.backup.BackupManager;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Hecker Lab's local backup dashboard. Passwords never enter saved state. */
public final class VaultActivity extends Activity {
    private static final int FOLDER=8,AUTH=9;
    private LinearLayout shell,body,navigation;private TextView activityStatus;private int tab;
    private final Set<String> selected=new LinkedHashSet<>(),categories=new LinkedHashSet<>();
    private List<PackageInfo> apps=Collections.emptyList();private boolean systemApps,loaded;
    private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler handler=new Handler(Looper.getMainLooper());
    private VaultRequest authenticating;private String shownPrepared="";private long lastFinished;
    private final Runnable refresh=new Runnable(){@Override public void run(){if(isFinishing()||isDestroyed())return;
        activityStatus.setText(VaultService.busy?VaultService.progress:getString(R.string.vault_ready));
        long finished=VaultStorage.prefs(VaultActivity.this).getLong("last_finished",0);
        if(lastFinished!=finished&&!VaultService.busy){lastFinished=finished;showTab(tab);}
        VaultEngine.Prepared p=VaultEngine.prepared;if(!VaultService.busy&&p!=null&&!p.id.equals(shownPrepared)){shownPrepared=p.id;preview(p);}
        handler.postDelayed(this,700);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setHideOverlayWindows(true);
        if(getActionBar()!=null)getActionBar().hide();
        categories.addAll(VaultStorage.prefs(this).getStringSet("categories",VaultSystemData.CATEGORIES));
        selected.addAll(VaultStorage.prefs(this).getStringSet("selected_apps",Collections.emptySet()));
        tab=state==null?0:state.getInt("tab");
        shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(getColor(R.color.vault_background));
        shell.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bounds=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bounds.left,bounds.top,bounds.right,bounds.bottom);return insets;});
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(16),dp(8),dp(16),0);
        Button back=button(getString(R.string.vault_back));back.setOnClickListener(v->finish());heading.addView(back);
        heading.addView(text(getString(R.string.vault_title),25,true),new LinearLayout.LayoutParams(0,-2,1));shell.addView(heading);
        activityStatus=text(getString(R.string.vault_ready),12,false);activityStatus.setPadding(dp(20),dp(4),dp(20),dp(8));shell.addView(activityStatus);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        navigation=new LinearLayout(this);navigation.setPadding(dp(8),dp(4),dp(8),dp(8));shell.addView(navigation);setContentView(shell);
        lastFinished=VaultStorage.prefs(this).getLong("last_finished",0);showTab(tab);loadApps();
        io.execute(()->{if(!VaultService.busy){VaultNative.recoverIdle(this);VaultSystemData.recoverSmsWrite(this);}});
    }
    @Override protected void onStart(){super.onStart();handler.post(refresh);}
    @Override protected void onStop(){handler.removeCallbacks(refresh);super.onStop();}
    @Override protected void onDestroy(){if(authenticating!=null){authenticating.clear();authenticating=null;}io.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("tab",tab);super.onSaveInstanceState(state);}
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private TextView text(String value,int size,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);
        view.setTextColor(getColor(bold?R.color.vault_foreground:R.color.vault_secondary));if(bold)view.setTypeface(null,Typeface.BOLD);view.setPadding(0,dp(4),0,dp(4));return view;}
    private Button button(String title){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(13);b.setMinHeight(dp(48));b.setFilterTouchesWhenObscured(true);return b;}
    private GradientDrawable shape(int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;}
    private LinearLayout card(LinearLayout parent){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(14),dp(18),dp(14));
        card.setBackground(shape(getColor(R.color.vault_card),24));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(12);parent.addView(card,params);return card;}
    private LinearLayout scrolling(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(10),dp(20),dp(20));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return content;}
    private void action(LinearLayout parent,int label,Runnable action){Button b=button(getString(label));b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private void showTab(int value){tab=value;body.removeAllViews();navigation.removeAllViews();int[] labels={R.string.vault_overview,R.string.vault_apps,R.string.vault_archives,R.string.vault_settings};
        for(int i=0;i<labels.length;i++){final int destination=i;Button b=button(getString(labels[i]));b.setTextSize(11);b.setSelected(i==tab);
            if(i==tab)b.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.vault_chip)));b.setOnClickListener(v->{showTab(destination);body.setAlpha(0);body.animate().alpha(1).setDuration(160).start();});navigation.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        switch(tab){case 0:overview();break;case 1:appPage();break;case 2:archives();break;default:settings();}
    }
    private void overview(){LinearLayout page=scrolling(),hero=card(page);hero.setBackground(shape(getColor(R.color.vault_chip),28));
        hero.addView(text(getString(R.string.vault_tagline),24,true));hero.addView(text(getString(R.string.vault_workflow),14,false));
        hero.addView(text(getString(R.string.vault_selected_count,selected.size(),categories.size()),13,true));
        action(hero,R.string.vault_backup_selected,()->password("backup",null));
        action(hero,R.string.vault_backup_all,()->{if(!loaded){message(getString(R.string.vault_loading));return;}selected.clear();for(PackageInfo p:apps)if((p.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)selected.add(p.packageName);saveScope();password("backup",null);});
        if(VaultService.busy)action(hero,android.R.string.cancel,()->startService(new Intent(this,VaultService.class).setAction(VaultService.CANCEL)));
        LinearLayout location=card(page);location.addView(text(getString(R.string.vault_folder),18,true));location.addView(text(folderLabel(),12,false));action(location,R.string.vault_choose_folder,this::chooseFolder);
        LinearLayout personal=card(page);personal.addView(text(getString(R.string.vault_personal),19,true));
        int[] names={R.string.vault_wifi,R.string.vault_contacts,R.string.vault_messages,R.string.vault_calls,R.string.vault_wallpapers,R.string.vault_preferences,R.string.vault_launcher};
        String[] ids=VaultSystemData.CATEGORIES.toArray(new String[0]);for(int i=0;i<ids.length;i++){String id=ids[i];CheckBox box=new CheckBox(this);box.setText(names[i]);box.setChecked(categories.contains(id));
            box.setOnCheckedChangeListener((v,on)->{if(on)categories.add(id);else categories.remove(id);saveScope();});personal.addView(box);}
        personal.addView(text(getString(R.string.vault_personal_note),12,false));
        String report=VaultStorage.prefs(this).getString("report","");if(!report.isEmpty()){LinearLayout result=card(page);result.addView(text(getString(R.string.vault_last_result),19,true));result.addView(text(report,13,false));}
    }
    private String folderLabel(){String folder=VaultStorage.prefs(this).getString("folder",null);return folder==null?getString(R.string.vault_no_folder):Uri.decode(folder.substring(folder.lastIndexOf('/')+1));}
    private void loadApps(){io.execute(()->{List<PackageInfo> result=VaultPackages.installed(this,true);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;apps=result;loaded=true;
            if(!VaultStorage.prefs(this).contains("selected_apps")){for(PackageInfo p:apps)if((p.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)selected.add(p.packageName);saveScope();}if(tab==1)showTab(tab);});});}
    private void saveScope(){VaultStorage.prefs(this).edit().putStringSet("selected_apps",new HashSet<>(selected)).putStringSet("categories",new HashSet<>(categories)).apply();}
    private void appPage(){LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(20),0,dp(20),dp(8));body.addView(top);
        EditText search=new EditText(this);search.setSingleLine();search.setHint(R.string.vault_search_apps);top.addView(search);
        CheckBox showSystem=new CheckBox(this);showSystem.setText(R.string.vault_show_system);showSystem.setChecked(systemApps);top.addView(showSystem);
        LinearLayout controls=new LinearLayout(this);Button all=button(getString(R.string.vault_select_all)),clear=button(getString(R.string.vault_clear));controls.addView(all,new LinearLayout.LayoutParams(0,-2,1));controls.addView(clear,new LinearLayout.LayoutParams(0,-2,1));top.addView(controls);
        LinearLayout groups=new LinearLayout(this);Button loadGroup=button(getString(R.string.vault_load_group)),saveGroup=button(getString(R.string.vault_save_group));
        groups.addView(loadGroup,new LinearLayout.LayoutParams(0,-2,1));groups.addView(saveGroup,new LinearLayout.LayoutParams(0,-2,1));top.addView(groups);
        loadGroup.setOnClickListener(v->loadGroup());saveGroup.setOnClickListener(v->saveGroup());
        ListView list=new ListView(this);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));List<PackageInfo> filtered=new ArrayList<>();
        BaseAdapter adapter=new BaseAdapter(){@Override public int getCount(){return filtered.size();}@Override public Object getItem(int p){return filtered.get(p);}@Override public long getItemId(int p){return p;}
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){PackageInfo app=filtered.get(position);LinearLayout row=new LinearLayout(VaultActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(8),dp(16),dp(8));
                ImageView icon=new ImageView(VaultActivity.this);icon.setImageDrawable(app.applicationInfo.loadIcon(getPackageManager()));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
                LinearLayout copy=new LinearLayout(VaultActivity.this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);
                copy.addView(text(app.applicationInfo.loadLabel(getPackageManager()).toString(),15,true));copy.addView(text(app.packageName+" · "+(((app.applicationInfo.flags&ApplicationInfo.FLAG_ALLOW_BACKUP)!=0)?getString(R.string.vault_native_data):getString(R.string.vault_apk_only)),11,false));
                row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));CheckBox check=new CheckBox(VaultActivity.this);check.setContentDescription(app.applicationInfo.loadLabel(getPackageManager()));check.setChecked(selected.contains(app.packageName));
                check.setOnCheckedChangeListener((v,on)->{if(on)selected.add(app.packageName);else selected.remove(app.packageName);saveScope();});row.addView(check);row.setOnClickListener(v->check.setChecked(!check.isChecked()));return row;}};
        list.setAdapter(adapter);Runnable filter=()->{String query=search.getText().toString().toLowerCase(Locale.ROOT);filtered.clear();for(PackageInfo app:apps){
            if(!systemApps&&(app.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)continue;String label=app.applicationInfo.loadLabel(getPackageManager()).toString();
            if(query.isEmpty()||app.packageName.toLowerCase(Locale.ROOT).contains(query)||label.toLowerCase(Locale.ROOT).contains(query))filtered.add(app);}adapter.notifyDataSetChanged();};
        showSystem.setOnCheckedChangeListener((v,on)->{systemApps=on;filter.run();});search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});
        all.setOnClickListener(v->{for(PackageInfo p:filtered)selected.add(p.packageName);saveScope();adapter.notifyDataSetChanged();});clear.setOnClickListener(v->{for(PackageInfo p:filtered)selected.remove(p.packageName);saveScope();adapter.notifyDataSetChanged();});filter.run();
    }
    private void saveGroup(){
        if(selected.isEmpty()&&categories.isEmpty()){message(getString(R.string.vault_select_items));return;}
        EditText name=new EditText(this);name.setSingleLine();name.setHint(R.string.vault_group_name);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.vault_save_group).setView(name).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            VaultGroups.save(this,name.getText().toString().trim(),selected,categories);dialog.dismiss();message(getString(R.string.vault_group_saved));
        }catch(Exception e){name.setError(getString(R.string.vault_group_invalid));}}));dialog.show();
    }
    private void loadGroup(){
        try{JSONObject groups=VaultGroups.load(this);List<String> names=new ArrayList<>();Iterator<String> keys=groups.keys();while(keys.hasNext())names.add(keys.next());Collections.sort(names,String.CASE_INSENSITIVE_ORDER);
            if(names.isEmpty()){message(getString(R.string.vault_no_groups));return;}
            new AlertDialog.Builder(this).setTitle(R.string.vault_load_group).setItems(names.toArray(new String[0]),(d,which)->{
                String name=names.get(which);new AlertDialog.Builder(this).setTitle(name).setMessage(R.string.vault_group_scope)
                        .setNegativeButton(android.R.string.cancel,null).setNeutralButton(R.string.vault_delete_group,(dialog,w)->{try{VaultGroups.remove(this,name);}catch(Exception e){message(getString(R.string.vault_group_invalid));}})
                        .setPositiveButton(R.string.vault_use_group,(dialog,w)->{try{JSONObject group=groups.getJSONObject(name);selected.clear();categories.clear();
                            JSONArray appList=group.getJSONArray("apps"),categoryList=group.getJSONArray("categories");for(int i=0;i<appList.length();i++)selected.add(appList.getString(i));
                            for(int i=0;i<categoryList.length();i++)categories.add(categoryList.getString(i));saveScope();showTab(1);
                        }catch(Exception e){message(getString(R.string.vault_group_invalid));}}).show();}).show();
        }catch(Exception e){message(getString(R.string.vault_group_invalid));}
    }
    private void archives(){LinearLayout page=scrolling(),heading=card(page);heading.addView(text(getString(R.string.vault_archives),23,true));heading.addView(text(getString(R.string.vault_archives_note),13,false));
        action(heading,R.string.vault_choose_folder,this::chooseFolder);EditText search=new EditText(this);search.setSingleLine();search.setHint(R.string.vault_search_archives);heading.addView(search);
        List<View> archiveCards=new ArrayList<>();Runnable filter=()->{String query=search.getText().toString().toLowerCase(Locale.ROOT);for(View card:archiveCards)card.setVisibility(card.getTag().toString().toLowerCase(Locale.ROOT).contains(query)?View.VISIBLE:View.GONE);};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});
        TextView loading=text(getString(R.string.vault_loading),13,false);page.addView(loading);
        io.execute(()->{try{List<VaultStorage.Archive> entries=VaultStorage.list(this);runOnUiThread(()->{if(tab!=2||isDestroyed())return;page.removeView(loading);
            if(entries.isEmpty())page.addView(text(getString(R.string.vault_no_archives),14,false));for(VaultStorage.Archive entry:entries){LinearLayout item=card(page);item.setTag(entry.name);archiveCards.add(item);item.addView(text(entry.name,15,true));
                item.addView(text(android.text.format.Formatter.formatFileSize(this,entry.size)+" · AES-256-GCM",12,false));action(item,R.string.vault_inspect,()->password("inspect",entry.uri));
                Button remove=button(getString(R.string.vault_delete));remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(R.string.vault_delete).setMessage(R.string.vault_delete_confirm)
                        .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.vault_delete,(d,w)->io.execute(()->{try{VaultStorage.remove(this,entry.uri);runOnUiThread(()->showTab(2));}catch(Exception e){runOnUiThread(()->message(getString(R.string.vault_folder_error)));}})).show());item.addView(remove);}filter.run();});
        }catch(Exception e){runOnUiThread(()->{loading.setText(R.string.vault_folder_error);});}});
    }
    private void settings(){LinearLayout page=scrolling(),security=card(page);security.addView(text(getString(R.string.vault_security),20,true));security.addView(text(getString(R.string.vault_encryption_note),13,false));
        action(security,R.string.vault_choose_folder,this::chooseFolder);
        LinearLayout nativeBackup=card(page);nativeBackup.addView(text(getString(R.string.vault_native_backend),19,true));nativeBackup.addView(text(getString(R.string.vault_native_note),13,false));
        action(nativeBackup,R.string.vault_enable_android,()->new AlertDialog.Builder(this).setTitle(R.string.vault_enable_android).setMessage(R.string.vault_enable_android_note)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{new BackupManager(this).setBackupEnabled(true);message(getString(R.string.vault_android_enabled));}).show());
        LinearLayout schedules=card(page);schedules.addView(text(getString(R.string.vault_schedules),20,true));schedules.addView(text(getString(R.string.vault_schedule_note),13,false));
        boolean enabled=VaultStorage.prefs(this).getBoolean("scheduled",false);action(schedules,enabled?R.string.vault_change_schedule_password:R.string.vault_enable_schedule,()->password("schedule",null));
        if(enabled)action(schedules,R.string.vault_disable_schedule,()->{VaultJobs.configure(this,false);showTab(3);});
        check(schedules,R.string.vault_weekly,"weekly",false);check(schedules,R.string.vault_charging,"charging",true);check(schedules,R.string.vault_schedule_all,"schedule_all",true);
        schedules.addView(text(getString(R.string.vault_retention),14,true));Spinner retention=new Spinner(this);retention.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1","3","5","10"}));
        int[] values={1,3,5,10};int current=VaultStorage.prefs(this).getInt("retention",3);for(int i=0;i<values.length;i++)if(values[i]==current)retention.setSelection(i);
        retention.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){VaultStorage.prefs(VaultActivity.this).edit().putInt("retention",values[position]).apply();}});schedules.addView(retention);
        LinearLayout limits=card(page);limits.addView(text(getString(R.string.vault_limits_title),19,true));limits.addView(text(getString(R.string.vault_limits),13,false));
    }
    private void check(LinearLayout parent,int title,String key,boolean fallback){CheckBox box=new CheckBox(this);box.setText(title);box.setChecked(VaultStorage.prefs(this).getBoolean(key,fallback));
        box.setOnCheckedChangeListener((v,on)->{VaultStorage.prefs(this).edit().putBoolean(key,on).apply();if(VaultStorage.prefs(this).getBoolean("scheduled",false))try{VaultJobs.configure(this,true);}catch(Exception e){message(getString(R.string.vault_schedule_failed));}});parent.addView(box);}
    private void chooseFolder(){if(VaultService.busy){message(getString(R.string.vault_busy));return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),FOLDER);}
    private void password(String operation,Uri archive){if(VaultService.busy){message(getString(R.string.vault_busy));return;}
        try{VaultStorage.folder(this);VaultEngine.owner(this);}catch(Exception e){message(getString(R.string.vault_choose_folder));return;}
        if(operation.equals("backup")&&selected.isEmpty()&&categories.isEmpty()){message(getString(R.string.vault_select_items));return;}
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(24),dp(8),dp(24),dp(8));
        EditText password=secret(R.string.vault_password);fields.addView(password);EditText confirm=secret(R.string.vault_confirm_password);if(!operation.equals("inspect"))fields.addView(confirm);
        fields.addView(text(getString(R.string.vault_password_note),12,false));AlertDialog dialog=new AlertDialog.Builder(this).setTitle(operation.equals("inspect")?R.string.vault_unlock_archive:R.string.vault_archive_password)
                .setView(fields).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnDismissListener(d->{password.setText("");confirm.setText("");});dialog.setOnShowListener(d->{dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            char[] pass=new char[password.length()];password.getText().getChars(0,pass.length,pass,0);
            if(pass.length<12||pass.length>1024||!operation.equals("inspect")&&!TextUtils.equals(password.getText(),confirm.getText())){Arrays.fill(pass,'\0');password.setError(getString(R.string.vault_password_invalid));return;}
            VaultRequest request=new VaultRequest(operation,pass,selected,categories,archive,"both",false);dialog.dismiss();authenticate(request); });});dialog.show();
    }
    private EditText secret(int hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine();e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setTransformationMethod(PasswordTransformationMethod.getInstance());e.setSaveEnabled(false);e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);e.setFilterTouchesWhenObscured(true);return e;}
    private void authenticate(VaultRequest request){KeyguardManager kg=getSystemService(KeyguardManager.class);
        if(kg.isDeviceSecure()){Intent confirm=kg.createConfirmDeviceCredentialIntent(getString(R.string.vault_title),getString(R.string.vault_auth));if(confirm!=null){authenticating=request;startActivityForResult(confirm,AUTH);return;}}
        launch(request);
    }
    private void launch(VaultRequest request){try{VaultService.launch(this,request);activityStatus.setText(R.string.vault_preparing);}catch(Exception e){request.clear();message(getString(R.string.vault_start_failed));}}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);
        if(code==AUTH){VaultRequest request=authenticating;authenticating=null;if(request!=null){if(result==RESULT_OK)launch(request);else request.clear();}return;}
        if(code==FOLDER&&result==RESULT_OK&&data!=null&&data.getData()!=null){try{Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,
                data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));VaultStorage.prefs(this).edit().putString("folder",uri.toString()).apply();showTab(tab);}
            catch(SecurityException e){message(getString(R.string.vault_folder_error));}}
    }
    private void preview(VaultEngine.Prepared prepared){
        try{JSONObject snapshot=prepared.snapshot;JSONArray apps=snapshot.getJSONArray("apps"),available=snapshot.getJSONArray("categories");
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),0,dp(20),0);content.addView(text(getString(R.string.vault_preview_summary,apps.length(),available.length()),15,true));
            Set<String> chosenApps=new LinkedHashSet<>(),chosenCategories=new LinkedHashSet<>();
            RadioGroup modes=new RadioGroup(this);int[] modeIds={101,102,103};String[] modesValues={"both","apk","data"};int[] modeLabels={R.string.vault_apk_data,R.string.vault_apk_only,R.string.vault_data_only};
            for(int i=0;i<modeIds.length;i++){RadioButton b=new RadioButton(this);b.setId(modeIds[i]);b.setText(modeLabels[i]);modes.addView(b);}modes.check(modeIds[0]);content.addView(modes);
            for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");chosenApps.add(pkg);CheckBox check=new CheckBox(this);
                check.setText(app.getString("label")+"\n"+app.optString("result"));check.setChecked(true);check.setOnCheckedChangeListener((v,on)->{if(on)chosenApps.add(pkg);else chosenApps.remove(pkg);});content.addView(check);}
            for(int i=0;i<available.length();i++){String category=available.getString(i);chosenCategories.add(category);CheckBox check=new CheckBox(this);check.setText(categoryName(category));check.setChecked(true);
                check.setOnCheckedChangeListener((v,on)->{if(on)chosenCategories.add(category);else chosenCategories.remove(category);});content.addView(check);}
            JSONArray notes=snapshot.optJSONArray("notes");if(notes!=null)for(int i=0;i<notes.length();i++)content.addView(text(notes.getString(i),12,false));
            ScrollView scroll=new ScrollView(this);scroll.addView(content);
            new AlertDialog.Builder(this).setTitle(R.string.vault_restore_preview).setView(scroll).setNegativeButton(android.R.string.cancel,(d,w)->{VaultEngine.prepared=null;io.execute(()->VaultEngine.cleanup(prepared.root));})
                    .setPositiveButton(R.string.vault_restore_selected,(d,w)->{if(chosenApps.isEmpty()&&chosenCategories.isEmpty()){message(getString(R.string.vault_select_items));return;}
                        new AlertDialog.Builder(this).setTitle(R.string.vault_restore_selected).setMessage(R.string.vault_restore_confirm).setNegativeButton(android.R.string.cancel,null)
                                .setPositiveButton(R.string.vault_restore_selected,(confirm,which)->{int index=modes.getCheckedRadioButtonId()-101;
                                    VaultRequest request=new VaultRequest("restore",new char[0],chosenApps,chosenCategories,null,modesValues[index],false);request.preparedId=prepared.id;authenticate(request);}).show();}).show();
        }catch(Exception e){message(getString(R.string.vault_invalid_archive));}
    }
    private String categoryName(String id){int[] names={R.string.vault_wifi,R.string.vault_contacts,R.string.vault_messages,R.string.vault_calls,R.string.vault_wallpapers,R.string.vault_preferences,R.string.vault_launcher};
        String[] ids=VaultSystemData.CATEGORIES.toArray(new String[0]);for(int i=0;i<ids.length;i++)if(id.equals(ids[i]))return getString(names[i]);return id;}
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
