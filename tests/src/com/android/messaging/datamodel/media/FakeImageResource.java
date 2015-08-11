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

public class FakeImageResource extends RefCountedMediaResource {
    private boolean mClosed = false;
    private boolean mCached = false;
    private final int mSize;
    private final String mImageId;

    public FakeImageResource(final int size, final String imageId) {
        super(null);
        mSize = size;
        mImageId = imageId;
    }

    public boolean isClosed() {
        return mClosed;
    }

    public String getImageId() {
        return mImageId;
    }

    public void setCached(final boolean cached) {
        mCached = cached;
    }

    public boolean getCached() {
        return mCached;
    }

    @Override
    public int getMediaSize() {
        return mSize;
    }

    @Override
    protected void close() {
        mClosed = true;
    }
}
