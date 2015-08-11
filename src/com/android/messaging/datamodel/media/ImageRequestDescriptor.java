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

import android.content.Context;

import com.android.messaging.util.Assert;

/**
 * The base ImageRequest descriptor that describes the requirement of the requested image
 * resource, including the desired size. It holds request info that will be consumed by
 * ImageRequest instances. Subclasses of ImageRequest are expected to take
 * more descriptions such as content URI or file path.
 */
public abstract class ImageRequestDescriptor extends MediaRequestDescriptor<ImageResource> {
    /** Desired size for the image (if known). This is used for bitmap downsampling */
    public final int desiredWidth;
    public final int desiredHeight;

    /** Source size of the image (if known). This is used so that we don't have to manually decode
     *  the metrics from the image resource */
    public final int sourceWidth;
    public final int sourceHeight;

    /**
     * A static image resource is required, even if the image format supports animation (like Gif).
     */
    public final boolean isStatic;

    /**
     * The loaded image will be cropped to circular shape.
     */
    public final boolean cropToCircle;

    /**
     * The loaded image will be cropped to circular shape with the background color.
     */
    public final int circleBackgroundColor;

    /**
     * The loaded image will be cropped to circular shape with a stroke color.
     */
    public final int circleStrokeColor;

    protected static final char KEY_PART_DELIMITER = '|';

    /**
     * Creates a new image request with unspecified width and height. In this case, the full
     * bitmap is loaded and decoded, so unless you are sure that the image will be of
     * reasonable size, you should consider limiting at least one of the two dimensions
     * (for example, limiting the image width to the width of the ImageView container).
     */
    public ImageRequestDescriptor() {
        this(ImageRequest.UNSPECIFIED_SIZE, ImageRequest.UNSPECIFIED_SIZE,
                ImageRequest.UNSPECIFIED_SIZE, ImageRequest.UNSPECIFIED_SIZE, false, false, 0, 0);
    }

    public ImageRequestDescriptor(final int desiredWidth, final int desiredHeight) {
        this(desiredWidth, desiredHeight,
                ImageRequest.UNSPECIFIED_SIZE, ImageRequest.UNSPECIFIED_SIZE, false, false, 0, 0);
    }

    public ImageRequestDescriptor(final int desiredWidth,
            final int desiredHeight, final int sourceWidth, final int sourceHeight,
            final boolean isStatic, final boolean cropToCircle, final int circleBackgroundColor,
            int circleStrokeColor) {
        Assert.isTrue(desiredWidth == ImageRequest.UNSPECIFIED_SIZE || desiredWidth > 0);
        Assert.isTrue(desiredHeight == ImageRequest.UNSPECIFIED_SIZE || desiredHeight > 0);
        Assert.isTrue(sourceWidth == ImageRequest.UNSPECIFIED_SIZE || sourceWidth > 0);
        Assert.isTrue(sourceHeight == ImageRequest.UNSPECIFIED_SIZE || sourceHeight > 0);
        this.desiredWidth = desiredWidth;
        this.desiredHeight = desiredHeight;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.isStatic = isStatic;
        this.cropToCircle = cropToCircle;
        this.circleBackgroundColor = circleBackgroundColor;
        this.circleStrokeColor = circleStrokeColor;
    }

    public String getKey() {
        return new StringBuilder()
                .append(desiredWidth).append(KEY_PART_DELIMITER)
                .append(desiredHeight).append(KEY_PART_DELIMITER)
                .append(String.valueOf(cropToCircle)).append(KEY_PART_DELIMITER)
                .append(String.valueOf(circleBackgroundColor)).append(KEY_PART_DELIMITER)
                .append(String.valueOf(isStatic)).toString();
    }

    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public abstract MediaRequest<ImageResource> buildSyncMediaRequest(Context context);

    // Called once source dimensions finally determined upon loading the image
    public void updateSourceDimensions(final int sourceWidth, final int sourceHeight) {
    }
}