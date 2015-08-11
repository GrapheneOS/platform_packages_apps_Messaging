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

import com.android.messaging.datamodel.binding.BindableOnceData;
import com.android.messaging.datamodel.media.MediaResourceManager.MediaResourceLoadListener;

/**
 * The {@link MediaRequest} interface is threading-model-blind, allowing the implementations to
 * be processed synchronously or asynchronously.
 * This is a {@link MediaRequest} implementation that includes functionalities such as binding and
 * event callbacks for multi-threaded media request processing.
 */
public abstract class BindableMediaRequest<T extends RefCountedMediaResource>
        extends BindableOnceData
        implements MediaRequest<T>, MediaResourceLoadListener<T> {
    private MediaResourceLoadListener<T> mListener;

    public BindableMediaRequest(final MediaResourceLoadListener<T> listener) {
        mListener = listener;
    }

    /**
     * Delegates the media resource callback to the listener. Performs binding check to ensure
     * the listener is still bound to this request.
     */
    @Override
    public void onMediaResourceLoaded(final MediaRequest<T> request, final T resource,
            final boolean cached) {
        if (isBound() && mListener != null) {
            mListener.onMediaResourceLoaded(request, resource, cached);
        }
    }

    /**
     * Delegates the media resource callback to the listener. Performs binding check to ensure
     * the listener is still bound to this request.
     */
    @Override
    public void onMediaResourceLoadError(final MediaRequest<T> request, final Exception exception) {
        if (isBound() && mListener != null) {
            mListener.onMediaResourceLoadError(request, exception);
        }
    }

    @Override
    protected void unregisterListeners() {
        mListener = null;
    }
}
