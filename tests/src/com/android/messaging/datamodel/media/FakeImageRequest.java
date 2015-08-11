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

import android.text.TextUtils;

import java.util.List;

public class FakeImageRequest implements MediaRequest<FakeImageResource> {
    public static final String INVALID_KEY = "invalid";
    private final String mKey;
    private final int mSize;

    public FakeImageRequest(final String key, final int size) {
        mKey = key;
        mSize = size;
    }

    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public FakeImageResource loadMediaBlocking(List<MediaRequest<FakeImageResource>> chainedTask)
            throws Exception {
        if (TextUtils.equals(mKey, INVALID_KEY)) {
            throw new Exception();
        } else {
            return new FakeImageResource(mSize, mKey);
        }
    }

    @Override
    public int getCacheId() {
        return FakeMediaCacheManager.FAKE_IMAGE_CACHE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public MediaCache<FakeImageResource> getMediaCache() {
        return (MediaCache<FakeImageResource>) MediaCacheManager.get().getOrCreateMediaCacheById(
                getCacheId());
    }

    @Override
    public int getRequestType() {
        return MediaRequest.REQUEST_LOAD_MEDIA;
    }

    @Override
    public MediaRequestDescriptor<FakeImageResource> getDescriptor() {
        return null;
    }
}
