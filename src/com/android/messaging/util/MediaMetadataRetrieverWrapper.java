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
package com.android.messaging.util;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;

import com.android.messaging.Factory;

import java.io.IOException;

/**
 * Convenience wrapper for {@link MediaMetadataRetriever} to help with its eccentric error handling.
 */
public class MediaMetadataRetrieverWrapper {
    private final MediaMetadataRetriever mRetriever = new MediaMetadataRetriever();

    public MediaMetadataRetrieverWrapper() {
    }

    public void setDataSource(Uri uri) throws IOException {
        ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        AssetFileDescriptor fd = resolver.openAssetFileDescriptor(uri, "r");
        if (fd == null) {
            throw new IOException("openAssetFileDescriptor returned null for " + uri);
        }
        try {
            mRetriever.setDataSource(fd.getFileDescriptor());
        } catch (RuntimeException e) {
            release();
            throw new IOException(e);
        } finally {
            fd.close();
        }
    }

    public int extractInteger(final int key, final int defaultValue) {
        final String s = mRetriever.extractMetadata(key);
        if (TextUtils.isEmpty(s)) {
            return defaultValue;
        }
        return Integer.parseInt(s);
    }

    public String extractMetadata(final int key) {
        return mRetriever.extractMetadata(key);
    }

    public Bitmap getFrameAtTime() {
        return mRetriever.getFrameAtTime();
    }

    public Bitmap getFrameAtTime(final long timeUs) {
        return mRetriever.getFrameAtTime(timeUs);
    }

    public void release() {
        try {
            mRetriever.release();
        } catch (RuntimeException e) {
            LogUtil.e(LogUtil.BUGLE_TAG, "MediaMetadataRetriever.release failed", e);
        }
    }
}
