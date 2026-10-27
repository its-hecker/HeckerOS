// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.hecker;

import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import me.kavishdevar.librepods.R;
import org.hecker.earbuds.HeckerEarbudsView;

/** Foreground companion for connected non-Apple audio. AirPods retain their native route. */
public final class HeckerHeadphonesActivity extends Activity {
    private static final String DEVICE="hecker_device_address", AIRPODS="hecker_airpods_controls";
    private static final Map<Activity,BroadcastReceiver> ROUTES=new WeakHashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private BluetoothDevice selected;
    private SoundcoreR50iReader reader;
    private SoundcoreR50iCodec.State vendor;
    private TextView details;
    private final TextView[] levels=new TextView[3];
    private boolean listening;
    private int generation;

    /** Called before upstream Compose starts, so Soundcore never enters AirPods onboarding. */
    public static boolean route(Activity activity) {
        if(activity.isFinishing() || activity.getIntent().getBooleanExtra(AIRPODS,false)) return false;
        List<BluetoothDevice> devices=ConnectedHeadsets.devices(activity);
        if(devices.isEmpty()) return false;
        Intent intent=new Intent(activity,HeckerHeadphonesActivity.class);
        String requested=activity.getIntent().getStringExtra(DEVICE);
        intent.putExtra(DEVICE,requested==null?devices.get(0).getAddress():requested);
        activity.startActivity(intent);activity.finish();return true;
    }
    public static void watch(Activity activity) {
        stopWatching(activity);
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
            if(activity.hasWindowFocus()) route(activity);
        }};
        IntentFilter filter=new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction("android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED");
        activity.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);ROUTES.put(activity,receiver);
    }
    public static void stopWatching(Activity activity) {
        BroadcastReceiver receiver=ROUTES.remove(activity);
        if(receiver!=null) activity.unregisterReceiver(receiver);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xFF0B1522);
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setBackgroundColor(0xFF0B1522);
        page.setOnApplyWindowInsetsListener((view,insets)->{
            var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
        });
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(20),dp(8),dp(16),dp(8));
        TextView title=text(getString(R.string.hecker_headphones_title),22,0xFFF1F6FF);title.setTypeface(null,Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView airpods=button("AirPods",()->{
            Intent intent=new Intent().setClassName(this,"me.kavishdevar.librepods.MainActivity");
            intent.putExtra(AIRPODS,true);startActivity(intent);finish();
        });bar.addView(airpods);
        page.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(20),dp(12),dp(20),dp(24));
        scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
        choose(getIntent().getStringExtra(DEVICE));
    }
    private void choose(String address) {
        List<BluetoothDevice> devices=ConnectedHeadsets.devices(this);
        selected=devices.stream().filter(d->d.getAddress().equals(address)).findFirst().orElse(devices.isEmpty()?null:devices.get(0));
        vendor=null;render(devices);
    }
    private void render(List<BluetoothDevice> devices) {
        content.removeAllViews();Arrays.fill(levels,null);
        if(selected==null){content.addView(text(getString(R.string.hecker_headphones_none),18,0xFFBED1E2));
            content.addView(button(getString(R.string.hecker_headphones_bluetooth),this::bluetooth));return;}
        if(devices.size()>1){
            HorizontalScrollView choices=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);
            for(BluetoothDevice device:devices) row.addView(button(ConnectedHeadsets.name(device),()->{
                closeReader();selected=device;vendor=null;render(devices);readDetails();
            }));choices.addView(row);content.addView(choices);
        }
        LinearLayout hero=card();hero.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name=text(ConnectedHeadsets.name(selected),24,0xFFF2F7FF);name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setTypeface(null,Typeface.BOLD);hero.addView(name);
        TextView status=text(getString(R.string.hecker_headphones_connected),13,0xFF92E7D1);status.setGravity(Gravity.CENTER);hero.addView(status);
        HeckerEarbudsView artwork=new HeckerEarbudsView(this);artwork.setDeviceName(ConnectedHeadsets.name(selected));hero.addView(artwork,new LinearLayout.LayoutParams(-1,dp(230)));
        LinearLayout batteries=new LinearLayout(this);
        int[] labels={R.string.hecker_headphones_left,R.string.hecker_headphones_right,R.string.hecker_headphones_case};
        for(int i=0;i<3;i++){
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(1);cell.setPadding(dp(8),dp(12),dp(8),dp(12));cell.setGravity(Gravity.CENTER);
            GradientDrawable bg=new GradientDrawable();bg.setColor(0x193F7297);bg.setCornerRadius(dp(20));cell.setBackground(bg);
            cell.addView(text(getString(labels[i]),12,0xFFBED1E2));levels[i]=text("—",21,0xFFF2F7FF);cell.addView(levels[i]);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.setMargins(dp(3),0,dp(3),0);batteries.addView(cell,size);
        }hero.addView(batteries,new LinearLayout.LayoutParams(-1,-2));content.addView(hero);
        details=text("",15,0xFFBED1E2);details.setPadding(dp(4),dp(18),dp(4),dp(12));content.addView(details);
        if(SoundcoreR50iCodec.matchesModel(ConnectedHeadsets.name(selected))) content.addView(button(getString(R.string.hecker_headphones_refresh),this::readDetails));
        content.addView(text(getString(R.string.hecker_headphones_reported_only),12,0xFF9BB2C8));
        if(SoundcoreR50iCodec.matchesModel(ConnectedHeadsets.name(selected)))
            content.addView(text(getString(R.string.hecker_headphones_r50i_readings),12,0xFF9BB2C8));
        content.addView(button(getString(R.string.hecker_headphones_bluetooth),this::bluetooth));update();
    }
    private void update() {
        if(selected==null || details==null) return;
        int left=vendor!=null&&vendor.left>=0?vendor.left:ConnectedHeadsets.metadata(selected,"METADATA_UNTETHERED_LEFT_BATTERY");
        int right=vendor!=null&&vendor.right>=0?vendor.right:ConnectedHeadsets.metadata(selected,"METADATA_UNTETHERED_RIGHT_BATTERY");
        int box=ConnectedHeadsets.metadata(selected,"METADATA_UNTETHERED_CASE_BATTERY");
        int[] values={left,right,box};
        for(int i=0;i<3;i++)if(levels[i]!=null)levels[i].setText(values[i]<0?"—":values[i]+"%");
        StringBuilder body=new StringBuilder();int overall=ConnectedHeadsets.battery(selected);
        if(overall>=0) body.append(getString(R.string.hecker_headphones_overall,overall)).append('\n');
        if(vendor!=null){
            if(vendor.leftFirmware!=null) body.append(getString(R.string.hecker_headphones_firmware_left,vendor.leftFirmware)).append('\n');
            if(vendor.rightFirmware!=null) body.append(getString(R.string.hecker_headphones_firmware_right,vendor.rightFirmware)).append('\n');
            if(Boolean.TRUE.equals(vendor.leftCharging))body.append(getString(R.string.hecker_headphones_charging_left)).append('\n');
            if(Boolean.TRUE.equals(vendor.rightCharging))body.append(getString(R.string.hecker_headphones_charging_right)).append('\n');
        }else if(SoundcoreR50iCodec.matchesModel(ConnectedHeadsets.name(selected)))body.append(getString(R.string.hecker_headphones_read_unavailable));
        details.setText(body.toString().trim());
    }
    private void readDetails() {
        if(selected==null || !SoundcoreR50iCodec.matchesModel(ConnectedHeadsets.name(selected)))return;
        closeReader();int request=++generation;BluetoothDevice device=selected;
        SoundcoreR50iReader owned=new SoundcoreR50iReader();reader=owned;details.setText(R.string.hecker_headphones_reading);
        worker.execute(()->{var result=owned.read(device);main.post(()->{
            if(request==generation && !isFinishing()){vendor=result;reader=null;update();}
        });});
    }
    private void closeReader(){generation++;if(reader!=null)reader.close();reader=null;}
    private final BroadcastReceiver changes=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        List<BluetoothDevice> devices=ConnectedHeadsets.devices(HeckerHeadphonesActivity.this);
        if(selected!=null && devices.stream().anyMatch(d->d.getAddress().equals(selected.getAddress())))update();
        else {closeReader();choose(null);readDetails();}
        if(BluetoothDevice.ACTION_UUID.equals(intent.getAction()) && reader==null)readDetails();
    }};
    @Override protected void onResume(){super.onResume();
        IntentFilter filter=new IntentFilter("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED");
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_UUID);filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(changes,filter,Context.RECEIVER_EXPORTED);listening=true;
        choose(selected==null?null:selected.getAddress());
        try {if(selected!=null && selected.getUuids()==null)selected.fetchUuidsWithSdp();}catch(SecurityException unavailable){}
        readDetails();}
    @Override protected void onStop(){closeReader();if(listening){unregisterReceiver(changes);listening=false;}super.onStop();}
    @Override protected void onDestroy(){closeReader();worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    private void bluetooth(){startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private TextView button(String value,Runnable action){TextView v=text(value,14,0xFFE2EFFF);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(16),dp(10),dp(16),dp(10));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF294B66);bg.setCornerRadius(dp(24));v.setBackground(bg);v.setOnClickListener(view->action.run());return v;}
    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);v.setPadding(dp(20),dp(20),dp(20),dp(20));GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF203D55,0xFF102235});bg.setCornerRadius(dp(36));v.setBackground(bg);return v;}
}
