// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.ImageView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.android.settings.R;

/** Presentation only: native controllers, restriction badges and destinations stay in charge. */
public final class HeckerHomepageStyle {
    private HeckerHomepageStyle() {}

    public static void bind(PreferenceViewHolder holder, Preference preference) {
        View summary = holder.findViewById(android.R.id.summary);
        if (summary != null) summary.setVisibility(View.GONE);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        View frame = holder.findViewById(R.id.icon_frame);
        ImageView icon = (ImageView) holder.findViewById(android.R.id.icon);
        if (frame == null || icon == null) return;
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(color(preference.getKey()));
        frame.setBackground(disc);
        Drawable glyph = preference.getIcon();
        if (glyph instanceof AdaptiveIconDrawable) {
            glyph = ((AdaptiveIconDrawable) glyph).getForeground();
        } else if (glyph instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) glyph;
            glyph = layers.getDrawable(layers.getNumberOfLayers() - 1);
        }
        if (glyph != null) {
            Drawable.ConstantState state = glyph.getConstantState();
            if (state != null) glyph = state.newDrawable(preference.getContext().getResources());
            glyph = glyph.mutate();
            glyph.setTint(Color.WHITE);
            icon.setImageDrawable(glyph);
        }
    }

    private static int color(String key) {
        if (key == null) return 0xff3864ff;
        switch (key) {
            case "top_level_notifications": case "top_level_emergency": return 0xffff612b;
            case "top_level_battery": case "top_level_accessibility": return 0xff86ce00;
            case "top_level_display": return 0xffb9cf00;
            case "top_level_wallpaper": return 0xfff5589a;
            case "top_level_sound": case "top_level_priority_modes": return 0xff7951ff;
            case "top_level_safety_center": case "top_level_security":
            case "top_level_privacy": case "top_level_location": return 0xff6a46ff;
            case "top_level_storage": case "top_level_system": return 0xff8c7ae0;
            case "top_level_support": return 0xffebbd27;
            default: return 0xff3864ff;
        }
    }
}
