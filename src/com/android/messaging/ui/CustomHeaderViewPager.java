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
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import com.android.messaging.R;
import com.android.messaging.util.Assert;

/**
 * A view that contains both a view pager and a tab strip wrapped in a linear layout.
 */
public class CustomHeaderViewPager extends LinearLayout {
    public final static int DEFAULT_TAB_STRIP_SIZE = -1;
    private final int mDefaultTabStripSize;
    private ViewPager mViewPager;
    private ViewPagerTabs mTabstrip;

    public CustomHeaderViewPager(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.custom_header_view_pager, this, true);
        setOrientation(LinearLayout.VERTICAL);

        mTabstrip = (ViewPagerTabs) findViewById(R.id.tab_strip);
        mViewPager = (ViewPager) findViewById(R.id.pager);

        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true);
        mDefaultTabStripSize = context.getResources().getDimensionPixelSize(tv.resourceId);
    }

    public void setCurrentItem(final int position) {
        mViewPager.setCurrentItem(position);
    }

    public void setViewPagerTabHeight(final int tabHeight) {
        mTabstrip.getLayoutParams().height = tabHeight == DEFAULT_TAB_STRIP_SIZE ?
                mDefaultTabStripSize : tabHeight;
    }

    public void setViewHolders(final CustomHeaderPagerViewHolder[] viewHolders) {
        Assert.notNull(mViewPager);
        final PagerAdapter adapter = new CustomHeaderViewPagerAdapter(viewHolders);
        mViewPager.setAdapter(adapter);
        mTabstrip.setViewPager(mViewPager);
        mViewPager.setOnPageChangeListener(new OnPageChangeListener() {

            @Override
            public void onPageScrollStateChanged(int state) {
                mTabstrip.onPageScrollStateChanged(state);
            }

            @Override
            public void onPageScrolled(int position, float positionOffset,
                    int positionOffsetPixels) {
                mTabstrip.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }

            @Override
            public void onPageSelected(int position) {
                mTabstrip.onPageSelected(position);
            }
        });
    }

    public int getSelectedItemPosition() {
        return mTabstrip.getSelectedItemPosition();
    }
}
