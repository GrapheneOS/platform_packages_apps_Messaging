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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ScrollView;

import com.android.messaging.R;

/**
 * A ScrollView that limits the maximum height that it can take. This is to work around android
 * layout's limitation of not having android:maxHeight.
 */
public class MaxHeightScrollView extends ScrollView {
    private static final int NO_MAX_HEIGHT = -1;

    private final int mMaxHeight;

    public MaxHeightScrollView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final TypedArray attr = context.obtainStyledAttributes(attrs,
                R.styleable.MaxHeightScrollView, 0, 0);
        mMaxHeight = attr.getDimensionPixelSize(R.styleable.MaxHeightScrollView_android_maxHeight,
                NO_MAX_HEIGHT);
        attr.recycle();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mMaxHeight != NO_MAX_HEIGHT) {
            setMeasuredDimension(getMeasuredWidth(), Math.min(getMeasuredHeight(), mMaxHeight));
        }
    }
}