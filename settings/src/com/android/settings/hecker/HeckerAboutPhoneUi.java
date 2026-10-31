// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import com.android.settings.R;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.ArrayList;
import java.util.List;

/** A compact native toolbar for the reference home; all shared chrome is restored on exit. */
public final class HeckerAboutPhoneUi implements AutoCloseable {
    public static final String DETAILS = "hecker_about_device_details";
    private static final String SEARCH_KEY = ":settings:fragment_args_key";
    private final Activity mActivity;
    private final List<SavedView> mViews = new ArrayList<>();
    private final int mStatusColor, mNavigationColor;
    private CollapsingToolbarLayout mCollapsing;
    private boolean mTitleEnabled;
    private Drawable mContentScrim, mStatusScrim;
    private AppBarLayout.LayoutParams mCollapsingParams;
    private int mScrollFlags;
    private Toolbar mToolbar;
    private CharSequence mToolbarTitle;

    public static boolean isDetails(Bundle args, Intent intent) {
        if (args != null && (args.getBoolean(DETAILS, false) || args.getString(SEARCH_KEY) != null)) return true;
        if (intent == null) return false;
        if (intent.getStringExtra(SEARCH_KEY) != null) return true;
        Bundle nested = intent.getBundleExtra(":settings:show_fragment_args");
        return nested != null && (nested.getBoolean(DETAILS, false) || nested.getString(SEARCH_KEY) != null);
    }

    public HeckerAboutPhoneUi(Activity activity, View list) {
        mActivity = activity;
        int color = activity.getColor(R.color.hecker_about_background);
        mStatusColor = activity.getWindow().getStatusBarColor();
        mNavigationColor = activity.getWindow().getNavigationBarColor();
        activity.getWindow().setStatusBarColor(color);
        activity.getWindow().setNavigationBarColor(color);
        save(activity.findViewById(android.R.id.content), color);
        save(list, color);
        list.setPadding(0, list.getPaddingTop(), 0, list.getPaddingBottom());
        View appBar = named("app_bar");
        save(appBar, color);
        View collapsing = named("collapsing_toolbar");
        save(collapsing, color);
        if (collapsing instanceof CollapsingToolbarLayout) {
            mCollapsing = (CollapsingToolbarLayout) collapsing;
            mTitleEnabled = mCollapsing.isTitleEnabled();
            mContentScrim = mCollapsing.getContentScrim();
            mStatusScrim = mCollapsing.getStatusBarScrim();
            mCollapsing.setTitleEnabled(false);
            mCollapsing.setContentScrimColor(Color.TRANSPARENT);
            mCollapsing.setStatusBarScrimColor(Color.TRANSPARENT);
            ViewGroup.LayoutParams params = mCollapsing.getLayoutParams();
            if (params instanceof AppBarLayout.LayoutParams) {
                mCollapsingParams = (AppBarLayout.LayoutParams) params;
                mScrollFlags = mCollapsingParams.getScrollFlags();
                mCollapsingParams.setScrollFlags(0);
            }
            params.height = Math.round(56 * activity.getResources().getDisplayMetrics().density);
            mCollapsing.setLayoutParams(params);
        }
        if (appBar instanceof AppBarLayout) ((AppBarLayout) appBar).setExpanded(true, false);
        View toolbar = named("action_bar");
        save(toolbar, color);
        if (toolbar instanceof Toolbar) {
            mToolbar = (Toolbar) toolbar;
            mToolbarTitle = mToolbar.getTitle();
            mToolbar.setTitle(activity.getTitle());
        }
    }

    private View named(String name) {
        int id = mActivity.getResources().getIdentifier(name, "id", mActivity.getPackageName());
        return id == 0 ? null : mActivity.findViewById(id);
    }

    private void save(View view, int color) {
        if (view == null) return;
        mViews.add(new SavedView(view));
        view.setBackgroundColor(color);
        view.setElevation(0);
        view.setStateListAnimator(null);
    }

    @Override public void close() {
        if (mCollapsing != null) {
            mCollapsing.setTitleEnabled(mTitleEnabled);
            mCollapsing.setContentScrim(mContentScrim);
            mCollapsing.setStatusBarScrim(mStatusScrim);
        }
        if (mCollapsingParams != null) mCollapsingParams.setScrollFlags(mScrollFlags);
        for (SavedView saved : mViews) saved.restore();
        if (mToolbar != null) mToolbar.setTitle(mToolbarTitle);
        mActivity.getWindow().setStatusBarColor(mStatusColor);
        mActivity.getWindow().setNavigationBarColor(mNavigationColor);
        mViews.clear();
    }

    private static final class SavedView {
        final View view;
        final Drawable background;
        final float elevation;
        final android.animation.StateListAnimator animator;
        final int height, left, top, right, bottom;
        SavedView(View view) {
            this.view = view;
            background = view.getBackground();
            elevation = view.getElevation();
            animator = view.getStateListAnimator();
            height = view.getLayoutParams() == null ? -1 : view.getLayoutParams().height;
            left = view.getPaddingLeft(); top = view.getPaddingTop();
            right = view.getPaddingRight(); bottom = view.getPaddingBottom();
        }
        void restore() {
            view.setBackground(background);
            view.setElevation(elevation);
            view.setStateListAnimator(animator);
            view.setPadding(left, top, right, bottom);
            if (view.getLayoutParams() != null) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.height = height;
                view.setLayoutParams(params);
            }
        }
    }
}
