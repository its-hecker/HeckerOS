// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.*;
import android.app.backup.BackupManager;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.InputFilter;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.hecker.ui.HeckerChoiceSheet;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Native, local-only backup dashboard. Passwords never enter saved UI state. */
public final class VaultActivity extends Activity {
    private static final int FOLDER=8,AUTH=9,RESET_AUTH=10;
    private LinearLayout shell,body,navigation,toolbar,workBanner;
    private TextView activityStatus,selectionCount;private VaultUi ui;private int tab;
    private String subpage="",scope="all",query="",restoreScope="all",restoreCategory="all";
    private boolean showSearch;
    private final Set<String> selected=new LinkedHashSet<>(),categories=new LinkedHashSet<>();
    private List<AppEntry> apps=Collections.emptyList();private boolean systemApps,loaded;
    private JSONObject history=new JSONObject();private Runnable refilter=()->{};
    private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean unlocked,authPending,setupShowing;
    private final BroadcastReceiver screenOff=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        unlocked=false;if(shell!=null)shell.setVisibility(View.INVISIBLE);
    }};
    private String shownPrepared="";private long lastFinished;
    private static final class AppEntry {
        final PackageInfo info;final String label,search;final Drawable icon;
        AppEntry(Context c,PackageInfo p){info=p;CharSequence name=p.applicationInfo.loadLabel(c.getPackageManager());label=name==null?p.packageName:name.toString();
            search=(p.packageName+" "+label).toLowerCase(Locale.ROOT);icon=p.applicationInfo.loadIcon(c.getPackageManager());}
    }
    private final Runnable refresh=new Runnable(){@Override public void run(){if(isFinishing()||isDestroyed())return;
        workBanner.setVisibility(VaultService.busy?View.VISIBLE:View.GONE);activityStatus.setText(VaultService.progress);
        long finished=VaultStorage.prefs(VaultActivity.this).getLong("last_finished",0);
        if(lastFinished!=finished&&!VaultService.busy){lastFinished=finished;history=VaultHistory.load(VaultActivity.this);render();}
        if(!unlocked){handler.postDelayed(this,700);return;}
        Uri needsPassword=VaultEngine.needsPassword;if(!VaultService.busy&&needsPassword!=null){VaultEngine.needsPassword=null;importArchivePassword(needsPassword);}
        VaultEngine.Prepared p=VaultEngine.prepared;if(!VaultService.busy&&p!=null&&!p.id.equals(shownPrepared)){shownPrepared=p.id;preview(p);}
        handler.postDelayed(this,700);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);ui=new VaultUi(this);
        getWindow().setHideOverlayWindows(true);if(getActionBar()!=null)getActionBar().hide();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
        getWindow().getDecorView().setSystemUiVisibility(0);
        categories.addAll(VaultStorage.prefs(this).getStringSet("categories",VaultSystemData.CATEGORIES));
        selected.addAll(VaultStorage.prefs(this).getStringSet("selected_apps",Collections.emptySet()));
        systemApps=VaultStorage.prefs(this).getBoolean("show_system",false);history=VaultHistory.load(this);
        if(state!=null){tab=state.getInt("tab");subpage=state.getString("page","");scope=state.getString("scope","all");query=state.getString("query","");showSearch=state.getBoolean("show_search",false);
            restoreScope=state.getString("restore_scope","all");restoreCategory=state.getString("restore_category","all");}
        shell=ui.column();shell.setBackgroundColor(getColor(R.color.vault_background));
        shell.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bounds=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bounds.left,bounds.top,bounds.right,bounds.bottom);return insets;});
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(16),dp(10),dp(12),dp(12));shell.addView(toolbar);
        workBanner=ui.column();workBanner.setPadding(dp(20),dp(8),dp(20),dp(8));workBanner.setBackgroundColor(getColor(R.color.vault_card));
        activityStatus=text("",12,false);workBanner.addView(activityStatus);ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);workBanner.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        workBanner.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(R.string.vault_operation).setMessage(VaultService.progress)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.vault_cancel_operation,(d,w)->startService(new Intent(this,VaultService.class).setAction(VaultService.CANCEL))).show());
        workBanner.setVisibility(View.GONE);shell.addView(workBanner);
        body=ui.column();shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));navigation=new LinearLayout(this);shell.addView(navigation);setContentView(shell);
        lastFinished=VaultStorage.prefs(this).getLong("last_finished",0);render();loadApps();
        shell.setVisibility(View.INVISIBLE);registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);
        io.execute(()->{if(!VaultService.busy){VaultNative.recoverIdle(this);VaultSystemData.recoverSmsWrite(this);}});
    }
    @Override protected void onStart(){super.onStart();handler.post(refresh);ensureUnlocked();}
    @Override protected void onStop(){handler.removeCallbacks(refresh);super.onStop();}
    @Override protected void onDestroy(){unregisterReceiver(screenOff);io.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("tab",tab);state.putString("page",subpage);state.putString("scope",scope);state.putString("query",query);
        state.putBoolean("show_search",showSearch);state.putString("restore_scope",restoreScope);state.putString("restore_category",restoreCategory);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){back();}
    private void back(){if(!subpage.isEmpty()){subpage="";render();}else if(tab!=0){showTab(0);}else finish();}
    private int dp(int n){return ui.dp(n);}
    private TextView text(String value,int size,boolean medium){return ui.text(value,size,medium);}
    private Button button(String title){return ui.button(title,false);}
    private GradientDrawable shape(int color,int radius){return ui.shape(color,radius);}
    private LinearLayout card(LinearLayout parent){return ui.card(parent,true,true);}
    private LinearLayout scrolling(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout content=ui.column();
        content.setPadding(dp(12),0,dp(12),dp(28));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return content;}
    private void action(LinearLayout parent,int label,Runnable action){ui.action(parent,getString(label),action);}
    private void showTab(int value){tab=value;subpage="";query="";showSearch=false;render();}
    private void openPage(String value){subpage=value;render();}
    private void render(){body.removeAllViews();refilter=()->{};selectionCount=null;heading();
        navigation.setVisibility(subpage.isEmpty()?View.VISIBLE:View.GONE);ui.navigation(navigation,tab,destination->{
            if(destination==1)scope="all";if(destination==2){restoreScope="all";restoreCategory="all";}showTab(destination);});
        if(subpage.equals("quick")){quickActions();return;}if(subpage.equals("personal")){personalPage();return;}
        if(subpage.equals("app_settings")){appSettings();return;}
        switch(tab){case 0:overview();break;case 1:appPage();break;case 2:archives();break;default:settings();}
    }
    private void toolbarIcon(int icon,String label,Runnable action){toolbar.addView(ui.icon(icon,label,action),new LinearLayout.LayoutParams(dp(42),dp(42)));}
    private void heading(){toolbar.removeAllViews();toolbarIcon(R.drawable.v_back,getString(R.string.vault_back),this::back);
        LinearLayout copy=ui.column();copy.setPadding(dp(12),0,0,0);String title=getString(R.string.vault_title);
        if(subpage.equals("quick"))title=getString(R.string.vault_quick_actions);else if(subpage.equals("personal"))title=getString(R.string.vault_personal);
        else if(subpage.equals("app_settings"))title=getString(R.string.vault_app_backups);else if(tab==1)title=backupTitle();else if(tab==2)title=getString(R.string.vault_archives);else if(tab==3)title=getString(R.string.vault_settings);
        TextView name=text(title,tab==1&&subpage.isEmpty()?13:18,true);copy.addView(name);
        if(tab==1&&subpage.isEmpty()){selectionCount=text("",12,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.topMargin=dp(4);copy.addView(selectionCount,p);updateCount();}
        toolbar.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        if(subpage.isEmpty()&&tab==1){toolbarIcon(R.drawable.v_search,getString(R.string.vault_search_apps),()->{showSearch=!showSearch;render();});
            toolbarIcon(R.drawable.v_filter,getString(R.string.vault_filter),this::filterDialog);toolbarIcon(R.drawable.v_check,getString(R.string.vault_select_all),this::toggleVisible);}
        else if(subpage.isEmpty()&&tab==0)toolbarIcon(R.drawable.v_search,getString(R.string.vault_search_apps),()->{scope="all";showTab(1);showSearch=true;render();});
        toolbarIcon(R.drawable.v_more,getString(R.string.vault_more),this::moreMenu);
    }
    private String backupTitle(){int id=R.string.vault_backup_apps_title;if(scope.equals("pending"))id=R.string.vault_pending_title;else if(scope.equals("updated"))id=R.string.vault_updated_title;else if(scope.equals("existing"))id=R.string.vault_existing_title;return getString(id);}
    private void updateCount(){if(selectionCount!=null){List<AppEntry> shown=visibleApps();int count=0;for(AppEntry app:shown)if(selected.contains(app.info.packageName))count++;selectionCount.setText(getString(R.string.vault_shown_selection_count,count,shown.size()));}}
    private void moreMenu(){List<String> names=Arrays.asList(getString(R.string.vault_quick_actions),getString(R.string.vault_app_backups),getString(R.string.vault_load_group),getString(R.string.vault_save_group),getString(R.string.vault_choose_folder));
        HeckerChoiceSheet.show(this, getString(R.string.vault_title), names.toArray(new String[0]), w -> {
            switch(w){case 0:openPage("quick");break;case 1:openPage("app_settings");break;case 2:loadGroup();break;case 3:saveGroup();break;default:chooseFolder();}
        });}
    private void quickCard(LinearLayout page,boolean first,boolean last,int title,int detail,int button,Runnable action){LinearLayout item=ui.card(page,first,last);ui.copy(item,getString(title),getString(detail));
        if(button!=0)ui.action(item,getString(button),action);else{item.setOnClickListener(v->action.run());item.setFocusable(true);}}
    private void overview(){LinearLayout page=scrolling();storageDashboard(page);
        ui.section(page,R.drawable.v_apps,getString(R.string.vault_apps_quick_heading));
        quickCard(page,true,false,R.string.vault_backup_all_short,R.string.vault_backup_all_detail,R.string.vault_to_device,()->selectScope("all"));
        quickCard(page,false,false,R.string.vault_restore_all,R.string.vault_restore_all_detail,R.string.vault_from_device,()->restoreArchives("all",""));
        LinearLayout more=ui.card(page,false,true);TextView link=text(getString(R.string.vault_more_quick),16,true);link.setTextColor(getColor(R.color.vault_accent));
        link.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,getDrawable(R.drawable.v_arrow),null);link.setCompoundDrawableTintList(ColorStateList.valueOf(getColor(R.color.vault_accent)));more.addView(link);more.setOnClickListener(v->openPage("quick"));
        categoryActions(page,"messages",R.drawable.v_messages,R.string.vault_messages_heading,R.string.vault_backup_messages,R.string.vault_restore_messages,R.string.vault_messages_detail);
        categoryActions(page,"calls",R.drawable.v_phone,R.string.vault_calls_heading,R.string.vault_backup_calls,R.string.vault_restore_calls,R.string.vault_calls_detail);
        categoryActions(page,"wifi",R.drawable.v_restore,R.string.vault_wifi_heading,R.string.vault_backup_wifi,R.string.vault_restore_wifi,R.string.vault_wifi_detail);
        ui.section(page,R.drawable.v_folder,getString(R.string.vault_setup_heading));LinearLayout setup=ui.card(page,true,false);
        ui.copy(setup,getString(R.string.vault_personal),getString(R.string.vault_selected_count,selected.size(),categories.size()));
        action(setup,R.string.vault_choose_parts,()->openPage("personal"));action(setup,R.string.vault_backup_selected,()->backupOptions(true));
        LinearLayout location=ui.card(page,false,true);ui.copy(location,getString(R.string.vault_folder),folderLabel());action(location,R.string.vault_choose_folder,this::chooseFolder);
        TextView note=text(getString(R.string.vault_workflow),13,false);note.setPadding(dp(12),dp(20),dp(12),0);page.addView(note);
        String report=VaultStorage.prefs(this).getString("report","");if(!report.isEmpty()){ui.settingsSection(page,getString(R.string.vault_last_result));
            LinearLayout result=card(page);ui.copy(result,getString(VaultStorage.prefs(this).getBoolean("last_failed",false)?R.string.vault_result_attention:R.string.vault_result_saved),getString(R.string.vault_view_report));
            result.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(R.string.vault_last_result).setMessage(report).setPositiveButton(android.R.string.ok,null).show());}
    }
    private void storageDashboard(LinearLayout page){
        ui.section(page,R.drawable.v_folder,getString(R.string.vault_storage_heading));LinearLayout storage=ui.card(page,true,false);
        java.io.File volume=Environment.getDataDirectory();long total=volume.getTotalSpace(),free=volume.getUsableSpace(),used=Math.max(0,total-free);
        ui.copy(storage,getString(R.string.vault_storage_used,android.text.format.Formatter.formatFileSize(this,used)),
                getString(R.string.vault_storage_available,android.text.format.Formatter.formatFileSize(this,free),android.text.format.Formatter.formatFileSize(this,total)));
        ProgressBar meter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);meter.setMax(1000);
        meter.setProgress(total<=0?0:(int)Math.min(1000,(double)used/total*1000));meter.setProgressTintList(ColorStateList.valueOf(getColor(R.color.vault_accent)));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(6));mp.topMargin=dp(16);storage.addView(meter,mp);
        LinearLayout archives=ui.card(page,false,true);TextView summary=text(getString(R.string.vault_loading),14,true);archives.addView(summary);
        TextView location=text(folderLabel(),12,false);location.setPadding(0,dp(6),0,0);archives.addView(location);
        archives.setOnClickListener(v->showTab(2));
        io.execute(()->{String result;
            try{List<VaultStorage.Archive> entries=VaultStorage.list(this);long bytes=0;for(VaultStorage.Archive archive:entries)bytes=Math.addExact(bytes,archive.size);
                result=getString(R.string.vault_archive_storage,entries.size(),android.text.format.Formatter.formatFileSize(this,bytes));}
            catch(Exception e){result=getString(R.string.vault_no_folder);}
            String value=result;runOnUiThread(()->{if(!isDestroyed())summary.setText(value);});
        });
    }
    private void categoryActions(LinearLayout page,String category,int icon,int heading,int backup,int restore,int detail){ui.section(page,icon,getString(heading));
        quickCard(page,true,false,backup,detail,0,()->beginOperation("backup",null,Collections.emptySet(),Set.of(category),Collections.emptySet()));
        quickCard(page,false,true,restore,R.string.vault_restore_category_detail,R.string.vault_from_device,()->restoreArchives("all",category));}
    private void quickActions(){LinearLayout page=scrolling();ui.section(page,R.drawable.v_apps,getString(R.string.vault_quick_backup_heading));
        quickCard(page,true,false,R.string.vault_backup_all_short,R.string.vault_backup_all_detail,R.string.vault_to_device,()->selectScope("all"));
        quickCard(page,false,false,R.string.vault_backup_pending,R.string.vault_backup_pending_detail,R.string.vault_to_device,()->selectScope("pending"));
        quickCard(page,false,false,R.string.vault_backup_updated,R.string.vault_backup_updated_detail,R.string.vault_to_device,()->selectScope("updated"));
        quickCard(page,false,true,R.string.vault_backup_existing,R.string.vault_backup_existing_detail,R.string.vault_to_device,()->selectScope("existing"));
        ui.section(page,R.drawable.v_restore,getString(R.string.vault_quick_restore_heading));
        quickCard(page,true,false,R.string.vault_restore_all,R.string.vault_restore_all_detail,R.string.vault_from_device,()->restoreArchives("all",""));
        quickCard(page,false,false,R.string.vault_restore_missing,R.string.vault_restore_missing_detail,R.string.vault_from_device,()->restoreArchives("missing",""));
        quickCard(page,false,true,R.string.vault_restore_newer,R.string.vault_restore_newer_detail,R.string.vault_from_device,()->restoreArchives("newer",""));
    }
    private void restoreArchives(String filter,String category){restoreScope=filter;restoreCategory=category;showTab(2);}
    private void selectScope(String filter){if(!loaded){message(getString(R.string.vault_loading));return;}scope=filter;query="";selected.clear();
        for(AppEntry a:apps)if(visible(a))selected.add(a.info.packageName);saveScope();showTab(1);}
    private String folderLabel(){String folder=VaultStorage.prefs(this).getString("folder",null);return folder==null?getString(R.string.vault_no_folder):Uri.decode(folder.substring(folder.lastIndexOf('/')+1));}
    private void loadApps(){io.execute(()->{List<AppEntry> result=new ArrayList<>();for(PackageInfo p:VaultPackages.installed(this,true)){
            if(Thread.currentThread().isInterrupted())return;try{result.add(new AppEntry(this,p));}catch(RuntimeException unavailable){/* A removed package is omitted until the next scan. */}}
        result.sort(Comparator.comparing(p->p.label,String.CASE_INSENSITIVE_ORDER));
        try{VaultHistory.reconcile(this,VaultStorage.list(this));}catch(Exception ignored){}
        runOnUiThread(()->{if(isFinishing()||isDestroyed())return;apps=result;loaded=true;history=VaultHistory.load(this);
            if(!VaultStorage.prefs(this).contains("selected_apps")){for(AppEntry p:apps)if((p.info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)selected.add(p.info.packageName);saveScope();}
            if(tab==0||tab==1)render();});});}
    private void saveScope(){VaultStorage.prefs(this).edit().putStringSet("selected_apps",new HashSet<>(selected)).putStringSet("categories",new HashSet<>(categories)).apply();updateCount();}
    private boolean visible(AppEntry app){if(!systemApps&&(app.info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)return false;
        JSONObject backup=history.optJSONObject(app.info.packageName);Long version=backup==null?null:backup.optLong("version");
        return app.search.contains(query.toLowerCase(Locale.ROOT))&&VaultSelection.backup(scope,app.info.getLongVersionCode(),version);}
    private List<AppEntry> visibleApps(){List<AppEntry> result=new ArrayList<>();for(AppEntry app:apps)if(visible(app))result.add(app);return result;}
    private void toggleVisible(){List<AppEntry> visible=visibleApps();boolean all=!visible.isEmpty();for(AppEntry app:visible)all&=selected.contains(app.info.packageName);
        for(AppEntry app:visible)if(all)selected.remove(app.info.packageName);else selected.add(app.info.packageName);saveScope();refilter.run();}
    private void filterDialog(){String[] labels={getString(R.string.vault_filter_all),getString(R.string.vault_backup_pending),getString(R.string.vault_backup_updated),getString(R.string.vault_backup_existing),
            getString(systemApps ? R.string.vault_hide_system : R.string.vault_show_system)};String[] scopes={"all","pending","updated","existing"};
        HeckerChoiceSheet.show(this, getString(R.string.vault_filter), labels, Arrays.asList(scopes).indexOf(scope), which -> {
            if (which < scopes.length) scope = scopes[which];
            else {
                systemApps = !systemApps;
                VaultStorage.prefs(this).edit().putBoolean("show_system", systemApps).apply();
            }
            render();
        });}
    private void appPage(){FrameLayout frame=new FrameLayout(this);body.addView(frame,new LinearLayout.LayoutParams(-1,-1));LinearLayout page=ui.column();frame.addView(page,new FrameLayout.LayoutParams(-1,-1));
        if(showSearch){EditText search=new EditText(this);search.setSingleLine();search.setHint(R.string.vault_search_apps);search.setText(query);search.setTextColor(getColor(R.color.vault_foreground));
            search.setTextSize(15);search.setPadding(dp(20),dp(8),dp(20),dp(8));page.addView(search);
            search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();refilter.run();}public void afterTextChanged(Editable e){}});}
        ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(0);list.setSelector(android.R.color.transparent);list.setClipToPadding(false);list.setPadding(dp(12),dp(2),dp(12),dp(88));
        page.addView(list,new LinearLayout.LayoutParams(-1,0,1));List<AppEntry> filtered=new ArrayList<>();
        BaseAdapter adapter=new BaseAdapter(){@Override public int getCount(){return filtered.size();}@Override public Object getItem(int p){return filtered.get(p);}@Override public long getItemId(int p){return p;}
            @Override public View getView(int position,View recycled,ViewGroup parent){AppEntry app=filtered.get(position);AppRow row=recycled instanceof AppRow?(AppRow)recycled:new AppRow();row.bind(app);return row;}};
        TextView empty=text(getString(loaded?R.string.vault_no_matching_apps:R.string.vault_loading),15,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(24),dp(20),dp(24));page.addView(empty);list.setEmptyView(empty);list.setAdapter(adapter);
        refilter=()->{filtered.clear();filtered.addAll(visibleApps());adapter.notifyDataSetChanged();updateCount();};refilter.run();
        Button options=button(getString(R.string.vault_backup_options));options.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.v_edit,0,0,0);options.setCompoundDrawablePadding(dp(10));options.setCompoundDrawableTintList(ColorStateList.valueOf(getColor(R.color.vault_accent)));
        options.setBackground(ui.ripple(getColor(R.color.vault_fab),18));options.setElevation(dp(5));options.setPadding(dp(18),dp(16),dp(18),dp(16));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);p.setMargins(dp(16),0,dp(18),dp(18));frame.addView(options,p);options.setOnClickListener(v->backupOptions(false));}
    private final class AppRow extends LinearLayout {
        final LinearLayout surface;final ImageView icon;final TextView packageName,label,lastBackup;final CheckBox check;
        AppRow(){super(VaultActivity.this);setOrientation(VERTICAL);setPadding(0,0,0,dp(3));surface=new LinearLayout(VaultActivity.this);surface.setGravity(Gravity.CENTER_VERTICAL);surface.setPadding(dp(16),dp(14),dp(14),dp(14));surface.setMinimumHeight(dp(86));addView(surface,new LinearLayout.LayoutParams(-1,-2));
            icon=new ImageView(VaultActivity.this);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);surface.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
            LinearLayout copy=ui.column();copy.setPadding(dp(16),0,dp(4),0);packageName=text("",11,false);packageName.setSingleLine();packageName.setEllipsize(TextUtils.TruncateAt.END);packageName.setTextColor(getColor(R.color.vault_muted));copy.addView(packageName);
            label=text("",16,true);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams title=new LinearLayout.LayoutParams(-1,-2);title.topMargin=dp(3);copy.addView(label,title);
            lastBackup=text("",12,false);LinearLayout.LayoutParams last=new LinearLayout.LayoutParams(-1,-2);last.topMargin=dp(4);copy.addView(lastBackup,last);surface.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            check=new CheckBox(VaultActivity.this);check.setButtonTintList(ColorStateList.valueOf(getColor(R.color.vault_accent)));surface.addView(check,new LinearLayout.LayoutParams(dp(36),dp(48)));}
        void bind(AppEntry app){String pkg=app.info.packageName;icon.setImageDrawable(app.icon);packageName.setText(pkg);label.setText(app.label);JSONObject saved=history.optJSONObject(pkg);
            lastBackup.setText(saved==null?getString(R.string.vault_no_backup_recorded):getString(R.string.vault_last_backup_date,android.text.format.DateFormat.getMediumDateFormat(VaultActivity.this).format(new Date(saved.optLong("time")))));
            lastBackup.setTextColor(getColor(saved==null?R.color.vault_muted:R.color.vault_success));check.setOnCheckedChangeListener(null);check.setChecked(selected.contains(pkg));
            check.setContentDescription(app.label);surface.setBackground(ui.ripple(getColor(selected.contains(pkg)?R.color.vault_selected:R.color.vault_card),16));
            check.setOnCheckedChangeListener((v,on)->{if(on)selected.add(pkg);else selected.remove(pkg);saveScope();surface.setBackground(ui.ripple(getColor(on?R.color.vault_selected:R.color.vault_card),16));});
            surface.setOnClickListener(v->check.setChecked(!check.isChecked()));surface.setOnLongClickListener(v->{new AlertDialog.Builder(VaultActivity.this).setTitle(app.label)
                    .setMessage(pkg+"\n\n"+getString((app.info.applicationInfo.flags&ApplicationInfo.FLAG_ALLOW_BACKUP)!=0?R.string.vault_native_data:R.string.vault_apk_only)).setPositiveButton(android.R.string.ok,null).show();return true;});}
    }
    private void personalPage(){LinearLayout page=scrolling();ui.settingsSection(page,getString(R.string.vault_personal));String[] ids=VaultSystemData.CATEGORIES.toArray(new String[0]);
        for(int i=0;i<ids.length;i++){String id=ids[i];LinearLayout row=ui.card(page,i==0,i==ids.length-1);CheckBox box=new CheckBox(this);box.setText(categoryName(id));box.setTextColor(getColor(R.color.vault_foreground));box.setTextSize(15);
            box.setButtonTintList(ColorStateList.valueOf(getColor(R.color.vault_accent)));box.setChecked(categories.contains(id));box.setOnCheckedChangeListener((v,on)->{if(on)categories.add(id);else categories.remove(id);saveScope();});row.addView(box);}
        TextView note=text(getString(R.string.vault_personal_note),13,false);note.setPadding(dp(12),dp(18),dp(12),dp(18));page.addView(note);Button backup=ui.button(getString(R.string.vault_backup_selected),true);page.addView(backup);backup.setOnClickListener(v->backupOptions(true));}
    private void backupOptions(boolean includePersonal){if(VaultService.busy){message(getString(R.string.vault_busy));return;}if(selected.isEmpty()&&(!includePersonal||categories.isEmpty())){message(getString(R.string.vault_select_items));return;}
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content=ui.column();content.setPadding(dp(16),dp(10),dp(16),dp(24));content.setBackground(shape(getColor(R.color.vault_sheet),28));
        View handle=new View(this);handle.setBackground(shape(getColor(R.color.vault_button),2));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(24),dp(4));hp.gravity=Gravity.CENTER;hp.bottomMargin=dp(28);content.addView(handle,hp);
        ui.copy(content,getString(R.string.vault_user_parts),getString(R.string.vault_app_selection_count,selected.size()));
        boolean[] data={VaultStorage.prefs(this).getBoolean("include_data",true)};LinearLayout parts=new LinearLayout(this);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(18);content.addView(parts,pp);
        Button apk=partChip(getString(R.string.vault_part_apks),true),appData=partChip(getString(R.string.vault_part_data),data[0]);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.setMarginEnd(dp(8));parts.addView(apk,ap);parts.addView(appData,new LinearLayout.LayoutParams(0,-2,1));
        apk.setOnClickListener(v->message(getString(R.string.vault_apks_required)));appData.setOnClickListener(v->{data[0]=!data[0];styleChip(appData,getString(R.string.vault_part_data),data[0]);});
        Set<VaultExternal.Part> external = EnumSet.noneOf(VaultExternal.Part.class);
        boolean sharedAvailable = VaultEngine.sharedStorageAvailable(this);
        if (!selected.isEmpty()) {
            addPartChoices(content, EnumSet.allOf(VaultExternal.Part.class), external, sharedAvailable);
        }
        TextView note=text(getString(sharedAvailable ? R.string.vault_parts_note : R.string.vault_parts_unavailable),12,false);
        note.setPadding(0,dp(12),0,dp(12));content.addView(note);
        ui.settingsSection(content,getString(R.string.vault_backup_location));Button location=partChip(getString(R.string.vault_device),true);content.addView(location,new LinearLayout.LayoutParams(-1,dp(48)));location.setOnClickListener(v->{dialog.dismiss();chooseFolder();});
        TextView folder=text(folderLabel(),12,false);folder.setPadding(dp(8),dp(10),dp(8),dp(20));content.addView(folder);
        Button backup=ui.button(getString(R.string.vault_backup_action),true);content.addView(backup,new LinearLayout.LayoutParams(-1,-2));backup.setOnClickListener(v->{VaultStorage.prefs(this).edit().putBoolean("include_data",data[0]).apply();dialog.dismiss();
            beginOperation("backup",null,selected,includePersonal?categories:Collections.emptySet(),external);});
        ScrollView scroll=new ScrollView(this);scroll.addView(content);dialog.setContentView(scroll);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.4f);}dialog.show();
        if(window!=null){window.setLayout(-1,-2);window.getDecorView().setPadding(0,0,0,0);}}
    private void addPartChoices(LinearLayout parent, Set<VaultExternal.Part> available,
            Set<VaultExternal.Part> selectedParts, boolean enabled) {
        for (VaultExternal.Part part : VaultExternal.Part.values()) {
            if (!available.contains(part)) continue;
            String label = partName(part);
            Button chip = partChip(label, selectedParts.contains(part));
            chip.setEnabled(enabled);
            if (!enabled) chip.setAlpha(.45f);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
            size.topMargin = dp(8);
            parent.addView(chip, size);
            chip.setOnClickListener(view -> {
                boolean checked = selectedParts.add(part);
                if (!checked) selectedParts.remove(part);
                styleChip(chip, label, checked);
            });
        }
    }
    private String partName(VaultExternal.Part part) {
        switch (part) {
            case DATA: return getString(R.string.vault_external_data);
            case OBB: return getString(R.string.vault_expansion);
            case MEDIA: return getString(R.string.vault_media);
            default: throw new AssertionError(part);
        }
    }
    private Button partChip(String name,boolean checked){Button b=button(name);styleChip(b,name,checked);return b;}
    private void styleChip(Button b,String name,boolean checked){b.setText((checked?"✓  ":"")+name);b.setSelected(checked);GradientDrawable bg=shape(getColor(checked?R.color.vault_selected:R.color.vault_navigation),26);
        bg.setStroke(dp(1),getColor(checked?R.color.vault_accent:R.color.vault_outline));b.setBackground(bg);b.setTextColor(getColor(checked?R.color.vault_accent:R.color.vault_secondary));}
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
            HeckerChoiceSheet.show(this, getString(R.string.vault_load_group), names.toArray(new String[0]), which -> {
                String name=names.get(which);new AlertDialog.Builder(this).setTitle(name).setMessage(R.string.vault_group_scope)
                        .setNegativeButton(android.R.string.cancel,null).setNeutralButton(R.string.vault_delete_group,(dialog,w)->{try{VaultGroups.remove(this,name);}catch(Exception e){message(getString(R.string.vault_group_invalid));}})
                        .setPositiveButton(R.string.vault_use_group,(dialog,w)->{try{JSONObject group=groups.getJSONObject(name);selected.clear();categories.clear();
                            JSONArray appList=group.getJSONArray("apps"),categoryList=group.getJSONArray("categories");for(int i=0;i<appList.length();i++)selected.add(appList.getString(i));
                            for(int i=0;i<categoryList.length();i++)categories.add(categoryList.getString(i));saveScope();showTab(1);
                        }catch(Exception e){message(getString(R.string.vault_group_invalid));}}).show();});
        }catch(Exception e){message(getString(R.string.vault_group_invalid));}
    }
    private void archives(){LinearLayout page=scrolling();ui.settingsSection(page,getString(R.string.vault_device));LinearLayout heading=card(page);ui.copy(heading,getString(R.string.vault_folder),folderLabel());
        TextView note=text(getString(restoreCategory.equals("all")&&restoreScope.equals("all")?R.string.vault_archives_note:R.string.vault_restore_filter_note),13,false);note.setPadding(0,dp(10),0,dp(8));heading.addView(note);
        action(heading,R.string.vault_choose_folder,this::chooseFolder);EditText search=new EditText(this);search.setSingleLine();search.setHint(R.string.vault_search_archives);heading.addView(search);
        List<View> archiveCards=new ArrayList<>();Runnable filter=()->{String query=search.getText().toString().toLowerCase(Locale.ROOT);for(View card:archiveCards)card.setVisibility(card.getTag().toString().toLowerCase(Locale.ROOT).contains(query)?View.VISIBLE:View.GONE);};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});
        TextView loading=text(getString(R.string.vault_loading),13,false);page.addView(loading);
        io.execute(()->{try{List<VaultStorage.Archive> entries=VaultStorage.list(this);runOnUiThread(()->{if(tab!=2||isDestroyed())return;page.removeView(loading);
            if(entries.isEmpty())page.addView(text(getString(R.string.vault_no_archives),14,false));for(VaultStorage.Archive entry:entries){LinearLayout item=card(page);LinearLayout.LayoutParams spacing=(LinearLayout.LayoutParams)item.getLayoutParams();spacing.topMargin=dp(8);item.setLayoutParams(spacing);item.setTag(entry.name);archiveCards.add(item);item.addView(text(entry.name,15,true));
                item.addView(text(android.text.format.Formatter.formatFileSize(this,entry.size)+" · AES-256-GCM",12,false));action(item,R.string.vault_inspect,()->beginOperation("inspect",entry.uri));
                Button remove=button(getString(R.string.vault_delete));remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(R.string.vault_delete).setMessage(R.string.vault_delete_confirm)
                        .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.vault_delete,(d,w)->io.execute(()->{try{VaultStorage.remove(this,entry.uri);runOnUiThread(()->showTab(2));}catch(Exception e){runOnUiThread(()->message(getString(R.string.vault_folder_error)));}})).show());
                LinearLayout.LayoutParams deleteSize = new LinearLayout.LayoutParams(-1, -2);
                deleteSize.topMargin = dp(8);
                item.addView(remove, deleteSize);
            }filter.run();});
        }catch(Exception e){runOnUiThread(()->{loading.setText(R.string.vault_folder_error);});}});
    }
    private void settingRow(LinearLayout page,boolean first,boolean last,String title,String detail,Runnable action){LinearLayout row=ui.card(page,first,last);ui.copy(row,title,detail);if(action!=null){row.setOnClickListener(v->action.run());row.setFocusable(true);}}
    private void switchRow(LinearLayout page,boolean first,boolean last,int title,String key,boolean fallback,Runnable changed){LinearLayout row=ui.card(page,first,last);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text(getString(title),16,true);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));Switch toggle=new Switch(this);toggle.setContentDescription(getString(title));toggle.setChecked(VaultStorage.prefs(this).getBoolean(key,fallback));
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{getColor(R.color.vault_accent),getColor(R.color.vault_secondary)}));
        toggle.setTrackTintList(ColorStateList.valueOf(getColor(R.color.vault_outline)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));p.setMarginStart(dp(16));row.addView(toggle,p);
        toggle.setOnCheckedChangeListener((v,on)->{VaultStorage.prefs(this).edit().putBoolean(key,on).apply();if(changed!=null)changed.run();});row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));}
    private void appSettings(){LinearLayout page=scrolling();ui.settingsSection(page,getString(R.string.vault_general));
        switchRow(page,true,false,R.string.vault_show_system,"show_system",false,()->systemApps=VaultStorage.prefs(this).getBoolean("show_system",false));
        settingRow(page,false,false,getString(R.string.vault_manage_groups),getString(R.string.vault_manage_groups_detail),()->
                HeckerChoiceSheet.show(this, getString(R.string.vault_manage_groups),
                        new String[]{getString(R.string.vault_load_group),getString(R.string.vault_save_group)},
                        which -> { if (which == 0) loadGroup(); else saveGroup(); }));
        switchRow(page,false,true,R.string.vault_include_app_data,"include_data",true,null);
        ui.settingsSection(page,getString(R.string.vault_multiple_backups));settingRow(page,true,true,getString(R.string.vault_backup_strategy),getString(R.string.vault_strategy_detail,VaultStorage.prefs(this).getInt("retention",3)),this::retentionDialog);
        encryptionSettings(page);ui.settingsSection(page,getString(R.string.vault_app_data));settingRow(page,true,false,getString(R.string.vault_native_backend),getString(R.string.vault_native_note),null);
        settingRow(page,false,true,getString(R.string.vault_enable_android),getString(R.string.vault_enable_android_short),this::enableAndroidBackup);
    }
    private void settings(){LinearLayout page=scrolling();ui.settingsSection(page,getString(R.string.vault_general));
        settingRow(page,true,false,getString(R.string.vault_app_backups),getString(R.string.vault_app_settings_detail),()->openPage("app_settings"));
        settingRow(page,false,true,getString(R.string.vault_folder),folderLabel(),this::chooseFolder);encryptionSettings(page);
        ui.settingsSection(page,getString(R.string.vault_schedules));boolean enabled=VaultStorage.prefs(this).getBoolean("scheduled",false);
        settingRow(page,true,false,getString(enabled?R.string.vault_schedule_enabled:R.string.vault_enable_schedule),getString(R.string.vault_schedule_note),()->beginOperation("schedule",null));
        if(enabled)settingRow(page,false,false,getString(R.string.vault_disable_schedule),null,()->{VaultJobs.configure(this,false);render();});
        switchRow(page,false,false,R.string.vault_weekly,"weekly",false,this::reschedule);switchRow(page,false,false,R.string.vault_charging,"charging",true,this::reschedule);
        switchRow(page,false,false,R.string.vault_schedule_all,"schedule_all",true,this::reschedule);
        settingRow(page,false,true,getString(R.string.vault_retention),getString(R.string.vault_retention_count,VaultStorage.prefs(this).getInt("retention",3)),this::retentionDialog);
        ui.settingsSection(page,getString(R.string.vault_limits_title));settingRow(page,true,true,getString(R.string.vault_native_backend),getString(R.string.vault_limits),null);
    }
    private void encryptionSettings(LinearLayout page){ui.settingsSection(page,getString(R.string.vault_encryption_compression));
        settingRow(page,true,false,getString(R.string.vault_encrypted_snapshots),getString(R.string.vault_encrypted_detail),()->new AlertDialog.Builder(this).setTitle(R.string.vault_security).setMessage(R.string.vault_encryption_note).setPositiveButton(android.R.string.ok,null).show());
        int level=VaultStorage.prefs(this).getInt("compression",VaultArchive.FASTEST);int label=level==VaultArchive.BALANCED?R.string.vault_balanced:level==VaultArchive.SMALLEST?R.string.vault_smallest:R.string.vault_fastest;
        settingRow(page,false,false,getString(R.string.vault_compression),getString(label),this::compressionDialog);
        settingRow(page,false,true,getString(R.string.vault_reset_password),getString(R.string.vault_reset_password_detail),this::resetPassword);}
    private void compressionDialog(){int[] levels={VaultArchive.FASTEST,VaultArchive.BALANCED,VaultArchive.SMALLEST};String[] labels={getString(R.string.vault_fastest),getString(R.string.vault_balanced),getString(R.string.vault_smallest)};
        int current=VaultStorage.prefs(this).getInt("compression",VaultArchive.FASTEST),choice=0;for(int i=0;i<levels.length;i++)if(levels[i]==current)choice=i;
        HeckerChoiceSheet.show(this,getString(R.string.vault_compression),labels,choice,w->{VaultStorage.prefs(this).edit().putInt("compression",levels[w]).apply();render();});}
    private void retentionDialog(){String[] labels={"1","3","5","10"};int[] values={1,3,5,10};int current=VaultStorage.prefs(this).getInt("retention",3),choice=1;
        for(int i=0;i<values.length;i++)if(values[i]==current)choice=i;
        HeckerChoiceSheet.show(this,getString(R.string.vault_retention),labels,choice,w->{VaultStorage.prefs(this).edit().putInt("retention",values[w]).apply();render();});}
    private void reschedule(){if(VaultStorage.prefs(this).getBoolean("scheduled",false))try{VaultJobs.configure(this,true);}catch(Exception e){message(getString(R.string.vault_schedule_failed));}}
    private void enableAndroidBackup(){new AlertDialog.Builder(this).setTitle(R.string.vault_enable_android).setMessage(R.string.vault_enable_android_note)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{new BackupManager(this).setBackupEnabled(true);message(getString(R.string.vault_android_enabled));}).show();}
    private void chooseFolder(){if(VaultService.busy){message(getString(R.string.vault_busy));return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),FOLDER);}
    private void beginOperation(String operation,Uri archive){beginOperation(operation,archive,selected,categories,Collections.emptySet());}
    private void beginOperation(String operation,Uri archive,Collection<String> chosenApps,Collection<String> chosenCategories,
            Set<VaultExternal.Part> externalParts){
        if(!unlocked){ensureUnlocked();return;}
        if(VaultService.busy){message(getString(R.string.vault_busy));return;}
        try{VaultStorage.folder(this);VaultEngine.owner(this);}catch(Exception e){message(getString(R.string.vault_choose_folder));return;}
        if(operation.equals("backup")&&chosenApps.isEmpty()&&chosenCategories.isEmpty()){message(getString(R.string.vault_select_items));return;}
        List<String> requestedApps=new ArrayList<>(chosenApps);Set<String> requestedCategories=new HashSet<>(chosenCategories);
        Set<VaultExternal.Part> requestedParts=Set.copyOf(externalParts);
        io.execute(()->{try{char[] pass=VaultKeys.recalled(this);
            VaultRequest request=new VaultRequest(operation,pass,requestedApps,requestedCategories,archive,
                    VaultStorage.prefs(this).getBoolean("include_data",true)?"both":"apk",false,requestedParts);
            runOnUiThread(()->{if(isDestroyed()||!unlocked){request.clear();return;}launch(request);});
        }catch(Exception e){runOnUiThread(()->message(getString(R.string.vault_key_unavailable)));}});
    }
    private void ensureUnlocked(){
        if(unlocked||authPending||setupShowing||isFinishing())return;
        shell.setVisibility(View.INVISIBLE);KeyguardManager kg=getSystemService(KeyguardManager.class);
        if(kg.isDeviceSecure()){
            Intent confirm=kg.createConfirmDeviceCredentialIntent(getString(R.string.vault_title),getString(R.string.vault_entry_auth));
            if(confirm==null){message(getString(R.string.vault_key_unavailable));finish();return;}
            authPending=true;startActivityForResult(confirm,AUTH);
        }else entered();
    }
    private void entered(){
        if(!VaultKeys.configured(this)){configurePassword(false);return;}
        unlocked=true;shell.setVisibility(View.VISIBLE);
    }
    private void resetPassword(){
        if(VaultService.busy){message(getString(R.string.vault_busy));return;}
        KeyguardManager kg=getSystemService(KeyguardManager.class);
        if(!kg.isDeviceSecure()){message(getString(R.string.vault_reset_requires_lock));return;}
        Intent confirm=kg.createConfirmDeviceCredentialIntent(getString(R.string.vault_reset_password),getString(R.string.vault_reset_auth));
        if(confirm!=null){authPending=true;startActivityForResult(confirm,RESET_AUTH);}
    }
    private void configurePassword(boolean resetting){
        if(setupShowing)return;setupShowing=true;
        LinearLayout fields=ui.column();fields.setPadding(dp(24),dp(8),dp(24),dp(8));
        EditText password=secret(R.string.vault_password),confirm=secret(R.string.vault_confirm_password);
        fields.addView(password);fields.addView(confirm);fields.addView(text(getString(resetting?R.string.vault_reset_note:R.string.vault_first_setup_note),13,false));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(resetting?R.string.vault_reset_password:R.string.vault_first_setup)
                .setView(fields).setCancelable(resetting).setNegativeButton(android.R.string.cancel,(d,w)->{if(!resetting)finish();})
                .setPositiveButton(R.string.vault_save_password,null).create();
        dialog.setOnDismissListener(d->{setupShowing=false;password.setText("");confirm.setText("");});
        dialog.setOnShowListener(d->{dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                char[] pass=new char[password.length()];password.getText().getChars(0,pass.length,pass,0);
                if(pass.length<12||pass.length>1024||!TextUtils.equals(password.getText(),confirm.getText())){
                    Arrays.fill(pass,'\0');password.setError(getString(R.string.vault_password_invalid));return;}
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                io.execute(()->{boolean saved=false;
                    try{VaultKeys.remember(this,pass);saved=true;}catch(Exception ignored){}finally{Arrays.fill(pass,'\0');}
                    boolean success=saved;runOnUiThread(()->{if(isDestroyed())return;
                        if(success){dialog.dismiss();unlocked=true;shell.setVisibility(View.VISIBLE);render();}
                        else{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);password.setError(getString(R.string.vault_key_unavailable));}});
                });
            });});dialog.show();
    }
    private void importArchivePassword(Uri archive){
        EditText password=secret(R.string.vault_password);LinearLayout fields=ui.column();fields.setPadding(dp(24),dp(8),dp(24),dp(8));
        fields.addView(text(getString(R.string.vault_import_password_note),13,false));fields.addView(password);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.vault_unlock_archive).setView(fields)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnDismissListener(d->password.setText(""));dialog.setOnShowListener(d->{dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{char[] pass=new char[password.length()];password.getText().getChars(0,pass.length,pass,0);
                if(pass.length<12||pass.length>1024){Arrays.fill(pass,'\0');password.setError(getString(R.string.vault_password_invalid));return;}
                VaultRequest request=new VaultRequest("inspect",pass,Collections.emptySet(),Collections.emptySet(),archive,"both",false);request.rememberPassword=true;
                dialog.dismiss();launch(request);
            });});dialog.show();
    }
    private EditText secret(int hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine();e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setTransformationMethod(PasswordTransformationMethod.getInstance());e.setSaveEnabled(false);e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);e.setFilterTouchesWhenObscured(true);return e;}
    private void launch(VaultRequest request){if(!unlocked){request.clear();ensureUnlocked();return;}try{VaultService.launch(this,request);activityStatus.setText(R.string.vault_preparing);}catch(Exception e){request.clear();message(getString(R.string.vault_start_failed));}}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);
        if(code==AUTH||code==RESET_AUTH){authPending=false;
            if(result==RESULT_OK){if(code==RESET_AUTH)configurePassword(true);else entered();}else if(code==AUTH)finish();return;}
        if(code==FOLDER&&result==RESULT_OK&&data!=null&&data.getData()!=null){try{Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,
                data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));VaultStorage.prefs(this).edit().putString("folder",uri.toString()).apply();history=VaultHistory.load(this);render();}
            catch(SecurityException e){message(getString(R.string.vault_folder_error));}}
    }
    private void preview(VaultEngine.Prepared prepared){
        try{JSONObject snapshot=prepared.snapshot;JSONArray apps=snapshot.getJSONArray("apps"),available=snapshot.getJSONArray("categories");
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),0,dp(20),0);content.addView(text(getString(R.string.vault_preview_summary,apps.length(),available.length()),15,true));
            Set<String> chosenApps=new LinkedHashSet<>(),chosenCategories=new LinkedHashSet<>();
            RadioGroup modes=new RadioGroup(this);int[] modeIds={101,102,103};String[] modesValues={"both","apk","data"};int[] modeLabels={R.string.vault_apk_data,R.string.vault_apk_only,R.string.vault_data_only};
            for(int i=0;i<modeIds.length;i++){RadioButton b=new RadioButton(this);b.setId(modeIds[i]);b.setText(modeLabels[i]);modes.addView(b);}modes.check(modeIds[0]);content.addView(modes);
            Set<VaultExternal.Part> archiveParts = EnumSet.noneOf(VaultExternal.Part.class);
            for (int i = 0; i < apps.length(); i++) {
                archiveParts.addAll(VaultPackages.externalParts(apps.getJSONObject(i)));
            }
            Set<VaultExternal.Part> chosenParts = EnumSet.noneOf(VaultExternal.Part.class);
            if (!archiveParts.isEmpty()) {
                boolean canRestore = VaultEngine.sharedStorageAvailable(this);
                if (canRestore) chosenParts.addAll(archiveParts);
                LinearLayout partOptions = ui.column();
                ui.settingsSection(partOptions, getString(R.string.vault_restore_parts));
                addPartChoices(partOptions, archiveParts, chosenParts, canRestore);
                if (!canRestore) partOptions.addView(text(getString(R.string.vault_parts_unavailable), 12, false));
                content.addView(partOptions);
                modes.setOnCheckedChangeListener((group, id) ->
                        partOptions.setVisibility(id == modeIds[1] ? View.GONE : View.VISIBLE));
            }
            for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.getString("package");Long installed=null;for(AppEntry entry:this.apps)if(entry.info.packageName.equals(pkg)){installed=entry.info.getLongVersionCode();break;}
                boolean included=(restoreCategory.equals("all")||restoreCategory.isEmpty())&&VaultSelection.restore(restoreScope,app.getLong("version"),installed);
                if(included)chosenApps.add(pkg);CheckBox check=new CheckBox(this);
                List<String> partNames = new ArrayList<>();
                for (VaultExternal.Part part : VaultPackages.externalParts(app)) partNames.add(partName(part));
                String details = app.optString("result");
                if (!partNames.isEmpty()) details += " · " + String.join(", ", partNames);
                check.setText(app.getString("label")+"\n"+details);check.setChecked(included);check.setButtonTintList(ColorStateList.valueOf(getColor(R.color.vault_accent)));check.setOnCheckedChangeListener((v,on)->{if(on)chosenApps.add(pkg);else chosenApps.remove(pkg);});content.addView(check);}
            for(int i=0;i<available.length();i++){String category=available.getString(i);boolean included=restoreCategory.equals("all")||restoreCategory.equals(category);if(included)chosenCategories.add(category);CheckBox check=new CheckBox(this);check.setText(categoryName(category));check.setChecked(included);check.setButtonTintList(ColorStateList.valueOf(getColor(R.color.vault_accent)));
                check.setOnCheckedChangeListener((v,on)->{if(on)chosenCategories.add(category);else chosenCategories.remove(category);});content.addView(check);}
            JSONArray notes=snapshot.optJSONArray("notes");if(notes!=null)for(int i=0;i<notes.length();i++)content.addView(text(notes.getString(i),12,false));
            ScrollView scroll=new ScrollView(this);scroll.addView(content);
            new AlertDialog.Builder(this).setTitle(R.string.vault_restore_preview).setView(scroll).setNegativeButton(android.R.string.cancel,(d,w)->{VaultEngine.prepared=null;io.execute(()->VaultEngine.cleanup(prepared.root));})
                    .setPositiveButton(R.string.vault_restore_selected,(d,w)->{if(chosenApps.isEmpty()&&chosenCategories.isEmpty()){message(getString(R.string.vault_select_items));return;}
                        new AlertDialog.Builder(this).setTitle(R.string.vault_restore_selected).setMessage(R.string.vault_restore_confirm).setNegativeButton(android.R.string.cancel,null)
                                .setPositiveButton(R.string.vault_restore_selected,(confirm,which)->{int index=modes.getCheckedRadioButtonId()-101;
                                    VaultRequest request=new VaultRequest("restore",new char[0],chosenApps,chosenCategories,null,modesValues[index],false,chosenParts);request.preparedId=prepared.id;launch(request);}).show();}).show();
        }catch(Exception e){message(getString(R.string.vault_invalid_archive));}
    }
    private String categoryName(String id){int[] names={R.string.vault_wifi,R.string.vault_contacts,R.string.vault_messages,R.string.vault_calls,R.string.vault_wallpapers,R.string.vault_preferences,R.string.vault_launcher};
        String[] ids=VaultSystemData.CATEGORIES.toArray(new String[0]);for(int i=0;i<ids.length;i++)if(id.equals(ids[i]))return getString(names[i]);return id;}
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
