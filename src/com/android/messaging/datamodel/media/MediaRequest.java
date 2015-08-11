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
 * Keeps track of a media loading request. MediaResourceManager uses this interface to load, encode,
 * decode, and cache different types of media resource.
 *
 * This interface defines a media request class that's threading-model-blind. Wrapper classes
 * (such as {@link AsyncMediaRequestWrapper} wraps around any base media request to offer async
 * extensions).
 */
public interface MediaRequest<T extends RefCountedMediaResource> {
    public static final int REQUEST_ENCODE_MEDIA = 1;
    public static final int REQUEST_DECODE_MEDIA = 2;
    public static final int REQUEST_LOAD_MEDIA = 3;

    /**
     * Returns a unique key used for storing and looking up the MediaRequest.
     */
    String getKey();

    /**
     * This method performs the heavy-lifting work of synchronously loading the media bytes for
     * this MediaRequest on a single threaded executor.
     * @param chainedTask subsequent tasks to be performed after this request is complete. For
     * example, an image request may need to compress the image resource before putting it in the
     * cache
     */
    T loadMediaBlocking(List<MediaRequest<T>> chainedTask) throws Exception;

    /**
     * Returns the media cache where this MediaRequest wants to store the loaded
     * media resource.
     */
    MediaCache<T> getMediaCache();

    /**
     * Returns the id of the cache where this MediaRequest wants to store the loaded
     * media resource.
     */
    int getCacheId();

    /**
     * Returns the request type of this media request, i.e. one of {@link #REQUEST_ENCODE_MEDIA},
     * {@link #REQUEST_DECODE_MEDIA}, or {@link #REQUEST_LOAD_MEDIA}. The default is
     * {@link #REQUEST_LOAD_MEDIA}
     */
    int getRequestType();

    /**
     * Returns the descriptor defining the request.
     */
    MediaRequestDescriptor<T> getDescriptor();
}