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
            activity.getActionBar().hide();
        }
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 20);
        page.setPadding(padding, padding, padding, padding);
        return page;
    }

    static void scroll(Activity activity, LinearLayout page) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(page);
        content(activity, scroll);
    }

    static void content(Activity activity, View body) {
        // A measured toolbar and scroll body are siblings. An overlay ActionBar
        // cannot cover the first setting, even with edge-to-edge or large fonts.
        activity.getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(activity.getColor(R.color.hecker_background));
        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolbar.setMinimumHeight(dp(activity, 56));
        toolbar.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 20), dp(activity, 4));
        android.widget.ImageButton back = new android.widget.ImageButton(activity);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.hecker_foreground)));
        back.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        TypedValue ripple = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        if (ripple.resourceId != 0) back.setBackgroundResource(ripple.resourceId);
        back.setContentDescription(activity.getString(R.string.navigate_back));
        back.setOnClickListener(v -> activity.finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        TextView title = text(activity, activity.getTitle(), 22, true);
        title.setAccessibilityHeading(true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(toolbar);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return android.view.WindowInsets.CONSUMED;
        });
        activity.setContentView(root);
        root.post(root::requestApplyInsets);
    }

    static void hero(Activity activity, LinearLayout page, int animation, int summary) {
        LinearLayout card = card(activity);
        SuiteAnimation art = new SuiteAnimation(activity, card);
        art.show(animation, true);
        card.addView(text(activity, activity.getString(summary), 15, false));
        page.addView(card);
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
