/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.messaging.ui;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.CursorAdapter;
import android.widget.ListView;

import com.android.messaging.util.ImeUtil;

/**
 * Produces and holds a list view and its tab header to be displayed in a ViewPager.
 */
public abstract class CustomHeaderPagerListViewHolder extends BasePagerViewHolder
        implements CustomHeaderPagerViewHolder {
    private final Context mContext;
    private final CursorAdapter mListAdapter;
    private boolean mListCursorInitialized;
    private ListView mListView;

    public CustomHeaderPagerListViewHolder(final Context context,
            final CursorAdapter adapter) {
        mContext = context;
        mListAdapter = adapter;
    }

    @Override
    protected View createView(ViewGroup container) {
        final LayoutInflater inflater = (LayoutInflater)
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(
                getLayoutResId(),
                null /* root */,
                false /* attachToRoot */);
        final ListView listView = (ListView) view.findViewById(getListViewResId());
        listView.setAdapter(mListAdapter);
        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(final AbsListView view, final int scrollState) {
                if (scrollState != SCROLL_STATE_IDLE) {
                    ImeUtil.get().hideImeKeyboard(mContext, view);
                }
            }

            @Override
            public void onScroll(final AbsListView view, final int firstVisibleItem,
                    final int visibleItemCount, final int totalItemCount) {
            }
        });
        mListView = listView;
        maybeSetEmptyView();
        return view;
    }

    public void onContactsCursorUpdated(final Cursor data) {
        mListAdapter.swapCursor(data);
        if (!mListCursorInitialized) {
            // We set the emptyView here instead of in create so that the initial load won't show
            // the empty UI - the system handles this and doesn't do what we would like.
            mListCursorInitialized = true;
            maybeSetEmptyView();
        }
    }

    /**
     *  We don't want to show the empty view hint until BOTH conditions are met:
     *  1. The view has been created.
     *  2. Cursor data has been loaded once.
     *  Due to timing when data is loaded, the view may not be ready (and vice versa). So we
     *  are calling this method from both onContactsCursorUpdated & createView.
     */
    private void maybeSetEmptyView() {
        if (mView != null && mListCursorInitialized) {
            final ListEmptyView emptyView = (ListEmptyView) mView.findViewById(getEmptyViewResId());
            if (emptyView != null) {
                emptyView.setTextHint(getEmptyViewTitleResId());
                emptyView.setImageHint(getEmptyViewImageResId());
                final ListView listView = (ListView) mView.findViewById(getListViewResId());
                listView.setEmptyView(emptyView);
            }
        }
    }

    public void invalidateList() {
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * In order for scene transition to work, we toggle the visibility for each individual list
     * view items so that they can be properly tracked by the scene transition manager.
     * @param show whether the pending transition is to show or hide the list.
     */
    public void toggleVisibilityForPendingTransition(final boolean show, final View epicenterView) {
        if (mListView == null) {
            return;
        }
        final int childCount = mListView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View childView = mListView.getChildAt(i);
            if (childView != epicenterView) {
                childView.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    @Override
    public CharSequence getPageTitle(Context context) {
        return context.getString(getPageTitleResId());
    }

    protected abstract int getLayoutResId();
    protected abstract int getPageTitleResId();
    protected abstract int getEmptyViewResId();
    protected abstract int getEmptyViewTitleResId();
    protected abstract int getEmptyViewImageResId();
    protected abstract int getListViewResId();
}
