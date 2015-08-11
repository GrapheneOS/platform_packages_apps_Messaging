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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.SparseArray;

import com.android.messaging.datamodel.MemoryCacheManager.MemoryCache;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;

import java.io.InputStream;

/**
 * Class for creating / loading / reusing bitmaps. This class allow the user to create a new bitmap,
 * reuse an bitmap from the pool and to return a bitmap for future reuse.  The pool of bitmaps
 * allows for faster decode and more efficient memory usage.
 * Note: consumers should not create BitmapPool directly, but instead get the pool they want from
 * the BitmapPoolManager.
 */
public class BitmapPool implements MemoryCache {
    public static final int MAX_SUPPORTED_IMAGE_DIMENSION = 0xFFFF;

    protected static final boolean VERBOSE = false;

    /**
     * Number of reuse failures to skip before reporting.
     */
    private static final int FAILED_REPORTING_FREQUENCY = 100;

    /**
     * Count of reuse failures which have occurred.
     */
    private static volatile int sFailedBitmapReuseCount = 0;

    /**
     * Overall pool data structure which currently only supports rectangular bitmaps. The size of
     * one of the sides is used to index into the SparseArray.
     */
    private final SparseArray<SingleSizePool> mPool;
    private final Object mPoolLock = new Object();
    private final String mPoolName;
    private final int mMaxSize;

    /**
     * Inner structure which holds a pool of bitmaps all the same size (i.e. all have the same
     * width as each other and height as each other, but not necessarily the same).
     */
    private class SingleSizePool {
        int mNumItems;
        final Bitmap[] mBitmaps;

        SingleSizePool(final int maxPoolSize) {
            mNumItems = 0;
            mBitmaps = new Bitmap[maxPoolSize];
        }
    }

    /**
     * Creates a pool of reused bitmaps with helper decode methods which will attempt to use the
     * reclaimed bitmaps. This will help speed up the creation of bitmaps by using already allocated
     * bitmaps.
     * @param maxSize The overall max size of the pool. When the pool exceeds this size, all calls
     * to reclaimBitmap(Bitmap) will result in recycling the bitmap.
     * @param name Name of the bitmap pool and only used for logging. Can not be null.
     */
    BitmapPool(final int maxSize, @NonNull final String name) {
        Assert.isTrue(maxSize > 0);
        Assert.isTrue(!TextUtils.isEmpty(name));
        mPoolName = name;
        mMaxSize = maxSize;
        mPool = new SparseArray<SingleSizePool>();
    }

    @Override
    public void reclaim() {
        synchronized (mPoolLock) {
            for (int p = 0; p < mPool.size(); p++) {
                final SingleSizePool singleSizePool = mPool.valueAt(p);
                for (int i = 0; i < singleSizePool.mNumItems; i++) {
                    singleSizePool.mBitmaps[i].recycle();
                    singleSizePool.mBitmaps[i] = null;
                }
                singleSizePool.mNumItems = 0;
            }
            mPool.clear();
        }
    }

    /**
     * Creates a new BitmapFactory.Options.
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

    /**
     * @return The pool key for the provided image dimensions or 0 if either width or height is
     * greater than the max supported image dimension.
     */
    private int getPoolKey(final int width, final int height) {
        if (width > MAX_SUPPORTED_IMAGE_DIMENSION || height > MAX_SUPPORTED_IMAGE_DIMENSION) {
            return 0;
        }
        return (width << 16) | height;
    }

    /**
     *
     * @return A bitmap in the pool with the specified dimensions or null if no bitmap with the
     * specified dimension is available.
     */
    private Bitmap findPoolBitmap(final int width, final int height) {
        final int poolKey = getPoolKey(width, height);
        if (poolKey != 0) {
            synchronized (mPoolLock) {
                // Take a bitmap from the pool if one is available
                final SingleSizePool singlePool = mPool.get(poolKey);
                if (singlePool != null && singlePool.mNumItems > 0) {
                    singlePool.mNumItems--;
                    final Bitmap foundBitmap = singlePool.mBitmaps[singlePool.mNumItems];
                    singlePool.mBitmaps[singlePool.mNumItems] = null;
                    return foundBitmap;
                }
            }
        }
        return null;
    }

