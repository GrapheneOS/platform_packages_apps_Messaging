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
package com.android.messaging.datamodel.media;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

/**
 * Base class for holding some form of image resource. The subclass gets to define the specific
 * type of data format it's holding, whether it be Bitmap objects or compressed byte arrays.
 */
public abstract class ImageResource extends RefCountedMediaResource {
    protected final int mOrientation;

    public ImageResource(final String key, int orientation) {
        super(key);
        mOrientation = orientation;
    }

    /**
     * Gets the contained image in drawable format.
     */
    public abstract Drawable getDrawable(Resources resources);

    /**
     * Gets the contained image in bitmap format.
     */
    public abstract Bitmap getBitmap();

    /**
     * Gets the contained image in byte array format.
     */
    public abstract byte[] getBytes();

    /**
     * Attempt to reuse the bitmap in the image resource and re-purpose it for something else.
     * After this, the image resource will relinquish ownership on the bitmap resource so that
     * it doesn't try to recycle it when getting closed.
     */
    public abstract Bitmap reuseBitmap();
    public abstract boolean supportsBitmapReuse();

    /**
     * Gets the orientation of the image as one of the ExifInterface.ORIENTATION_* constants
     */
    public int getOrientation() {
        return mOrientation;
    }
}
