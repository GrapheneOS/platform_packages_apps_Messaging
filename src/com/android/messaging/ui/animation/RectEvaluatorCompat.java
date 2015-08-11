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
package com.android.messaging.ui.animation;

import android.animation.RectEvaluator;
import android.animation.TypeEvaluator;
import android.graphics.Rect;

import com.android.messaging.util.OsUtil;

/**
 * This evaluator can be used to perform type interpolation between <code>Rect</code> values.
 * It's backward compatible to Api Level 11.
 */
public class RectEvaluatorCompat implements TypeEvaluator<Rect> {
    public static TypeEvaluator<Rect> create() {
        if (OsUtil.isAtLeastJB_MR2()) {
            return new RectEvaluator();
        } else {
            return new RectEvaluatorCompat();
        }
    }

    @Override
    public Rect evaluate(float fraction, Rect startValue, Rect endValue) {
        int left = startValue.left + (int) ((endValue.left - startValue.left) * fraction);
        int top = startValue.top + (int) ((endValue.top - startValue.top) * fraction);
        int right = startValue.right + (int) ((endValue.right - startValue.right) * fraction);
        int bottom = startValue.bottom + (int) ((endValue.bottom - startValue.bottom) * fraction);
        return new Rect(left, top, right, bottom);
    }
}
