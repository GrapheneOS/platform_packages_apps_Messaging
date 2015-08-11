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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.DoesNotRunOnMainThread;

import java.util.Arrays;
import java.util.List;

/**
 * A cache-facing image resource that's much more compact than the raw Bitmap objects stored in
 * {@link com.android.messaging.datamodel.media.DecodedImageResource}.
 *
 * This resource is created from a regular Bitmap-based ImageResource before being pushed to
 * {@link com.android.messaging.datamodel.media.MediaCache}, if the image request
 * allows for resource encoding/compression.
 *
 * During resource retrieval on cache hit,
 * {@link #getMediaDecodingRequest(MediaRequest)} is invoked to create a async
 * decode task, which decodes the compressed byte array back to a regular image resource to
 * be consumed by the UI.
 */
public class EncodedImageResource extends ImageResource {
    private final byte[] mImageBytes;

    public EncodedImageResource(String key, byte[] imageBytes, int orientation) {
        super(key, orientation);
        mImageBytes = imageBytes;
    }

    @Override
    @DoesNotRunOnMainThread
    public Bitmap getBitmap() {
        acquireLock();
        try {
            // This should only be called during the decode request.
            Assert.isNotMainThread();
            return BitmapFactory.decodeByteArray(mImageBytes, 0, mImageBytes.length);
        } finally {
            releaseLock();
        }
    }

    @Override
    public byte[] getBytes() {
        acquireLock();
        try {
            return Arrays.copyOf(mImageBytes, mImageBytes.length);
        } finally {
            releaseLock();
        }
    }

    @Override
    public Bitmap reuseBitmap() {
        return null;
    }

    @Override
    public boolean supportsBitmapReuse() {
        return false;
    }

    @Override
    public int getMediaSize() {
        return mImageBytes.length;
    }

    @Override
    protected void close() {
    }

    @Override
    public Drawable getDrawable(Resources resources) {
        return null;
    }

    @Override
    boolean isEncoded() {
        return true;
    }

    @Override
    MediaRequest<? extends RefCountedMediaResource> getMediaDecodingRequest(
            final MediaRequest<? extends RefCountedMediaResource> originalRequest) {
        Assert.isTrue(isEncoded());
        return new DecodeImageRequest();
    }

    /**
     * A MediaRequest that decodes the encoded image resource. This class is chained to the
     * original media request that requested the image, so it inherits the listener and
     * properties such as binding.
     */
    private class DecodeImageRequest implements MediaRequest<ImageResource> {
        public DecodeImageRequest() {
            // Hold a ref onto the encoded resource before the request finishes.
            addRef();
        }

        @Override
        public String getKey() {
            return EncodedImageResource.this.getKey();
        }

        @Override
        @DoesNotRunOnMainThread
        public ImageResource loadMediaBlocking(List<MediaRequest<ImageResource>> chainedTask)
                throws Exception {
            Assert.isNotMainThread();
            acquireLock();
            try {
                final Bitmap decodedBitmap = BitmapFactory.decodeByteArray(mImageBytes, 0,
                        mImageBytes.length);
                return new DecodedImageResource(getKey(), decodedBitmap, getOrientation());
            } finally {
                releaseLock();
                release();
            }
        }

        @Override
        public MediaCache<ImageResource> getMediaCache() {
            // Decoded resource is non-cachable, it's for UI consumption only (for now at least)
            return null;
        }

        @Override
        public int getCacheId() {
            return 0;
        }

        @Override
        public int getRequestType() {
            return REQUEST_DECODE_MEDIA;
        }

        @Override
        public MediaRequestDescriptor<ImageResource> getDescriptor() {
            return null;
        }
    }
}
