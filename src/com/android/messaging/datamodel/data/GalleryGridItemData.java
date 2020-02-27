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
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;

import com.android.messaging.datamodel.media.FileImageRequestDescriptor;
import com.android.messaging.datamodel.media.ImageRequest;
import com.android.messaging.datamodel.media.UriImageRequestDescriptor;
import com.android.messaging.datamodel.media.VideoThumbnailRequestDescriptor;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.UriUtil;

/**
 * Provides data for GalleryGridItemView
 */
public class GalleryGridItemData {
    public static final String[] MEDIA_PROJECTION = new String[] {
        MediaColumns._ID,
        MediaColumns.DATA,
        MediaColumns.WIDTH,
        MediaColumns.HEIGHT,
        MediaColumns.MIME_TYPE,
        MediaColumns.DATE_MODIFIED,
        MediaColumns.DISPLAY_NAME};

    public static final String[] SPECIAL_ITEM_COLUMNS = new String[] {
        BaseColumns._ID
    };

    private static final int INDEX_ID = 0;

    // For local media gallery.
    private static final int INDEX_DATA_PATH = 1;
    private static final int INDEX_WIDTH = 2;
    private static final int INDEX_HEIGHT = 3;
    private static final int INDEX_MIME_TYPE = 4;
    private static final int INDEX_DATE_MODIFIED = 5;
    private static final int INDEX_DISPLAY_NAME = 6;

    /** A special item's id for picking a media from document picker */
    public static final String ID_DOCUMENT_PICKER_ITEM = "-1";

    private UriImageRequestDescriptor mImageData;
    private String mContentType;
    private boolean mIsDocumentPickerItem;
    private long mDateSeconds;
    private String mFileName;
    private Uri mAudioUri;

    public GalleryGridItemData() {
    }

    public void bind(final Cursor cursor, final int desiredWidth, final int desiredHeight) {
        mIsDocumentPickerItem = TextUtils.equals(cursor.getString(INDEX_ID),
                ID_DOCUMENT_PICKER_ITEM);
        if (mIsDocumentPickerItem) {
            mImageData = null;
            mContentType = null;
        } else {
            mContentType = cursor.getString(INDEX_MIME_TYPE);
            final String filePath = cursor.getString(INDEX_DATA_PATH);
            final String dateModified = cursor.getString(INDEX_DATE_MODIFIED);
            mDateSeconds = !TextUtils.isEmpty(dateModified) ? Long.parseLong(dateModified) : -1;
            if (ContentType.isAudioType(mContentType)) {
                mImageData = null;
                mAudioUri = UriUtil.getUriForResourceFile(filePath);
                mFileName = cursor.getString(INDEX_DISPLAY_NAME);
            } else { // For image and video types
                int sourceWidth = cursor.getInt(INDEX_WIDTH);
                int sourceHeight = cursor.getInt(INDEX_HEIGHT);

                // Guard against bad data
                if (sourceWidth <= 0) {
                    sourceWidth = ImageRequest.UNSPECIFIED_SIZE;
                }
                if (sourceHeight <= 0) {
                    sourceHeight = ImageRequest.UNSPECIFIED_SIZE;
                }

                if (ContentType.isVideoType(mContentType)) {
                    mImageData = new VideoThumbnailRequestDescriptor(
                            cursor.getLong(INDEX_ID),
                            desiredWidth,
                            desiredHeight,
                            sourceWidth,
                            sourceHeight);
                } else {
                    mImageData = new FileImageRequestDescriptor(
                            filePath,
                            desiredWidth,
                            desiredHeight,
                            sourceWidth,
                            sourceHeight,
                            true /* canUseThumbnail */,
                            true /* allowCompression */,
                            true /* isStatic */);
                }
            }
        }
    }

    public boolean isDocumentPickerItem() {
        return mIsDocumentPickerItem;
    }

    public Uri getImageUri() {
        return ContentType.isAudioType(mContentType) ? mAudioUri : mImageData.uri;
    }

    public UriImageRequestDescriptor getImageRequestDescriptor() {
        return mImageData;
    }

    public MessagePartData constructMessagePartData(final Rect startRect) {
        Assert.isTrue(!mIsDocumentPickerItem);
        return ContentType.isAudioType(mContentType)
                ? new MediaPickerMessagePartData(startRect, mContentType, mAudioUri, 0, 0)
                : new MediaPickerMessagePartData(startRect, mContentType, mImageData.uri,
                        mImageData.sourceWidth, mImageData.sourceHeight);
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

    public String getFileName() {
        return mFileName;
    }
}
