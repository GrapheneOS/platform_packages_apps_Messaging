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

import android.os.AsyncTask;

import com.android.messaging.Factory;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.RunsOnAnyThread;
import com.android.messaging.util.LogUtil;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * <p>Loads and maintains a set of in-memory LRU caches for different types of media resources.
 * Right now we don't utilize any disk cache as all media urls are expected to be resolved to
 * local content.<p/>
 *
 * <p>The MediaResourceManager takes media loading requests through one of two ways:</p>
 *
 * <ol>
 * <li>{@link #requestMediaResourceAsync(MediaRequest)} that takes a MediaRequest, which may be a
 *  regular request if the caller doesn't want to listen for events (fire-and-forget),
 *  or an async request wrapper if event callback is needed.</li>
 * <li>{@link #requestMediaResourceSync(MediaRequest)} which takes a MediaRequest and synchronously
 *  returns the loaded result, or null if failed.</li>
 * </ol>
 *
 * <p>For each media loading task, MediaResourceManager starts an AsyncTask that runs on a
 * dedicated thread, which calls MediaRequest.loadMediaBlocking() to perform the actual media
 * loading work. As the media resources are loaded, MediaResourceManager notifies the callers
 * (which must implement the MediaResourceLoadListener interface) via onMediaResourceLoaded()
 * callback. Meanwhile, MediaResourceManager also pushes the loaded resource onto its dedicated
 * cache.</p>
 *
 * <p>The media resource caches ({@link MediaCache}) are maintained as a set of LRU caches. They are
 * created on demand by the incoming MediaRequest's getCacheId() method. The implementations of
 * MediaRequest (such as {@link ImageRequest}) get to determine the desired cache id. For Bugle,
 * the list of available caches are in {@link BugleMediaCacheManager}</p>
 *
 * <p>Optionally, media loading can support on-demand media encoding and decoding.
 * All {@link MediaRequest}'s can opt to chain additional {@link MediaRequest}'s to be executed
 * after the completion of the main media loading task, by adding new tasks to the chained
 * task list in {@link MediaRequest#loadMediaBlocking(List)}. One possible type of chained task is
 * media encoding task. Loaded media will be encoded on a dedicated single threaded executor
 * *after* the UI is notified of the loaded media. In this case, the encoded media resource will
 * be eventually pushed to the cache, which will later be decoded before posting to the UI thread
 * on cache hit.</p>
 *
 * <p><b>To add support for a new type of media resource,</b></p>
 *
 * <ol>
 * <li>Create a new subclass of {@link RefCountedMediaResource} for the new resource type (example:
 *    {@link ImageResource} class).</li>
 *
 * <li>Implement the {@link MediaRequest} interface (example: {@link ImageRequest}). Perform the
 *    media loading work in loadMediaBlocking() and return a cache id in getCacheId().</li>
 *
 * <li>For the UI component that requests the media resource, let it implement
 *    {@link MediaResourceLoadListener} interface to listen for resource load callback. Let the
 *    UI component call MediaResourceManager.requestMediaResourceAsync() to request a media source.
 *    (example: {@link com.android.messaging.ui.ContactIconView}</li>
 * </ol>
 */
public class MediaResourceManager {
    private static final String TAG = LogUtil.BUGLE_TAG;

    public static MediaResourceManager get() {
        return Factory.get().getMediaResourceManager();
    }

    /**
     * Listener for asynchronous callback from media loading events.
     */
    public interface MediaResourceLoadListener<T extends RefCountedMediaResource> {
        void onMediaResourceLoaded(MediaRequest<T> request, T resource, boolean cached);
        void onMediaResourceLoadError(MediaRequest<T> request, Exception exception);
    }

    // We use a fixed thread pool for handling media loading tasks. Using a cached thread pool
    // allows for unlimited thread creation which can lead to OOMs so we limit the threads here.
    private static final Executor MEDIA_LOADING_EXECUTOR = Executors.newFixedThreadPool(10);

    // A dedicated single thread executor for performing background task after loading the resource
    // on the media loading executor. This includes work such as encoding loaded media to be cached.
    // These tasks are run on a single worker thread with low priority so as not to contend with the
    // media loading tasks.
    private static final Executor MEDIA_BACKGROUND_EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(final Runnable runnable) {
                    final Thread encodingThread = new Thread(runnable);
                    encodingThread.setPriority(Thread.MIN_PRIORITY);
                    return encodingThread;
                }
            });

    /**
     * Requests a media resource asynchronously. Upon completion of the media loading task,
     * the listener will be notified of success/failure iff it's still bound. A refcount on the
     * resource is held and guaranteed for the caller for the duration of the
     * {@link MediaResourceLoadListener#onMediaResourceLoaded(
     * MediaRequest, RefCountedMediaResource, boolean)} callback.
     * @param mediaRequest the media request. May be either an
     * {@link AsyncMediaRequestWrapper} for listening for event callbacks, or a regular media
     * request for fire-and-forget type of behavior.
     */
    public <T extends RefCountedMediaResource> void requestMediaResourceAsync(
            final MediaRequest<T> mediaRequest) {
        scheduleAsyncMediaRequest(mediaRequest, MEDIA_LOADING_EXECUTOR);
    }

    /**
     * Requests a media resource synchronously.
     * @return the loaded resource with a refcount reserved for the caller. The caller must call
     * release() on the resource once it's done using it (like with Cursors).
     */
    public <T extends RefCountedMediaResource> T requestMediaResourceSync(
            final MediaRequest<T> mediaRequest) {
        Assert.isNotMainThread();
        // Block and load media.
        MediaLoadingResult<T> loadResult = null;
        try {
            loadResult = processMediaRequestInternal(mediaRequest);
            // The loaded resource should have at least one refcount by now reserved for the caller.
            Assert.isTrue(loadResult.loadedResource.getRefCount() > 0);
            return loadResult.loadedResource;
        } catch (final Exception e) {
            LogUtil.e(LogUtil.BUGLE_TAG, "Synchronous media loading failed, key=" +
                    mediaRequest.getKey(), e);
            return null;
        } finally {
            if (loadResult != null) {
                // Schedule the background requests chained to the main request.
                loadResult.scheduleChainedRequests();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends RefCountedMediaResource> MediaLoadingResult<T> processMediaRequestInternal(
            final MediaRequest<T> mediaRequest)
                    throws Exception {
        final List<MediaRequest<T>> chainedRequests = new ArrayList<>();
        T loadedResource = null;
        // Try fetching from cache first.
        final T cachedResource = loadMediaFromCache(mediaRequest);
        if (cachedResource != null) {
            if (cachedResource.isEncoded()) {
                // The resource is encoded, issue a decoding request.
                final MediaRequest<T> decodeRequest = (MediaRequest<T>) cachedResource
                        .getMediaDecodingRequest(mediaRequest);
                Assert.notNull(decodeRequest);
                cachedResource.release();
                loadedResource = loadMediaFromRequest(decodeRequest, chainedRequests);
            } else {
                // The resource is ready-to-use.
                loadedResource = cachedResource;
            }
        } else {
            // Actually load the media after cache miss.
            loadedResource = loadMediaFromRequest(mediaRequest, chainedRequests);
        }
        return new MediaLoadingResult<>(loadedResource, cachedResource != null /* fromCache */,
                chainedRequests);
    }

    private <T extends RefCountedMediaResource> T loadMediaFromCache(
            final MediaRequest<T> mediaRequest) {
        if (mediaRequest.getRequestType() != MediaRequest.REQUEST_LOAD_MEDIA) {
            // Only look up in the cache if we are loading media.
            return null;
        }
        final MediaCache<T> mediaCache = mediaRequest.getMediaCache();
        if (mediaCache != null) {
            final T mediaResource = mediaCache.fetchResourceFromCache(mediaRequest.getKey());
            if (mediaResource != null) {
                return mediaResource;
            }
        }
        return null;
    }

    private <T extends RefCountedMediaResource> T loadMediaFromRequest(
            final MediaRequest<T> mediaRequest, final List<MediaRequest<T>> chainedRequests)
                    throws Exception {
        final T resource = mediaRequest.loadMediaBlocking(chainedRequests);
        // mediaRequest.loadMediaBlocking() should never return null without
        // throwing an exception.
        Assert.notNull(resource);
        // It's possible for the media to be evicted right after it's added to
        // the cache (possibly because it's by itself too big for the cache).
        // It's also possible that, after added to the cache, something else comes
        // to the cache and evicts this media resource. To prevent this from
        // recycling the underlying resource objects, make sure to add ref before
        // adding to cache so that the caller is guaranteed a ref on the resource.
        resource.addRef();
        // Don't cache the media request if it is defined as non-cacheable.
        if (resource.isCacheable()) {
            addResourceToMemoryCache(mediaRequest, resource);
        }
        return resource;
    }

    /**
     * Schedule an async media request on the given <code>executor</code>.
     * @param mediaRequest the media request to be processed asynchronously. May be either an
     * {@link AsyncMediaRequestWrapper} for listening for event callbacks, or a regular media
     * request for fire-and-forget type of behavior.
     */
    private <T extends RefCountedMediaResource> void scheduleAsyncMediaRequest(
            final MediaRequest<T> mediaRequest, final Executor executor) {
        final BindableMediaRequest<T> bindableRequest =
                (mediaRequest instanceof BindableMediaRequest<?>) ?
                        (BindableMediaRequest<T>) mediaRequest : null;
        if (bindableRequest != null && !bindableRequest.isBound()) {
            return; // Request is obsolete
        }
        // We don't use SafeAsyncTask here since it enforces the shared thread pool executor
        // whereas we want a dedicated thread pool executor.
        AsyncTask<Void, Void, MediaLoadingResult<T>> mediaLoadingTask =
                new AsyncTask<Void, Void, MediaLoadingResult<T>>() {
            private Exception mException;

            @Override
            protected MediaLoadingResult<T> doInBackground(Void... params) {
                // Double check the request is still valid by the time we start processing it
                if (bindableRequest != null && !bindableRequest.isBound()) {
                    return null; // Request is obsolete
                }
                try {
                    return processMediaRequestInternal(mediaRequest);
                } catch (Exception e) {
                    mException = e;
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final MediaLoadingResult<T> result) {
                if (result != null) {
                    Assert.isNull(mException);
                    Assert.isTrue(result.loadedResource.getRefCount() > 0);
                    try {
                        if (bindableRequest != null) {
                            bindableRequest.onMediaResourceLoaded(
                                    bindableRequest, result.loadedResource, result.fromCache);
                        }
                    } finally {
                        result.loadedResource.release();
                        result.scheduleChainedRequests();
                    }
                } else if (mException != null) {
                    LogUtil.e(LogUtil.BUGLE_TAG, "Asynchronous media loading failed, key=" +
                            mediaRequest.getKey(), mException);
                    if (bindableRequest != null) {
                        bindableRequest.onMediaResourceLoadError(bindableRequest, mException);
                    }
                } else {
                    Assert.isTrue(bindableRequest == null || !bindableRequest.isBound());
                    if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                        LogUtil.v(TAG, "media request not processed, no longer bound; key=" +
                                LogUtil.sanitizePII(mediaRequest.getKey()) /* key with phone# */);
                    }
                }
            }
        };
        mediaLoadingTask.executeOnExecutor(executor, (Void) null);
    }

    @VisibleForTesting
    @RunsOnAnyThread
    <T extends RefCountedMediaResource> void addResourceToMemoryCache(
            final MediaRequest<T> mediaRequest, final T mediaResource) {
        Assert.isTrue(mediaResource != null);
        final MediaCache<T> mediaCache = mediaRequest.getMediaCache();
        if (mediaCache != null) {
            mediaCache.addResourceToCache(mediaRequest.getKey(), mediaResource);
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "added media resource to " + mediaCache.getName() + ". key=" +
                        LogUtil.sanitizePII(mediaRequest.getKey()) /* key can contain phone# */);
            }
        }
    }

    private class MediaLoadingResult<T extends RefCountedMediaResource> {
        public final T loadedResource;
        public final boolean fromCache;
        private final List<MediaRequest<T>> mChainedRequests;

        MediaLoadingResult(final T loadedResource, final boolean fromCache,
                final List<MediaRequest<T>> chainedRequests) {
            this.loadedResource = loadedResource;
            this.fromCache = fromCache;
            mChainedRequests = chainedRequests;
        }

        /**
         * Asynchronously schedule a list of chained requests on the background thread.
         */
        public void scheduleChainedRequests() {
            for (final MediaRequest<T> mediaRequest : mChainedRequests) {
                scheduleAsyncMediaRequest(mediaRequest, MEDIA_BACKGROUND_EXECUTOR);
            }
        }
    }
}
