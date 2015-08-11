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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;

import com.android.messaging.datamodel.media.PoolableImageCache.ReusableImageResourcePool;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.LogUtil;

import java.io.IOException;

/**
 * Serves file system based image requests. Since file paths can be expressed in Uri form, this
 * extends regular UriImageRequest but performs additional optimizations such as loading thumbnails
 * directly from Exif information.
 */
public class FileImageRequest extends UriImageRequest {
    private final String mPath;
    private final boolean mCanUseThumbnail;

    public FileImageRequest(final Context context,
            final FileImageRequestDescriptor descriptor) {
        super(context, descriptor);
        mPath = descriptor.path;
        mCanUseThumbnail = descriptor.canUseThumbnail;
    }

    @Override
    protected Bitmap loadBitmapInternal()
            throws IOException {
        // Before using the FileInputStream, check if the Exif has a thumbnail that we can use.
        if (mCanUseThumbnail) {
            byte[] thumbnail = null;
            try {
                final ExifInterface exif = new ExifInterface(mPath);
                if (exif.hasThumbnail()) {
                    thumbnail = exif.getThumbnail();
                }
            } catch (final IOException e) {
                // Nothing to do
            }

            if (thumbnail != null) {
                final BitmapFactory.Options options = PoolableImageCache.getBitmapOptionsForPool(
                        false /* scaled */, 0 /* inputDensity */, 0 /* targetDensity */);
                // First, check dimensions of the bitmap.
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length, options);

                // Calculate inSampleSize
                options.inSampleSize = ImageUtils.get().calculateInSampleSize(options,
                        mDescriptor.desiredWidth, mDescriptor.desiredHeight);

                options.inJustDecodeBounds = false;

                // Actually decode the bitmap, optionally using the bitmap pool.
                try {
                    // Get the orientation. We should be able to get the orientation from
                    // the thumbnail itself but at least on some phones, the thumbnail
                    // doesn't have an orientation tag. So use the outer image's orientation
                    // tag and hope for the best.
                    mOrientation = ImageUtils.getOrientation(getInputStreamForResource());
                    if (com.android.messaging.util.exif.ExifInterface.
                            getOrientationParams(mOrientation).invertDimensions) {
                        mDescriptor.updateSourceDimensions(options.outHeight, options.outWidth);
                    } else {
                        mDescriptor.updateSourceDimensions(options.outWidth, options.outHeight);
                    }
                    final ReusableImageResourcePool bitmapPool = getBitmapPool();
                    if (bitmapPool == null) {
                        return BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length,
                                options);
                    } else {
                        final int sampledWidth = options.outWidth / options.inSampleSize;
                        final int sampledHeight = options.outHeight / options.inSampleSize;
                        return bitmapPool.decodeByteArray(thumbnail, options, sampledWidth,
                                sampledHeight);
                    }
                } catch (IOException ex) {
                    // If the thumbnail is broken due to IOException, this will
                    // fall back to default bitmap loading.
                    LogUtil.e(LogUtil.BUGLE_IMAGE_TAG, "FileImageRequest: failed to load " +
                            "thumbnail from Exif", ex);
                }
            }
        }

        // Fall back to default InputStream-based loading if no thumbnails could be retrieved.
        return super.loadBitmapInternal();
    }
}
