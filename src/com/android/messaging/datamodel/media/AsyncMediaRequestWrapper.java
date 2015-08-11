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

import com.android.messaging.datamodel.media.MediaResourceManager.MediaResourceLoadListener;

import java.util.List;

/**
 * A mix-in style class that wraps around a normal, threading-agnostic MediaRequest object with
 * functionalities offered by {@link BindableMediaRequest} to allow for async processing.
 */
class AsyncMediaRequestWrapper<T extends RefCountedMediaResource> extends BindableMediaRequest<T> {

    /**
     * Create a new async media request wrapper instance given the listener.
     */
    public static <T extends RefCountedMediaResource> AsyncMediaRequestWrapper<T>
            createWith(final MediaRequest<T> wrappedRequest,
                    final MediaResourceLoadListener<T> listener) {
        return new AsyncMediaRequestWrapper<T>(listener, wrappedRequest);
    }

    private final MediaRequest<T> mWrappedRequest;

    private AsyncMediaRequestWrapper(final MediaResourceLoadListener<T> listener,
            final MediaRequest<T> wrappedRequest) {
        super(listener);
        mWrappedRequest = wrappedRequest;
    }

    @Override
    public String getKey() {
        return mWrappedRequest.getKey();
    }

    @Override
    public MediaCache<T> getMediaCache() {
        return mWrappedRequest.getMediaCache();
    }

    @Override
    public int getRequestType() {
        return mWrappedRequest.getRequestType();
    }

    @Override
    public T loadMediaBlocking(List<MediaRequest<T>> chainedTask) throws Exception {
        return mWrappedRequest.loadMediaBlocking(chainedTask);
    }

    @Override
    public int getCacheId() {
        return mWrappedRequest.getCacheId();
    }

    @Override
    public MediaRequestDescriptor<T> getDescriptor() {
        return mWrappedRequest.getDescriptor();
    }
}