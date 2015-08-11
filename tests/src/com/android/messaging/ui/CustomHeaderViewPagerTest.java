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

import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;

public class CustomHeaderViewPagerTest extends ViewTest<CustomHeaderViewPager> {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(getInstrumentation().getTargetContext());
    }

    public void testBindFirstLevel() {
        final CustomHeaderViewPager view = new CustomHeaderViewPager(getActivity(), null);
        final SimpleCursorAdapter adapter =
                new SimpleCursorAdapter(getActivity(), 0, null, null, null, 0);
        final CustomHeaderPagerViewHolder[] viewHolders = {
                new FakeListViewHolder(getActivity(), adapter),
                new FakeListViewHolder(getActivity(), adapter)
        };

        view.setViewHolders(viewHolders);
        final ViewPager pager = (ViewPager) view.findViewById(R.id.pager);
        final ViewGroup tabStrip = (ViewGroup) view.findViewById(R.id.tab_strip);
        final ViewPagerTabStrip realTab = (ViewPagerTabStrip) tabStrip.getChildAt(0);

        assertEquals(2, realTab.getChildCount());
        View headerTitleButton = realTab.getChildAt(1);
        // Click on the first page. Now the view pager should switch to that page accordingly.
        clickButton(headerTitleButton);
        assertEquals(1, pager.getCurrentItem());
    }

    @Override
    protected int getLayoutIdForView() {
        // All set up should be done by creating a CustomHeaderViewPager which handles inflating
        // the layout
        return 0;
    }
}
