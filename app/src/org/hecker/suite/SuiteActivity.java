// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class SuiteActivity extends Activity {
    private SuiteAnimation animation;
    private LinearLayout content;
    private Button customizations, about;
    private boolean showingAbout;
    private int message;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null && "themes".equals(getIntent().getStringExtra("org.hecker.suite.PAGE"))) {
            open("StatusBarSettingsActivity"); finish(); return;
        }
        showingAbout = state != null ? state.getBoolean("about")
                : getIntent().getBooleanExtra("org.hecker.suite.SHOW_ABOUT", false);
        message = state == null ? SuiteGreetings.next(this) : state.getInt("message");
        LinearLayout page = Ui.page(this, R.string.suite_title);
        LinearLayout hero = Ui.card(this);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView greeting = Ui.text(this, SuiteGreetings.text(this, message), 23, true); copy.addView(greeting);
        copy.addView(Ui.text(this, getString(R.string.suite_hero_summary), 12, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView mascot = new ImageView(this); mascot.setImageResource(R.drawable.hecker_mascot);
        mascot.setScaleType(ImageView.ScaleType.CENTER_CROP); mascot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(mascot, new LinearLayout.LayoutParams(Ui.dp(this, 116), Ui.dp(this, 132))); hero.addView(row);
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout orbit = new LinearLayout(this); animation = new SuiteAnimation(this, orbit, 40); controls.addView(orbit);
        Button shuffle = Ui.button(this); shuffle.setText(R.string.greeting_shuffle); shuffle.setTextSize(11);
        shuffle.setOnClickListener(v -> { message = SuiteGreetings.next(this); greeting.setText(SuiteGreetings.text(this,message)); animation.show(R.raw.suite_orbit,true); });
        controls.addView(shuffle, new LinearLayout.LayoutParams(0,-2,1)); hero.addView(controls); page.addView(hero);
        LinearLayout tabs = new LinearLayout(this);
        customizations = Ui.button(this); customizations.setText(R.string.tab_customizations);
        about = Ui.button(this); about.setText(R.string.tab_about);
        tabs.addView(customizations,new LinearLayout.LayoutParams(0,-2,1)); tabs.addView(about,new LinearLayout.LayoutParams(0,-2,1));
        customizations.setOnClickListener(v->showTab(false)); about.setOnClickListener(v->showTab(true)); page.addView(tabs);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); page.addView(content); showTab(showingAbout);
        Ui.scroll(this,page);
    }
    private void showTab(boolean selectedAbout) {
        showingAbout=selectedAbout; customizations.setSelected(!selectedAbout); about.setSelected(selectedAbout); content.removeAllViews();
        if(selectedAbout){SuiteAbout.populate(this,content);return;}
        addRow(tile(R.string.qs_title,R.string.qs_summary,R.drawable.ic_qs,v->open("QuickSettingsActivity")),
                tile(R.string.lock_screen_title,R.string.lock_screen_summary,R.drawable.ic_lock,v->open("LockScreenSettingsActivity")));
        addRow(tile(R.string.status_bar_title,R.string.status_bar_summary,R.drawable.ic_signal,v->open("StatusBarSettingsActivity")),
                tile(R.string.motion_sense_title,R.string.motion_sense_summary,R.drawable.ic_motion_sense,v->openMotionSense()));
        addRow(tile(R.string.app_lock_title,R.string.app_lock_summary,R.drawable.ic_lock,v->open("AppLockActivity")),
                tile(R.string.gaming_title,R.string.gaming_summary,R.drawable.ic_gaming,v->open("GamingActivity")));
        addRow(tile(R.string.sandbox_title,R.string.sandbox_summary,R.drawable.ic_sandbox,v->open("SandboxActivity")),
                tile(R.string.headphones_title,R.string.headphones_summary,R.drawable.ic_headphones,v->open("HeadphonesActivity")));
        addRow(tile(R.string.ir_title,R.string.ir_summary,R.drawable.ic_face_unlock,v->open("InfraredCameraActivity")),
                tile(R.string.capture_title,R.string.capture_summary,R.drawable.ic_qs,v->open("CaptureSettingsActivity")));
        LinearLayout integrity=Ui.card(this);
        integrity.addView(Ui.text(this,getString(R.string.integrity_title),17,true));
        integrity.addView(Ui.text(this,getString(R.string.integrity_summary),12,false));
        integrity.setFocusable(true);integrity.setClickable(true);
        integrity.setOnClickListener(v->openIntegrity());content.addView(integrity);
        LinearLayout footer=Ui.card(this); footer.addView(Ui.text(this,"HeckerOS",17,true));
        footer.addView(Ui.text(this,getString(R.string.suite_footer),12,false));content.addView(footer);
    }
    private LinearLayout tile(int title,int summary,int icon,View.OnClickListener click) {
        LinearLayout card=Ui.card(this);card.setMinimumHeight(Ui.dp(this,154));
        ImageView image=new ImageView(this);image.setImageResource(icon);image.setImageTintList(ColorStateList.valueOf(getColor(R.color.hecker_accent)));
        GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(getColor(R.color.hecker_icon_background));
        image.setBackground(circle);int pad=Ui.dp(this,10);image.setPadding(pad,pad,pad,pad);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(image,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
        card.addView(Ui.text(this,getString(title),15,true));card.addView(Ui.text(this,getString(summary),11,false));
        card.setFocusable(true);card.setClickable(true);card.setOnClickListener(click);
        android.util.TypedValue selectable=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,selectable,true);
        if(selectable.resourceId!=0)card.setForeground(getDrawable(selectable.resourceId));return card;
    }
    private void addRow(LinearLayout left,LinearLayout right) {
        LinearLayout row=new LinearLayout(this);row.setBaselineAligned(false);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-1,1),b=new LinearLayout.LayoutParams(0,-1,1);
        a.setMarginEnd(Ui.dp(this,5));b.setMarginStart(Ui.dp(this,5));a.bottomMargin=b.bottomMargin=Ui.dp(this,10);
        row.addView(left,a);row.addView(right,b);content.addView(row);
    }
    private void open(String page) {
        try {
            startActivity(new Intent().setClassName("org.hecker.suite", "org.hecker.suite.SuiteRouterActivity")
                    .putExtra("org.hecker.suite.PAGE", page)
                    .putExtra("category", getIntent().getIntExtra("category", -1)));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.native_page_unavailable, Toast.LENGTH_LONG).show();
        }
    }
    private void openIntegrity(){
        try{startActivity(new Intent().setClassName("com.android.settings","com.android.settings.hecker.HeckerIntegrityActivity"));}
        catch(ActivityNotFoundException|SecurityException unavailable){Toast.makeText(this,R.string.native_page_unavailable,Toast.LENGTH_LONG).show();}
    }
    private void openFace(){settings("android.settings.FACE_SETTINGS");}
    private void settings(String action) {
        try { startActivity(new Intent(action).setPackage("com.android.settings")); }
        catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.native_page_unavailable, Toast.LENGTH_LONG).show();
        }
    }
    private void openMotionSense(){
        try{startActivity(new Intent().setClassName("com.android.settings","org.hecker.floral.motionsense.motionsense.MotionSenseActivity"));}
        catch(ActivityNotFoundException|SecurityException unavailable){Toast.makeText(this,R.string.motion_sense_unavailable,Toast.LENGTH_LONG).show();}
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("about",showingAbout);state.putInt("message",message);super.onSaveInstanceState(state);}
    @Override protected void onStart(){super.onStart();if(animation!=null)animation.show(R.raw.suite_orbit,true);}
    @Override protected void onStop(){if(animation!=null)animation.stop();super.onStop();}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
}
