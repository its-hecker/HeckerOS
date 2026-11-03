// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.app.*;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared native Studio surfaces and accessible controls; no second preference store. */
final class HeckerStudioUi {
    static final int TEXT = 0xffeff4fa, MUTED = 0xffa4b5c8, ACCENT = 0xffa0e8d7, SURFACE = 0xff17222f;
    static int dp(Context c, int value) { return HeckerStudio.dp(c, value); }
    static LinearLayout card(Context c) {
        LinearLayout card = HeckerStudio.column(c);
        card.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        card.setBackground(HeckerStudio.panel(SURFACE, dp(c, 24), 0xff2c3b4c));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(c, 10); card.setLayoutParams(params); return card;
    }
    static TextView label(Context c, String title, int size, int color) {
        TextView text = HeckerStudio.label(c, title, size); text.setTextColor(color);
        if (size >= 16) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }
    static View toolbar(Activity activity, String title) {
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(activity); back.setText("‹"); back.setTextSize(32); back.setTextColor(TEXT);
        back.setMinWidth(0); back.setMinimumWidth(0); back.setPadding(0, 0, 0, 0);
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4086cbbb),
                HeckerStudio.panel(0xff1b2936, dp(activity, 20), 0xff314252), null));
        back.setContentDescription(HeckerStudio.text(activity, "back")); back.setOnClickListener(v -> activity.finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        TextView name = label(activity, title, 26, TEXT); name.setPadding(dp(activity, 14), dp(activity, 8), 0, dp(activity, 8));
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.setPadding(0, dp(activity, 4), 0, dp(activity, 20)); return row;
    }
    static Switch toggle(Context c, LinearLayout parent, String title, boolean checked,
            CompoundButton.OnCheckedChangeListener changed) {
        LinearLayout row = card(c); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = label(c, title, 16, TEXT);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(c); toggle.setText(""); toggle.setShowText(false); toggle.setMinHeight(dp(c, 48));
        toggle.setContentDescription(title); toggle.setSwitchMinWidth(dp(c, 52));
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, 0xffa1adbb}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{0xff366b62, 0xff354252}));
        toggle.setChecked(checked); toggle.setOnCheckedChangeListener(changed);
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(v -> toggle.performClick()); parent.addView(row); return toggle;
    }
    static void section(Context c, LinearLayout parent, String title) {
        TextView header = label(c, title, 13, ACCENT); header.setLetterSpacing(.08f);
        header.setPadding(dp(c, 6), dp(c, 20), dp(c, 6), dp(c, 10)); parent.addView(header);
    }
    static AlertDialog show(AlertDialog dialog) {
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(
                HeckerStudio.panel(0xff1b2938, dp(dialog.getContext(), 28), 0xff3a5065));
        ListView list = dialog.getListView();
        if (list != null) { list.setPadding(12, 12, 12, 20); list.setDividerHeight(0); }
        return dialog;
    }
    private HeckerStudioUi() { }
}
