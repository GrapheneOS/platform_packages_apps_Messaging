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
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.messaging.util.UiUtils;

/**
 * A simple extension on the standard ViewPager which lets you turn paging on/off.
 */
public class PagingAwareViewPager extends ViewPager {
    private boolean mPagingEnabled = true;

    public PagingAwareViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setCurrentItem(int item, boolean smoothScroll) {
        super.setCurrentItem(getRtlPosition(item), smoothScroll);
    }

    @Override
    public void setCurrentItem(int item) {
        super.setCurrentItem(getRtlPosition(item));
    }

    @Override
    public int getCurrentItem() {
        int position = super.getCurrentItem();
        return getRtlPosition(position);
    }

    /**
     * Switches position in pager to be adjusted for if we are in RtL mode
     *
     * @param position
     * @return position adjusted if in rtl mode
     */
    protected int getRtlPosition(final int position) {
        final PagerAdapter adapter = getAdapter();
        if (adapter != null && UiUtils.isRtlMode()) {
            return adapter.getCount() - 1 - position;
        }
        return position;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (!mPagingEnabled) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent event) {
        if (!mPagingEnabled) {
            return false;
        }
        return super.onInterceptTouchEvent(event);
    }

    public void setPagingEnabled(final boolean enabled) {
        this.mPagingEnabled = enabled;
    }

    /** This prevents touch-less scrolling eg. while doing accessibility navigation. */
    @Override
    public boolean canScrollHorizontally(int direction) {
        if (mPagingEnabled) {
            return super.canScrollHorizontally(direction);
        } else {
            return false;
        }
    }
}
