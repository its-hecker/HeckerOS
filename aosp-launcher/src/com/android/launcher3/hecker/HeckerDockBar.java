// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.Context;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Always-available native search surface in the hotseat's existing reserved QSB slot. */
public final class HeckerDockBar extends LinearLayout {
    public HeckerDockBar(Context context) {
        super(context); setGravity(Gravity.CENTER_VERTICAL);
        int pad = HeckerStudio.dp(context, 18); setPadding(pad, 0, pad, 0);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xd52c394b, 0xd51c2938});
        glass.setCornerRadius(HeckerStudio.dp(context, 28));
        glass.setStroke(HeckerStudio.dp(context, 1), 0x6087b7da);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x408dbdff), glass, null));
        TextView mark = HeckerStudio.label(context, "⌕", 29); mark.setTextColor(0xff92dfea);
        mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(mark);
        TextView label = HeckerStudio.label(context, HeckerStudio.text(context, "search_hint"), 14);
        label.setSingleLine(); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(HeckerStudio.dp(context, 12), 0, 0, 0);
        addView(label, new LayoutParams(0, -2, 1));
        TextView detail = HeckerStudio.label(context, "✦", 18); detail.setTextColor(0xffc8b3ff);
        detail.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(detail);
        setFocusable(true); setContentDescription(HeckerStudio.text(context, "search_hint"));
        setOnClickListener(view -> HeckerStudio.search(context));
        setOnLongClickListener(view -> { HeckerStudio.open(context, "root"); return true; });
        int id = HeckerStudio.res(context, "id", "search_container_hotseat");
        if (id != 0) setId(id);
    }
}
