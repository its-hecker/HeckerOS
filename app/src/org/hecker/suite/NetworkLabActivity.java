// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.os.*;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.hecker.netlab.INetLabService;
import org.hecker.netlab.NetLabGroupPolicy;
import org.hecker.netlab.NetLabIdentity;
import org.hecker.netlab.NetLabVendors;
import org.hecker.netlab.NetLabSliderPolicy;

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
    private TextView counters,scanStatus,rates,brandPopup;
    private final List<TextView> tabLabels=new ArrayList<>();
    private final List<View> tabLines=new ArrayList<>();
    private TextView rootStatus;
    private Button rootProbe,rootScan,rootChoose,rootStop;
    private Runnable rootPending;
    private Runnable pendingRateChoice;
    private NetLabTrafficView graph;
    private LinearLayout sessionCard;
    private TextView sessionMessage;
    private Button scanButton;
    private ImageButton masterButton,masterPause,masterRestore;
    private LinearLayout masterControls;
    private SeekBar masterSpeed;
    private TextView masterValue;
    private NetLabVendors vendors;
    private Button stopControlButton;
    private NetLabDiscovery scanner;
    private NetLabDiscovery.Link link;
    private boolean started,bound,polling,acting,scanning,rootScanning,rateReady,adjustingRate;
    private int tab,filter,limit=50,generation;
    private String query="",scope="",gateway="",gatewayMac="",arpStatus="",lastList="";
    private String expandedDevice;
    private final Runnable hideBrand=()->{if(brandPopup!=null)brandPopup.setVisibility(View.GONE);};
    private long previousRx=-1,previousTx=-1,previousTime,rxRate,txRate;
    private final Runnable poll=new Runnable(){public void run(){refresh();if(started)main.postDelayed(this,2000);}};
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){controller=INetLabService.Stub.asInterface(binder);refresh();}
        public void onServiceDisconnected(ComponentName name){controller=null;state=new Bundle();updateHeader();renderTab();}
        public void onBindingDied(ComponentName name){controller=null;state=new Bundle();updateHeader();}
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);preferences=new NetLabPreferences(this);tab=saved==null?0:Math.max(0,Math.min(6,saved.getInt("tab")));
        if(saved!=null){query=saved.getString("query","");filter=saved.getInt("filter");expandedDevice=saved.getString("expanded");}
        setTitle(R.string.netlab_ref_brand);if(getActionBar()!=null)getActionBar().hide();
        getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(NetLabStyle.PANEL);
        LinearLayout page=NetLabStyle.column(this);page.setBackgroundColor(NetLabStyle.BLACK);root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout toolbar=NetLabStyle.row(this);toolbar.setMinimumHeight(Ui.dp(this,52));toolbar.setBackgroundColor(NetLabStyle.PANEL);
        toolbar.setPadding(Ui.dp(this,16),0,Ui.dp(this,4),0);
        TextView title=NetLabStyle.text(this,getString(R.string.netlab_ref_brand),22,true);title.setAccessibilityHeading(true);
        title.setOnClickListener(v->showBrand());toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button exit=button(R.string.netlab_ref_exit,v->finish());exit.setTextSize(14);exit.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        toolbar.addView(exit,new LinearLayout.LayoutParams(-2,Ui.dp(this,48)));page.addView(toolbar);
        LinearLayout tabs=NetLabStyle.row(this);int[] labels={R.string.netlab_ref_cut,R.string.netlab_jail,R.string.netlab_ref_option};
        int[] targets={0,1,4};
        for(int i=0;i<labels.length;i++){
            final int target=targets[i];LinearLayout item=NetLabStyle.column(this);
            TextView label=NetLabStyle.text(this,getString(labels[i]).toUpperCase(Locale.getDefault()),13,true);
            label.setGravity(Gravity.CENTER);label.setMinHeight(Ui.dp(this,48));item.addView(label,new LinearLayout.LayoutParams(-1,-2));
            View underline=new View(this);item.addView(underline,new LinearLayout.LayoutParams(-1,Ui.dp(this,2)));
            item.setContentDescription(label.getText());item.setFocusable(true);item.setOnClickListener(v->{tab=target;renderTab();});
            tabLabels.add(label);tabLines.add(underline);tabs.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        page.addView(tabs);
        LinearLayout scanActions=NetLabStyle.row(this);scanActions.setGravity(Gravity.CENTER);
        scanActions.setPadding(Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10),0);
        scanButton=button(R.string.netlab_ui_scan,v->scanWifi());scanButton.setMinimumWidth(Ui.dp(this,170));
        scanActions.addView(scanButton,new LinearLayout.LayoutParams(-2,Ui.dp(this,48)));page.addView(scanActions);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=NetLabStyle.column(this);body.setPadding(Ui.dp(this,5),Ui.dp(this,6),Ui.dp(this,5),0);
        counters=NetLabStyle.text(this,"",23,true);counters.setGravity(Gravity.CENTER);counters.setAccessibilityHeading(true);
        counters.setPadding(0,Ui.dp(this,9),0,Ui.dp(this,12));body.addView(counters);
        content=NetLabStyle.column(this);body.addView(content);scroll.addView(body);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=NetLabStyle.column(this);footer.setBackgroundColor(NetLabStyle.PANEL);
        masterValue=NetLabStyle.text(this,"",11,false);masterValue.setGravity(Gravity.CENTER);footer.addView(masterValue);
        masterControls=NetLabStyle.row(this);masterControls.setBackgroundColor(NetLabStyle.BLACK);
        masterPause=NetLabStyle.icon(this,NetLabStyle.CUT,getString(R.string.netlab_feedback_pause_all),v->controlAll(false,0));masterControls.addView(masterPause);
        masterSpeed=NetLabStyle.speed(this);masterSpeed.setContentDescription(getString(R.string.netlab_feedback_limit_all));masterControls.addView(masterSpeed);
        masterRestore=NetLabStyle.icon(this,NetLabStyle.WIFI,getString(R.string.netlab_ui_restore_all),v->confirmRestore());masterControls.addView(masterRestore);
        footer.addView(masterControls);
        LinearLayout actions=NetLabStyle.row(this);actions.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);
        Button monitor=button(R.string.netlab_ref_monitor,v->{tab=2;renderTab();});monitor.setTextSize(10);monitor.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);
        android.graphics.drawable.GradientDrawable outline=NetLabStyle.shape(this,NetLabStyle.PANEL,10);
        outline.setStroke(Ui.dp(this,2),NetLabStyle.ORANGE);monitor.setBackground(outline);
        LinearLayout.LayoutParams monitorSize=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1.35f);monitorSize.setMarginEnd(Ui.dp(this,8));actions.addView(monitor,monitorSize);
        addFooterIcon(actions,NetLabStyle.LOCK,R.string.netlab_ui_restore_all,v->confirmRestore());
        masterButton=addFooterIcon(actions,NetLabStyle.WIFI,R.string.netlab_feedback_master,v->toggleAll());
        addFooterIcon(actions,NetLabStyle.REFRESH,R.string.netlab_ref_refresh,v->refresh());
        addFooterIcon(actions,NetLabStyle.SEARCH,R.string.netlab_search,v->searchDevices());footer.addView(actions);
        sessionCard=NetLabStyle.column(this);sessionCard.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);sessionCard.setVisibility(View.GONE);
        sessionMessage=NetLabStyle.text(this,"",12,false);sessionMessage.setMaxLines(3);
        sessionMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);sessionCard.addView(sessionMessage);
        stopControlButton=button(R.string.netlab_ui_stop_restore,v->confirmRestore());sessionCard.addView(stopControlButton);footer.addView(sessionCard);
        scanStatus=NetLabStyle.text(this,getString(R.string.netlab_ui_scan_hint),10,true);scanStatus.setGravity(Gravity.CENTER);
        scanStatus.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,10));scanStatus.setMinHeight(Ui.dp(this,38));
        scanStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);footer.addView(scanStatus);page.addView(footer);
        brandPopup=NetLabStyle.text(this,getString(R.string.netlab_ref_popup),16,true);brandPopup.setGravity(Gravity.CENTER);
        brandPopup.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));brandPopup.setMinHeight(Ui.dp(this,58));
        brandPopup.setBackgroundColor(0xff303030);brandPopup.setVisibility(View.GONE);brandPopup.setOnClickListener(v->hideBrand.run());
        root.addView(brandPopup,new FrameLayout.LayoutParams(Ui.dp(this,132),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return WindowInsets.CONSUMED;
        });
        setContentView(root);root.post(root::requestApplyInsets);
        if(getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        if(saved==null)root.post(this::showBrand);
        updateHeader();renderTab();
    }
    private ImageButton addFooterIcon(LinearLayout row,int icon,int title,View.OnClickListener action){
        ImageButton button=NetLabStyle.icon(this,icon,getString(title),action);button.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));
        row.addView(button,new LinearLayout.LayoutParams(0,Ui.dp(this,56),1));return button;
    }
    private void showBrand(){main.removeCallbacks(hideBrand);brandPopup.setVisibility(View.VISIBLE);main.postDelayed(hideBrand,3500);}
    private Button button(int title,View.OnClickListener click){Button button=NetLabStyle.button(this);button.setText(title);button.setOnClickListener(click);return button;}
    private void text(LinearLayout parent,int title,int summary){parent.addView(NetLabStyle.text(this,getString(title),18,true));parent.addView(NetLabStyle.text(this,getString(summary),14,false));}
    @Override protected void onStart(){super.onStart();started=true;
        try{bound=bindService(new Intent().setComponent(new ComponentName("org.hecker.netlab","org.hecker.netlab.NetLabService")),connection,Context.BIND_AUTO_CREATE);}
        catch(SecurityException unavailable){bound=false;}main.post(poll);
    }
    @Override protected void onStop(){started=false;rootPending=null;main.removeCallbacks(poll);main.removeCallbacks(hideBrand);hideBrand.run();cancelRateChoice();generation++;if(scanner!=null)scanner.close();scanner=null;scanning=false;
        if(bound){unbindService(connection);bound=false;}controller=null;super.onStop();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("tab",tab);out.putInt("filter",filter);out.putString("query",query);out.putString("expanded",expandedDevice);super.onSaveInstanceState(out);}
    private void refresh(){
        if(polling||acting||!started)return;polling=true;INetLabService service=controller;
        worker.execute(()->{Bundle updated=new Bundle();NetLabDiscovery.Link current=null;String mac="";long rx=-1,tx=-1;
            NetLabVendors database=vendors;
            if(database==null)try(java.io.InputStream input=getAssets().open("netlab/ieee-vendors.tsv.gz");
                    java.io.Reader reader=new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(input),java.nio.charset.StandardCharsets.UTF_8)){
                database=NetLabVendors.read(reader);
            }catch(java.io.IOException|RuntimeException unavailable){database=NetLabVendors.empty();}
            try{if(service!=null)updated=service.state();else updated.putString("error","service_unavailable");}catch(RemoteException|RuntimeException unavailable){updated.putString("error","service_unavailable");}
            try{current=NetLabDiscovery.wifi(this);if(current!=null){mac=NetLabDiscovery.gatewayMac(current);String iface=current.properties.getInterfaceName();
                if(iface!=null){rx=TrafficStats.getRxBytes(iface);tx=TrafficStats.getTxBytes(iface);}}}catch(RuntimeException unavailable){}
            Bundle result=updated;NetLabDiscovery.Link network=current;String neighbor=mac;long received=rx,sent=tx;NetLabVendors registry=database;
            main.post(()->{polling=false;if(!started)return;vendors=registry;state=result;updateLink(network,neighbor,received,sent);mergeDevices();updateHeader();
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
            if(old!=null)for(String key:new String[]{"name","os","model","identity_source","brand","brand_source"})if(!old.getString(key,"").isEmpty()&&(d.getString(key,"").isEmpty()||d.getString(key,"").equals("unknown")))d.putString(key,old.getString(key));
            all.put(d.getString("id"),d);
        }
        for(Bundle d:all.values())if(d.getBoolean("online"))preferences.seen(d.getString("id"),addresses(d),d.getLong("observed_wall",System.currentTimeMillis()));
        for(Bundle d:preferences.remembered())all.putIfAbsent(d.getString("id"),d);
        for(Bundle d:all.values())enrichIdentity(d);
        devices.clear();devices.addAll(all.values());selected.retainAll(all.keySet());
    }
    private void enrichIdentity(Bundle d){
        String manufacturer=vendors==null?"":vendors.lookup(d.getString("mac",""));
        String advertised=NetLabIdentity.brand(d.getString("name",""),d.getString("model",""),
                "advertised".equals(d.getString("brand_source"))?d.getString("brand",""):"");
        d.putString("vendor",manufacturer);
        d.putString("brand",advertised.isEmpty()?manufacturer:advertised);
        d.putString("brand_source",advertised.isEmpty()?manufacturer.isEmpty()?"":"ieee":"advertised");
        if(d.getString("os","unknown").equals("unknown"))d.putString("os",NetLabIdentity.kind(d.getString("name",""),d.getString("model",""),""));
    }
    private boolean jailed(Bundle d){return Arrays.asList("applying","enforced","restoring","failed","active_unverified","blocking","relaying").contains(d.getString("state"));}
    private void updateHeader(){
        int online=0,controlled=0;for(Bundle d:devices){if(d.getBoolean("online"))online++;if(jailed(d))controlled++;}
        Bundle root=rootState();boolean busy=root.getBoolean("busy");
        rootScanning=busy&&"SCAN".equals(root.getString("action"))||rootScanning&&acting;
        boolean scanningNow=scanning||rootScanning;
        counters.setText(getString(R.string.netlab_ref_users,tab==1?controlled:online));
        counters.setContentDescription(getString(R.string.netlab_ui_counts,devices.size(),online,controlled));
        scanButton.setText(scanningNow?R.string.netlab_ui_stop_scan:R.string.netlab_ui_scan);
        scanButton.setEnabled(!acting&&(scanningNow||link!=null&&!busy));
        updateMasterControls(controlled>0||root.getBoolean("active"));
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
    private List<Bundle> allWifiPeers(){
        Set<String> own=new HashSet<>();if(link!=null)for(android.net.LinkAddress address:link.properties.getLinkAddresses())own.add(address.getAddress().getHostAddress());
        List<NetLabGroupPolicy.Peer> peers=new ArrayList<>();
        for(Bundle d:devices){ArrayList<String> addresses=d.getStringArrayList("addresses");
            peers.add(new NetLabGroupPolicy.Peer(d.getString("id",""),addresses==null||addresses.isEmpty()?"":addresses.get(0),canOperate(d),d.getBoolean("managed")));}
        Set<String> ids=new HashSet<>(NetLabGroupPolicy.wifiIds(link==null?"":scope,gateway,own,peers));
        List<Bundle> result=new ArrayList<>();for(Bundle d:devices)if(ids.contains(d.getString("id")))result.add(d);return result;
    }
    private void toggleAll(){
        for(Bundle d:devices)if(jailed(d)){confirmRestore();return;}
        if(rootState().getBoolean("active")){confirmRestore();return;}controlAll(false,0);
    }
    private void controlAll(boolean speedLimit,int download){
        List<Bundle> peers=allWifiPeers();
        if(peers.isEmpty()){toast(link==null?R.string.netlab_no_wifi:R.string.netlab_feedback_no_peers);return;}
        if(!NetLabGroupPolicy.withinLimit(peers.size())){
            new AlertDialog.Builder(this).setTitle(R.string.netlab_feedback_master).setMessage(R.string.netlab_feedback_group_limit)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_ui_multiple,(d,w)->chooseRootDevices()).show();return;
        }
        controlWifi(peers,speedLimit,download);
    }
    private void updateMasterControls(boolean controlled){
        List<Bundle> peers=allWifiPeers();boolean ready=!acting&&!scanning&&!rootScanning;
        boolean live=rootState().getBoolean("active")&&!"restoring".equals(rootState().getString("status"));
        int action=controlled?R.string.netlab_ui_restore_all:R.string.netlab_feedback_pause_all;
        masterButton.setContentDescription(getString(action));masterButton.setTooltipText(getString(action));masterButton.setAlpha(controlled?.55f:1f);
        masterButton.setEnabled(ready&&(controlled||!peers.isEmpty()&&!rootState().getBoolean("busy")));
        masterPause.setEnabled(ready&&(!rootState().getBoolean("busy")||live)&&!peers.isEmpty());masterRestore.setEnabled(!acting&&controlled);
        masterControls.setVisibility(tab==0||tab==1?View.VISIBLE:View.GONE);
        masterValue.setVisibility(masterControls.getVisibility());
        if(adjustingRate)return;
        int position=12;boolean uniform=true,first=true;String policy="";
        boolean pending=false;
        for(Bundle peer:peers){int value=sliderPosition(peer);pending|=Arrays.asList("applying","active_unverified").contains(peer.getString("state"));
            String next=value==12?"PASS":peer.getString("requested_mode",peer.getString("mode","PASS"))+":"+peer.getInt("requested_up",peer.getInt("up_kbps"))+":"+peer.getInt("requested_down",peer.getInt("down_kbps"));
            if(first){position=value;policy=next;first=false;}else if(position!=value||!policy.equals(next))uniform=false;}
        if(!uniform)position=6;masterSpeed.setProgress(position);
        String value="restoring".equals(rootState().getString("status"))?getString(R.string.netlab_ui_restoring):!uniform?getString(R.string.netlab_feedback_mixed):pending?getString(R.string.netlab_slider_pending):position==0?getString(R.string.netlab_ui_paused):position==12?getString(R.string.netlab_slider_enabled):peers.isEmpty()?rate(speedRate(position)):shortStatus(peers.get(0));
        masterSpeed.setStateDescription(value);masterValue.setText(getString(R.string.netlab_slider_ends,value));
        masterSpeed.setEnabled(ready&&(!rootState().getBoolean("busy")||live)&&(!peers.isEmpty()||controlled));bindSpeed(masterSpeed,null,true,position);
    }
    private void searchDevices(){
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint(R.string.netlab_search);search.setText(query);
        search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128)});
        new AlertDialog.Builder(this).setTitle(R.string.netlab_search).setView(search).setNegativeButton(android.R.string.cancel,null)
                .setNeutralButton(R.string.netlab_ref_clear_search,(d,w)->{query="";tab=0;renderTab();})
                .setPositiveButton(android.R.string.ok,(d,w)->{query=search.getText().toString().trim();tab=0;limit=50;renderTab();}).show();
    }
    private void filterDevices(){
        String[] labels={getString(R.string.netlab_ui_all_devices),getString(R.string.netlab_ui_controlled),getString(R.string.netlab_trusted)};
        new AlertDialog.Builder(this).setTitle(R.string.netlab_ui_filter).setSingleChoiceItems(labels,filter==3?1:filter==2?2:0,(dialog,which)->{
            filter=new int[]{0,3,2}[which];tab=0;limit=50;dialog.dismiss();renderTab();}).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void renderTab(){
        cancelRateChoice();
        content.removeAllViews();graph=null;rates=null;list=null;rootStatus=null;lastList="";
        int section=tab==0?0:tab==1?1:2;
        for(int i=0;i<tabLabels.size();i++){
            tabLabels.get(i).setTextColor(i==section?NetLabStyle.WHITE:NetLabStyle.MUTED);
            ((View)tabLabels.get(i).getParent()).setSelected(i==section);
            tabLines.get(i).setBackgroundColor(i==section?NetLabStyle.WHITE:NetLabStyle.BLACK);
        }
        counters.setVisibility(tab==0||tab==1?View.VISIBLE:View.GONE);updateHeader();
        if(tab!=0&&tab!=1&&tab!=4)content.addView(button(R.string.netlab_ref_back_options,v->{tab=4;renderTab();}));
        if(tab==6){renderRoot();return;}
        if(tab==0||tab==1){
            if(tab==0&&(!query.isEmpty()||filter!=0))content.addView(button(R.string.netlab_ref_clear_search,v->{query="";filter=0;renderTab();}));
            list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);renderDevices();return;
        }
        if(tab==2){LinearLayout card=NetLabStyle.card(this);text(card,R.string.netlab_defender,R.string.netlab_defender_summary);
            card.addView(NetLabStyle.text(this,getString(R.string.netlab_gateway,gateway.isEmpty()?getString(R.string.netlab_unknown):gateway),14,true));
            card.addView(NetLabStyle.text(this,link==null?getString(R.string.netlab_no_wifi):arpStatus,13,false));
            if(!gatewayMac.isEmpty())card.addView(NetLabStyle.text(this,getString(R.string.netlab_gateway_neighbor,gatewayMac),13,false));
            for(String note:observations)card.addView(NetLabStyle.text(this,note,13,false));content.addView(card);return;}
        if(tab==3){LinearLayout card=NetLabStyle.card(this);text(card,R.string.netlab_traffic,R.string.netlab_traffic_summary);
            rates=NetLabStyle.text(this,"",16,true);card.addView(rates);graph=new NetLabTrafficView(this);card.addView(graph,new LinearLayout.LayoutParams(-1,Ui.dp(this,160)));content.addView(card);updateTraffic();return;}
        if(tab==4){LinearLayout card=NetLabStyle.card(this);text(card,R.string.netlab_ui_options,R.string.netlab_ui_options_summary);
            card.addView(button(R.string.netlab_search,v->searchDevices()));card.addView(button(R.string.netlab_ui_filter,v->filterDevices()));
            card.addView(button(R.string.netlab_ui_multiple,v->chooseRootDevices()));card.addView(button(R.string.netlab_ui_restore_all,v->confirmRestore()));
            card.addView(button(R.string.netlab_traffic,v->{tab=3;renderTab();}));card.addView(button(R.string.netlab_ui_network_info,v->{tab=2;renderTab();}));
            card.addView(button(R.string.netlab_diagnostics,v->{tab=5;renderTab();}));card.addView(button(R.string.netlab_ui_advanced,v->{tab=6;renderTab();}));
            if(state.getBoolean("hotspot"))card.addView(button(R.string.netlab_pause_current,v->{List<Bundle> clients=new ArrayList<>();for(Bundle d:devices)if(d.getBoolean("managed")&&d.getBoolean("online")&&!jailed(d))clients.add(d);chooseDuration(clients);}));
            Switch history=new Switch(this);history.setText(R.string.netlab_history);history.setTextColor(getColor(R.color.hecker_foreground));history.setChecked(preferences.history());
            history.setOnCheckedChangeListener((v,value)->{preferences.history(value);mergeDevices();updateHeader();});card.addView(history);
            card.addView(NetLabStyle.text(this,getString(R.string.netlab_history_summary),13,false));
            card.addView(button(R.string.netlab_clear_history,v->{preferences.clearHistory();mergeDevices();updateHeader();toast(R.string.netlab_history_cleared);}));
            card.addView(button(R.string.netlab_clear_metadata,v->new AlertDialog.Builder(this).setTitle(R.string.netlab_clear_metadata).setMessage(R.string.netlab_clear_metadata_summary)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{preferences.clearAll();mergeDevices();updateHeader();renderTab();}).show()));content.addView(card);return;}
        LinearLayout card=NetLabStyle.card(this);text(card,R.string.netlab_diagnostics,R.string.netlab_diagnostics_summary);
        diagnostic(card,R.string.netlab_controller,state.getBoolean("available"));diagnostic(card,R.string.netlab_hal,state.getBoolean("hal_block"));
        diagnostic(card,R.string.netlab_native_callbacks,state.getBoolean("callbacks"));diagnostic(card,R.string.netlab_timers,state.getBoolean("can_time"));
        if(!state.getBoolean("can_time")){
            card.addView(NetLabStyle.text(this,getString(R.string.netlab_timer_setup),13,false));
            card.addView(button(R.string.netlab_alarm_settings,v->{try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:org.hecker.netlab")));}catch(ActivityNotFoundException e){toast(R.string.native_page_unavailable);}}));
            card.addView(button(R.string.netlab_battery_settings,v->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(ActivityNotFoundException e){toast(R.string.native_page_unavailable);}}));
        }
        diagnostic(card,R.string.netlab_hotspot_speed,false);diagnostic(card,R.string.netlab_router,false);diagnostic(card,R.string.netlab_lan_root_grant,rootState().getBoolean("root"));
        card.addView(NetLabStyle.text(this,getString(R.string.netlab_offload,state.getInt("offload",-1)),13,false));
        card.addView(NetLabStyle.text(this,explain(state.getString("error","")),13,false));card.addView(NetLabStyle.text(this,getString(R.string.netlab_capability_limits),13,false));
        card.addView(button(R.string.netlab_hotspot_settings,v->{try{startActivity(new Intent("android.settings.WIFI_TETHER_SETTINGS").setPackage("com.android.settings"));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}}));
        ArrayList<Bundle> events=state.getParcelableArrayList("events",Bundle.class);if(events!=null)for(Bundle e:events)
            card.addView(NetLabStyle.text(this,android.text.format.DateFormat.getTimeFormat(this).format(new Date(e.getLong("time")))+" · "+explain(e.getString("code","")),12,false));
        content.addView(card);
    }
    private void diagnostic(LinearLayout card,int label,boolean supported){card.addView(NetLabStyle.text(this,getString(label)+" · "+getString(supported?R.string.netlab_supported:R.string.netlab_unavailable),14,false));}
    private void renderDevices(){
        if(list==null||adjustingRate)return;
        String fingerprint=devices.toString()+query+filter+tab+expandedDevice+acting+rootState().getBoolean("busy")+limit;
        if(fingerprint.equals(lastList))return;lastList=fingerprint;list.removeAllViews();int shown=0,total=0;
        String needle=tab==0?query.toLowerCase(Locale.ROOT):"";
        for(Bundle d:devices){String id=d.getString("id"),name=label(d);
            if(tab==1&&!jailed(d))continue;
            if(tab==0&&(filter==3&&!jailed(d)||filter==2&&!preferences.trusted(id)))continue;
            if(!needle.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(needle)&&!addresses(d).contains(needle)&&!d.getString("mac","").toLowerCase(Locale.ROOT).contains(needle))continue;
            total++;if(shown++>=limit)continue;
            if(expandedDevice==null&&!router(d)&&!d.getBoolean("managed"))expandedDevice=id;
            list.addView(peerCard(d));
        }
        if(total==0){LinearLayout empty=NetLabStyle.card(this);empty.addView(NetLabStyle.text(this,getString(devices.isEmpty()?R.string.netlab_ui_find_devices:R.string.netlab_no_devices),17,true));
            if(tab==1){empty.removeAllViews();empty.addView(NetLabStyle.text(this,getString(R.string.netlab_no_jailed),14,false));}
            else if(devices.isEmpty())empty.addView(NetLabStyle.text(this,getString(R.string.netlab_ui_scan_hint),14,false));list.addView(empty);}
        if(total>limit){list.addView(NetLabStyle.text(this,getString(R.string.netlab_page_count,limit,total),12,false));list.addView(button(R.string.netlab_show_more,v->{limit+=50;lastList="";renderDevices();}));}
    }
    private LinearLayout peerCard(Bundle d){
        String id=d.getString("id"),name=displayName(d);
        LinearLayout card=NetLabStyle.card(this);card.setPadding(0,0,0,0);
        LinearLayout heading=NetLabStyle.row(this);heading.setPadding(Ui.dp(this,10),0,Ui.dp(this,4),0);
        heading.setMinimumHeight(Ui.dp(this,48));
        TextView title=NetLabStyle.text(this,name,21,true);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setContentDescription(getString(R.string.netlab_ref_expand_device,name));title.setFocusable(true);title.setMinHeight(Ui.dp(this,48));
        title.setOnClickListener(v->{expandedDevice=id.equals(expandedDevice)?"":id;lastList="";renderDevices();});
        title.setOnLongClickListener(v->{deviceActions(d);return true;});
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton timer=NetLabStyle.icon(this,NetLabStyle.TIMER,getString(R.string.netlab_ref_timer_device,name),v->deviceActions(d));
        timer.setEnabled(!acting&&!router(d));heading.addView(timer);
        ImageButton wifi=NetLabStyle.icon(this,NetLabStyle.WIFI,getString(jailed(d)?R.string.netlab_restore:R.string.netlab_ui_pause_internet),v->togglePeer(d));
        wifi.setEnabled(!acting&&(jailed(d)||canOperate(d)&&!router(d)));wifi.setAlpha(jailed(d)?.45f:1f);heading.addView(wifi);card.addView(heading);
        LinearLayout info=NetLabStyle.column(this);info.setBackgroundColor(NetLabStyle.BODY);
        LinearLayout identityRow=NetLabStyle.row(this);identityRow.setPadding(Ui.dp(this,10),0,Ui.dp(this,4),0);
        TextView ip=NetLabStyle.text(this,getString(R.string.netlab_ref_ip,addresses(d).isEmpty()?getString(R.string.netlab_unknown):addresses(d)),12,false);
        ip.setSingleLine(true);ip.setEllipsize(android.text.TextUtils.TruncateAt.END);ip.setContentDescription(ip.getText());
        String mac=d.getString("mac","");
        TextView macLabel=NetLabStyle.text(this,getString(R.string.netlab_ref_mac,mac.isEmpty()?getString(R.string.netlab_not_shared):mac.toUpperCase(Locale.ROOT)),12,false);
        macLabel.setSingleLine(true);macLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);macLabel.setContentDescription(macLabel.getText());
        if(getResources().getConfiguration().fontScale>1.3f||getResources().getConfiguration().screenWidthDp<360){
            ip.setSingleLine(false);info.addView(ip);ip.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),0);
            identityRow.addView(macLabel,new LinearLayout.LayoutParams(0,-2,1));
        }else{identityRow.addView(ip,new LinearLayout.LayoutParams(0,-2,1));identityRow.addView(macLabel,new LinearLayout.LayoutParams(0,-2,1.8f));}
        identityRow.addView(NetLabStyle.icon(this,NetLabStyle.COPY,getString(R.string.netlab_ref_copy_device,name),v->copyPeer(d)));info.addView(identityRow);
        LinearLayout facts=NetLabStyle.row(this);facts.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),Ui.dp(this,3));facts.setMinimumHeight(Ui.dp(this,36));
        TextView trust=NetLabStyle.text(this,getString(R.string.netlab_ref_trusted,getString(preferences.trusted(id)?R.string.netlab_ref_yes:R.string.netlab_ref_no)),12,true);
        String control=jailed(d)?shortStatus(d):getString(R.string.netlab_ref_no);
        TextView stateLabel=NetLabStyle.text(this,getString(R.string.netlab_ref_control,control),12,false);
        String expiry=d.getLong("until")>0?android.text.format.DateFormat.getTimeFormat(this).format(new Date(d.getLong("until"))):"—";
        TextView until=NetLabStyle.text(this,getString(R.string.netlab_ref_timer,expiry),12,false);until.setGravity(Gravity.END);
        facts.addView(trust,new LinearLayout.LayoutParams(0,-2,1));facts.addView(stateLabel,new LinearLayout.LayoutParams(0,-2,1.4f));facts.addView(until,new LinearLayout.LayoutParams(0,-2,1));info.addView(facts);card.addView(info);
        if(id.equals(expandedDevice)){
            LinearLayout controls=NetLabStyle.row(this);controls.setBackgroundColor(NetLabStyle.BLACK);
            ImageButton pause=NetLabStyle.icon(this,NetLabStyle.CUT,getString(R.string.netlab_ui_pause_internet),v->pausePeer(d));
            pause.setEnabled(!acting&&(!jailed(d)||d.getBoolean("root_peer")&&rootState().getBoolean("active"))&&canOperate(d)&&!router(d)&&sliderPosition(d)!=0);controls.addView(pause);
            SeekBar speed=NetLabStyle.speed(this);int position=sliderPosition(d);
            speed.setProgress(position);speed.setContentDescription(getString(R.string.netlab_ui_speed_limit));
            speed.setStateDescription(Arrays.asList("applying","active_unverified").contains(d.getString("state"))?getString(R.string.netlab_slider_pending):"restoring".equals(d.getString("state"))?shortStatus(d):position==12?getString(R.string.netlab_slider_enabled):shortStatus(d));
            speed.setEnabled(!acting&&canOperate(d)&&!d.getBoolean("managed")&&!router(d));
            bindSpeed(speed,d,false,position);controls.addView(speed);
            ImageButton restore=NetLabStyle.icon(this,NetLabStyle.WIFI,getString(R.string.netlab_restore),v->restorePeer(d));restore.setEnabled(!acting&&jailed(d));controls.addView(restore);card.addView(controls);
        }
        LinearLayout brand=NetLabStyle.row(this);brand.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);brand.setMinimumHeight(Ui.dp(this,26));
        String manufacturer=d.getString("brand","");
        if(manufacturer.isEmpty())manufacturer=getString(NetLabVendors.privateMac(d.getString("mac",""))?R.string.netlab_feedback_private:R.string.netlab_unknown);
        TextView brandName=NetLabStyle.text(this,getString(R.string.netlab_ref_brands,manufacturer),12,false);
        brandName.setSingleLine(true);brandName.setEllipsize(android.text.TextUtils.TruncateAt.END);brand.addView(brandName,new LinearLayout.LayoutParams(0,-2,1));
        ImageView os=new ImageView(this);int icon=peerIcon(d);
        os.setImageDrawable(icon==NetLabStyle.UNKNOWN?NetLabStyle.brandMark(d.getString("brand","")):new NetLabStyle.Icon(icon));os.setScaleType(ImageView.ScaleType.FIT_CENTER);
        os.setContentDescription(router(d)?getString(R.string.netlab_ui_router):manufacturer+" · "+identity(d));brand.addView(os,new LinearLayout.LayoutParams(Ui.dp(this,20),Ui.dp(this,20)));card.addView(brand);return card;
    }
    private int peerIcon(Bundle d){
        if(router(d))return NetLabStyle.ROUTER;
        switch(d.getString("os","unknown")){
            case "android":return NetLabStyle.ANDROID;
            case "ios":case "macos":case "apple":return NetLabStyle.APPLE;
            case "windows":return NetLabStyle.WINDOWS;
            case "linux":return NetLabStyle.LINUX;
            case "media":return NetLabStyle.MEDIA;
            default:return d.getString("brand","").toLowerCase(Locale.ROOT).startsWith("apple")?NetLabStyle.APPLE:NetLabStyle.UNKNOWN;
        }
    }
    private void bindSpeed(SeekBar speed,Bundle peer,boolean all,int original){
        final String gestureScope=scope;
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            private boolean changed,touch,up,cancelled;
            private final Runnable apply=()->{
                pendingRateChoice=null;adjustingRate=false;if(!started)return;
                int value=speed.getProgress();speed.setProgress(original);
                NetLabSliderPolicy.Action action=NetLabSliderPolicy.action(original,value,changed,cancelled||touch&&!up);touch=false;changed=false;
                if(action==NetLabSliderPolicy.Action.NONE){updateHeader();lastList="";renderDevices();return;}
                if(!gestureScope.equals(scope)){toast(R.string.netlab_device_changed);updateHeader();lastList="";renderDevices();return;}
                if(all){if(value==12)confirmRestore();else controlAll(value!=0,speedRate(value));}
                else if(value==0)pausePeer(peer);else if(value==12){if(jailed(peer))restorePeer(peer);}else controlWifi(Collections.singletonList(peer),true,speedRate(value));
                lastList="";updateHeader();renderDevices();
            };
            {
                speed.setOnTouchListener((v,event)->{
                    switch(event.getActionMasked()){
                        case MotionEvent.ACTION_DOWN:cancelRateChoice();adjustingRate=true;changed=false;touch=true;up=false;cancelled=false;break;
                        case MotionEvent.ACTION_UP:up=true;break;
                        case MotionEvent.ACTION_CANCEL:cancelled=true;break;
                    }return false;
                });
            }
            public void onStartTrackingTouch(SeekBar bar){cancelRateChoice();adjustingRate=true;}
            public void onProgressChanged(SeekBar bar,int value,boolean user){
                bar.setStateDescription(value==0?getString(R.string.netlab_ui_paused):rate(speedRate(value)));
                if(user){changed=true;if(!touch){if(pendingRateChoice!=null)main.removeCallbacks(pendingRateChoice);adjustingRate=true;pendingRateChoice=apply;main.postDelayed(apply,400);}}
            }
            public void onStopTrackingTouch(SeekBar bar){if(pendingRateChoice!=null)main.removeCallbacks(pendingRateChoice);apply.run();}
        });
    }
    private String displayName(Bundle d){String name=preferences.label(d.getString("id"),d.getString("name",""));return name.isEmpty()?getString(R.string.netlab_ref_untitled):name;}
    private void copyPeer(Bundle d){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(displayName(d),getString(R.string.netlab_ref_ip,addresses(d))+"\n"+getString(R.string.netlab_ref_mac,d.getString("mac",""))));
    }
    private void togglePeer(Bundle d){if(jailed(d))restorePeer(d);else pausePeer(d);}
    private void pausePeer(Bundle d){
        if(acting)return;if(router(d)||!canOperate(d)){details(d);return;}
        if(jailed(d)&&!(d.getBoolean("root_peer")&&rootState().getBoolean("active"))){deviceActions(d);return;}
        if(d.getBoolean("managed"))chooseDuration(Collections.singletonList(d));else controlWifi(Collections.singletonList(d),false);
    }
    private void restorePeer(Bundle d){if(!acting&&jailed(d))perform("restore",Collections.singletonList(d),0);}
    private static int speedRate(int position){return NetLabSliderPolicy.rate(position);}
    private static int speedPosition(int kbps){return NetLabSliderPolicy.ratePosition(kbps);}
    private int sliderPosition(Bundle d){return NetLabSliderPolicy.position(d.getString("state",""),d.getString("mode",""),d.getString("requested_mode",""),d.getInt("down_kbps"),d.getInt("requested_down",d.getInt("down_kbps")));}
    private boolean canOperate(Bundle d){return d.getBoolean("online")||d.getBoolean("session_member")&&rootState().getBoolean("active")
            &&rootState().getString("scope","").equals(scope)&&d.getString("id","").startsWith("lan:"+scope+":");}
    private void cancelRateChoice(){if(pendingRateChoice!=null)main.removeCallbacks(pendingRateChoice);pendingRateChoice=null;adjustingRate=false;}
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
        else if(canOperate(device)&&!router(device)){
            labels.add(device.getBoolean("managed")?R.string.netlab_pause:R.string.netlab_ui_pause_internet);
            actions.add(()->{if(device.getBoolean("managed"))chooseDuration(Collections.singletonList(device));else controlWifi(Collections.singletonList(device),false);});
        }
        if(!device.getBoolean("managed")&&!jailed(device)&&canOperate(device)&&!router(device)){
            labels.add(R.string.netlab_ui_speed_limit);actions.add(()->controlWifi(Collections.singletonList(device),true));
        }
        labels.add(R.string.netlab_details);actions.add(()->details(device));labels.add(R.string.netlab_rename_trust);actions.add(()->rename(device));
        String[] titles=new String[labels.size()];for(int i=0;i<titles.length;i++)titles[i]=getString(labels.get(i));
        new AlertDialog.Builder(this).setTitle(label(device)).setItems(titles,(dialog,which)->actions.get(which).run()).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void controlWifi(List<Bundle> targets,boolean speedLimit){
        controlWifi(targets,speedLimit,-1);
    }
    private void controlWifi(List<Bundle> targets,boolean speedLimit,int initialDownload){
        Bundle root=rootState();
        if(root.getBoolean("busy")&&!root.getBoolean("active")){new AlertDialog.Builder(this).setTitle(R.string.netlab_ui_controls_busy).setMessage(R.string.netlab_ui_wait_busy)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_ui_stop_restore,(dialog,which)->confirmRestore()).show();return;}
        ArrayList<Bundle> peers=root.getParcelableArrayList("devices",Bundle.class);List<Bundle> chosen=new ArrayList<>();
        if(root.getString("scope","").equals(scope)&&peers!=null)for(Bundle target:targets)for(Bundle peer:peers)
            if(peer.getString("id","").equals(target.getString("id"))){chosen.add(peer);break;}
        if(link==null||chosen.size()!=targets.size()||chosen.isEmpty()){
            if(root.getBoolean("active")){new AlertDialog.Builder(this).setTitle(R.string.netlab_ui_controls_busy).setMessage(R.string.netlab_slider_existing_group)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_ui_stop_restore,(d,w)->confirmRestore()).show();return;}
            new AlertDialog.Builder(this).setTitle(R.string.netlab_ui_scan_needed).setMessage(R.string.netlab_ui_scan_for_controls)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.netlab_ui_scan,(dialog,which)->scanWifi()).show();return;
        }
        configureRoot(chosen,speedLimit,initialDownload);
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
        if(!d.getString("vendor","").isEmpty())body+="\n"+getString(R.string.netlab_feedback_vendor_source,d.getString("vendor"));
        if("advertised".equals(d.getString("brand_source")))body+="\n"+getString(R.string.netlab_feedback_advertised_brand,d.getString("brand"));
        if(NetLabVendors.privateMac(d.getString("mac","")))body+="\n"+getString(R.string.netlab_feedback_private_hint);
        if(d.getBoolean("root_peer"))body+="\n\n"+getString(R.string.netlab_lan_counts,d.getLong("forwarded_up"),d.getLong("forwarded_down"),d.getLong("dropped_up"),d.getLong("dropped_down"));
        if(d.getLong("until")>0)body+="\n"+getString(d.getBoolean("root_peer")?R.string.netlab_lan_until:R.string.netlab_until,android.text.format.DateFormat.getTimeFormat(this).format(new Date(d.getLong("until"))));
        if(d.getBoolean("root_peer")&&d.getBoolean("lan_managed"))body+="\n"+getString(R.string.netlab_ui_rates,rate(d.getInt("down_kbps")),rate(d.getInt("up_kbps")));
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(label(d)).setMessage(body).setNegativeButton(android.R.string.cancel,null)
                .setNeutralButton(R.string.netlab_rename_trust,(v,w)->rename(d));
        dialog.setPositiveButton(R.string.netlab_ui_manage,(v,w)->deviceActions(d));dialog.show();
    }
    private void rename(Bundle d){LinearLayout page=Ui.page(this,R.string.netlab_rename_trust);EditText name=new EditText(this);name.setSingleLine(true);name.setText(label(d));name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});page.addView(name);
        CheckBox trust=new CheckBox(this);trust.setText(R.string.netlab_trusted);trust.setChecked(preferences.trusted(d.getString("id")));page.addView(trust);page.addView(NetLabStyle.text(this,getString(R.string.netlab_identity_warning),13,false));
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
        if(scanning||rootScanning){
            if(scanning)scan();
            if(rootScanning&&rootState().getBoolean("busy"))rootAction("stop",Collections.emptyList(),true,0,0,0);
            scanStatus.setTag(null);scanStatus.setText(R.string.netlab_scan_cancelled);updateHeader();return;
        }
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
        LinearLayout card=NetLabStyle.card(this);text(card,R.string.netlab_lan_title,R.string.netlab_lan_summary);
        rootStatus=NetLabStyle.text(this,"",14,true);rootStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(rootStatus);
        rootProbe=button(R.string.netlab_lan_probe,v->rootAction("probe",Collections.emptyList(),true,0,0,0));card.addView(rootProbe);
        rootScan=button(R.string.netlab_lan_scan,v->rootAction("scan",Collections.emptyList(),true,0,0,0));card.addView(rootScan);
        rootChoose=button(R.string.netlab_lan_choose,v->chooseRootDevices());card.addView(rootChoose);
        rootStop=button(R.string.netlab_lan_stop,v->rootAction("stop",Collections.emptyList(),true,0,0,0));card.addView(rootStop);
        card.addView(NetLabStyle.text(this,getString(R.string.netlab_lan_limits),13,false));content.addView(card);updateRoot();
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
        configureRoot(peers,speedLimit,-1);
    }
    private void configureRoot(List<Bundle> peers,boolean speedLimit,int initialDownload){
        final String configuredScope=scope;
        final boolean live=rootState().getBoolean("active");
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,8));
        page.addView(NetLabStyle.text(this,peers.size()==1?label(peers.get(0)):getString(R.string.netlab_ui_selected_count,peers.size()),16,true));
        EditText down=null,up=null;
        if(speedLimit){
            down=number(page,R.string.netlab_download,initialDownload<0?preferences.download():initialDownload);up=number(page,R.string.netlab_upload,preferences.upload());
            page.addView(NetLabStyle.text(this,getString(R.string.netlab_ui_rate_hint),12,false));
        }else page.addView(NetLabStyle.text(this,getString(R.string.netlab_ui_pause_hint),13,false));
        TextView durationTitle=NetLabStyle.text(this,getString(R.string.netlab_ui_duration),14,true);page.addView(durationTitle);
        Spinner duration=new Spinner(this);duration.setContentDescription(getString(R.string.netlab_ui_duration));
        duration.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{getString(R.string.netlab_duration_5m),getString(R.string.netlab_ui_15_minutes),getString(R.string.netlab_duration_hour),getString(R.string.netlab_duration_custom)}));page.addView(duration);
        LinearLayout custom=new LinearLayout(this);custom.setOrientation(LinearLayout.VERTICAL);
        EditText seconds=number(custom,R.string.netlab_ui_duration_seconds,300);custom.setVisibility(View.GONE);page.addView(custom);
        duration.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){custom.setVisibility(position==3?View.VISIBLE:View.GONE);}});
        if(live){durationTitle.setVisibility(View.GONE);duration.setVisibility(View.GONE);custom.setVisibility(View.GONE);page.addView(NetLabStyle.text(this,getString(R.string.netlab_slider_live_duration),13,false));}
        ScrollView scroll=new ScrollView(this);scroll.addView(page);final EditText upload=up,download=down;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(speedLimit?R.string.netlab_ui_speed_limit:R.string.netlab_ui_pause_internet).setView(scroll)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(speedLimit?R.string.netlab_ui_apply_limit:R.string.netlab_ui_pause_internet,null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            if(link==null||!configuredScope.equals(scope)){dialog.dismiss();toast(R.string.netlab_device_changed);return;}
            int value=0;if(!live)try{value=duration.getSelectedItemPosition()==3?Integer.parseInt(seconds.getText().toString()):new int[]{300,900,3600}[duration.getSelectedItemPosition()];
                if(value<1||value>3600)throw new NumberFormatException();}catch(NumberFormatException invalid){seconds.setError(getString(R.string.netlab_lan_duration_error));return;}
            int a=0,c=0;if(speedLimit)try{a=Integer.parseInt(upload.getText().toString());c=Integer.parseInt(download.getText().toString());
                if(a<0||c<0||a>1_024_000||c>1_024_000||(a==0&&c==0))throw new NumberFormatException();}
                catch(NumberFormatException invalid){download.setError(getString(R.string.netlab_lan_rate_error));return;}
            dialog.dismiss();if(speedLimit)preferences.preset(c,a);rootAction(live?"update":"start",peers,!speedLimit,a,c,value);
        }));dialog.show();
    }
    private void rootAction(String operation,List<Bundle> peers,boolean block,int up,int down,int seconds){
        if(acting)return;INetLabService service=controller;if(service==null){toast(R.string.netlab_ui_controller_unavailable);return;}
        if(!operation.equals("stop")&&checkSelfPermission(LOCAL_PERMISSION)!=PackageManager.PERMISSION_GRANTED){rootPending=()->rootAction(operation,peers,block,up,down,seconds);requestPermissions(new String[]{LOCAL_PERMISSION},438);return;}
        List<Bundle> copy=new ArrayList<>(peers);acting=true;if(operation.equals("scan"))rootScanning=true;updateHeader();updateRoot();lastList="";renderDevices();
        worker.execute(()->{Bundle updated=null;String result;
            try{
                if(operation.equals("probe"))updated=service.lanProbe();else if(operation.equals("scan"))updated=service.lanScan();else if(operation.equals("stop"))updated=service.lanStop();
                else{String[] ips=new String[copy.size()],macs=new String[copy.size()];for(int i=0;i<copy.size();i++){ips[i]=copy.get(i).getStringArrayList("addresses").get(0);macs[i]=copy.get(i).getString("mac");}updated=operation.equals("update")?service.lanUpdate(ips,macs,block,up,down):service.lanStart(ips,macs,block,up,down,seconds);}
                result=updated.getString("result","service_unavailable");
            }catch(RemoteException|RuntimeException unavailable){result="service_unavailable";}
            final Bundle next=updated;final String outcome=result;
            main.post(()->{acting=false;if(!started)return;if(next!=null)state=next;mergeDevices();updateHeader();lastList="";renderDevices();updateRoot();
                Toast.makeText(this,"ok".equals(outcome)?getString(R.string.netlab_lan_requested):rootError(outcome),Toast.LENGTH_LONG).show();refresh();});
        });
    }
    private String rootError(String code){
        String message=rootErrorMessage(code);
        if(Arrays.asList("peer_unreachable","peer_mac_changed").contains(code)){
            ArrayList<Bundle> failures=rootState().getParcelableArrayList("verification",Bundle.class);
            if(failures!=null){
                for(int i=0;i<Math.min(3,failures.size());i++){Bundle failure=failures.get(i);String observed=failure.getString("observed","");
                    message+="\n"+(observed.equals("missing")?getString(R.string.netlab_lan_verify_missing,failure.getString("address"),failure.getString("expected"))
                            :getString(R.string.netlab_lan_verify_changed,failure.getString("address"),failure.getString("expected"),observed));}
                if(failures.size()>3)message+="\n"+getString(R.string.netlab_lan_verify_more,failures.size()-3);
            }
        }
        return code!=null&&code.matches("[a-z0-9_]{1,80}")?getString(R.string.netlab_slider_failure_code,message,code):message;
    }
    private String rootErrorMessage(String code){
        switch(code){case "service_unavailable":return getString(R.string.netlab_ui_controller_unavailable);
            case "startup_timeout":return getString(R.string.netlab_slider_start_timeout);
            case "not_active":case "session_timeout":case "controller_lost":return getString(R.string.netlab_slider_not_active);
            case "invalid_peer":case "duplicate_peer_mac":case "not_owned":return getString(R.string.netlab_slider_peer_conflict);
            case "root_required":case "root_or_helper_unavailable":case "raw_socket_denied":case "packet_filter_denied":return getString(R.string.netlab_lan_root_error);
            case "scan_required":return getString(R.string.netlab_lan_scan_first);
            case "peer_unreachable":return getString(R.string.netlab_lan_peer_unreachable);
            case "peer_mac_changed":return getString(R.string.netlab_lan_peer_mac_changed);
            case "peer_unreachable_or_changed":case "neighbor_changed":return getString(R.string.netlab_lan_peer_changed);
            case "wifi_unavailable":case "ipv4_unavailable":case "invalid_link":case "invalid_gateway":case "link_changed":case "route_changed":case "vpn_active":return getString(R.string.netlab_lan_network_error);
            case "kernel_forwarding_enabled":case "forwarding_guard_unavailable":case "forwarding_guard_lost":return getString(R.string.netlab_lan_forwarding_error);
            case "forwarding_guard_cleanup_failed":return getString(R.string.netlab_lan_cleanup_error);
            case "notification_unavailable":return getString(R.string.netlab_lan_notification_error);
            case "lan_busy":return getString(R.string.netlab_lan_busy);
            case "local_access_revoked":return getString(R.string.netlab_permission_needed);
            default:if(code.endsWith("repair_unavailable"))return getString(R.string.netlab_lan_repair_error);return getString(R.string.netlab_lan_action_error);}
    }
    private EditText number(LinearLayout page,int title,int value){page.addView(NetLabStyle.text(this,getString(title),14,true));EditText field=new EditText(this);field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.setSingleLine(true);field.setSelectAllOnFocus(true);field.setTextColor(getColor(R.color.hecker_foreground));field.setMinHeight(Ui.dp(this,48));field.setText(String.valueOf(value));page.addView(field);return field;}
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
