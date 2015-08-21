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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.provider.MediaStore.Video.Thumbnails;

import com.android.messaging.Factory;
import com.android.messaging.util.MediaMetadataRetrieverWrapper;
import com.android.messaging.util.MediaUtil;
import com.android.messaging.util.OsUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class to request a video thumbnail.
 * Users of this class as responsible for checking {@link #shouldShowIncomingVideoThumbnails}
 */
public class VideoThumbnailRequest extends ImageRequest<UriImageRequestDescriptor> {

    public VideoThumbnailRequest(final Context context,
            final UriImageRequestDescriptor descriptor) {
        super(context, descriptor);
    }

    public static boolean shouldShowIncomingVideoThumbnails() {
        return MediaUtil.canAutoAccessIncomingMedia();
    }

    @Override
    protected InputStream getInputStreamForResource() throws FileNotFoundException {
        return null;
    }

    @Override
    protected boolean hasBitmapObject() {
        return true;
    }

    @Override
    protected Bitmap getBitmapForResource() throws IOException {
        final Long mediaId = mDescriptor.getMediaStoreId();
        Bitmap bitmap = null;
        if (mediaId != null) {
            final ContentResolver cr = Factory.get().getApplicationContext().getContentResolver();
            bitmap = Thumbnails.getThumbnail(cr, mediaId, Thumbnails.MICRO_KIND, null);
        } else {
            final MediaMetadataRetrieverWrapper retriever = new MediaMetadataRetrieverWrapper();
            try {
                retriever.setDataSource(mDescriptor.uri);
                bitmap = retriever.getFrameAtTime();
            } finally {
                retriever.release();
            }
        }
        if (bitmap != null) {
            mDescriptor.updateSourceDimensions(bitmap.getWidth(), bitmap.getHeight());
        }
        return bitmap;
    }
}
