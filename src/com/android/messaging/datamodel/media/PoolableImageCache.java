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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.SparseArray;

import com.android.messaging.Factory;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * A media cache that holds image resources, which doubles as a bitmap pool that allows the
 * consumer to optionally decode image resources using unused bitmaps stored in the cache.
 */
public class PoolableImageCache extends MediaCache<ImageResource> {
    private static final int MIN_TIME_IN_POOL = 5000;

    /** Encapsulates bitmap pool representation of the image cache */
    private final ReusableImageResourcePool mReusablePoolAccessor = new ReusableImageResourcePool();

    public PoolableImageCache(final int id, final String name) {
        this(DEFAULT_MEDIA_RESOURCE_CACHE_SIZE_IN_KILOBYTES, id, name);
    }

    public PoolableImageCache(final int maxSize, final int id, final String name) {
        super(maxSize, id, name);
    }

    /**
     * Creates a new BitmapFactory.Options for using the self-contained bitmap pool.
     */
    public static BitmapFactory.Options getBitmapOptionsForPool(final boolean scaled,
            final int inputDensity, final int targetDensity) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = scaled;
        options.inDensity = inputDensity;
        options.inTargetDensity = targetDensity;
        options.inSampleSize = 1;
        options.inJustDecodeBounds = false;
        options.inMutable = true;
        return options;
    }

    @Override
    public synchronized ImageResource addResourceToCache(final String key,
            final ImageResource imageResource) {
        mReusablePoolAccessor.onResourceEnterCache(imageResource);
        return super.addResourceToCache(key, imageResource);
    }

    @Override
    protected synchronized void entryRemoved(final boolean evicted, final String key,
            final ImageResource oldValue, final ImageResource newValue) {
        mReusablePoolAccessor.onResourceLeaveCache(oldValue);
        super.entryRemoved(evicted, key, oldValue, newValue);
    }

    /**
     * Returns a representation of the image cache as a reusable bitmap pool.
     */
    public ReusableImageResourcePool asReusableBitmapPool() {
        return mReusablePoolAccessor;
    }

    /**
     * A bitmap pool representation built on top of the image cache. It treats the image resources
     * stored in the image cache as a self-contained bitmap pool and is able to create or
     * reclaim bitmap resource as needed.
     */
    public class ReusableImageResourcePool {
        private static final int MAX_SUPPORTED_IMAGE_DIMENSION = 0xFFFF;
        private static final int INVALID_POOL_KEY = 0;

        /**
         * Number of reuse failures to skip before reporting.
         * For debugging purposes, change to a lower number for more frequent reporting.
         */
        private static final int FAILED_REPORTING_FREQUENCY = 100;

        /**
         * Count of reuse failures which have occurred.
         */
        private volatile int mFailedBitmapReuseCount = 0;

        /**
         * Count of reuse successes which have occurred.
         */
        private volatile int mSucceededBitmapReuseCount = 0;

        /**
         * A sparse array from bitmap size to a list of image cache entries that match the
         * given size. This map is used to quickly retrieve a usable bitmap to be reused by an
         * incoming ImageRequest. We need to ensure that this sparse array always contains only
         * elements currently in the image cache with no other consumer.
         */
        private final SparseArray<LinkedList<ImageResource>> mImageListSparseArray;

        public ReusableImageResourcePool() {
            mImageListSparseArray = new SparseArray<LinkedList<ImageResource>>();
        }

        /**
         * Load an input stream into a bitmap. Uses a bitmap from the pool if possible to reduce
         * memory turnover.
         * @param inputStream InputStream load. Cannot be null.
         * @param optionsTmp Should be the same options returned from getBitmapOptionsForPool().
         * Cannot be null.
         * @param width The width of the bitmap.
         * @param height The height of the bitmap.
         * @return The decoded Bitmap with the resource drawn in it.
         * @throws IOException
         */
        public Bitmap decodeSampledBitmapFromInputStream(@NonNull final InputStream inputStream,
                @NonNull final BitmapFactory.Options optionsTmp,
                final int width, final int height) throws IOException {
            if (width <= 0 || height <= 0) {
                // This is an invalid / corrupted image of zero size.
                LogUtil.w(LogUtil.BUGLE_IMAGE_TAG, "PoolableImageCache: Decoding bitmap with " +
                        "invalid size");
                throw new IOException("Invalid size / corrupted image");
            }
            Assert.notNull(inputStream);
            assignPoolBitmap(optionsTmp, width, height);
            Bitmap b = null;
            try {
                b = BitmapFactory.decodeStream(inputStream, null, optionsTmp);
                mSucceededBitmapReuseCount++;
            } catch (final IllegalArgumentException e) {
                // BitmapFactory couldn't decode the file, try again without an inputBufferBitmap.
                if (optionsTmp.inBitmap != null) {
                    optionsTmp.inBitmap.recycle();
                    optionsTmp.inBitmap = null;
                    b = BitmapFactory.decodeStream(inputStream, null, optionsTmp);
                    onFailedToReuse();
                }
            } catch (final OutOfMemoryError e) {
                LogUtil.w(LogUtil.BUGLE_IMAGE_TAG, "Oom decoding inputStream");
                Factory.get().reclaimMemory();
            }
            return b;
        }

        /**
         * Turn encoded bytes into a bitmap. Uses a bitmap from the pool if possible to reduce
         * memory turnover.
         * @param bytes Encoded bytes to draw on the bitmap. Cannot be null.
         * @param optionsTmp The bitmap will set here and the input should be generated from
         * getBitmapOptionsForPool(). Cannot be null.
         * @param width The width of the bitmap.
         * @param height The height of the bitmap.
         * @return A Bitmap with the encoded bytes drawn in it.
         * @throws IOException
         */
        public Bitmap decodeByteArray(@NonNull final byte[] bytes,
                @NonNull final BitmapFactory.Options optionsTmp, final int width,
                final int height) throws OutOfMemoryError, IOException {
            if (width <= 0 || height <= 0) {
                // This is an invalid / corrupted image of zero size.
                LogUtil.w(LogUtil.BUGLE_IMAGE_TAG, "PoolableImageCache: Decoding bitmap with " +
                        "invalid size");
                throw new IOException("Invalid size / corrupted image");
            }
            Assert.notNull(bytes);
            Assert.notNull(optionsTmp);
            assignPoolBitmap(optionsTmp, width, height);
            Bitmap b = null;
            try {
                b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, optionsTmp);
                mSucceededBitmapReuseCount++;
            } catch (final IllegalArgumentException e) {
                // BitmapFactory couldn't decode the file, try again without an inputBufferBitmap.
                // (i.e. without the bitmap from the pool)
                if (optionsTmp.inBitmap != null) {
                    optionsTmp.inBitmap.recycle();
                    optionsTmp.inBitmap = null;
                    b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, optionsTmp);
                    onFailedToReuse();
                }
            } catch (final OutOfMemoryError e) {
                LogUtil.w(LogUtil.BUGLE_IMAGE_TAG, "Oom decoding inputStream");
                Factory.get().reclaimMemory();
            }
            return b;
        }

        /**
         * Called when a new image resource is added to the cache. We add the resource to the
         * pool so it's properly keyed into the pool structure.
         */
        void onResourceEnterCache(final ImageResource imageResource) {
            if (getPoolKey(imageResource) != INVALID_POOL_KEY) {
                addResourceToPool(imageResource);
            }
        }

        /**
         * Called when an image resource is evicted from the cache. Bitmap pool's entries are
         * strictly tied to their presence in the image cache. Once an image is evicted from the
         * cache, it should be removed from the pool.
         */
        void onResourceLeaveCache(final ImageResource imageResource) {
            if (getPoolKey(imageResource) != INVALID_POOL_KEY) {
                removeResourceFromPool(imageResource);
            }
        }

        private void addResourceToPool(final ImageResource imageResource) {
            synchronized (PoolableImageCache.this) {
                final int poolKey = getPoolKey(imageResource);
                Assert.isTrue(poolKey != INVALID_POOL_KEY);
                LinkedList<ImageResource> imageList = mImageListSparseArray.get(poolKey);
                if (imageList == null) {
                    imageList = new LinkedList<ImageResource>();
                    mImageListSparseArray.put(poolKey, imageList);
                }
                imageList.addLast(imageResource);
            }
        }

        private void removeResourceFromPool(final ImageResource imageResource) {
            synchronized (PoolableImageCache.this) {
                final int poolKey = getPoolKey(imageResource);
                Assert.isTrue(poolKey != INVALID_POOL_KEY);
                final LinkedList<ImageResource> imageList = mImageListSparseArray.get(poolKey);
                if (imageList != null) {
                    imageList.remove(imageResource);
                }
            }
        }

        /**
         * Try to get a reusable bitmap from the pool with the given width and height. As a
         * result of this call, the caller will assume ownership of the returned bitmap.
         */
        private Bitmap getReusableBitmapFromPool(final int width, final int height) {
            synchronized (PoolableImageCache.this) {
                final int poolKey = getPoolKey(width, height);
                if (poolKey != INVALID_POOL_KEY) {
                    final LinkedList<ImageResource> images = mImageListSparseArray.get(poolKey);
                    if (images != null && images.size() > 0) {
                        // Try to reuse the first available bitmap from the pool list. We start from
                        // the least recently added cache entry of the given size.
                        ImageResource imageToUse = null;
                        for (int i = 0; i < images.size(); i++) {
                            final ImageResource image = images.get(i);
                            if (image.getRefCount() == 1) {
                                image.acquireLock();
                                if (image.getRefCount() == 1) {
                                    // The image is only used by the cache, so it's reusable.
                                    imageToUse = images.remove(i);
                                    break;
                                } else {
                                    // Logically, this shouldn't happen, because as soon as the
                                    // cache is the only user of this resource, it will not be
                                    // used by anyone else until the next cache access, but we
                                    // currently hold on to the cache lock. But technically
                                    // future changes may violate this assumption, so warn about
                                    // this.
                                    LogUtil.w(LogUtil.BUGLE_IMAGE_TAG, "Image refCount changed " +
                                            "from 1 in getReusableBitmapFromPool()");
                                    image.releaseLock();
                                }
                            }
                        }

                        if (imageToUse == null) {
                            return null;
                        }

                        try {
                            imageToUse.assertLockHeldByCurrentThread();

                            // Only reuse the bitmap if the last time we use was greater than 5s.
                            // This allows the cache a chance to reuse instead of always taking the
                            // oldest.
                            final long timeSinceLastRef = SystemClock.elapsedRealtime() -
                                    imageToUse.getLastRefAddTimestamp();
                            if (timeSinceLastRef < MIN_TIME_IN_POOL) {
                                if (LogUtil.isLoggable(LogUtil.BUGLE_IMAGE_TAG, LogUtil.VERBOSE)) {
                                    LogUtil.v(LogUtil.BUGLE_IMAGE_TAG, "Not reusing reusing " +
                                            "first available bitmap from the pool because it " +
                                            "has not been in the pool long enough. " +
                                            "timeSinceLastRef=" + timeSinceLastRef);
                                }
                                // Put back the image and return no reuseable bitmap.
                                images.addLast(imageToUse);
                                return null;
                            }

                            // Add a temp ref on the image resource so it won't be GC'd after
                            // being removed from the cache.
                            imageToUse.addRef();

                            // Remove the image resource from the image cache.
                            final ImageResource removed = remove(imageToUse.getKey());
                            Assert.isTrue(removed == imageToUse);

                            // Try to reuse the bitmap from the image resource. This will transfer
                            // ownership of the bitmap object to the caller of this method.
                            final Bitmap reusableBitmap = imageToUse.reuseBitmap();

                            imageToUse.release();
                            return reusableBitmap;
                        } finally {
                            // We are either done with the reuse operation, or decided not to use
                            // the image. Either way, release the lock.
                            imageToUse.releaseLock();
                        }
                    }
                }
            }
            return null;
        }

        /**
         * Try to locate and return a reusable bitmap from the pool, or create a new bitmap.
         * @param width desired bitmap width
         * @param height desired bitmap height
         * @return the created or reused mutable bitmap that has its background cleared to
         * {@value Color#TRANSPARENT}
         */
        public Bitmap createOrReuseBitmap(final int width, final int height) {
            return createOrReuseBitmap(width, height, Color.TRANSPARENT);
        }

        /**
         * Try to locate and return a reusable bitmap from the pool, or create a new bitmap.
         * @param width desired bitmap width
         * @param height desired bitmap height
         * @param backgroundColor the background color for the returned bitmap
         * @return the created or reused mutable bitmap with the requested background color
         */
        public Bitmap createOrReuseBitmap(final int width, final int height,
                final int backgroundColor) {
            Bitmap retBitmap = null;
            try {
                final Bitmap poolBitmap = getReusableBitmapFromPool(width, height);
                retBitmap = (poolBitmap != null) ? poolBitmap :
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                retBitmap.eraseColor(backgroundColor);
            } catch (final OutOfMemoryError e) {
                LogUtil.w(LogUtil.BUGLE_IMAGE_TAG, "PoolableImageCache:try to createOrReuseBitmap");
                Factory.get().reclaimMemory();
            }
            return retBitmap;
        }

        private void assignPoolBitmap(final BitmapFactory.Options optionsTmp, final int width,
                final int height) {
            if (optionsTmp.inJustDecodeBounds) {
                return;
            }
            optionsTmp.inBitmap = getReusableBitmapFromPool(width, height);
        }

        /**
         * @return The pool key for the provided image dimensions or 0 if either width or height is
         * greater than the max supported image dimension.
         */
        private int getPoolKey(final int width, final int height) {
            if (width > MAX_SUPPORTED_IMAGE_DIMENSION || height > MAX_SUPPORTED_IMAGE_DIMENSION) {
                return INVALID_POOL_KEY;
            }
            return (width << 16) | height;
        }

        /**
         * @return the pool key for a given image resource.
         */
        private int getPoolKey(final ImageResource imageResource) {
            if (imageResource.supportsBitmapReuse()) {
                final Bitmap bitmap = imageResource.getBitmap();
                if (bitmap != null && bitmap.isMutable()) {
                    final int width = bitmap.getWidth();
                    final int height = bitmap.getHeight();
                    if (width > 0 && height > 0) {
                        return getPoolKey(width, height);
                    }
                }
            }
            return INVALID_POOL_KEY;
        }

        /**
         * Called when bitmap reuse fails. Conditionally report the failure with statistics.
         */
        private void onFailedToReuse() {
            mFailedBitmapReuseCount++;
            if (mFailedBitmapReuseCount % FAILED_REPORTING_FREQUENCY == 0) {
                LogUtil.w(LogUtil.BUGLE_IMAGE_TAG,
                        "Pooled bitmap consistently not being reused. Failure count = " +
                                mFailedBitmapReuseCount + ", success count = " +
                                mSucceededBitmapReuseCount);
            }
        }
    }
}
