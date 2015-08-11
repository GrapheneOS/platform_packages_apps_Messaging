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
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;

import com.android.messaging.util.Assert;
import com.android.messaging.util.ImageUtils;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

/**
 * Requests a composite image resource. The composite image resource is constructed by first
 * sequentially requesting a number of sub image resources specified by
 * {@link CompositeImageRequestDescriptor#getChildRequestDescriptors()}. After this, the
 * individual sub images are composed into the final image onto their respective target rects
 * returned by {@link CompositeImageRequestDescriptor#getChildRequestTargetRects()}.
 */
public class CompositeImageRequest<D extends CompositeImageRequestDescriptor>
        extends ImageRequest<D> {
    private final Bitmap mBitmap;
    private final Canvas mCanvas;
    private final Paint mPaint;

    public CompositeImageRequest(final Context context, final D descriptor) {
        super(context, descriptor);
        mBitmap = getBitmapPool().createOrReuseBitmap(
                mDescriptor.desiredWidth, mDescriptor.desiredHeight);
        mCanvas = new Canvas(mBitmap);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    protected ImageResource loadMediaInternal(List<MediaRequest<ImageResource>> chainedTask) {
        final List<? extends ImageRequestDescriptor> descriptors =
                mDescriptor.getChildRequestDescriptors();
        final List<RectF> targetRects = mDescriptor.getChildRequestTargetRects();
        Assert.equals(descriptors.size(), targetRects.size());
        Assert.isTrue(descriptors.size() > 1);

        for (int i = 0; i < descriptors.size(); i++) {
            final MediaRequest<ImageResource> request =
                    descriptors.get(i).buildSyncMediaRequest(mContext);
            // Synchronously request the child image.
            final ImageResource resource =
                    MediaResourceManager.get().requestMediaResourceSync(request);
            if (resource != null) {
                try {
                    final RectF avatarDestOnGroup = targetRects.get(i);

                    // Draw the bitmap into a smaller size with a circle mask.
                    final Bitmap resourceBitmap = resource.getBitmap();
                    final RectF resourceRect = new RectF(
                            0, 0, resourceBitmap.getWidth(), resourceBitmap.getHeight());
                    final Bitmap smallCircleBitmap = getBitmapPool().createOrReuseBitmap(
                            Math.round(avatarDestOnGroup.width()),
                            Math.round(avatarDestOnGroup.height()));
                    final RectF smallCircleRect = new RectF(
                            0, 0, smallCircleBitmap.getWidth(), smallCircleBitmap.getHeight());
                    final Canvas smallCircleCanvas = new Canvas(smallCircleBitmap);
                    ImageUtils.drawBitmapWithCircleOnCanvas(resource.getBitmap(), smallCircleCanvas,
                            resourceRect, smallCircleRect, null /* bitmapPaint */,
                            false /* fillBackground */,
                            ImageUtils.DEFAULT_CIRCLE_BACKGROUND_COLOR /* circleBackgroundColor */,
                            ImageUtils.DEFAULT_CIRCLE_STROKE_COLOR /* circleStrokeColor */);
                    final Matrix matrix = new Matrix();
                    matrix.setRectToRect(smallCircleRect, avatarDestOnGroup,
                            Matrix.ScaleToFit.FILL);
                    mCanvas.drawBitmap(smallCircleBitmap, matrix, mPaint);
                } finally {
                    resource.release();
                }
            }
        }

        return new DecodedImageResource(getKey(), mBitmap, ExifInterface.ORIENTATION_NORMAL);
    }

    @Override
    public int getCacheId() {
        return BugleMediaCacheManager.AVATAR_IMAGE_CACHE;
    }

    @Override
    protected InputStream getInputStreamForResource() throws FileNotFoundException {
        throw new IllegalStateException("Composite image request doesn't support input stream!");
    }
}
