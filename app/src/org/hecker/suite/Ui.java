// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
    private Ui() {}

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static LinearLayout page(Activity activity, int title) {
        activity.setTitle(title);
        if (activity.getActionBar() != null) {
            activity.getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 20);
        page.setPadding(padding, padding, padding, padding);
        // Android 17 enforces edge-to-edge. Consume system bars around the content.
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(padding + bars.left, padding,
                    padding + bars.right, padding + bars.bottom);
            return insets;
        });
        return page;
    }

    static void scroll(Activity activity, LinearLayout page) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(page);
        activity.setContentView(scroll);
    }

    static TextView text(Activity activity, CharSequence text, int sp, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sp);
        // The dashboard also runs inside Settings, whose theme can supply dark
        // default TextView colors on our dark cards. Always use our own palette.
        view.setTextColor(activity.getColor(bold || sp >= 17
                ? R.color.hecker_foreground : R.color.hecker_secondary));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
        return view;
    }

    static Button button(Activity activity) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinHeight(dp(activity, 48));
        button.setMinimumHeight(dp(activity, 48));
        button.setPadding(dp(activity, 16), dp(activity, 10),
                dp(activity, 16), dp(activity, 10));
        int[][] states = { {-android.R.attr.state_enabled},
                {android.R.attr.state_selected}, {} };
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(activity, 18));
        shape.setColor(activity.getColor(R.color.hecker_button));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x337DAAFF), shape, null));
        button.setBackgroundTintList(new ColorStateList(states, new int[] {
                activity.getColor(R.color.hecker_border),
                activity.getColor(R.color.hecker_accent),
                activity.getColor(R.color.hecker_button) }));
        button.setTextColor(new ColorStateList(states, new int[] {
                activity.getColor(R.color.hecker_secondary),
                activity.getColor(R.color.hecker_on_accent),
                activity.getColor(R.color.hecker_foreground) }));
        return button;
    }

    static LinearLayout card(Activity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        TypedValue value = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.colorBackgroundFloating, value, true);
        background.setColor(activity.getColor(R.color.hecker_card));
        background.setStroke(dp(activity, 1), activity.getColor(R.color.hecker_border));
        background.setCornerRadius(dp(activity, 24));
        card.setBackground(background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(activity, 16);
        card.setLayoutParams(lp);
        return card;
    }

    static void actionCard(Activity activity, LinearLayout page, int title, int summary,
            View.OnClickListener action) {
        LinearLayout card = card(activity);
        TextView titleView = text(activity, activity.getString(title), 22, true);
        TextView summaryView = text(activity, activity.getString(summary), 15, false);
        // Theme-supplied TextView interaction must not consume the card's tap.
        for (TextView label : new TextView[] { titleView, summaryView }) {
            label.setClickable(false);
            label.setLongClickable(false);
            label.setFocusable(false);
        }
        card.addView(titleView);
        card.addView(summaryView);
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        TypedValue selectable = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                selectable, true);
        card.setForeground(activity.getDrawable(selectable.resourceId));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(action);
        page.addView(card);
    }
}
