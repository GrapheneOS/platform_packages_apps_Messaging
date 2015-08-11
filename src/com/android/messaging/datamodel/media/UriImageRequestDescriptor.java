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
import android.net.Uri;

import com.android.messaging.util.UriUtil;

public class UriImageRequestDescriptor extends ImageRequestDescriptor {
    public final Uri uri;
    public final boolean allowCompression;

    public UriImageRequestDescriptor(final Uri uri) {
        this(uri, UriImageRequest.UNSPECIFIED_SIZE, UriImageRequest.UNSPECIFIED_SIZE, false, false,
                false, 0, 0);
    }

    public UriImageRequestDescriptor(final Uri uri, final int desiredWidth, final int desiredHeight)
    {
        this(uri, desiredWidth, desiredHeight, false, false, false, 0, 0);
    }

    public UriImageRequestDescriptor(final Uri uri, final int desiredWidth, final int desiredHeight,
            final boolean cropToCircle, final int circleBackgroundColor, int circleStrokeColor)
    {
        this(uri, desiredWidth, desiredHeight, false,
                false, cropToCircle, circleBackgroundColor, circleStrokeColor);
    }

    public UriImageRequestDescriptor(final Uri uri, final int desiredWidth,
            final int desiredHeight, final boolean allowCompression, boolean isStatic,
            boolean cropToCircle, int circleBackgroundColor, int circleStrokeColor) {
        this(uri, desiredWidth, desiredHeight, UriImageRequest.UNSPECIFIED_SIZE,
                UriImageRequest.UNSPECIFIED_SIZE, allowCompression, isStatic, cropToCircle,
                circleBackgroundColor, circleStrokeColor);
    }

    /**
     * Creates a new Uri-based image request.
     * @param uri the content Uri. Currently Bugle only supports local resources Uri (i.e. it has
     * to begin with content: or android.resource:
     * @param circleStrokeColor
     */
    public UriImageRequestDescriptor(final Uri uri, final int desiredWidth,
            final int desiredHeight, final int sourceWidth, final int sourceHeight,
            final boolean allowCompression, final boolean isStatic, final boolean cropToCircle,
            final int circleBackgroundColor, int circleStrokeColor) {
        super(desiredWidth, desiredHeight, sourceWidth, sourceHeight, isStatic,
                cropToCircle, circleBackgroundColor, circleStrokeColor);
        this.uri = uri;
        this.allowCompression = allowCompression;
    }

    @Override
    public String getKey() {
        if (uri != null) {
            final String key = super.getKey();
            if (key != null) {
                return new StringBuilder()
                    .append(uri).append(KEY_PART_DELIMITER)
                    .append(String.valueOf(allowCompression)).append(KEY_PART_DELIMITER)
                    .append(key).toString();
            }
        }
        return null;
    }

    @Override
    public MediaRequest<ImageResource> buildSyncMediaRequest(final Context context) {
        if (uri == null || UriUtil.isLocalUri(uri)) {
            return new UriImageRequest<UriImageRequestDescriptor>(context, this);
        } else {
            return new NetworkUriImageRequest<UriImageRequestDescriptor>(context, this);
        }
    }

    /** ID of the resource in MediaStore or null if this resource didn't come from MediaStore */
    public Long getMediaStoreId() {
        return null;
    }
}