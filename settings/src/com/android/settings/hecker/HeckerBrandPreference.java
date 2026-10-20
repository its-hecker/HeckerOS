// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.android.settings.R;

/** Decorative branding around the native Settings pages. Device summaries remain controller-owned. */
public final class HeckerBrandPreference extends Preference {
    private final boolean maintainer, display;
    public HeckerBrandPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        maintainer = "hecker_maintainer".equals(getKey());
        display = "hecker_display_header".equals(getKey());
        setLayoutResource(maintainer ? R.layout.hecker_maintainer_preference : R.layout.hecker_brand_preference);
        setSelectable(!display);
        setOnPreferenceClickListener(preference -> {
            if (maintainer) suite(true);
            else if (!display) context.startActivity(new Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS));
            return true;
        });
    }
    private void suite(boolean about) {
        Intent intent = new Intent().setClassName("com.android.settings", "org.hecker.suite.SuiteActivity")
                .putExtra("org.hecker.suite.SHOW_ABOUT", about);
        try { getContext().startActivity(intent); }
        catch (android.content.ActivityNotFoundException unavailable) {
            android.widget.Toast.makeText(getContext(), R.string.hecker_suite_unavailable, android.widget.Toast.LENGTH_LONG).show();
        }
    }
    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false); holder.setDividerAllowedBelow(false);
        holder.itemView.setClipToOutline(true);
        if (maintainer) {
            holder.findViewById(R.id.hecker_suite_button).setOnClickListener(v -> suite(false));
            return;
        }
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        title.setVisibility(android.view.View.VISIBLE); summary.setVisibility(android.view.View.VISIBLE);
        ImageView art = (ImageView) holder.findViewById(R.id.hecker_hero_art);
        if (display) {
            title.setText(R.string.hecker_display_hero_title); summary.setText(R.string.hecker_display_hero_summary);
            art.setImageResource(R.drawable.hecker_phone_art);
            holder.findViewById(R.id.hecker_android_chip).setVisibility(android.view.View.GONE);
        } else {
            SpannableString label = new SpannableString("HeckerOS");
            label.setSpan(new ForegroundColorSpan(getContext().getColor(R.color.hecker_accent)), 6, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setText(label); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            summary.setText(R.string.hecker_brand_tagline); art.setImageResource(R.drawable.hecker_mascot);
        }
    }
}
