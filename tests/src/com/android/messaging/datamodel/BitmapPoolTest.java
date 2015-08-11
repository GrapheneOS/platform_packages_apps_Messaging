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

package com.android.messaging.datamodel;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeFactory;
import com.android.messaging.R;

import org.mockito.Mock;

import java.util.HashSet;
import java.util.Set;

@SmallTest
public class BitmapPoolTest extends BugleTestCase {
    private static final int POOL_SIZE = 5;
    private static final int IMAGE_DIM = 1;
    private static final String NAME = "BitmapPoolTest";

    @Mock private MemoryCacheManager mockMemoryCacheManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(getTestContext())
                .withMemoryCacheManager(mockMemoryCacheManager);
    }

    private Set<Bitmap> fillPoolAndGetPoolContents(final BitmapPool pool, final int width,
            final int height) {
        final Set<Bitmap> returnedBitmaps = new HashSet<Bitmap>();
        for (int i = 0; i < POOL_SIZE; i++) {
            final Bitmap temp = pool.createOrReuseBitmap(width, height);
            assertFalse(returnedBitmaps.contains(temp));
            returnedBitmaps.add(temp);
        }
        for (final Bitmap b : returnedBitmaps) {
            pool.reclaimBitmap(b);
        }
        assertTrue(pool.isFull(width, height));
        return returnedBitmaps;
    }

    public void testCreateAndPutBackInPoolTest() {
        final BitmapPool pool = new BitmapPool(POOL_SIZE, NAME);
        final Bitmap bitmap = pool.createOrReuseBitmap(IMAGE_DIM, IMAGE_DIM);
        assertFalse(bitmap.isRecycled());
        assertFalse(pool.isFull(IMAGE_DIM, IMAGE_DIM));
        pool.reclaimBitmap(bitmap);

        // Don't recycle because the pool isn't full yet.
        assertFalse(bitmap.isRecycled());
    }

    public void testCreateBeyondFullAndCheckReuseTest() {
        final BitmapPool pool = new BitmapPool(POOL_SIZE, NAME);
        final Set<Bitmap> returnedBitmaps =
                fillPoolAndGetPoolContents(pool, IMAGE_DIM, IMAGE_DIM);
        final Bitmap overflowBitmap = pool.createOrReuseBitmap(IMAGE_DIM, IMAGE_DIM);
        assertFalse(overflowBitmap.isRecycled());
        assertTrue(returnedBitmaps.contains(overflowBitmap));
    }

    /**
     * Make sure that we have the correct options to create mutable for bitmap pool reuse.
     */
    public void testAssertBitmapOptionsAreMutable() {
        final BitmapFactory.Options options =
                BitmapPool.getBitmapOptionsForPool(false, IMAGE_DIM, IMAGE_DIM);
        assertTrue(options.inMutable);
    }

    public void testDecodeFromResourceBitmap() {
        final BitmapPool pool = new BitmapPool(POOL_SIZE, NAME);
        final BitmapFactory.Options options =
                BitmapPool.getBitmapOptionsForPool(true, IMAGE_DIM, IMAGE_DIM);
        final Resources resources = getContext().getResources();
        final Bitmap resourceBitmap = pool.decodeSampledBitmapFromResource(
                R.drawable.msg_bubble_incoming, resources, options, IMAGE_DIM, IMAGE_DIM);
        assertNotNull(resourceBitmap);
        assertTrue(resourceBitmap.getByteCount() > 0);
    }
}
