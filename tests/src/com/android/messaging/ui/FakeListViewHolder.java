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
import android.widget.CursorAdapter;

import com.android.messaging.R;

/**
 * A fake {@link CustomHeaderPagerListViewHolder} for CustomHeaderViewPager tests only.
 */
public class FakeListViewHolder extends CustomHeaderPagerListViewHolder {
    public FakeListViewHolder(final Context context, final CursorAdapter adapter) {
        super(context, adapter);
    }

    @Override
    protected int getLayoutResId() {
        return 0;
    }

    @Override
    protected int getPageTitleResId() {
        return android.R.string.untitled;
    }

    @Override
    protected int getEmptyViewResId() {
        return R.id.empty_view;
    }

    @Override
    protected int getListViewResId() {
        return android.R.id.list;
    }

    @Override
    protected int getEmptyViewTitleResId() {
        return R.string.contact_list_empty_text;
    }

    @Override
    protected int getEmptyViewImageResId() {
        return R.drawable.ic_oobe_freq_list;
    }
}