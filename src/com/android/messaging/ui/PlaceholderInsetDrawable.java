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

import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

/**
 * A "placeholder" drawable that has the same sizing properties as the real UI element it
 * replaces.
 *
 * This is an InsetDrawable that takes a placeholder drawable (an animation list, or simply
 * a color drawable) and place it in the center of the inset drawable that's sized to the
 * requested source width and height of the image that it replaces. Unlike the base
 * InsetDrawable, this implementation returns the true width and height of the real image
 * that it's placeholding, instead of the intrinsic size of the contained drawable, so that
 * when used in an ImageView, it may be positioned/scaled/cropped the same way the real
 * image is.
 */
public class PlaceholderInsetDrawable extends InsetDrawable {
    // The dimensions of the real image that this drawable is replacing.
    private final int mSourceWidth;
    private final int mSourceHeight;

    /**
     * Given a source drawable, wraps it around in this placeholder drawable by placing the
     * drawable at the center of the container if possible (or fill the container if the
     * drawable doesn't have intrinsic size such as color drawable).
     */
    public static PlaceholderInsetDrawable fromDrawable(final Drawable drawable,
            final int sourceWidth, final int sourceHeight) {
        final int drawableWidth = drawable.getIntrinsicWidth();
        final int drawableHeight = drawable.getIntrinsicHeight();
        final int insetHorizontal = drawableWidth < 0 || drawableWidth > sourceWidth ?
                0 : (sourceWidth - drawableWidth) / 2;
        final int insetVertical = drawableHeight < 0 || drawableHeight > sourceHeight ?
                0 : (sourceHeight - drawableHeight) / 2;
        return new PlaceholderInsetDrawable(drawable, insetHorizontal, insetVertical,
                insetHorizontal, insetVertical, sourceWidth, sourceHeight);
    }

    private PlaceholderInsetDrawable(final Drawable drawable, final int insetLeft,
            final int insetTop, final int insetRight, final int insetBottom,
            final int sourceWidth, final int sourceHeight) {
        super(drawable, insetLeft, insetTop, insetRight, insetBottom);
        mSourceWidth = sourceWidth;
        mSourceHeight = sourceHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return mSourceWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSourceHeight;
    }
}