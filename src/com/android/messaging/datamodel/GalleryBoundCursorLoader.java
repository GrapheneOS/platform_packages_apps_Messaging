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

package com.android.messaging.datamodel;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.Media;

import com.android.messaging.datamodel.data.GalleryGridItemData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.google.common.base.Joiner;

/**
 * A BoundCursorLoader that reads local media on the device.
 */
public class GalleryBoundCursorLoader extends BoundCursorLoader {
    public static final String MEDIA_SCANNER_VOLUME_EXTERNAL = "external";
    private static final Uri STORAGE_URI = Files.getContentUri(MEDIA_SCANNER_VOLUME_EXTERNAL);
    private static final String SORT_ORDER = Media.DATE_MODIFIED + " DESC";
    private static final String IMAGE_SELECTION = createSelection(
            MessagePartData.ACCEPTABLE_IMAGE_TYPES,
            new Integer[] { FileColumns.MEDIA_TYPE_IMAGE });

    public GalleryBoundCursorLoader(final String bindingId, final Context context) {
        super(bindingId, context, STORAGE_URI, GalleryGridItemData.IMAGE_PROJECTION,
                IMAGE_SELECTION, null, SORT_ORDER);
    }

    private static String createSelection(final String[] mimeTypes, Integer[] mediaTypes) {
        return Media.MIME_TYPE + " IN ('" + Joiner.on("','").join(mimeTypes) + "') AND "
                + FileColumns.MEDIA_TYPE + " IN (" + Joiner.on(',').join(mediaTypes) + ")";
    }
}
