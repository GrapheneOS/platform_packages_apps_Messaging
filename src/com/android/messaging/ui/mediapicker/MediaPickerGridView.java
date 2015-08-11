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

package com.android.messaging.ui.mediapicker;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.GridView;

public class MediaPickerGridView extends GridView {

    public MediaPickerGridView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Returns if the grid view can be swiped down further. It cannot be swiped down
     * if there's no item or if we are already at the top.
     */
    public boolean canSwipeDown() {
        if (getAdapter() == null || getAdapter().getCount() == 0 || getChildCount() == 0) {
            return false;
        }

        final int firstVisiblePosition = getFirstVisiblePosition();
        if (firstVisiblePosition == 0 && getChildAt(0).getTop() >= 0) {
            return false;
        }
        return true;
    }
}
