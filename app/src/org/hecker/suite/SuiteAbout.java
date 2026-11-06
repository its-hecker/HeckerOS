// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemProperties;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Maintainer, build identity, community and credits for HeckerOS. */
final class SuiteAbout {
    private SuiteAbout() {}

    static void populate(Activity activity, LinearLayout page) {
        LinearLayout profile = Ui.card(activity);
        profile.setPadding(Ui.dp(activity,24),Ui.dp(activity,24),Ui.dp(activity,24),Ui.dp(activity,24));
        android.graphics.drawable.GradientDrawable hero = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xff283658,0xff28223e,0xff18243b});
        hero.setCornerRadius(Ui.dp(activity,28));
        hero.setStroke(Ui.dp(activity,1),0x666b8ac4); profile.setBackground(hero);
        LinearLayout identity = new LinearLayout(activity); identity.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout title = new LinearLayout(activity); title.setOrientation(LinearLayout.VERTICAL);
        TextView brand=Ui.text(activity,"HeckerOS",24,true);brand.setTextColor(0xfff4f7ff);title.addView(brand);
        TextView version=Ui.text(activity,SystemProperties.get("ro.heckeros.version", "1.5"),48,true);
        version.setTextColor(0xfff4f7ff);title.addView(version);
        identity.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.ImageView mascot = new android.widget.ImageView(activity);
        mascot.setImageResource(R.drawable.hecker_mascot);mascot.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        mascot.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (activity.getResources().getConfiguration().screenWidthDp >= 360
                && activity.getResources().getConfiguration().fontScale <= 1.3f)
            identity.addView(mascot,new LinearLayout.LayoutParams(Ui.dp(activity,112),Ui.dp(activity,128)));
        profile.addView(identity);
        TextView maintainer=Ui.text(activity,activity.getString(R.string.about_maintainer),15,true);
        maintainer.setTextColor(0xffdce5ff);profile.addView(maintainer);
        String type = SystemProperties.get("ro.heckeros.build_type", "");
        int label = "OFFICIAL".equals(type) ? R.string.about_build_official
                : "UNOFFICIAL".equals(type) ? R.string.about_build_unofficial : R.string.about_build_unspecified;
        TextView badge=Ui.text(activity,activity.getString(label),12,true);
        badge.setTextColor(0xffa9cdff);profile.addView(badge);page.addView(profile);
        LinearLayout device=Ui.card(activity);
        detail(activity,device,R.string.about_device,android.os.Build.MODEL);
        detail(activity,device,R.string.about_android,android.os.Build.VERSION.RELEASE);
        detail(activity,device,R.string.about_security_patch,android.os.Build.VERSION.SECURITY_PATCH);
        page.addView(device);
        page.addView(Ui.text(activity,activity.getString(R.string.about_community),18,true));
        Ui.actionCard(activity, page, R.string.about_channel, R.string.about_channel_summary,
                v -> open(activity, "https://t.me/pixel4seriesupdates"));
        Ui.actionCard(activity, page, R.string.about_group, R.string.about_group_summary,
                v -> open(activity, "https://t.me/Pixel4Roms"));
        Ui.actionCard(activity, page, R.string.about_source, R.string.about_source_summary,
                v -> open(activity, "https://github.com/its-hecker/HeckerOS"));
        // Keep the default page compact; reveal the exact original donation
        // details only when requested, with the existing selectable/copy actions.
        android.widget.Button support=Ui.button(activity);support.setText(R.string.about_donate);
        LinearLayout donations=new LinearLayout(activity);donations.setOrientation(LinearLayout.VERTICAL);
        donations.setVisibility(android.view.View.GONE);
        donations.addView(Ui.text(activity,activity.getString(R.string.about_donate_summary),14,false));
        address(activity,donations,R.string.about_binance,R.string.about_binance_id);
        address(activity,donations,R.string.about_usdt,R.string.about_usdt_address);
        support.setOnClickListener(v -> donations.setVisibility(donations.getVisibility()==android.view.View.VISIBLE
                ? android.view.View.GONE : android.view.View.VISIBLE));
        page.addView(support);page.addView(donations);
        LinearLayout credits = Ui.card(activity);
        credits.addView(Ui.text(activity, activity.getString(R.string.about_credits), 20, true));
        credits.addView(Ui.text(activity, activity.getString(R.string.about_credits_body), 14, false));
        page.addView(credits);
    }

    private static void detail(Activity activity,LinearLayout page,int label,String value) {
        LinearLayout row=new LinearLayout(activity);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(activity,activity.getString(label),13,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView text=Ui.text(activity,value==null||value.isEmpty()?"—":value,14,true);
        text.setGravity(android.view.Gravity.END);text.setTextIsSelectable(true);
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));page.addView(row);
    }

    private static void address(Activity activity, LinearLayout page, int title, int value) {
        LinearLayout card = Ui.card(activity);
        card.addView(Ui.text(activity, activity.getString(title), 19, true));
        TextView address = Ui.text(activity, activity.getString(value), 15, false);
        address.setTextIsSelectable(true);
        card.addView(address);
        android.widget.Button copy = Ui.button(activity);
        copy.setText(R.string.about_copy);
        copy.setContentDescription(activity.getString(R.string.about_copy_address, activity.getString(title)));
        copy.setOnClickListener(v -> {
            activity.getSystemService(ClipboardManager.class).setPrimaryClip(
                    ClipData.newPlainText(activity.getString(title), activity.getString(value)));
        });
        card.addView(copy);
        page.addView(card);
    }

    private static void open(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(activity, R.string.about_link_unavailable, Toast.LENGTH_LONG).show();
        }
    }
}
