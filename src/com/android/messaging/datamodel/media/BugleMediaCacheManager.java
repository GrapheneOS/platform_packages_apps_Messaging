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

import com.android.messaging.util.Assert;

/**
 * An implementation of {@link MediaCacheManager} that creates caches specific to Bugle's needs.
 *
 * To create a new type of cache, add to the list of cache ids and create a new MediaCache<>
 * for your cache id / media resource type in createMediaCacheById().
 */
public class BugleMediaCacheManager extends MediaCacheManager {
    // List of available cache ids.
    public static final int DEFAULT_IMAGE_CACHE = 1;
    public static final int AVATAR_IMAGE_CACHE = 2;
    public static final int VCARD_CACHE = 3;

    // VCard cache size - we compute the size by count, not by bytes.
    private static final int VCARD_CACHE_SIZE = 5;
    private static final int SHARED_IMAGE_CACHE_SIZE = 1024 * 10;   // 10MB

    @Override
    protected MediaCache<?> createMediaCacheById(final int id) {
        switch (id) {
            case DEFAULT_IMAGE_CACHE:
                return new PoolableImageCache(SHARED_IMAGE_CACHE_SIZE, id, "DefaultImageCache");

            case AVATAR_IMAGE_CACHE:
                return new PoolableImageCache(id, "AvatarImageCache");

            case VCARD_CACHE:
                return new MediaCache<VCardResource>(VCARD_CACHE_SIZE, id, "VCardCache");

            default:
                Assert.fail("BugleMediaCacheManager: unsupported cache id " + id);
                break;
        }
        return null;
    }
}
