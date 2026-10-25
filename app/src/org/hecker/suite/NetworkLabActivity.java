// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.os.*;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.hecker.netlab.INetLabService;

/** Native NetLab presentation; network administration is confined to a separate signature service. */
public final class NetworkLabActivity extends Activity {
    private static final String LOCAL_PERMISSION="android.permission.ACCESS_LOCAL_NETWORK";
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Bundle> lan=new ArrayList<>(),devices=new ArrayList<>();
    private final Set<String> selected=new LinkedHashSet<>();
    private final List<Button> tabButtons=new ArrayList<>();
    private final ArrayDeque<String> observations=new ArrayDeque<>();
    private NetLabPreferences preferences;
    private INetLabService controller;
    private Bundle state=new Bundle();
    private LinearLayout content,list;
    private TextView badge,counters,scanStatus,rates,report;
    private NetLabTrafficView graph;
    private SuiteAnimation animation;
    private NetLabDiscovery scanner;
    private NetLabDiscovery.Link link;
    private boolean started,bound,polling,acting,scanning,rateReady;
    private int tab,filter,limit=50,generation;
    private String query="",scope="",gateway="",gatewayMac="",arpStatus="",lastList="";
    private long previousRx=-1,previousTx=-1,previousTime,rxRate,txRate;
    private final Runnable poll=new Runnable(){public void run(){refresh();if(started)main.postDelayed(this,2000);}};
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){controller=INetLabService.Stub.asInterface(binder);refresh();}
        public void onServiceDisconnected(ComponentName name){controller=null;state=new Bundle();updateHeader();renderTab();}
        public void onBindingDied(ComponentName name){controller=null;state=new Bundle();updateHeader();}
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);preferences=new NetLabPreferences(this);tab=saved==null?0:Math.max(0,Math.min(5,saved.getInt("tab")));
        LinearLayout page=Ui.page(this,R.string.netlab_title);
        LinearLayout hero=Ui.card(this);animation=new SuiteAnimation(this,hero);animation.show(R.raw.suite_netlab,true);
        hero.addView(Ui.text(this,"HeckerNetLab",24,true));hero.addView(Ui.text(this,getString(R.string.netlab_intro),14,false));
        badge=Ui.text(this,"",16,true);hero.addView(badge);counters=Ui.text(this,"",13,false);hero.addView(counters);
        report=Ui.text(this,"",13,false);report.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);hero.addView(report);page.addView(hero);
        LinearLayout actions=new LinearLayout(this);
        Button scan=button(R.string.netlab_scan,v->scan());Button restore=button(R.string.netlab_restore_all,v->confirmRestore());
        actions.addView(scan,new LinearLayout.LayoutParams(0,-2,1));actions.addView(restore,new LinearLayout.LayoutParams(0,-2,1));page.addView(actions);
        scanStatus=Ui.text(this,getString(R.string.netlab_scan_idle),12,false);scanStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(scanStatus);
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this);int[] titles={R.string.netlab_devices,R.string.netlab_jail,R.string.netlab_defender,R.string.netlab_traffic,R.string.netlab_profiles,R.string.netlab_diagnostics};
        for(int i=0;i<titles.length;i++){final int target=i;Button b=button(titles[i],v->{tab=target;renderTab();});tabButtons.add(b);row.addView(b);}tabs.addView(row);page.addView(tabs);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,Ui.dp(this,12),0,0);page.addView(content);Ui.scroll(this,page);
        updateHeader();renderTab();
    }
    private Button button(int title,View.OnClickListener click){Button button=Ui.button(this);button.setText(title);button.setOnClickListener(click);return button;}
    private void text(LinearLayout parent,int title,int summary){parent.addView(Ui.text(this,getString(title),18,true));parent.addView(Ui.text(this,getString(summary),14,false));}
    @Override protected void onStart(){super.onStart();started=true;animation.show(R.raw.suite_netlab,true);
        try{bound=bindService(new Intent().setComponent(new ComponentName("org.hecker.netlab","org.hecker.netlab.NetLabService")),connection,Context.BIND_AUTO_CREATE);}
        catch(SecurityException unavailable){bound=false;}main.post(poll);
    }
    @Override protected void onStop(){started=false;main.removeCallbacks(poll);generation++;if(scanner!=null)scanner.close();scanner=null;scanning=false;
        animation.stop();if(bound){unbindService(connection);bound=false;}controller=null;super.onStop();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("tab",tab);super.onSaveInstanceState(out);}
    private void refresh(){
        if(polling||acting||!started)return;polling=true;INetLabService service=controller;
        worker.execute(()->{Bundle updated=new Bundle();NetLabDiscovery.Link current=null;String mac="";long rx=-1,tx=-1;
            try{if(service!=null)updated=service.state();else updated.putString("error","service_unavailable");}catch(RemoteException|RuntimeException unavailable){updated.putString("error","service_unavailable");}
            try{current=NetLabDiscovery.wifi(this);if(current!=null){mac=NetLabDiscovery.gatewayMac(current);String iface=current.properties.getInterfaceName();
                if(iface!=null){rx=TrafficStats.getRxBytes(iface);tx=TrafficStats.getTxBytes(iface);}}}catch(RuntimeException unavailable){}
            Bundle result=updated;NetLabDiscovery.Link network=current;String neighbor=mac;long received=rx,sent=tx;
            main.post(()->{polling=false;if(!started)return;state=result;updateLink(network,neighbor,received,sent);mergeDevices();updateHeader();
                if(tab==0||tab==1)renderDevices();else if(tab==3)updateTraffic();else if(tab==2||tab==5)renderTab();});
        });
    }
    private void updateLink(NetLabDiscovery.Link current,String mac,long rx,long tx){
        String next=current==null?"":current.scope;boolean changed=!scope.equals(next);
        if(changed){scope=next;lan.clear();generation++;if(scanner!=null)scanner.close();scanner=null;scanning=false;
            gateway="";gatewayMac="";observations.clear();previousRx=previousTx=-1;rxRate=txRate=0;
            scanStatus.setText(current==null?R.string.netlab_no_wifi:R.string.netlab_scan_idle);}
        link=current;
        if(current!=null){String nextGateway=current.gateway();
            if(!gateway.isEmpty()&&!gateway.equals(nextGateway))observation(R.string.netlab_gateway_changed);
            if(!gatewayMac.isEmpty()&&!mac.isEmpty()&&!gatewayMac.equals(mac))observation(R.string.netlab_neighbor_changed);
            gateway=nextGateway;gatewayMac=mac;arpStatus=getString(mac.isEmpty()?R.string.netlab_arp_unavailable:R.string.netlab_arp_observed);}
        long now=SystemClock.elapsedRealtime();
        rateReady=rx>=0&&tx>=0&&previousRx>=0&&rx>=previousRx&&tx>=previousTx&&now>previousTime;
        if(rateReady){rxRate=(rx-previousRx)*1000/(now-previousTime);txRate=(tx-previousTx)*1000/(now-previousTime);}
        else rxRate=txRate=0;previousRx=rx;previousTx=tx;previousTime=now;
    }
    private void observation(int message){String value=getString(message);if(observations.isEmpty()||!observations.getLast().equals(value)){if(observations.size()==8)observations.removeFirst();observations.addLast(value);}}
    private void mergeDevices(){
        LinkedHashMap<String,Bundle> all=new LinkedHashMap<>();
        ArrayList<Bundle> nativeClients=state.getParcelableArrayList("devices",Bundle.class);
        if(nativeClients!=null)for(Bundle d:nativeClients)all.put(d.getString("id"),d);
        for(Bundle d:lan){boolean recent=SystemClock.elapsedRealtime()-d.getLong("observed_elapsed")<30_000;d.putBoolean("online",recent);d.putString("state",recent?"observed":"last_scan");all.put(d.getString("id"),d);}
        for(Bundle d:all.values())if(d.getBoolean("online"))preferences.seen(d.getString("id"),addresses(d),d.getLong("observed_wall",System.currentTimeMillis()));
        for(Bundle d:preferences.remembered())all.putIfAbsent(d.getString("id"),d);
        devices.clear();devices.addAll(all.values());selected.retainAll(all.keySet());
    }
    private boolean jailed(Bundle d){return Arrays.asList("applying","enforced","restoring","failed").contains(d.getString("state"));}
    private void updateHeader(){int online=0,jail=0;for(Bundle d:devices){if(d.getBoolean("online"))online++;if(jailed(d))jail++;}
        badge.setText(getString(state.getBoolean("hotspot")?R.string.netlab_phone_hotspot:R.string.netlab_monitor_only)
                +" · "+getString(state.getBoolean("can_pause")?R.string.netlab_controls_ready:state.getBoolean("hotspot")?R.string.netlab_controls_unavailable:R.string.netlab_read_only));
        counters.setText(getString(R.string.netlab_counters,devices.size(),online,jail));
        String error=state.getString("error","");report.setVisibility(error.isEmpty()?View.GONE:View.VISIBLE);
        if(!error.isEmpty())report.setText(getString(R.string.netlab_last_report,explain(error)));
    }
    private void renderTab(){
        for(int i=0;i<tabButtons.size();i++)tabButtons.get(i).setSelected(i==tab);content.removeAllViews();graph=null;rates=null;list=null;lastList="";
        if(tab==0||tab==1){
            if(tab==0){EditText search=new EditText(this);search.setSingleLine(true);search.setHint(R.string.netlab_search);search.setTextColor(getColor(R.color.hecker_foreground));search.setHintTextColor(getColor(R.color.hecker_secondary));search.setText(query);
                search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}
                    public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();limit=50;lastList="";renderDevices();}});content.addView(search);
                LinearLayout filters=new LinearLayout(this);int[] names={R.string.netlab_all,R.string.netlab_online,R.string.netlab_trusted};
                for(int i=0;i<names.length;i++){final int f=i;Button b=button(names[i],v->{filter=f;renderTab();});b.setSelected(filter==i);filters.addView(b,new LinearLayout.LayoutParams(0,-2,1));}content.addView(filters);
            }else content.addView(Ui.text(this,getString(R.string.netlab_jail_summary),14,false));
            LinearLayout bulk=new LinearLayout(this);bulk.addView(button(R.string.netlab_pause_selected,v->chooseDuration(selectedDevices(false))),new LinearLayout.LayoutParams(0,-2,1));
            bulk.addView(button(R.string.netlab_restore_selected,v->perform("restore",selectedDevices(true),0)),new LinearLayout.LayoutParams(0,-2,1));content.addView(bulk);
            list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);renderDevices();return;
        }
        if(tab==2){LinearLayout card=Ui.card(this);text(card,R.string.netlab_defender,R.string.netlab_defender_summary);
            card.addView(Ui.text(this,getString(R.string.netlab_gateway,gateway.isEmpty()?getString(R.string.netlab_unknown):gateway),14,true));
            card.addView(Ui.text(this,link==null?getString(R.string.netlab_no_wifi):arpStatus,13,false));
            if(!gatewayMac.isEmpty())card.addView(Ui.text(this,getString(R.string.netlab_gateway_neighbor,gatewayMac),13,false));
            for(String note:observations)card.addView(Ui.text(this,note,13,false));content.addView(card);return;}
        if(tab==3){LinearLayout card=Ui.card(this);text(card,R.string.netlab_traffic,R.string.netlab_traffic_summary);
            rates=Ui.text(this,"",16,true);card.addView(rates);graph=new NetLabTrafficView(this);card.addView(graph,new LinearLayout.LayoutParams(-1,Ui.dp(this,160)));content.addView(card);updateTraffic();return;}
        if(tab==4){LinearLayout card=Ui.card(this);text(card,R.string.netlab_profiles,R.string.netlab_profiles_summary);
            card.addView(button(R.string.netlab_pause_current,v->{List<Bundle> clients=new ArrayList<>();for(Bundle d:devices)if(d.getBoolean("managed")&&d.getBoolean("online")&&!jailed(d))clients.add(d);chooseDuration(clients);}));
            card.addView(button(R.string.netlab_speed_limiter,v->limiter()));
            card.addView(Ui.text(this,getString(R.string.netlab_lockdown_unavailable),13,false));
            Switch history=new Switch(this);history.setText(R.string.netlab_history);history.setTextColor(getColor(R.color.hecker_foreground));history.setChecked(preferences.history());
            history.setOnCheckedChangeListener((v,value)->{preferences.history(value);mergeDevices();updateHeader();});card.addView(history);
            card.addView(Ui.text(this,getString(R.string.netlab_history_summary),13,false));
            card.addView(button(R.string.netlab_clear_history,v->{preferences.clearHistory();mergeDevices();updateHeader();toast(R.string.netlab_history_cleared);}));
            card.addView(button(R.string.netlab_clear_metadata,v->new AlertDialog.Builder(this).setTitle(R.string.netlab_clear_metadata).setMessage(R.string.netlab_clear_metadata_summary)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{preferences.clearAll();mergeDevices();updateHeader();renderTab();}).show()));content.addView(card);return;}
        LinearLayout card=Ui.card(this);text(card,R.string.netlab_diagnostics,R.string.netlab_diagnostics_summary);
        diagnostic(card,R.string.netlab_controller,state.getBoolean("available"));diagnostic(card,R.string.netlab_hal,state.getBoolean("hal_block"));
        diagnostic(card,R.string.netlab_native_callbacks,state.getBoolean("callbacks"));diagnostic(card,R.string.netlab_timers,state.getBoolean("can_time"));
        if(!state.getBoolean("can_time")){
            card.addView(Ui.text(this,getString(R.string.netlab_timer_setup),13,false));
            card.addView(button(R.string.netlab_alarm_settings,v->{try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:org.hecker.netlab")));}catch(ActivityNotFoundException e){toast(R.string.native_page_unavailable);}}));
            card.addView(button(R.string.netlab_battery_settings,v->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(ActivityNotFoundException e){toast(R.string.native_page_unavailable);}}));
        }
        diagnostic(card,R.string.netlab_speed_limiter,false);diagnostic(card,R.string.netlab_router,false);
        card.addView(Ui.text(this,getString(R.string.netlab_offload,state.getInt("offload",-1)),13,false));
        card.addView(Ui.text(this,explain(state.getString("error","")),13,false));card.addView(Ui.text(this,getString(R.string.netlab_capability_limits),13,false));
        card.addView(button(R.string.netlab_hotspot_settings,v->{try{startActivity(new Intent("android.settings.WIFI_TETHER_SETTINGS").setPackage("com.android.settings"));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}}));
        ArrayList<Bundle> events=state.getParcelableArrayList("events",Bundle.class);if(events!=null)for(Bundle e:events)
            card.addView(Ui.text(this,android.text.format.DateFormat.getTimeFormat(this).format(new Date(e.getLong("time")))+" · "+explain(e.getString("code","")),12,false));
        content.addView(card);
    }
    private void diagnostic(LinearLayout card,int label,boolean supported){card.addView(Ui.text(this,getString(label)+" · "+getString(supported?R.string.netlab_supported:R.string.netlab_unavailable),14,false));}
    private void renderDevices(){
        if(list==null)return;String fingerprint=devices.toString()+query+filter+tab+selected.toString()+acting+state.getBoolean("can_pause")+limit;
        if(fingerprint.equals(lastList))return;lastList=fingerprint;list.removeAllViews();int shown=0,total=0;
        String needle=tab==0?query.toLowerCase(Locale.ROOT):"";
        for(Bundle d:devices){String id=d.getString("id"),name=label(d);if(tab==1&&!jailed(d))continue;
            if(tab==0&&(filter==1&&!d.getBoolean("online")||filter==2&&!preferences.trusted(id)))continue;
            if(!needle.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(needle)&&!addresses(d).contains(needle)&&!d.getString("mac","").contains(needle))continue;
            total++;if(shown++>=limit)continue;LinearLayout card=Ui.card(this);LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox check=new CheckBox(this);check.setContentDescription(getString(R.string.netlab_select,name));check.setChecked(selected.contains(id));
            check.setOnCheckedChangeListener((v,on)->{if(on)selected.add(id);else selected.remove(id);});title.addView(check);
            TextView nameView=Ui.text(this,name,17,true);title.addView(nameView,new LinearLayout.LayoutParams(0,-2,1));card.addView(title);
            card.addView(Ui.text(this,(d.getBoolean("managed")?getString(R.string.netlab_phone_hotspot):getString(R.string.netlab_monitor_only))+" · "+status(d),12,false));
            if(!addresses(d).isEmpty())card.addView(Ui.text(this,addresses(d),13,false));
            if(d.getLong("until")>0)card.addView(Ui.text(this,getString(R.string.netlab_until,android.text.format.DateFormat.getTimeFormat(this).format(new Date(d.getLong("until")))),12,false));
            nameView.setOnClickListener(v->details(d));Button action=button(jailed(d)?R.string.netlab_restore:R.string.netlab_details,v->{if(jailed(d))perform("restore",Collections.singletonList(d),0);else details(d);});action.setEnabled(!acting);card.addView(action);list.addView(card);
        }
        if(total==0)list.addView(Ui.text(this,getString(tab==1?R.string.netlab_no_jailed:R.string.netlab_no_devices),14,false));
        if(total>limit){list.addView(Ui.text(this,getString(R.string.netlab_page_count,limit,total),12,false));list.addView(button(R.string.netlab_show_more,v->{limit+=50;lastList="";renderDevices();}));}
    }
    private String label(Bundle device){String fallback=device.getBoolean("managed")?device.getString("mac",getString(R.string.netlab_unknown)):addresses(device);return preferences.label(device.getString("id"),fallback);}
    private String addresses(Bundle device){ArrayList<String> values=device.getStringArrayList("addresses");return values==null?"":android.text.TextUtils.join(" · ",values);}
    private String status(Bundle d){switch(d.getString("state","available")){case "applying":return getString(R.string.netlab_applying);case "enforced":return getString(R.string.netlab_enforced);
        case "restoring":return getString(R.string.netlab_restoring);case "failed":return getString(R.string.netlab_failed);case "history":return getString(R.string.netlab_last_seen);
        case "observed":return getString(R.string.netlab_observed);case "last_scan":return getString(R.string.netlab_last_scan);
        default:return getString(d.getBoolean("online")?R.string.netlab_online:R.string.netlab_offline);}}
    private void details(Bundle d){String body=(d.getBoolean("managed")?getString(R.string.netlab_hotspot_details):getString(R.string.netlab_lan_details))
                +"\n\n"+addresses(d)+(d.getString("mac","").isEmpty()?"":"\n"+d.getString("mac"))+"\n"+status(d);
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(label(d)).setMessage(body).setNegativeButton(android.R.string.cancel,null)
                .setNeutralButton(R.string.netlab_rename_trust,(v,w)->rename(d));
        if(jailed(d))dialog.setPositiveButton(R.string.netlab_restore,(v,w)->perform("restore",Collections.singletonList(d),0));
        else if(d.getBoolean("managed")&&d.getBoolean("online")&&state.getBoolean("can_pause"))dialog.setPositiveButton(R.string.netlab_pause,(v,w)->chooseDuration(Collections.singletonList(d)));
        else dialog.setPositiveButton(R.string.netlab_speed_limiter,(v,w)->limiter());dialog.show();
    }
    private void rename(Bundle d){LinearLayout page=Ui.page(this,R.string.netlab_rename_trust);EditText name=new EditText(this);name.setSingleLine(true);name.setText(label(d));name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});page.addView(name);
        CheckBox trust=new CheckBox(this);trust.setText(R.string.netlab_trusted);trust.setChecked(preferences.trusted(d.getString("id")));page.addView(trust);page.addView(Ui.text(this,getString(R.string.netlab_identity_warning),13,false));
        new AlertDialog.Builder(this).setTitle(R.string.netlab_rename_trust).setView(page).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(v,w)->{
            preferences.save(d.getString("id"),name.getText().toString(),trust.isChecked());lastList="";renderDevices();}).show();}
    private List<Bundle> selectedDevices(boolean jail){List<Bundle> result=new ArrayList<>();for(Bundle d:devices)if(selected.contains(d.getString("id"))&&d.getBoolean("managed")&&(jail?jailed(d):d.getBoolean("online")&&!jailed(d)))result.add(d);return result;}
    private void chooseDuration(List<Bundle> clients){
        if(clients.isEmpty()){toast(R.string.netlab_select_hotspot);return;}if(!state.getBoolean("can_pause")){toast(R.string.netlab_pause_unavailable);return;}
        int[] names=state.getBoolean("can_time")?new int[]{R.string.netlab_duration_session,R.string.netlab_duration_30s,R.string.netlab_duration_5m,R.string.netlab_duration_hour,R.string.netlab_duration_custom}:new int[]{R.string.netlab_duration_session};
        String[] labels=new String[names.length];for(int i=0;i<names.length;i++)labels[i]=getString(names[i]);
        new AlertDialog.Builder(this).setTitle(R.string.netlab_pause).setMessage(R.string.netlab_pause_explanation).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.netlab_continue,(v,w)->new AlertDialog.Builder(this).setTitle(R.string.netlab_duration).setItems(labels,(dialog,which)->{
                    if(which==4){EditText seconds=new EditText(this);seconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);seconds.setHint(R.string.netlab_seconds);
                        AlertDialog custom=new AlertDialog.Builder(this).setTitle(R.string.netlab_duration_custom).setView(seconds).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_pause,null).create();custom.setOnShowListener(x->custom.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(y->{
                            try{long value=Long.parseLong(seconds.getText().toString());if(value<1||value>86400)throw new NumberFormatException();custom.dismiss();perform("pause",clients,value*1000);}catch(NumberFormatException invalid){seconds.setError(getString(R.string.netlab_duration_error));}}));custom.show();
                    }else perform("pause",clients,new long[]{0,30_000,300_000,3_600_000}[which]);}).show()).show();
    }
    private void confirmRestore(){new AlertDialog.Builder(this).setTitle(R.string.netlab_restore_all).setMessage(R.string.netlab_restore_explanation)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_restore_all,(d,w)->perform("restore_all",Collections.emptyList(),0)).show();}
    private void perform(String operation,List<Bundle> targets,long duration){
        if(acting)return;INetLabService service=controller;if(service==null){toast(R.string.netlab_pause_unavailable);return;}
        if(!operation.equals("restore_all")&&targets.isEmpty()){toast(R.string.netlab_select_hotspot);return;}
        acting=true;lastList="";renderDevices();List<Bundle> copy=new ArrayList<>(targets);
        worker.execute(()->{int accepted=0,failed=0;String error="";Bundle result=null;
            try{if(operation.equals("restore_all")){result=service.restoreAll();if("ok".equals(result.getString("result")))accepted++;else{failed++;error=result.getString("result","");}}
                else for(Bundle d:copy){result=operation.equals("pause")?service.pause(d.getString("mac"),duration):service.restore(d.getString("mac"));
                    if("ok".equals(result.getString("result")))accepted++;else{failed++;error=result.getString("result","");}}}
            catch(RemoteException|RuntimeException unavailable){failed++;error="service_unavailable";}
            final int ok=accepted,bad=failed;final String detail=error;final Bundle updated=result;
            main.post(()->{acting=false;if(!started)return;if(updated!=null)state=updated;mergeDevices();updateHeader();lastList="";renderDevices();
                Toast.makeText(this,getString(R.string.netlab_action_result,ok,bad)+(detail.isEmpty()?"":"\n"+explain(detail)),Toast.LENGTH_LONG).show();refresh();});
        });
    }
    private void scan(){
        if(scanning){generation++;if(scanner!=null)scanner.close();scanner=null;scanning=false;scanStatus.setText(R.string.netlab_scan_cancelled);return;}
        if(checkSelfPermission(LOCAL_PERMISSION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{LOCAL_PERMISSION},437);return;}
        NetLabDiscovery.Link current=link;if(current==null){toast(R.string.netlab_no_wifi);return;}
        if(current.targets().isEmpty()){scanStatus.setText(R.string.netlab_scan_scope_unavailable);return;}
        final int token=++generation;scanning=true;scanStatus.setText(getString(R.string.netlab_scanning,current.targets().size()));scanner=new NetLabDiscovery();
        scanner.scan(current,(found,probed,cancelled)->main.post(()->{if(!started||generation!=token)return;scanning=false;scanner=null;
            if(cancelled){scanStatus.setText(R.string.netlab_scan_cancelled);return;}lan.clear();lan.addAll(found);mergeDevices();updateHeader();lastList="";renderDevices();scanStatus.setText(getString(R.string.netlab_scan_finished,found.size(),probed));}));
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);if(request==437){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)scan();else scanStatus.setText(R.string.netlab_permission_needed);}}
    private void updateTraffic(){if(graph==null||rates==null)return;rates.setText(link==null?getString(R.string.netlab_no_wifi):previousRx<0||previousTx<0?getString(R.string.netlab_traffic_unavailable):!rateReady?getString(R.string.netlab_traffic_waiting):getString(R.string.netlab_rates,android.text.format.Formatter.formatFileSize(this,rxRate),android.text.format.Formatter.formatFileSize(this,txRate)));
        if(link!=null&&rateReady)graph.sample(rxRate,txRate);}
    private void limiter(){LinearLayout page=Ui.page(this,R.string.netlab_speed_limiter);page.addView(Ui.text(this,getString(R.string.netlab_limiter_unavailable),14,false));
        EditText down=number(page,R.string.netlab_download,preferences.download()),up=number(page,R.string.netlab_upload,preferences.upload());
        page.addView(Ui.text(this,getString(R.string.netlab_limit_units),12,false));SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.min(100,preferences.download()/1000));page.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user)down.setText(String.valueOf(p*1000));}});
        LinearLayout presets=new LinearLayout(this);int[] titles={R.string.netlab_low,R.string.netlab_balanced,R.string.netlab_unlimited};int[] values={128,2048,0};for(int i=0;i<titles.length;i++){final int rate=values[i];presets.addView(button(titles[i],v->{down.setText(String.valueOf(rate));up.setText(String.valueOf(rate==0?0:Math.max(64,rate/4)));slider.setProgress(rate/1000);}),new LinearLayout.LayoutParams(0,-2,1));}page.addView(presets);
        Button apply=button(R.string.netlab_apply_limit,v->{});apply.setEnabled(false);page.addView(apply);
        ScrollView scroll=new ScrollView(this);scroll.addView(page);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.netlab_speed_limiter).setView(scroll).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_save_preset,null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int a=Integer.parseInt(down.getText().toString()),b=Integer.parseInt(up.getText().toString());if(a<0||b<0||a>1_024_000||b>1_024_000)throw new NumberFormatException();preferences.preset(a,b);dialog.dismiss();toast(R.string.netlab_preset_saved);}catch(NumberFormatException invalid){down.setError(getString(R.string.netlab_limit_error));}}));dialog.show();
    }
    private EditText number(LinearLayout page,int title,int value){page.addView(Ui.text(this,getString(title),14,true));EditText field=new EditText(this);field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.setText(String.valueOf(value));page.addView(field);return field;}
    private String explain(String code){
        if(code==null||code.isEmpty())return getString(R.string.netlab_no_errors);
        switch(code){case "hotspot_off":return getString(R.string.netlab_hotspot_off);case "hal_unsupported":return getString(R.string.netlab_hal_unsupported);
            case "native_unconfirmed":return getString(R.string.netlab_native_unconfirmed);
            case "timer_unavailable":return getString(R.string.netlab_timer_unavailable);case "external_policy":return getString(R.string.netlab_external_policy);
            case "journal_unreadable":case "journal_failed":return getString(R.string.netlab_journal_failed);case "restore_pending":case "recovery_pending":return getString(R.string.netlab_recovery_pending);
            case "not_connected_client":case "not_owned":case "already_owned":return getString(R.string.netlab_device_changed);
            case "pause_requested":return getString(R.string.netlab_applying);case "restored":case "restored_all":case "startup_restored":return getString(R.string.netlab_restored);
            default:return getString(R.string.netlab_operation_failed);}
    }
    private void toast(int text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
