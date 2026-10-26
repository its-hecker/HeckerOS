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
    private final ArrayDeque<String> observations=new ArrayDeque<>();
    private NetLabPreferences preferences;
    private INetLabService controller;
    private Bundle state=new Bundle();
    private LinearLayout content,list;
    private TextView badge,counters,scanStatus,rates;
    private TextView rootStatus;
    private Button rootProbe,rootScan,rootChoose,rootStop;
    private Runnable rootPending;
    private NetLabTrafficView graph;
    private LinearLayout sessionCard;
    private TextView sessionMessage;
    private Button scanButton,stopControlButton;
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
        super.onCreate(saved);preferences=new NetLabPreferences(this);tab=saved==null?0:Math.max(0,Math.min(6,saved.getInt("tab")));
        LinearLayout page=Ui.page(this,R.string.netlab_title);
        if(tab==1)tab=0;
        LinearLayout network=Ui.card(this);
        badge=Ui.text(this,"",18,true);network.addView(badge);
        counters=Ui.text(this,"",13,false);network.addView(counters);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        scanButton=button(R.string.netlab_ui_scan,v->scanWifi());
        LinearLayout.LayoutParams primary=new LinearLayout.LayoutParams(0,-2,1);primary.setMarginEnd(Ui.dp(this,10));
        actions.addView(scanButton,primary);
        Button more=button(R.string.netlab_ui_more,v->showMore(v));actions.addView(more,new LinearLayout.LayoutParams(-2,-2));
        network.addView(actions);
        scanStatus=Ui.text(this,getString(R.string.netlab_ui_scan_hint),12,false);
        scanStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);network.addView(scanStatus);page.addView(network);
        sessionCard=Ui.card(this);sessionCard.setVisibility(View.GONE);
        sessionMessage=Ui.text(this,"",14,true);sessionMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);sessionCard.addView(sessionMessage);
        stopControlButton=button(R.string.netlab_ui_stop_restore,v->confirmRestore());sessionCard.addView(stopControlButton);page.addView(sessionCard);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,Ui.dp(this,12),0,0);page.addView(content);Ui.scroll(this,page);
        updateHeader();renderTab();
    }
    private Button button(int title,View.OnClickListener click){Button button=Ui.button(this);button.setText(title);button.setOnClickListener(click);return button;}
    private void text(LinearLayout parent,int title,int summary){parent.addView(Ui.text(this,getString(title),18,true));parent.addView(Ui.text(this,getString(summary),14,false));}
    @Override protected void onStart(){super.onStart();started=true;
        try{bound=bindService(new Intent().setComponent(new ComponentName("org.hecker.netlab","org.hecker.netlab.NetLabService")),connection,Context.BIND_AUTO_CREATE);}
        catch(SecurityException unavailable){bound=false;}main.post(poll);
    }
    @Override protected void onStop(){started=false;rootPending=null;main.removeCallbacks(poll);generation++;if(scanner!=null)scanner.close();scanner=null;scanning=false;
        if(bound){unbindService(connection);bound=false;}controller=null;super.onStop();}
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
                if(tab==0||tab==1)renderDevices();else if(tab==3)updateTraffic();else if(tab==6)updateRoot();else if(tab==2||tab==5)renderTab();});
        });
    }
    private void updateLink(NetLabDiscovery.Link current,String mac,long rx,long tx){
        String next=current==null?"":current.scope;boolean changed=!scope.equals(next);
        if(changed){scope=next;lan.clear();generation++;if(scanner!=null)scanner.close();scanner=null;scanning=false;
            gateway="";gatewayMac="";observations.clear();previousRx=previousTx=-1;rxRate=txRate=0;
            scanStatus.setTag(null);scanStatus.setText(current==null?R.string.netlab_ui_connect_hint:R.string.netlab_ui_scan_hint);}
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
        Bundle root=rootState();ArrayList<Bundle> rootPeers=root.getParcelableArrayList("devices",Bundle.class);
        if(rootPeers!=null)for(Bundle d:rootPeers)if(root.getString("scope","").equals(scope)||d.getBoolean("lan_managed")){
            Bundle old=all.get(d.getString("id"));
            if(old!=null)for(String key:new String[]{"name","os","model","identity_source"})if(!old.getString(key,"").isEmpty()&&(d.getString(key,"").isEmpty()||d.getString(key,"").equals("unknown")))d.putString(key,old.getString(key));
            all.put(d.getString("id"),d);
        }
        for(Bundle d:all.values())if(d.getBoolean("online"))preferences.seen(d.getString("id"),addresses(d),d.getLong("observed_wall",System.currentTimeMillis()));
        for(Bundle d:preferences.remembered())all.putIfAbsent(d.getString("id"),d);
        devices.clear();devices.addAll(all.values());selected.retainAll(all.keySet());
    }
    private boolean jailed(Bundle d){return Arrays.asList("applying","enforced","restoring","failed","active_unverified","blocking","relaying").contains(d.getString("state"));}
    private void updateHeader(){
        int online=0,controlled=0;for(Bundle d:devices){if(d.getBoolean("online"))online++;if(jailed(d))controlled++;}
        Bundle root=rootState();boolean busy=root.getBoolean("busy");
        badge.setText(link==null?getString(state.getBoolean("hotspot")?R.string.netlab_phone_hotspot:R.string.netlab_ui_not_connected)
                :getString(R.string.netlab_ui_wifi,gateway.isEmpty()?getString(R.string.netlab_ui_connected):gateway));
        counters.setText(getString(R.string.netlab_ui_counts,devices.size(),online,controlled));
        scanButton.setText(scanning?R.string.netlab_ui_stop_scan:busy&&!root.getBoolean("active")?R.string.netlab_ui_scanning:R.string.netlab_ui_scan);
        scanButton.setEnabled(scanning||link!=null&&!acting&&!busy);
        if(!scanning&&!busy&&"finishing".equals(scanStatus.getTag())){scanStatus.setTag(null);scanStatus.setText(R.string.netlab_ui_scan_complete);}
        String error=state.getString("error",""),rootError=root.getString("error","");
        // Hotspot-off is normal while this phone is a Wi-Fi client.
        if(error.equals("hotspot_off"))error="";
        String message=!rootError.isEmpty()?rootError(rootError):!error.isEmpty()?explain(error):busy?rootSummary(root):controlled>0?getString(R.string.netlab_ui_controls_active):"";
        sessionCard.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);sessionMessage.setText(message);
        stopControlButton.setVisibility(busy||controlled>0?View.VISIBLE:View.GONE);stopControlButton.setEnabled(!acting);
    }
    private String rootSummary(Bundle root){
        switch(root.getString("status","idle")){
            case "active":return getString(R.string.netlab_ui_session_active);
            case "active_unverified":return getString(R.string.netlab_lan_unverified);
            case "restoring":return getString(R.string.netlab_lan_restoring);
            case "requesting_root":return getString(R.string.netlab_lan_requesting);
            default:return getString(R.string.netlab_lan_resolving);
        }
    }
    private void showMore(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        int[] names={R.string.netlab_devices,R.string.netlab_ui_multiple,R.string.netlab_ui_restore_all,R.string.netlab_traffic,
                R.string.netlab_ui_network_info,R.string.netlab_ui_options,R.string.netlab_diagnostics,R.string.netlab_ui_advanced};
        for(int i=0;i<names.length;i++)menu.getMenu().add(0,i,i,names[i]);
        menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){
            case 0:tab=0;break;case 1:chooseRootDevices();return true;case 2:confirmRestore();return true;
            case 3:tab=3;break;case 4:tab=2;break;case 5:tab=4;break;case 6:tab=5;break;default:tab=6;
        }renderTab();return true;});menu.show();
    }
    private void renderTab(){
        content.removeAllViews();graph=null;rates=null;list=null;rootStatus=null;lastList="";
        if(tab!=0)content.addView(button(R.string.netlab_ui_back_devices,v->{tab=0;renderTab();}));
        if(tab==6){renderRoot();return;}
        if(tab==0){
            EditText search=new EditText(this);search.setSingleLine(true);search.setHint(R.string.netlab_search);
            search.setTextColor(getColor(R.color.hecker_foreground));search.setHintTextColor(getColor(R.color.hecker_secondary));search.setText(query);
            search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}
                public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();limit=50;lastList="";renderDevices();}});content.addView(search);
            Spinner filters=new Spinner(this);filters.setContentDescription(getString(R.string.netlab_ui_filter));
            filters.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                    new String[]{getString(R.string.netlab_ui_all_devices),getString(R.string.netlab_ui_controlled),getString(R.string.netlab_trusted)}));
            filters.setSelection(filter==3?1:filter==2?2:0);
            filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}
                public void onItemSelected(AdapterView<?> parent,View view,int position,long id){int next=new int[]{0,3,2}[position];if(next!=filter){filter=next;lastList="";renderDevices();}}});
            content.addView(filters);
            list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);renderDevices();return;
        }
        if(tab==2){LinearLayout card=Ui.card(this);text(card,R.string.netlab_defender,R.string.netlab_defender_summary);
            card.addView(Ui.text(this,getString(R.string.netlab_gateway,gateway.isEmpty()?getString(R.string.netlab_unknown):gateway),14,true));
            card.addView(Ui.text(this,link==null?getString(R.string.netlab_no_wifi):arpStatus,13,false));
            if(!gatewayMac.isEmpty())card.addView(Ui.text(this,getString(R.string.netlab_gateway_neighbor,gatewayMac),13,false));
            for(String note:observations)card.addView(Ui.text(this,note,13,false));content.addView(card);return;}
        if(tab==3){LinearLayout card=Ui.card(this);text(card,R.string.netlab_traffic,R.string.netlab_traffic_summary);
            rates=Ui.text(this,"",16,true);card.addView(rates);graph=new NetLabTrafficView(this);card.addView(graph,new LinearLayout.LayoutParams(-1,Ui.dp(this,160)));content.addView(card);updateTraffic();return;}
        if(tab==4){LinearLayout card=Ui.card(this);text(card,R.string.netlab_ui_options,R.string.netlab_ui_options_summary);
            if(state.getBoolean("hotspot"))card.addView(button(R.string.netlab_pause_current,v->{List<Bundle> clients=new ArrayList<>();for(Bundle d:devices)if(d.getBoolean("managed")&&d.getBoolean("online")&&!jailed(d))clients.add(d);chooseDuration(clients);}));
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
        diagnostic(card,R.string.netlab_hotspot_speed,false);diagnostic(card,R.string.netlab_router,false);diagnostic(card,R.string.netlab_lan_root_grant,rootState().getBoolean("root"));
        card.addView(Ui.text(this,getString(R.string.netlab_offload,state.getInt("offload",-1)),13,false));
        card.addView(Ui.text(this,explain(state.getString("error","")),13,false));card.addView(Ui.text(this,getString(R.string.netlab_capability_limits),13,false));
        card.addView(button(R.string.netlab_hotspot_settings,v->{try{startActivity(new Intent("android.settings.WIFI_TETHER_SETTINGS").setPackage("com.android.settings"));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}}));
        ArrayList<Bundle> events=state.getParcelableArrayList("events",Bundle.class);if(events!=null)for(Bundle e:events)
            card.addView(Ui.text(this,android.text.format.DateFormat.getTimeFormat(this).format(new Date(e.getLong("time")))+" · "+explain(e.getString("code","")),12,false));
        content.addView(card);
    }
    private void diagnostic(LinearLayout card,int label,boolean supported){card.addView(Ui.text(this,getString(label)+" · "+getString(supported?R.string.netlab_supported:R.string.netlab_unavailable),14,false));}
    private void renderDevices(){
        if(list==null)return;String fingerprint=devices.toString()+query+filter+acting+rootState().getBoolean("busy")+limit;
        if(fingerprint.equals(lastList))return;lastList=fingerprint;list.removeAllViews();int shown=0,total=0;
        String needle=query.toLowerCase(Locale.ROOT);
        for(Bundle d:devices){String id=d.getString("id"),name=label(d);
            if(filter==3&&!jailed(d)||filter==2&&!preferences.trusted(id))continue;
            if(!needle.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(needle)&&!addresses(d).contains(needle)&&!d.getString("mac","").contains(needle))continue;
            total++;if(shown++>=limit)continue;
            LinearLayout card=Ui.card(this);card.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_netlab_peer);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.hecker_accent)));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(Ui.dp(this,32),Ui.dp(this,40));iconSize.setMarginEnd(Ui.dp(this,12));row.addView(icon,iconSize);
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
            TextView nameView=Ui.text(this,d.getString("name","").isEmpty()&&preferences.label(id,"").isEmpty()?getString(R.string.netlab_ui_unnamed):name,16,true);nameView.setSingleLine(true);nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(nameView);
            TextView address=Ui.text(this,addresses(d),13,false);address.setSingleLine(true);address.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(address);
            TextView status=Ui.text(this,getString(d.getBoolean("managed")?R.string.netlab_phone_hotspot:R.string.netlab_ui_wifi_peer)+" · "+shortStatus(d),12,false);
            status.setTextColor(jailed(d)?getColor(R.color.hecker_accent):getColor(R.color.hecker_secondary));info.addView(status);
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            Button manage=button(R.string.netlab_ui_manage,v->deviceActions(d));manage.setContentDescription(getString(R.string.netlab_ui_manage_device,name));
            manage.setEnabled(!acting);LinearLayout.LayoutParams actionSize=new LinearLayout.LayoutParams(-2,-2);actionSize.setMarginStart(Ui.dp(this,8));row.addView(manage,actionSize);
            card.addView(row);card.setOnClickListener(v->{if(!acting)deviceActions(d);});list.addView(card);
        }
        if(total==0){LinearLayout empty=Ui.card(this);empty.addView(Ui.text(this,getString(devices.isEmpty()?R.string.netlab_ui_find_devices:R.string.netlab_no_devices),17,true));
            if(devices.isEmpty())empty.addView(Ui.text(this,getString(R.string.netlab_ui_scan_hint),14,false));list.addView(empty);}
        if(total>limit){list.addView(Ui.text(this,getString(R.string.netlab_page_count,limit,total),12,false));list.addView(button(R.string.netlab_show_more,v->{limit+=50;lastList="";renderDevices();}));}
    }
    private boolean router(Bundle d){ArrayList<String> values=d.getStringArrayList("addresses");return !d.getBoolean("managed")&&!gateway.isEmpty()&&values!=null&&values.contains(gateway);}
    private String shortStatus(Bundle d){
        if(router(d))return getString(R.string.netlab_ui_router);
        switch(d.getString("state","")){
            case "blocking":case "enforced":return getString(R.string.netlab_ui_paused);
            case "relaying":return getString(R.string.netlab_ui_limited)+" · "+getString(R.string.netlab_ui_rates,rate(d.getInt("down_kbps")),rate(d.getInt("up_kbps")));
            case "active_unverified":return getString(R.string.netlab_ui_waiting);
            case "applying":return getString(R.string.netlab_lan_applying);
            case "restoring":return getString(R.string.netlab_ui_restoring);
            case "failed":return getString(R.string.netlab_failed);
            default:return getString(d.getBoolean("online")?R.string.netlab_ui_available:R.string.netlab_last_scan);
        }
    }
    private String rate(int kbps){return kbps==0?getString(R.string.netlab_ui_unlimited):getString(R.string.netlab_ui_rate_value,kbps);}
    private void deviceActions(Bundle device){
        List<Integer> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        if(jailed(device)){labels.add(R.string.netlab_restore);actions.add(()->perform("restore",Collections.singletonList(device),0));}
        else if(device.getBoolean("online")&&!router(device)){
            labels.add(device.getBoolean("managed")?R.string.netlab_pause:R.string.netlab_ui_pause_internet);
            actions.add(()->{if(device.getBoolean("managed"))chooseDuration(Collections.singletonList(device));else controlWifi(Collections.singletonList(device),false);});
        }
        if(!device.getBoolean("managed")&&!jailed(device)&&device.getBoolean("online")&&!router(device)){
            labels.add(R.string.netlab_ui_speed_limit);actions.add(()->controlWifi(Collections.singletonList(device),true));
        }
        labels.add(R.string.netlab_details);actions.add(()->details(device));labels.add(R.string.netlab_rename_trust);actions.add(()->rename(device));
        String[] titles=new String[labels.size()];for(int i=0;i<titles.length;i++)titles[i]=getString(labels.get(i));
        new AlertDialog.Builder(this).setTitle(label(device)).setItems(titles,(dialog,which)->actions.get(which).run()).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void controlWifi(List<Bundle> targets,boolean speedLimit){
        Bundle root=rootState();
        if(root.getBoolean("busy")){new AlertDialog.Builder(this).setTitle(R.string.netlab_ui_controls_busy).setMessage(R.string.netlab_ui_wait_busy)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_ui_stop_restore,(dialog,which)->confirmRestore()).show();return;}
        ArrayList<Bundle> peers=root.getParcelableArrayList("devices",Bundle.class);List<Bundle> chosen=new ArrayList<>();
        if(root.getString("scope","").equals(scope)&&peers!=null)for(Bundle target:targets)for(Bundle peer:peers)
            if(peer.getString("id","").equals(target.getString("id"))){chosen.add(peer);break;}
        if(link==null||chosen.size()!=targets.size()||chosen.isEmpty()){
            new AlertDialog.Builder(this).setTitle(R.string.netlab_ui_scan_needed).setMessage(R.string.netlab_ui_scan_for_controls)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_ui_scan,(dialog,which)->scanWifi()).show();return;
        }
        configureRoot(chosen,speedLimit);
    }
    private String label(Bundle device){String fallback=device.getString("name", "");if(fallback.isEmpty())fallback=device.getBoolean("managed")?device.getString("mac",getString(R.string.netlab_unknown)):addresses(device);return preferences.label(device.getString("id"),fallback);}
    private String addresses(Bundle device){ArrayList<String> values=device.getStringArrayList("addresses");return values==null?"":android.text.TextUtils.join(" · ",values);}
    private String status(Bundle d){switch(d.getString("state","available")){case "applying":return getString(d.getBoolean("root_peer")?R.string.netlab_lan_applying:R.string.netlab_applying);case "enforced":return getString(R.string.netlab_enforced);
        case "restoring":return getString(d.getBoolean("root_peer")?R.string.netlab_lan_restoring:R.string.netlab_restoring);case "failed":return getString(R.string.netlab_failed);case "history":return getString(R.string.netlab_last_seen);
        case "observed":return getString(R.string.netlab_observed);case "last_scan":return getString(R.string.netlab_last_scan);
        case "active_unverified":return getString(R.string.netlab_lan_unverified);case "blocking":return getString(R.string.netlab_lan_blocking);case "relaying":return getString(R.string.netlab_lan_relaying);
        default:return getString(d.getBoolean("online")?R.string.netlab_online:R.string.netlab_offline);}}
    private String identity(Bundle device){
        int label;
        switch(device.getString("os","unknown")){
            case "android":label=R.string.netlab_type_android;break;
            case "ios":label=R.string.netlab_type_ios;break;
            case "macos":label=R.string.netlab_type_mac;break;
            case "windows":label=R.string.netlab_type_windows;break;
            case "linux":label=R.string.netlab_type_linux;break;
            case "apple":label=R.string.netlab_type_apple;break;
            case "media":label=R.string.netlab_type_media;break;
            default:label=R.string.netlab_type_unknown;
        }
        String model=device.getString("model","");
        return getString(label)+(model.isEmpty()?"":" · "+model);
    }
    private void details(Bundle d){String body=(d.getBoolean("managed")?getString(R.string.netlab_hotspot_details):getString(R.string.netlab_lan_details))
                +"\n\n"+addresses(d)+"\n"+identity(d)+"\n"+getString(R.string.netlab_mac,d.getString("mac","").isEmpty()?getString(R.string.netlab_not_shared):d.getString("mac"))+"\n"+getString(R.string.netlab_identity_explanation)+"\n\n"+status(d);
        if(d.getBoolean("root_peer"))body+="\n\n"+getString(R.string.netlab_lan_counts,d.getLong("forwarded_up"),d.getLong("forwarded_down"),d.getLong("dropped_up"),d.getLong("dropped_down"));
        if(d.getLong("until")>0)body+="\n"+getString(d.getBoolean("root_peer")?R.string.netlab_lan_until:R.string.netlab_until,android.text.format.DateFormat.getTimeFormat(this).format(new Date(d.getLong("until"))));
        if(d.getBoolean("root_peer")&&d.getBoolean("lan_managed"))body+="\n"+getString(R.string.netlab_ui_rates,rate(d.getInt("down_kbps")),rate(d.getInt("up_kbps")));
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(label(d)).setMessage(body).setNegativeButton(android.R.string.cancel,null)
                .setNeutralButton(R.string.netlab_rename_trust,(v,w)->rename(d));
        dialog.setPositiveButton(R.string.netlab_ui_manage,(v,w)->deviceActions(d));dialog.show();
    }
    private void rename(Bundle d){LinearLayout page=Ui.page(this,R.string.netlab_rename_trust);EditText name=new EditText(this);name.setSingleLine(true);name.setText(label(d));name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});page.addView(name);
        CheckBox trust=new CheckBox(this);trust.setText(R.string.netlab_trusted);trust.setChecked(preferences.trusted(d.getString("id")));page.addView(trust);page.addView(Ui.text(this,getString(R.string.netlab_identity_warning),13,false));
        new AlertDialog.Builder(this).setTitle(R.string.netlab_rename_trust).setView(page).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(v,w)->{
            preferences.save(d.getString("id"),name.getText().toString(),trust.isChecked());lastList="";renderDevices();}).show();}
    private List<Bundle> selectedDevices(boolean jail){List<Bundle> result=new ArrayList<>();for(Bundle d:devices)if(selected.contains(d.getString("id"))&&(d.getBoolean("managed")||(jail&&d.getBoolean("lan_managed")))&&(jail?jailed(d):d.getBoolean("online")&&!jailed(d)))result.add(d);return result;}
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
        if(acting)return;INetLabService service=controller;if(service==null){toast(R.string.netlab_ui_controller_unavailable);return;}
        if(!operation.equals("restore_all")&&targets.isEmpty()){toast(R.string.netlab_select_hotspot);return;}
        acting=true;lastList="";renderDevices();List<Bundle> copy=new ArrayList<>(targets);
        worker.execute(()->{int accepted=0,failed=0;String error="";Bundle result=null;
            try{if(operation.equals("restore_all")){result=service.restoreAll();if("ok".equals(result.getString("result")))accepted++;else{failed++;error=result.getString("result","");}}
                else for(Bundle d:copy){result=operation.equals("pause")?service.pause(d.getString("mac"),duration):d.getBoolean("lan_managed")?service.lanRestore(d.getStringArrayList("addresses").get(0)):service.restore(d.getString("mac"));
                    if("ok".equals(result.getString("result")))accepted++;else{failed++;error=result.getString("result","");}}}
            catch(RemoteException|RuntimeException unavailable){failed++;error="service_unavailable";}
            final int ok=accepted,bad=failed;final String detail=error;final Bundle updated=result;
            main.post(()->{acting=false;if(!started)return;if(updated!=null)state=updated;mergeDevices();updateHeader();lastList="";renderDevices();
                Toast.makeText(this,getString(R.string.netlab_action_result,ok,bad)+(detail.isEmpty()?"":"\n"+explain(detail)),Toast.LENGTH_LONG).show();refresh();});
        });
    }
    private void scanWifi(){
        if(scanning){scan();if(rootState().getBoolean("busy")&&!rootState().getBoolean("active"))rootAction("stop",Collections.emptyList(),true,0,0,0);updateHeader();return;}
        if(checkSelfPermission(LOCAL_PERMISSION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{LOCAL_PERMISSION},437);return;}
        if(rootState().getBoolean("busy")){toast(R.string.netlab_ui_wait_busy);return;}
        if(link==null){toast(R.string.netlab_no_wifi);return;}
        scan();if(scanning&&controller!=null)rootAction("scan",Collections.emptyList(),true,0,0,0);updateHeader();
    }
    private void scan(){
        if(scanning){generation++;if(scanner!=null)scanner.close();scanner=null;scanning=false;scanStatus.setText(R.string.netlab_scan_cancelled);updateHeader();return;}
        if(checkSelfPermission(LOCAL_PERMISSION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{LOCAL_PERMISSION},437);return;}
        NetLabDiscovery.Link current=link;if(current==null){toast(R.string.netlab_no_wifi);return;}
        if(current.targets().isEmpty()){scanStatus.setText(R.string.netlab_scan_scope_unavailable);return;}
        final int token=++generation;scanning=true;scanStatus.setTag(null);scanStatus.setText(getString(R.string.netlab_scanning,current.targets().size()));scanner=new NetLabDiscovery(this,current);
        scanner.scan(current,(found,probed,cancelled)->main.post(()->{if(!started||generation!=token)return;scanning=false;scanner=null;
            if(cancelled){scanStatus.setText(R.string.netlab_scan_cancelled);updateHeader();return;}lan.clear();lan.addAll(found);mergeDevices();updateHeader();lastList="";renderDevices();if(rootState().getBoolean("busy")&&!rootState().getBoolean("active")){scanStatus.setTag("finishing");scanStatus.setText(R.string.netlab_ui_finishing_scan);}
            else scanStatus.setText(R.string.netlab_ui_scan_complete);}));
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);if(request==437){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)scanWifi();else scanStatus.setText(R.string.netlab_permission_needed);}
        if(request==438){Runnable pending=rootPending;rootPending=null;if(started&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED&&pending!=null)pending.run();else toast(R.string.netlab_permission_needed);}}
    private void updateTraffic(){if(graph==null||rates==null)return;rates.setText(link==null?getString(R.string.netlab_no_wifi):previousRx<0||previousTx<0?getString(R.string.netlab_traffic_unavailable):!rateReady?getString(R.string.netlab_traffic_waiting):getString(R.string.netlab_rates,android.text.format.Formatter.formatFileSize(this,rxRate),android.text.format.Formatter.formatFileSize(this,txRate)));
        if(link!=null&&rateReady)graph.sample(rxRate,txRate);}
    private Bundle rootState(){Bundle b=state.getBundle("lan");return b==null?new Bundle():b;}
    private void renderRoot(){
        LinearLayout card=Ui.card(this);text(card,R.string.netlab_lan_title,R.string.netlab_lan_summary);
        rootStatus=Ui.text(this,"",14,true);rootStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(rootStatus);
        rootProbe=button(R.string.netlab_lan_probe,v->rootAction("probe",Collections.emptyList(),true,0,0,0));card.addView(rootProbe);
        rootScan=button(R.string.netlab_lan_scan,v->rootAction("scan",Collections.emptyList(),true,0,0,0));card.addView(rootScan);
        rootChoose=button(R.string.netlab_lan_choose,v->chooseRootDevices());card.addView(rootChoose);
        rootStop=button(R.string.netlab_lan_stop,v->rootAction("stop",Collections.emptyList(),true,0,0,0));card.addView(rootStop);
        card.addView(Ui.text(this,getString(R.string.netlab_lan_limits),13,false));content.addView(card);updateRoot();
    }
    private void updateRoot(){
        if(rootStatus==null)return;Bundle b=rootState();String value=b.getString("status","idle");int title;
        switch(value){case "requesting_root":title=R.string.netlab_lan_requesting;break;case "resolving":title=R.string.netlab_lan_resolving;break;
            case "active":title=R.string.netlab_lan_active_badge;break;case "active_unverified":title=R.string.netlab_lan_unverified;break;
            case "restoring":title=R.string.netlab_lan_restoring;break;case "ready":title=R.string.netlab_lan_ready;break;case "failed":title=R.string.netlab_lan_failed;break;default:title=R.string.netlab_lan_idle;}
        String detail=getString(title);String error=b.getString("error","");if(!error.isEmpty())detail+="\n"+rootError(error);
        ArrayList<Bundle> found=b.getParcelableArrayList("devices",Bundle.class);int count=b.getString("scope","").equals(scope)&&found!=null?found.size():0;
        detail+="\n"+getString(R.string.netlab_lan_found,count);rootStatus.setText(detail);
        boolean busy=b.getBoolean("busy");rootProbe.setEnabled(!acting&&!busy);rootScan.setEnabled(!acting&&!busy&&link!=null);
        rootChoose.setEnabled(!acting&&!busy&&count>0&&link!=null);rootStop.setEnabled(!acting&&busy);
    }
    private void chooseRootDevices(){
        Bundle b=rootState();ArrayList<Bundle> found=b.getParcelableArrayList("devices",Bundle.class);
        if(found==null||found.isEmpty()||!b.getString("scope","").equals(scope)){toast(R.string.netlab_lan_scan_first);return;}
        String[] labels=new String[found.size()];boolean[] checked=new boolean[found.size()];
        for(int i=0;i<found.size();i++){Bundle d=found.get(i);labels[i]=label(d)+" · "+d.getString("mac","");checked[i]=selected.contains(d.getString("id"));}
        AlertDialog picker=new AlertDialog.Builder(this).setTitle(R.string.netlab_lan_choose).setMultiChoiceItems(labels,checked,(d,i,on)->checked[i]=on)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_continue,null).create();
        picker.setOnShowListener(v->picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{List<Bundle> chosen=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])chosen.add(found.get(i));
            if(chosen.isEmpty()||chosen.size()>32){toast(R.string.netlab_lan_selection_error);return;}picker.dismiss();configureRoot(chosen);}));picker.show();
    }
    private void configureRoot(List<Bundle> peers){
        new AlertDialog.Builder(this).setTitle(R.string.netlab_ui_manage).setItems(new String[]{getString(R.string.netlab_ui_pause_internet),getString(R.string.netlab_ui_speed_limit)},
                (dialog,which)->controlWifi(peers,which==1)).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void configureRoot(List<Bundle> peers,boolean speedLimit){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,8));
        page.addView(Ui.text(this,peers.size()==1?label(peers.get(0)):getString(R.string.netlab_ui_selected_count,peers.size()),16,true));
        EditText down=null,up=null;
        if(speedLimit){
            down=number(page,R.string.netlab_download,preferences.download());up=number(page,R.string.netlab_upload,preferences.upload());
            page.addView(Ui.text(this,getString(R.string.netlab_ui_rate_hint),12,false));
        }else page.addView(Ui.text(this,getString(R.string.netlab_ui_pause_hint),13,false));
        page.addView(Ui.text(this,getString(R.string.netlab_ui_duration),14,true));
        Spinner duration=new Spinner(this);duration.setContentDescription(getString(R.string.netlab_ui_duration));
        duration.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{getString(R.string.netlab_duration_5m),getString(R.string.netlab_ui_15_minutes),getString(R.string.netlab_duration_hour),getString(R.string.netlab_duration_custom)}));page.addView(duration);
        LinearLayout custom=new LinearLayout(this);custom.setOrientation(LinearLayout.VERTICAL);
        EditText seconds=number(custom,R.string.netlab_ui_duration_seconds,300);custom.setVisibility(View.GONE);page.addView(custom);
        duration.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){custom.setVisibility(position==3?View.VISIBLE:View.GONE);}});
        ScrollView scroll=new ScrollView(this);scroll.addView(page);final EditText upload=up,download=down;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(speedLimit?R.string.netlab_ui_speed_limit:R.string.netlab_ui_pause_internet).setView(scroll)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(speedLimit?R.string.netlab_ui_apply_limit:R.string.netlab_ui_pause_internet,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            int value;try{value=duration.getSelectedItemPosition()==3?Integer.parseInt(seconds.getText().toString()):new int[]{300,900,3600}[duration.getSelectedItemPosition()];
                if(value<1||value>3600)throw new NumberFormatException();}catch(NumberFormatException invalid){seconds.setError(getString(R.string.netlab_lan_duration_error));return;}
            int a=0,c=0;if(speedLimit)try{a=Integer.parseInt(upload.getText().toString());c=Integer.parseInt(download.getText().toString());
                if(a<0||c<0||a>1_024_000||c>1_024_000||(a==0&&c==0))throw new NumberFormatException();}
                catch(NumberFormatException invalid){download.setError(getString(R.string.netlab_lan_rate_error));return;}
            dialog.dismiss();if(speedLimit)preferences.preset(c,a);rootAction("start",peers,!speedLimit,a,c,value);
        }));dialog.show();
    }
    private void rootAction(String operation,List<Bundle> peers,boolean block,int up,int down,int seconds){
        if(acting)return;INetLabService service=controller;if(service==null){toast(R.string.netlab_ui_controller_unavailable);return;}
        if(!operation.equals("stop")&&checkSelfPermission(LOCAL_PERMISSION)!=PackageManager.PERMISSION_GRANTED){rootPending=()->rootAction(operation,peers,block,up,down,seconds);requestPermissions(new String[]{LOCAL_PERMISSION},438);return;}
        List<Bundle> copy=new ArrayList<>(peers);acting=true;updateHeader();updateRoot();lastList="";renderDevices();
        worker.execute(()->{Bundle updated=null;String result;
            try{
                if(operation.equals("probe"))updated=service.lanProbe();else if(operation.equals("scan"))updated=service.lanScan();else if(operation.equals("stop"))updated=service.lanStop();
                else{String[] ips=new String[copy.size()],macs=new String[copy.size()];for(int i=0;i<copy.size();i++){ips[i]=copy.get(i).getStringArrayList("addresses").get(0);macs[i]=copy.get(i).getString("mac");}updated=service.lanStart(ips,macs,block,up,down,seconds);}
                result=updated.getString("result","service_unavailable");
            }catch(RemoteException|RuntimeException unavailable){result="service_unavailable";}
            final Bundle next=updated;final String outcome=result;
            main.post(()->{acting=false;if(!started)return;if(next!=null)state=next;mergeDevices();updateHeader();lastList="";renderDevices();updateRoot();
                Toast.makeText(this,"ok".equals(outcome)?getString(R.string.netlab_lan_requested):rootError(outcome),Toast.LENGTH_LONG).show();refresh();});
        });
    }
    private String rootError(String code){
        switch(code){case "service_unavailable":return getString(R.string.netlab_ui_controller_unavailable);
            case "root_required":case "root_or_helper_unavailable":case "raw_socket_denied":case "packet_filter_denied":return getString(R.string.netlab_lan_root_error);
            case "scan_required":case "peer_unreachable_or_changed":case "neighbor_changed":return getString(R.string.netlab_lan_peer_changed);
            case "wifi_unavailable":case "ipv4_unavailable":case "invalid_link":case "invalid_gateway":case "link_changed":case "route_changed":case "vpn_active":return getString(R.string.netlab_lan_network_error);
            case "kernel_forwarding_enabled":case "forwarding_guard_unavailable":case "forwarding_guard_lost":return getString(R.string.netlab_lan_forwarding_error);
            case "forwarding_guard_cleanup_failed":return getString(R.string.netlab_lan_cleanup_error);
            case "notification_unavailable":return getString(R.string.netlab_lan_notification_error);
            case "lan_busy":return getString(R.string.netlab_lan_busy);
            case "local_access_revoked":return getString(R.string.netlab_permission_needed);
            default:if(code.endsWith("repair_unavailable"))return getString(R.string.netlab_lan_repair_error);return getString(R.string.netlab_lan_action_error);}
    }
    private EditText number(LinearLayout page,int title,int value){page.addView(Ui.text(this,getString(title),14,true));EditText field=new EditText(this);field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.setSingleLine(true);field.setSelectAllOnFocus(true);field.setTextColor(getColor(R.color.hecker_foreground));field.setMinHeight(Ui.dp(this,48));field.setText(String.valueOf(value));page.addView(field);return field;}
    private String explain(String code){
        if(code==null||code.isEmpty())return getString(R.string.netlab_no_errors);
        if(code.equals("service_unavailable"))return getString(R.string.netlab_ui_controller_unavailable);
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
