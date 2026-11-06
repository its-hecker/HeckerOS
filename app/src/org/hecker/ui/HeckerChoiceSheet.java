// SPDX-License-Identifier: Apache-2.0
package org.hecker.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Native Hecker menus, tinted by the host's theme. */
public final class HeckerChoiceSheet {
    private static final class Multiple {
        final boolean[] checked;
        final CharSequence confirm;
        final Predicate<boolean[]> apply;

        Multiple(boolean[] checked, CharSequence confirm, Predicate<boolean[]> apply) {
            this.checked = checked.clone();
            this.confirm = confirm;
            this.apply = apply;
        }
    }
    private HeckerChoiceSheet() {}

    public static void show(Context context, CharSequence title, CharSequence[] choices, IntConsumer chosen) {
        show(context, title, choices, -1, chosen);
    }

    public static void show(Context context, CharSequence title, CharSequence[] choices,
            int selected, IntConsumer chosen) {
        show(context, title, choices, selected, chosen, null);
    }

    public static void showMultiple(Context context, CharSequence title, CharSequence[] choices,
            boolean[] selected, CharSequence confirm, Predicate<boolean[]> apply) {
        if (choices.length != selected.length) throw new IllegalArgumentException("Choice count differs");
        show(context, title, choices, -1, null, new Multiple(selected, confirm, apply));
    }

    private static void show(Context context, CharSequence title, CharSequence[] choices,
            int selected, IntConsumer chosen, Multiple multiple) {
        int surface;
        int foreground;
        int accent;
        TypedArray colors = context.obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground, android.R.attr.textColorPrimary, android.R.attr.colorAccent
        });
        try {
            surface = colors.getColor(0, 0xff12191f);
            foreground = colors.getColor(1, 0xffedf1f4);
            accent = colors.getColor(2, 0xffa8cfe3);
        } finally {
            colors.recycle();
        }
        int card = blend(surface, foreground, .09f);
        int active = blend(surface, accent, .22f);
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 16));
        panel.setBackground(shape(surface, dp(context, 28)));

        View handle = new View(context);
        handle.setBackground(shape(blend(surface, foreground, .25f), dp(context, 2)));
        LinearLayout.LayoutParams handleSize = new LinearLayout.LayoutParams(dp(context, 32), dp(context, 4));
        handleSize.gravity = Gravity.CENTER_HORIZONTAL;
        handleSize.bottomMargin = dp(context, 16);
        panel.addView(handle, handleSize);
        if (title != null && title.length() != 0) {
            TextView heading = new TextView(context);
            heading.setText(title);
            heading.setTextColor(foreground);
            heading.setTextSize(20);
            heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            heading.setPadding(dp(context, 6), 0, dp(context, 6), dp(context, 12));
            heading.setAccessibilityHeading(true);
            panel.addView(heading);
        }

        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int width, int height) {
                int limit = (int) (getResources().getDisplayMetrics().heightPixels * .57f);
                if (MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED) {
                    limit = Math.min(limit, MeasureSpec.getSize(height));
                }
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
            }
        };
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < choices.length; i++) {
            int index = i;
            Button button;
            if (multiple == null) {
                boolean checked = i == selected;
                button = new Button(context);
                styleButton(context, button, choices[i], checked ? accent : foreground, checked ? active : card);
                button.setSelected(checked);
                button.setOnClickListener(view -> {
                    dialog.dismiss();
                    chosen.accept(index);
                });
            } else {
                CheckBox check = new CheckBox(context);
                styleButton(context, check, choices[i], foreground, card);
                check.setButtonTintList(ColorStateList.valueOf(accent));
                check.setChecked(multiple.checked[index]);
                check.setOnCheckedChangeListener((view, checked) -> multiple.checked[index] = checked);
                button = check;
            }
            LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowSize.bottomMargin = dp(context, 6);
            rows.addView(button, rowSize);
        }
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (multiple != null) {
            Button confirm = new Button(context);
            styleButton(context, confirm, multiple.confirm, surface, accent);
            confirm.setGravity(Gravity.CENTER);
            confirm.setOnClickListener(view -> {
                if (multiple.apply.test(multiple.checked.clone())) dialog.dismiss();
            });
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            size.topMargin = dp(context, 8);
            panel.addView(confirm, size);
        }
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.45f);
        dialog.show();
        int width = Math.min(context.getResources().getDisplayMetrics().widthPixels, dp(context, 600));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static void styleButton(Context context, Button button, CharSequence text, int foreground, int fill) {
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(foreground);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPaddingRelative(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
        button.setMinHeight(dp(context, 52));
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337f9aaa),
                shape(fill, dp(context, 16)), null));
        button.setFilterTouchesWhenObscured(true);
    }

    private static GradientDrawable shape(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    private static int blend(int background, int foreground, float amount) {
        return Color.rgb(
                Math.round(Color.red(background) * (1 - amount) + Color.red(foreground) * amount),
                Math.round(Color.green(background) * (1 - amount) + Color.green(foreground) * amount),
                Math.round(Color.blue(background) * (1 - amount) + Color.blue(foreground) * amount));
    }
}
