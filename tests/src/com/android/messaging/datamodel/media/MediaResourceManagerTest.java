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

import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.MemoryCacheManager;
import com.android.messaging.datamodel.media.MediaResourceManager.MediaResourceLoadListener;

import java.util.concurrent.CountDownLatch;

@SmallTest
public class MediaResourceManagerTest extends BugleTestCase {
    private static final int KB = 1024;

    // Loaded image resource from the MediaResourceManager callback.
    private FakeImageResource mImageResource;
    private BindableMediaRequest<FakeImageResource> mImageRequest;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(getTestContext())
                .withMemoryCacheManager(new MemoryCacheManager())
                .withMediaCacheManager(new FakeMediaCacheManager());
    }

    public void testLoadFromCache() {
        final MediaResourceManager mediaResourceManager =
                new MediaResourceManager();
        MediaCacheManager.get().reclaim();
        assertNotNull(mediaResourceManager);

        // Load one image of 1KB
        loadImage(mediaResourceManager, "image1", 1 * KB, false /* shouldBeCached */, false);
        assertEquals("image1", mImageResource.getImageId());
        final FakeImageResource loadedResource = mImageResource;

        // Load the same image.
        loadImage(mediaResourceManager, "image1", 1 * KB, true /* shouldBeCached */, false);
        assertEquals(loadedResource, mImageResource);
    }

    public void testCacheEviction() {
        final MediaResourceManager mediaResourceManager =
                new MediaResourceManager();
        MediaCacheManager.get().reclaim();
        assertNotNull(mediaResourceManager);

        // Load one image of 1KB
        loadImage(mediaResourceManager, "image1", 1 * KB, false /* shouldBeCached */, false);
        assertEquals("image1", mImageResource.getImageId());

        // Load another image
        loadImage(mediaResourceManager, "image2", 2 * KB, false /* shouldBeCached */, false);
        assertEquals("image2", mImageResource.getImageId());

        // Load another image. This should fill the cache and cause eviction of image1.
        loadImage(mediaResourceManager, "image3", 2 * KB, false /* shouldBeCached */, false);
        assertEquals("image3", mImageResource.getImageId());

        // Load image1. It shouldn't be cached any more.
        loadImage(mediaResourceManager, "image1", 1 * KB, false /* shouldBeCached */, false);
        assertEquals("image1", mImageResource.getImageId());
    }

    public void testReclaimMemoryFromMediaCache() {
        final MediaResourceManager mediaResourceManager =
                new MediaResourceManager();
        MediaCacheManager.get().reclaim();
        assertNotNull(mediaResourceManager);

        // Load one image of 1KB
        loadImage(mediaResourceManager, "image1", 1 * KB, false /* shouldBeCached */, false);
        assertEquals("image1", mImageResource.getImageId());

        // Purge everything from the cache, now the image should no longer be cached.
        MediaCacheManager.get().reclaim();

        // The image resource should have no ref left.
        assertEquals(0, mImageResource.getRefCount());
        assertTrue(mImageResource.isClosed());
        loadImage(mediaResourceManager, "image1", 1 * KB, false /* shouldBeCached */, false);
        assertEquals("image1", mImageResource.getImageId());
    }

    public void testLoadInvalidImage() {
        final MediaResourceManager mediaResourceManager =
                new MediaResourceManager();
        MediaCacheManager.get().reclaim();
        assertNotNull(mediaResourceManager);

        // Test the failure case with invalid resource.
        loadImage(mediaResourceManager, FakeImageRequest.INVALID_KEY, 1 * KB, false,
                true /* shouldFail */);
    }

    public void testLoadImageSynchronously() {
        final MediaResourceManager mediaResourceManager =
                new MediaResourceManager();
        MediaCacheManager.get().reclaim();
        assertNotNull(mediaResourceManager);

        // Test a normal sync load.
        final FakeImageRequest request = new FakeImageRequest("image1", 1 * KB);
        final FakeImageResource resource = mediaResourceManager.requestMediaResourceSync(request);
        assertNotNull(resource);
        assertFalse(resource.isClosed());
        assertNotSame(0, resource.getRefCount());
        resource.release();

        // Test a failed sync load.
        final FakeImageRequest invalidRequest =
                new FakeImageRequest(FakeImageRequest.INVALID_KEY, 1 * KB);
        assertNull(mediaResourceManager.requestMediaResourceSync(invalidRequest));
    }

    private void loadImage(final MediaResourceManager manager, final String key,
            final int size, final boolean shouldBeCached, final boolean shouldFail) {
        try {
            final CountDownLatch signal = new CountDownLatch(1);
            mImageRequest = AsyncMediaRequestWrapper.createWith(new FakeImageRequest(key, size),
                    createAssertListener(shouldBeCached, shouldFail, signal));
            mImageRequest.bind("1");
            manager.requestMediaResourceAsync(mImageRequest);

            // Wait for the asynchronous callback before proceeding.
            signal.await();
        } catch (final InterruptedException e) {
            fail("Something interrupted the signal await.");
        }
    }

    private MediaResourceLoadListener<FakeImageResource> createAssertListener(
            final boolean shouldBeCached, final boolean shouldFail, final CountDownLatch signal) {
        return new MediaResourceLoadListener<FakeImageResource>() {
            @Override
            public void onMediaResourceLoaded(final MediaRequest<FakeImageResource> request,
                    final FakeImageResource resource, final boolean isCached) {
                assertEquals(mImageRequest, request);
                assertNotNull(resource);
                assertFalse(resource.isClosed());
                assertNotSame(0, resource.getRefCount());
                assertFalse(shouldFail);
                assertEquals(shouldBeCached, resource.getCached());
                resource.setCached(true);
                mImageResource = resource;
                signal.countDown();
            }

            @Override
            public void onMediaResourceLoadError(
                    final MediaRequest<FakeImageResource> request, final Exception exception) {
                assertTrue(shouldFail);
                mImageResource = null;
                signal.countDown();
            }};
    }
}
