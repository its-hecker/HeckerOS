// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Device information, community and credits below HeckerLab's shared header. */
final class SuiteAbout {
    private SuiteAbout() {}

    static void populate(Activity activity, LinearLayout page) {
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
