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

package com.android.messaging.ui.mediapicker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;

import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.exif.ExifInterface;
import com.android.messaging.util.exif.ExifTag;

import java.io.IOException;
import java.io.OutputStream;

public class ImagePersistTask extends SafeAsyncTask<Void, Void, Void> {
    private static final String JPEG_EXTENSION = "jpg";
    private static final String TAG = LogUtil.BUGLE_TAG;

    private int mWidth;
    private int mHeight;
    private final float mHeightPercent;
    private final byte[] mBytes;
    private final Context mContext;
    private final CameraManager.MediaCallback mCallback;
    private Uri mOutputUri;
    private Exception mException;

    public ImagePersistTask(
            final int width,
            final int height,
            final float heightPercent,
            final byte[] bytes,
            final Context context,
            final CameraManager.MediaCallback callback) {
        Assert.isTrue(heightPercent >= 0 && heightPercent <= 1);
        Assert.notNull(bytes);
        Assert.notNull(context);
        Assert.notNull(callback);
        mWidth = width;
        mHeight = height;
        mHeightPercent = heightPercent;
        mBytes = bytes;
        mContext = context;
        mCallback = callback;
        // TODO: We probably want to store directly in MMS storage to prevent this
        // intermediate step
        mOutputUri = MediaScratchFileProvider.buildMediaScratchSpaceUri(JPEG_EXTENSION);
    }

    @Override
    protected Void doInBackgroundTimed(final Void... params) {
        OutputStream outputStream = null;
        Bitmap bitmap = null;
        Bitmap clippedBitmap = null;
        try {
            outputStream =
                    mContext.getContentResolver().openOutputStream(mOutputUri);
            if (mHeightPercent != 1.0f) {
                int orientation = android.media.ExifInterface.ORIENTATION_UNDEFINED;
                final ExifInterface exifInterface = new ExifInterface();
                try {
                    exifInterface.readExif(mBytes);
                    final Integer orientationValue =
                            exifInterface.getTagIntValue(ExifInterface.TAG_ORIENTATION);
                    if (orientationValue != null) {
                        orientation = orientationValue.intValue();
                    }
                    // The thumbnail is of the full image, but we're cropping it, so just clear
                    // the thumbnail
                    exifInterface.setCompressedThumbnail((byte[]) null);
                } catch (IOException e) {
                    // Couldn't get exif tags, not the end of the world
                }
                bitmap = BitmapFactory.decodeByteArray(mBytes, 0, mBytes.length);
                final int clippedWidth;
                final int clippedHeight;
                if (ExifInterface.getOrientationParams(orientation).invertDimensions) {
                    Assert.equals(mWidth, bitmap.getHeight());
                    Assert.equals(mHeight, bitmap.getWidth());
                    clippedWidth = (int) (mHeight * mHeightPercent);
                    clippedHeight = mWidth;
                } else {
                    Assert.equals(mWidth, bitmap.getWidth());
                    Assert.equals(mHeight, bitmap.getHeight());
                    clippedWidth = mWidth;
                    clippedHeight = (int) (mHeight * mHeightPercent);
                }
                final int offsetTop = (bitmap.getHeight() - clippedHeight) / 2;
                final int offsetLeft = (bitmap.getWidth() - clippedWidth) / 2;
                mWidth = clippedWidth;
                mHeight = clippedHeight;
                clippedBitmap = Bitmap.createBitmap(clippedWidth, clippedHeight,
                        Bitmap.Config.ARGB_8888);
                clippedBitmap.setDensity(bitmap.getDensity());
                final Canvas clippedBitmapCanvas = new Canvas(clippedBitmap);
                final Matrix matrix = new Matrix();
                matrix.postTranslate(-offsetLeft, -offsetTop);
                clippedBitmapCanvas.drawBitmap(bitmap, matrix, null /* paint */);
                clippedBitmapCanvas.save();
                // EXIF data can take a big chunk of the file size and is often cleared by the
                // carrier, only store orientation since that's critical
                ExifTag orientationTag = exifInterface.getTag(ExifInterface.TAG_ORIENTATION);
                exifInterface.clearExif();
                exifInterface.setTag(orientationTag);
                exifInterface.writeExif(clippedBitmap, outputStream);
            } else {
                outputStream.write(mBytes);
            }
        } catch (final IOException e) {
            mOutputUri = null;
            mException = e;
            LogUtil.e(TAG, "Unable to persist image to temp storage " + e);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }

            if (clippedBitmap != null) {
                clippedBitmap.recycle();
            }

            if (outputStream != null) {
                try {
                    outputStream.flush();
                } catch (final IOException e) {
                    mOutputUri = null;
                    mException = e;
                    LogUtil.e(TAG, "error trying to flush and close the outputStream" + e);
                } finally {
                    try {
                        outputStream.close();
                    } catch (final IOException e) {
                        // Do nothing.
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(final Void aVoid) {
        if (mOutputUri != null) {
            mCallback.onMediaReady(mOutputUri, ContentType.IMAGE_JPEG, mWidth, mHeight);
        } else {
            Assert.notNull(mException);
            mCallback.onMediaFailed(mException);
        }
    }
}
