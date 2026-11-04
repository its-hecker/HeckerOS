/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: GPL-3.0-or-later */
package org.hecker.suite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.drdisagree.xmorphclock.engine.ClockFaceView;
import com.drdisagree.xmorphclock.nativehost.XMorphNative;
import com.drdisagree.xmorphclock.nativehost.XMorphWallpaper;
import java.util.Collections;
import java.util.List;

/** Native, recycled clock gallery. No module manager, root grant or external clock APK. */
public final class XMorphClockActivity extends Activity {
    private List<XMorphNative.Entry> entries = Collections.emptyList();
    private Bitmap backdrop;
    private final Gallery adapter = new Gallery();
    private int preview;
    private String selected;
    private boolean released;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) { getActionBar().setTitle(R.string.xmorph_title); getActionBar().setDisplayHomeAsUpEnabled(true); }
        LinearLayout page = Ui.page(this, R.string.xmorph_title);
        page.addView(Ui.text(this, getString(R.string.xmorph_summary), 14, false));
        page.addView(Ui.text(this, getString(R.string.xmorph_credit), 12, false));
        RadioGroup modes = new RadioGroup(this); modes.setOrientation(LinearLayout.HORIZONTAL);
        int[] labels = {R.string.xmorph_large, R.string.xmorph_small, R.string.xmorph_aod};
        for (int i = 0; i < labels.length; i++) {
            RadioButton mode = new RadioButton(this); mode.setId(View.generateViewId()); mode.setTag(i);
            mode.setText(labels[i]); mode.setTextColor(getColor(R.color.hecker_foreground)); modes.addView(mode);
            if (i == 0) modes.check(mode.getId());
        }
        modes.setOnCheckedChangeListener((group, id) -> {
            View child = group.findViewById(id); if (child != null) { preview = (Integer) child.getTag(); adapter.notifyDataSetChanged(); }
        }); page.addView(modes);
        ListView list = new ListView(this); list.setDivider(null); list.setAdapter(adapter);
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        selected = Settings.Secure.getString(getContentResolver(), XMorphNative.KEY);
        AppearanceBootReceiver.WORKER.execute(() -> {
            try {
                List<XMorphNative.Entry> faces = XMorphNative.entries(this);
                Bitmap wall = XMorphWallpaper.load(this, getApplicationInfo().uid / 100000);
                runOnUiThread(() -> { if (!released) { entries = faces; backdrop = wall; adapter.notifyDataSetChanged(); } });
            } catch (Exception | OutOfMemoryError error) {
                android.util.Log.w("HeckerXMorph", "Cannot load native clock gallery", error);
                runOnUiThread(() -> { if (!released) Toast.makeText(this, R.string.xmorph_failed, Toast.LENGTH_LONG).show(); });
            }
        });
    }

    @Override public boolean onNavigateUp() { finish(); return true; }
    @Override protected void onDestroy() { released = true; super.onDestroy(); }

    private void apply(XMorphNative.Entry entry) {
        boolean saved = Settings.Secure.putString(getContentResolver(), XMorphNative.KEY, entry.getId())
            && Settings.Secure.putInt(getContentResolver(), "hecker_lockscreen_clock_style", XMorphNative.CLOCK_STYLE)
            && Settings.Secure.putInt(getContentResolver(), "hecker_custom_lockscreen_clock", 1);
        if (saved) { selected = entry.getId(); adapter.notifyDataSetChanged(); }
        Toast.makeText(this, saved ? R.string.lock_clock_applied : R.string.xmorph_failed, Toast.LENGTH_SHORT).show();
    }

    private final class Gallery extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return entries.get(position).getId().hashCode(); }
        @Override public boolean hasStableIds() { return true; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            XMorphNative.Entry entry = entries.get(position);
            LinearLayout card;
            Holder holder;
            if (convert instanceof LinearLayout) { card = (LinearLayout) convert; holder = (Holder) card.getTag(); }
            else {
                card = Ui.card(XMorphClockActivity.this);
                holder = new Holder(); holder.title = Ui.text(XMorphClockActivity.this, "", 18, true);
                card.addView(holder.title);
                holder.view = new ClockFaceView(XMorphClockActivity.this, entry.getFace(), XMorphClockActivity.this, false);
                holder.view.setAnimationsEnabled(false);
                holder.view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout frame = new FrameLayout(XMorphClockActivity.this);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Ui.dp(XMorphClockActivity.this, 220), -2, android.view.Gravity.CENTER_HORIZONTAL);
                frame.addView(holder.view, params); card.addView(frame);
                card.setTag(holder);
            }
            holder.title.setText((entry.getId().equals(selected) ? "✓ " : "") + entry.getName());
            holder.view.setFace(entry.getFace()); holder.view.setLargeProgress(preview == 1 ? 0f : 1f);
            holder.view.setSleepProgress(preview == 2 ? 1f : 0f); holder.view.setAodProgress(preview == 2 ? 1f : 0f);
            holder.view.setBackdrop(backdrop);
            card.setOnClickListener(v -> apply(entry));
            holder.view.setOnClickListener(v -> apply(entry));
            card.setContentDescription(entry.getName() + ", " + entry.getCategory() + ", " + getString(R.string.xmorph_apply));
            return card;
        }
    }
    private static final class Holder { TextView title; ClockFaceView view; }
}
