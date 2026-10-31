// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import java.util.function.IntConsumer;

/** Reference About home. Native controllers own editing and all detail destinations. */
public final class HeckerAboutPhonePreference extends Preference {
    public static final String KEY = "hecker_about_phone";
    public static final int NAME = 0, ANDROID = 1, PROCESSOR = 2, SOFTWARE = 3, DEVICE = 4, LEGAL = 5;
    private View mCard;
    private IntConsumer mNavigate;
    private boolean mActive, mObserving, mNameEditable = true;
    private final ContentObserver mName = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { render(); }
    };

    public HeckerAboutPhonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.hecker_about_phone);
        setSelectable(false);
        setPersistent(false);
        setIconSpaceReserved(false);
    }

    public void setNavigator(IntConsumer listener, boolean nameEditable) {
        mNavigate = listener;
        mNameEditable = nameEditable;
        render();
    }

    public void setActive(boolean active) {
        mActive = active;
        if (active && !mObserving) {
            getContext().getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.DEVICE_NAME), false, mName);
            mObserving = true;
        } else if (!active && mObserving) {
            getContext().getContentResolver().unregisterContentObserver(mName);
            mObserving = false;
        }
        render();
    }

    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        if (mCard != null && mCard != holder.itemView) glow().setActive(false);
        mCard = holder.itemView;
        mCard.setBackgroundColor(getContext().getColor(R.color.hecker_about_background));
        int[] ids = {R.id.hecker_about_name_action, R.id.hecker_about_android_action,
                R.id.hecker_about_processor_action, R.id.hecker_about_software_action,
                R.id.hecker_about_device_action, R.id.hecker_about_community};
        for (int action = 0; action < ids.length; action++) {
            final int destination = action;
            mCard.findViewById(ids[action]).setOnClickListener(view -> {
                if (mNavigate != null) mNavigate.accept(destination);
            });
        }
        render();
    }

    @Override public void onDetached() {
        setActive(false);
        mNavigate = null;
        mCard = null;
        super.onDetached();
    }

    private HeckerAboutGlowLayout glow() { return mCard.findViewById(R.id.hecker_about_glow); }
    private String unavailable() { return getContext().getString(R.string.hecker_about_unavailable); }
    private void value(int id, CharSequence text) { ((TextView) mCard.findViewById(id)).setText(text); }

    private void render() {
        if (mCard == null) return;
        Context context = getContext();
        String name = Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
        value(R.id.hecker_about_model, name == null || name.isBlank() ? Build.MODEL : name);
        value(R.id.hecker_about_version_line, context.getString(R.string.hecker_about_version_line,
                SystemProperties.get("ro.heckeros.version", unavailable()), Build.VERSION.RELEASE));
        value(R.id.hecker_about_android, Build.VERSION.RELEASE);
        value(R.id.hecker_about_processor, HeckerAboutPhoneValues.processor(Build.SOC_MODEL, Build.HARDWARE));
        SpannableString credits = new SpannableString("\uFFFC  " + context.getString(
                R.string.hecker_about_credits, SystemProperties.get("ro.heckeros.maintainer", "Hecker")) + "  \uFFFC");
        Drawable heart = context.getDrawable(R.drawable.hecker_about_heart).mutate();
        Drawable external = context.getDrawable(R.drawable.hecker_about_external).mutate();
        int icon = Math.round(16 * context.getResources().getDisplayMetrics().density);
        heart.setBounds(0, 0, icon, icon);
        external.setBounds(0, 0, icon, icon);
        credits.setSpan(new ImageSpan(heart, ImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        credits.setSpan(new ImageSpan(external, ImageSpan.ALIGN_CENTER), credits.length() - 1,
                credits.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value(R.id.hecker_about_community, credits);
        View rename = mCard.findViewById(R.id.hecker_about_name_action);
        rename.setEnabled(mNameEditable);
        rename.setAlpha(mNameEditable ? 1f : .5f);
        glow().setActive(mActive);
    }
}
