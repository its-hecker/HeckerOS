// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

/** Actual system emoji selection; absent font packs cannot be selected. */
public final class EmojiStylesActivity extends Activity {
    private static final String KEY = "persist.sys.ax_emoji_style";
    private static final String SAMPLE = "😀😍🎉❤️👍";
    private static final String[] VALUES = {"android", "ios", "samsung", "swiftui", "facebook"};
    private static final String[] FONTS = {
        "/system/fonts/NotoColorEmoji.ttf", "/product/fonts/LunarisIosEmoji.ttf",
        "/product/fonts/SamsungColorEmoji.ttf", "/product/fonts/SwiftUIEmoji.ttf",
        "/product/fonts/FacebookEmoji.ttf"
    };
    private static final int[] TITLES = {
        R.string.emoji_styles_stock, R.string.emoji_styles_ios,
        R.string.emoji_styles_samsung, R.string.emoji_styles_swiftui,
        R.string.emoji_styles_facebook
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        populate();
    }

    private void populate() {
        LinearLayout page = Ui.page(this, R.string.emoji_styles_title);
        page.addView(Ui.text(this, getString(R.string.emoji_styles_tip), 14, false));
        final String current = SystemProperties.get(KEY, "android");
        for (int i = 0; i < VALUES.length; ++i) {
            final int index = i;
            final boolean stock = i == 0;
            final File font = new File(FONTS[i]);
            boolean available = stock || (font.isFile() &&
                    new File("/product/etc/fonts_customization_emoji_" + VALUES[i] + ".xml")
                            .isFile());
            boolean selected = VALUES[i].equals(current);
            LinearLayout card = Ui.card(this);
            LinearLayout title = new LinearLayout(this);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.addView(Ui.text(this, getString(TITLES[i]), 17, true),
                    new LinearLayout.LayoutParams(0, -2, 1));
            if (selected) title.addView(Ui.text(this,
                    getString(R.string.emoji_styles_selected) + " ✓", 12, false));
            card.addView(title);
            TextView preview = Ui.text(this, SAMPLE, 25, false);
            if (font.isFile()) {
                try {
                    Typeface face = new Typeface.Builder(font).build();
                    if (face != null) preview.setTypeface(face);
                } catch (RuntimeException badFont) {
                    if (!stock) available = false;
                }
            } else if (!stock) {
                preview.setText(R.string.emoji_styles_preview_unavailable);
                preview.setTextSize(13);
            }
            if (!stock && !available) {
                preview.setText(R.string.emoji_styles_preview_unavailable);
                preview.setTextSize(13);
            }
            card.addView(preview);
            if (!available) {
                card.addView(Ui.text(this, getString(R.string.emoji_styles_missing), 12, false));
            } else if (stock) {
                card.addView(Ui.text(this, getString(R.string.emoji_styles_stock_summary), 12, false));
            }
            if (selected) {
                GradientDrawable border = new GradientDrawable();
                border.setColor(getColor(R.color.hecker_button));
                border.setCornerRadius(Ui.dp(this, 24));
                border.setStroke(Ui.dp(this, 2), getColor(R.color.hecker_accent));
                card.setBackground(border);
            }
            card.setEnabled(available);
            card.setAlpha(available ? 1f : 0.5f);
            card.setFocusable(available && !selected);
            card.setClickable(available && !selected);
            card.setContentDescription(getString(TITLES[i])
                    + (selected ? ", " + getString(R.string.emoji_styles_selected) : "")
                    + (!available ? ", " + getString(R.string.emoji_styles_missing) : ""));
            if (available && !selected) {
                card.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle(R.string.emoji_styles_select)
                        .setMessage(getString(TITLES[index]))
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> apply(VALUES[index]))
                        .setNegativeButton(android.R.string.cancel, null).show());
            }
            page.addView(card);
        }
        Ui.scroll(this, page);
    }

    private void apply(String style) {
        try {
            SystemProperties.set(KEY, style);
            if (!style.equals(SystemProperties.get(KEY, "android"))) {
                throw new SecurityException("Property update was not persisted");
            }
        } catch (RuntimeException denied) {
            android.util.Log.e("HeckerEmoji", "Could not persist emoji style", denied);
            Toast.makeText(this, R.string.emoji_styles_failed, Toast.LENGTH_LONG).show();
            return;
        }
        populate();
        new AlertDialog.Builder(this).setTitle(R.string.emoji_styles_pending)
                .setMessage(R.string.emoji_styles_reboot_question)
                .setPositiveButton(R.string.emoji_styles_reboot, (dialog, which) -> {
                    try {
                        getSystemService(PowerManager.class).reboot(null);
                    } catch (RuntimeException denied) {
                        Toast.makeText(this, R.string.emoji_styles_failed, Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton(R.string.emoji_styles_later, null).show();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
