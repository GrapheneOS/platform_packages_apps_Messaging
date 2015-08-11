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

public class FakeMediaCacheManager extends MediaCacheManager {
    // List of available fake cache ids.
    public static final int FAKE_IMAGE_CACHE = 1;
    public static final int FAKE_BATCH_IMAGE_CACHE = 2;

    @Override
    public MediaCache<?> createMediaCacheById(final int id) {
        switch (id) {
            case FAKE_IMAGE_CACHE:
                // Make a cache of only 3 KB of data.
                return new MediaCache<FakeImageResource>(3, FAKE_IMAGE_CACHE, "FakeImageCache");

            case FAKE_BATCH_IMAGE_CACHE:
                return new MediaCache<FakeImageResource>(10, FAKE_BATCH_IMAGE_CACHE,
                        "FakeBatchImageCache");
        }
        return null;
    }
}