    /**
     * Internal function to try and find a bitmap in the pool which matches the desired width and
     * height and then set that in the bitmap options properly.
     *
     * TODO: Why do we take a width/height? Shouldn't this already be in the
     * BitmapFactory.Options instance? Can we assert that they match?
     * @param optionsTmp The BitmapFactory.Options to update with the bitmap for the system to try
     * to reuse.
     * @param width The width of the reusable bitmap.
     * @param height The height of the reusable bitmap.
     */
    private void assignPoolBitmap(final BitmapFactory.Options optionsTmp, final int width,
            final int height) {
        if (optionsTmp.inJustDecodeBounds) {
            return;
        }
        optionsTmp.inBitmap = findPoolBitmap(width, height);
    }

    /**
     * Load a resource into a bitmap. Uses a bitmap from the pool if possible to reduce memory
     * turnover.
     * @param resourceId Resource id to load.
     * @param resources Application resources. Cannot be null.
     * @param optionsTmp Should be the same options returned from getBitmapOptionsForPool(). Cannot
     * be null.
     * @param width The width of the bitmap.
     * @param height The height of the bitmap.
     * @return The decoded Bitmap with the resource drawn in it.
     */
    public Bitmap decodeSampledBitmapFromResource(final int resourceId,
            @NonNull final Resources resources, @NonNull final BitmapFactory.Options optionsTmp,
            final int width, final int height) {
        Assert.notNull(resources);
        Assert.notNull(optionsTmp);
        Assert.isTrue(width > 0);
        Assert.isTrue(height > 0);
        assignPoolBitmap(optionsTmp, width, height);
        Bitmap b = null;
        try {
            b = BitmapFactory.decodeResource(resources, resourceId, optionsTmp);
        } catch (final IllegalArgumentException e) {
            // BitmapFactory couldn't decode the file, try again without an inputBufferBitmap.
            if (optionsTmp.inBitmap != null) {
                optionsTmp.inBitmap = null;
                b = BitmapFactory.decodeResource(resources, resourceId, optionsTmp);
                sFailedBitmapReuseCount++;
                if (sFailedBitmapReuseCount % FAILED_REPORTING_FREQUENCY == 0) {
                    LogUtil.w(LogUtil.BUGLE_TAG,
                            "Pooled bitmap consistently not being reused count = " +
                            sFailedBitmapReuseCount);
                }
            }
        } catch (final OutOfMemoryError e) {
            LogUtil.w(LogUtil.BUGLE_TAG, "Oom decoding resource " + resourceId);
            reclaim();
        }
        return b;
    }

    /**
     * Load an input stream into a bitmap. Uses a bitmap from the pool if possible to reduce memory
     * turnover.
     * @param inputStream InputStream load. Cannot be null.
     * @param optionsTmp Should be the same options returned from getBitmapOptionsForPool(). Cannot
     * be null.
     * @param width The width of the bitmap.
     * @param height The height of the bitmap.
     * @return The decoded Bitmap with the resource drawn in it.
     */
    public Bitmap decodeSampledBitmapFromInputStream(@NonNull final InputStream inputStream,
            @NonNull final BitmapFactory.Options optionsTmp,
            final int width, final int height) {
        Assert.notNull(inputStream);
        Assert.isTrue(width > 0);
        Assert.isTrue(height > 0);
        assignPoolBitmap(optionsTmp, width, height);
        Bitmap b = null;
        try {
            b = BitmapFactory.decodeStream(inputStream, null, optionsTmp);
        } catch (final IllegalArgumentException e) {
            // BitmapFactory couldn't decode the file, try again without an inputBufferBitmap.
            if (optionsTmp.inBitmap != null) {
                optionsTmp.inBitmap = null;
                b = BitmapFactory.decodeStream(inputStream, null, optionsTmp);
                sFailedBitmapReuseCount++;
                if (sFailedBitmapReuseCount % FAILED_REPORTING_FREQUENCY == 0) {
                    LogUtil.w(LogUtil.BUGLE_TAG,
                            "Pooled bitmap consistently not being reused count = " +
                            sFailedBitmapReuseCount);
                }
            }
        } catch (final OutOfMemoryError e) {
            LogUtil.w(LogUtil.BUGLE_TAG, "Oom decoding inputStream");
            reclaim();
        }
        return b;
    }

