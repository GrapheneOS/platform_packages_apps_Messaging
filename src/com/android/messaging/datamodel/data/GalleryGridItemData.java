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

package com.android.messaging.datamodel.data;

import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images.Media;
import android.text.TextUtils;

import com.android.messaging.datamodel.media.FileImageRequestDescriptor;
import com.android.messaging.datamodel.media.ImageRequest;
import com.android.messaging.datamodel.media.UriImageRequestDescriptor;
import com.android.messaging.util.Assert;

/**
 * Provides data for GalleryGridItemView
 */
public class GalleryGridItemData {
    public static final String[] IMAGE_PROJECTION = new String[] {
        Media._ID,
        Media.DATA,
        Media.WIDTH,
        Media.HEIGHT,
        Media.MIME_TYPE,
        Media.DATE_MODIFIED};

    public static final String[] SPECIAL_ITEM_COLUMNS = new String[] {
        BaseColumns._ID
    };

    private static final int INDEX_ID = 0;

    // For local image gallery.
    private static final int INDEX_DATA_PATH = 1;
    private static final int INDEX_WIDTH = 2;
    private static final int INDEX_HEIGHT = 3;
    private static final int INDEX_MIME_TYPE = 4;
    private static final int INDEX_DATE_MODIFIED = 5;

    /** A special item's id for picking images from document picker */
    public static final String ID_DOCUMENT_PICKER_ITEM = "-1";

    private UriImageRequestDescriptor mImageData;
    private String mContentType;
    private boolean mIsDocumentPickerItem;
    private long mDateSeconds;

    public GalleryGridItemData() {
    }

    public void bind(final Cursor cursor, final int desiredWidth, final int desiredHeight) {
        mIsDocumentPickerItem = TextUtils.equals(cursor.getString(INDEX_ID),
                ID_DOCUMENT_PICKER_ITEM);
        if (mIsDocumentPickerItem) {
            mImageData = null;
            mContentType = null;
        } else {
            int sourceWidth = cursor.getInt(INDEX_WIDTH);
            int sourceHeight = cursor.getInt(INDEX_HEIGHT);

            // Guard against bad data
            if (sourceWidth <= 0) {
                sourceWidth = ImageRequest.UNSPECIFIED_SIZE;
            }
            if (sourceHeight <= 0) {
                sourceHeight = ImageRequest.UNSPECIFIED_SIZE;
            }

            mContentType = cursor.getString(INDEX_MIME_TYPE);
            final String dateModified = cursor.getString(INDEX_DATE_MODIFIED);
            mDateSeconds = !TextUtils.isEmpty(dateModified) ? Long.parseLong(dateModified) : -1;
            mImageData = new FileImageRequestDescriptor(
                    cursor.getString(INDEX_DATA_PATH),
                    desiredWidth,
                    desiredHeight,
                    sourceWidth,
                    sourceHeight,
                    true /* canUseThumbnail */,
                    true /* allowCompression */,
                    true /* isStatic */);
        }
    }

    public boolean isDocumentPickerItem() {
        return mIsDocumentPickerItem;
    }

    public Uri getImageUri() {
        return mImageData.uri;
    }

    public UriImageRequestDescriptor getImageRequestDescriptor() {
        return mImageData;
    }

    public MessagePartData constructMessagePartData(final Rect startRect) {
        Assert.isTrue(!mIsDocumentPickerItem);
        return new MediaPickerMessagePartData(startRect, mContentType,
                mImageData.uri, mImageData.sourceWidth, mImageData.sourceHeight);
    }

    /**
     * @return The date in seconds. This can be negative if we could not retreive date info
     */
    public long getDateSeconds() {
        return mDateSeconds;
    }

    public String getContentType() {
        return mContentType;
    }
}
