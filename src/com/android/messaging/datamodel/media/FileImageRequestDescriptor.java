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

import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.UriUtil;

/**
 * Holds image request info about file system based image resource.
 */
public class FileImageRequestDescriptor extends UriImageRequestDescriptor {
    public final String path;

    // Can we use the thumbnail image from Exif data?
    public final boolean canUseThumbnail;

    /**
     * Convenience constructor for when the image file's dimensions are not known.
     */
    public FileImageRequestDescriptor(final String path, final int desiredWidth,
            final int desiredHeight, final boolean canUseThumbnail, final boolean canCompress,
            final boolean isStatic) {
        this(path, desiredWidth, desiredHeight, FileImageRequest.UNSPECIFIED_SIZE,
                FileImageRequest.UNSPECIFIED_SIZE, canUseThumbnail, canCompress, isStatic);
    }

    /**
     * Creates a new file image request with this descriptor. Oftentimes image file metadata
     * has information such as the size of the image. Provide these metrics if they are known.
     */
    public FileImageRequestDescriptor(final String path, final int desiredWidth,
            final int desiredHeight, final int sourceWidth, final int sourceHeight,
            final boolean canUseThumbnail, final boolean canCompress, final boolean isStatic) {
        super(UriUtil.getUriForResourceFile(path), desiredWidth, desiredHeight, sourceWidth,
                sourceHeight, canCompress,  isStatic, false /* cropToCircle */,
                ImageUtils.DEFAULT_CIRCLE_BACKGROUND_COLOR /* circleBackgroundColor */,
                ImageUtils.DEFAULT_CIRCLE_STROKE_COLOR /* circleStrokeColor */);
        this.path = path;
        this.canUseThumbnail = canUseThumbnail;
    }

    @Override
    public String getKey() {
        final String prefixKey = super.getKey();
        return prefixKey == null ? null : new StringBuilder(prefixKey).append(KEY_PART_DELIMITER)
                .append(canUseThumbnail).toString();
    }

    @Override
    public MediaRequest<ImageResource> buildSyncMediaRequest(final Context context) {
        return new FileImageRequest(context, this);
    }
}