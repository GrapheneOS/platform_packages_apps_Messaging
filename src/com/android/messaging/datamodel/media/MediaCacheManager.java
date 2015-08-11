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

import android.util.SparseArray;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.MemoryCacheManager;
import com.android.messaging.datamodel.MemoryCacheManager.MemoryCache;
import com.android.messaging.datamodel.media.PoolableImageCache.ReusableImageResourcePool;

/**
 * Manages a set of media caches by id.
 */
public abstract class MediaCacheManager implements MemoryCache {
    public static MediaCacheManager get() {
        return Factory.get().getMediaCacheManager();
    }

    protected final SparseArray<MediaCache<?>> mCaches;

    public MediaCacheManager() {
        mCaches = new SparseArray<MediaCache<?>>();
        MemoryCacheManager.get().registerMemoryCache(this);
    }

    @Override
    public void reclaim() {
        final int count = mCaches.size();
        for (int i = 0; i < count; i++) {
            mCaches.valueAt(i).destroy();
        }
        mCaches.clear();
    }

    public synchronized MediaCache<?> getOrCreateMediaCacheById(final int id) {
        MediaCache<?> cache = mCaches.get(id);
        if (cache == null) {
            cache = createMediaCacheById(id);
            if (cache != null) {
                mCaches.put(id, cache);
            }
        }
        return cache;
    }

    public ReusableImageResourcePool getOrCreateBitmapPoolForCache(final int cacheId) {
        final MediaCache<?> cache = getOrCreateMediaCacheById(cacheId);
        if (cache != null && cache instanceof PoolableImageCache) {
            return ((PoolableImageCache) cache).asReusableBitmapPool();
        }
        return null;
    }

    protected abstract MediaCache<?> createMediaCacheById(final int id);
}