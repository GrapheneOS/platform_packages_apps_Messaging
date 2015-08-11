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

import java.util.List;

/**
 * Holds cached information of VCard contact info.
 * The temporarily persisted avatar icon Uri is tied to the VCardResource. As a result, whenever
 * the VCardResource is no longer used (i.e. close() is called), we need to asynchronously
 * delete the avatar image from temp storage since no one will have reference to the avatar Uri
 * again. The next time the same VCard is displayed, since the old resource has been evicted from
 * the memory cache, we'll load and persist the avatar icon again.
 */
public class VCardResource extends RefCountedMediaResource {
    private final List<VCardResourceEntry> mVCards;

    public VCardResource(final String key, final List<VCardResourceEntry> vcards) {
        super(key);
        mVCards = vcards;
    }

    public List<VCardResourceEntry> getVCards() {
        return mVCards;
    }

    @Override
    public int getMediaSize() {
        // Instead of track VCards by size in kilobytes, we track them by count.
        return 0;
    }

    @Override
    protected void close() {
        for (final VCardResourceEntry vcard : mVCards) {
            vcard.close();
        }
    }
}
