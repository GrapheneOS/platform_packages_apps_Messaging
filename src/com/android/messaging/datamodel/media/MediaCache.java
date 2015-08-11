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

import android.util.LruCache;

import com.android.messaging.util.LogUtil;

/**
 * A modified LruCache that is able to hold RefCountedMediaResource instances. It releases
 * ref on the entries as they are evicted from the cache, and it uses the media resource
 * size in kilobytes, instead of the entry count, as the size of the cache.
 *
 * This class is used by the MediaResourceManager class to maintain a number of caches for
 * holding different types of {@link RefCountedMediaResource}
 */
public class MediaCache<T extends RefCountedMediaResource> extends LruCache<String, T> {
    private static final String TAG = LogUtil.BUGLE_IMAGE_TAG;

    // Default memory cache size in kilobytes
    protected static final int DEFAULT_MEDIA_RESOURCE_CACHE_SIZE_IN_KILOBYTES = 1024 * 5;  // 5MB

    // Unique identifier for the cache.
    private final int mId;
    // Descriptive name given to the cache for debugging purposes.
    private final String mName;

    // Convenience constructor that uses the default cache size.
    public MediaCache(final int id, final String name) {
        this(DEFAULT_MEDIA_RESOURCE_CACHE_SIZE_IN_KILOBYTES, id, name);
    }

    public MediaCache(final int maxSize, final int id, final String name) {
        super(maxSize);
        mId = id;
        mName = name;
    }

    public void destroy() {
        evictAll();
    }

    public String getName() {
        return mName;
    }

    public int getId() {
        return mId;
    }

    /**
     * Gets a media resource from this cache. Must use this method to get resource instead of get()
     * to ensure addRef() on the resource.
     */
    public synchronized T fetchResourceFromCache(final String key) {
        final T ret = get(key);
        if (ret != null) {
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "cache hit in mediaCache @ " + getName() +
                        ", total cache hit = " + hitCount() +
                        ", total cache miss = " + missCount());
            }
            ret.addRef();
        } else if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "cache miss in mediaCache @ " + getName() +
                    ", total cache hit = " + hitCount() +
                    ", total cache miss = " + missCount());
        }
        return ret;
    }

    /**
     * Add a media resource to this cache. Must use this method to add resource instead of put()
     * to ensure addRef() on the resource.
     */
    public synchronized T addResourceToCache(final String key, final T mediaResource) {
        mediaResource.addRef();
        return put(key, mediaResource);
    }

    /**
     * Notify the removed entry that is no longer being cached
     */
    @Override
    protected synchronized void entryRemoved(final boolean evicted, final String key,
            final T oldValue, final T newValue) {
        oldValue.release();
    }

    /**
     * Measure item size in kilobytes rather than units which is more practical
     * for a media resource cache
     */
    @Override
    protected int sizeOf(final String key, final T value) {
        final int mediaSizeInKilobytes = value.getMediaSize() / 1024;
        // Never zero-count any resource, count as at least 1KB.
        return mediaSizeInKilobytes == 0 ? 1 : mediaSizeInKilobytes;
    }
}