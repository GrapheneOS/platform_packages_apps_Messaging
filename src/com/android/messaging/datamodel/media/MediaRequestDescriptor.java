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

import android.content.Context;

import com.android.messaging.datamodel.media.MediaResourceManager.MediaResourceLoadListener;

/**
 * The base data holder/builder class for constructing async/sync MediaRequest objects during
 * runtime.
 */
public abstract class MediaRequestDescriptor<T extends RefCountedMediaResource> {
    public abstract MediaRequest<T> buildSyncMediaRequest(Context context);

    /**
     * Builds an async media request to be used with
     * {@link MediaResourceManager#requestMediaResourceAsync(MediaRequest)}
     */
    public BindableMediaRequest<T> buildAsyncMediaRequest(final Context context,
            final MediaResourceLoadListener<T> listener) {
        final MediaRequest<T> syncRequest = buildSyncMediaRequest(context);
        return AsyncMediaRequestWrapper.createWith(syncRequest, listener);
    }
}
