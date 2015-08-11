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

import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import com.android.messaging.Factory;
import com.android.messaging.util.Assert;
import com.android.messaging.util.UiUtils;
import com.google.common.annotations.VisibleForTesting;

/**
 * A PagerAdapter that provides a fixed number of paged Views provided by a fixed set of
 * {@link PagerViewHolder}'s. This allows us to put a fixed number of Views, instead of fragments,
 * into a given ViewPager.
 */
public class FixedViewPagerAdapter<T extends PagerViewHolder> extends PagerAdapter {
    private final T[] mViewHolders;

    public FixedViewPagerAdapter(final T[] viewHolders) {
        Assert.notNull(viewHolders);
        mViewHolders = viewHolders;
    }

    @Override
    public Object instantiateItem(final ViewGroup container, final int position) {
        final PagerViewHolder viewHolder = getViewHolder(position);
        final View view = viewHolder.getView(container);
        if (view == null) {
            return null;
        }
        view.setTag(viewHolder);
        container.addView(view);
        return viewHolder;
    }

    @Override
    public void destroyItem(final ViewGroup container, final int position, final Object object) {
        final PagerViewHolder viewHolder = getViewHolder(position);
        final View destroyedView = viewHolder.destroyView();
        if (destroyedView != null) {
            container.removeView(destroyedView);
        }
    }

    @Override
    public int getCount() {
        return mViewHolders.length;
    }

    @Override
    public boolean isViewFromObject(final View view, final Object object) {
        return view.getTag() == object;
    }

    public T getViewHolder(final int i) {
        return getViewHolder(i, true /* rtlAware */);
    }

    @VisibleForTesting
    public T getViewHolder(final int i, final boolean rtlAware) {
        return mViewHolders[rtlAware ? getRtlPosition(i) : i];
    }

    @Override
    public Parcelable saveState() {
        // The paged views in the view pager gets created and destroyed as the user scrolls through
        // them. By default, only the pages to the immediate left and right of the current visible
        // page are realized. Moreover, if the activity gets destroyed and recreated, the pages are
        // automatically destroyed. Therefore, in order to preserve transient page UI states that
        // are not persisted in the DB we'd like to store them in a Bundle when views get
        // destroyed. When the views get recreated, we rehydrate them by passing them the saved
        // data. When the activity gets destroyed, it invokes saveState() on this adapter to
        // add this saved Bundle to the overall saved instance state.
        final Bundle savedViewHolderState = new Bundle(Factory.get().getApplicationContext()
                .getClassLoader());
        for (int i = 0; i < mViewHolders.length; i++) {
            final Parcelable pageState = getViewHolder(i).saveState();
            savedViewHolderState.putParcelable(getInstanceStateKeyForPage(i), pageState);
        }
        return savedViewHolderState;
    }

    @Override
    public void restoreState(final Parcelable state, final ClassLoader loader) {
        if (state instanceof Bundle) {
            final Bundle restoredViewHolderState = (Bundle) state;
            ((Bundle) state).setClassLoader(Factory.get().getApplicationContext().getClassLoader());
            for (int i = 0; i < mViewHolders.length; i++) {
                final Parcelable pageState = restoredViewHolderState
                        .getParcelable(getInstanceStateKeyForPage(i));
                getViewHolder(i).restoreState(pageState);
            }
        } else {
            super.restoreState(state, loader);
        }
    }

    public void resetState() {
        for (int i = 0; i < mViewHolders.length; i++) {
            getViewHolder(i).resetState();
        }
    }

    private String getInstanceStateKeyForPage(final int i) {
        return getViewHolder(i).getClass().getCanonicalName() + "_savedstate_" + i;
    }

    protected int getRtlPosition(final int position) {
        if (UiUtils.isRtlMode()) {
            return mViewHolders.length - 1 - position;
        }
        return position;
    }
}
