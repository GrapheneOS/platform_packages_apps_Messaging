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

import com.android.messaging.Factory;

import java.util.HashSet;

/**
 * Utility abstraction which allows MemoryCaches in an application to register and then when there
 * is memory pressure provide a callback to reclaim the memory in the caches.
 */
public class MemoryCacheManager {
    private final HashSet<MemoryCache> mMemoryCaches = new HashSet<MemoryCache>();
    private final Object mMemoryCacheLock = new Object();

    public static MemoryCacheManager get() {
        return Factory.get().getMemoryCacheManager();
    }

    /**
     * Extend this interface to provide a reclaim method on a memory cache.
     */
    public interface MemoryCache {
        void reclaim();
    }

    /**
     * Register the memory cache with the application.
     */
    public void registerMemoryCache(final MemoryCache cache) {
        synchronized (mMemoryCacheLock) {
            mMemoryCaches.add(cache);
        }
    }

    /**
     * Unregister the memory cache with the application.
     */
    public void unregisterMemoryCache(final MemoryCache cache) {
        synchronized (mMemoryCacheLock) {
            mMemoryCaches.remove(cache);
        }
    }

    /**
     * Reclaim memory in all the memory caches in the application.
     */
    @SuppressWarnings("unchecked")
    public void reclaimMemory() {
        // We're creating a cache copy in the lock to ensure we're not working on a concurrently
        // modified set, then reclaim outside of the lock to minimize the time within the lock.
        final HashSet<MemoryCache> shallowCopy;
        synchronized (mMemoryCacheLock) {
            shallowCopy = (HashSet<MemoryCache>) mMemoryCaches.clone();
        }
        for (final MemoryCache cache : shallowCopy) {
            cache.reclaim();
        }
    }
}