    /**
     * Turn encoded bytes into a bitmap. Uses a bitmap from the pool if possible to reduce memory
     * turnover.
     * @param bytes Encoded bytes to draw on the bitmap. Cannot be null.
     * @param optionsTmp The bitmap will set here and the input should be generated from
     * getBitmapOptionsForPool(). Cannot be null.
     * @param width The width of the bitmap.
     * @param height The height of the bitmap.
     * @return A Bitmap with the encoded bytes drawn in it.
     */
    public Bitmap decodeByteArray(@NonNull final byte[] bytes,
            @NonNull final BitmapFactory.Options optionsTmp, final int width,
            final int height) throws OutOfMemoryError {
        Assert.notNull(bytes);
        Assert.notNull(optionsTmp);
        Assert.isTrue(width > 0);
        Assert.isTrue(height > 0);
        assignPoolBitmap(optionsTmp, width, height);
        Bitmap b = null;
        try {
            b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, optionsTmp);
        } catch (final IllegalArgumentException e) {
            if (VERBOSE) {
                LogUtil.v(LogUtil.BUGLE_TAG, "BitmapPool(" + mPoolName +
                        ") Unable to use pool bitmap");
            }
            // BitmapFactory couldn't decode the file, try again without an inputBufferBitmap.
            // (i.e. without the bitmap from the pool)
            if (optionsTmp.inBitmap != null) {
                optionsTmp.inBitmap = null;
                b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, optionsTmp);
                sFailedBitmapReuseCount++;
                if (sFailedBitmapReuseCount % FAILED_REPORTING_FREQUENCY == 0) {
                    LogUtil.w(LogUtil.BUGLE_TAG,
                            "Pooled bitmap consistently not being reused count = " +
                            sFailedBitmapReuseCount);
                }
            }
        }
        return b;
    }

    /**
     * Creates a bitmap with the given size, this will reuse a bitmap in the pool, if one is
     * available, otherwise this will create a new one.
     * @param width The desired width of the bitmap.
     * @param height The desired height of the bitmap.
     * @return A bitmap with the desired width and height, this maybe a reused bitmap from the pool.
     */
    public Bitmap createOrReuseBitmap(final int width, final int height) {
        Bitmap b = findPoolBitmap(width, height);
        if (b == null) {
            b = createBitmap(width, height);
        }
        return b;
    }

    /**
     * This will create a new bitmap regardless of pool state.
     * @param width The desired width of the bitmap.
     * @param height The desired height of the bitmap.
     * @return A bitmap with the desired width and height.
     */
    private Bitmap createBitmap(final int width, final int height) {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    /**
     * Called when a bitmap is finished being used so that it can be used for another bitmap in the
     * future or recycled. Any bitmaps returned should not be used by the caller again.
     * @param b The bitmap to return to the pool for future usage or recycled. This cannot be null.
     */
    public void reclaimBitmap(@NonNull final Bitmap b) {
        Assert.notNull(b);
        final int poolKey = getPoolKey(b.getWidth(), b.getHeight());
        if (poolKey == 0 || !b.isMutable()) {
            // Unsupported image dimensions or a immutable bitmap.
            b.recycle();
            return;
        }
        synchronized (mPoolLock) {
            SingleSizePool singleSizePool = mPool.get(poolKey);
            if (singleSizePool == null) {
                singleSizePool = new SingleSizePool(mMaxSize);
                mPool.append(poolKey, singleSizePool);
            }
            if (singleSizePool.mNumItems < singleSizePool.mBitmaps.length) {
                singleSizePool.mBitmaps[singleSizePool.mNumItems] = b;
                singleSizePool.mNumItems++;
            } else {
                b.recycle();
            }
        }
    }

    /**
     * @return whether the pool is full for a given width and height.
     */
    public boolean isFull(final int width, final int height) {
        final int poolKey = getPoolKey(width, height);
        synchronized (mPoolLock) {
            final SingleSizePool singleSizePool = mPool.get(poolKey);
            if (singleSizePool != null &&
                    singleSizePool.mNumItems >= singleSizePool.mBitmaps.length) {
                return true;
            }
            return false;
        }
    }
}
