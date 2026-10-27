/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import com.android.systemui.res.R;

/** Small lock-screen signature; carrier information remains available in QS. */
public final class HeckerKeyguardBrand {
    public static void bind(View root) {
        TextView label = root.findViewById(R.id.keyguard_carrier_text);
        if (label == null) return;
        label.setText(R.string.hecker_lockscreen_motto);
        label.setSingleLine(true); label.setTextSize(11);
        Drawable mark = root.getContext().getDrawable(R.drawable.hecker_caracal_mark).mutate();
        int size = Math.round(15 * root.getResources().getDisplayMetrics().density);
        mark.setBounds(0, 0, size, size);
        label.setCompoundDrawablesRelative(mark, null, null, null);
        label.setCompoundDrawablePadding(Math.round(size / 3f));
        label.setCompoundDrawableTintList(ColorStateList.valueOf(label.getCurrentTextColor()));
    }
}
