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

/** The single maintainer, community and donation page for the experimental ROM. */
final class SuiteAbout {
    private SuiteAbout() {}

    static void populate(Activity activity, LinearLayout page) {
        LinearLayout profile = Ui.card(activity);
        profile.addView(Ui.text(activity, "Hecker", 30, true));
        profile.addView(Ui.text(activity, activity.getString(R.string.about_hecker), 16, false));
        page.addView(profile);
        Ui.actionCard(activity, page, R.string.about_channel, R.string.about_channel_summary,
                v -> open(activity, "https://t.me/pixel4seriesupdates"));
        Ui.actionCard(activity, page, R.string.about_group, R.string.about_group_summary,
                v -> open(activity, "https://t.me/Pixel4Roms"));
        Ui.actionCard(activity, page, R.string.about_source, R.string.about_source_summary,
                v -> open(activity, "https://github.com/its-hecker/HeckerOS"));
        page.addView(Ui.text(activity, activity.getString(R.string.about_donate), 22, true));
        page.addView(Ui.text(activity, activity.getString(R.string.about_donate_summary), 14, false));
        address(activity, page, R.string.about_binance, R.string.about_binance_id);
        address(activity, page, R.string.about_usdt, R.string.about_usdt_address);
        LinearLayout credits = Ui.card(activity);
        credits.addView(Ui.text(activity, activity.getString(R.string.about_credits), 20, true));
        credits.addView(Ui.text(activity, activity.getString(R.string.about_credits_body), 14, false));
        page.addView(credits);
    }

    private static void address(Activity activity, LinearLayout page, int title, int value) {
        LinearLayout card = Ui.card(activity);
        card.addView(Ui.text(activity, activity.getString(title), 19, true));
        TextView address = Ui.text(activity, activity.getString(value), 15, false);
        address.setTextIsSelectable(true);
        card.addView(address);
        android.widget.Button copy = new android.widget.Button(activity);
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
