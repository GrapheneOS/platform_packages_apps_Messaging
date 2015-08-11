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

import android.content.ContentResolver;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.MemoryCacheManager;
import com.android.messaging.util.ImageUtils;

import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.Spy;

import java.io.IOException;

@SmallTest
public class ImageRequestTest extends BugleTestCase {
    private static final int DOWNSAMPLE_IMAGE_SIZE = 2;

    @Spy protected ImageUtils spyImageUtils;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(getTestContext())
                   .withMemoryCacheManager(new MemoryCacheManager())
                   .withMediaCacheManager(new BugleMediaCacheManager());
        spyImageUtils = Mockito.spy(new ImageUtils());
        ImageUtils.set(spyImageUtils);
    }

    public void testLoadImageUnspecifiedSize() {
        final String uriString = ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                getContext().getPackageName() + "/" + R.drawable.ic_audio_light;
        final Uri uri = Uri.parse(uriString);
        final UriImageRequest imageRequest = new UriImageRequest(getContext(),
                new UriImageRequestDescriptor(uri));
        try {
            final ImageResource imageResource = imageRequest.loadMediaBlocking(null);
            final ArgumentCaptor<BitmapFactory.Options> options =
                    ArgumentCaptor.forClass(BitmapFactory.Options.class);
            Mockito.verify(spyImageUtils).calculateInSampleSize(
                    options.capture(),
                    Matchers.eq(ImageRequest.UNSPECIFIED_SIZE),
                    Matchers.eq(ImageRequest.UNSPECIFIED_SIZE));
            assertEquals(1, options.getValue().inSampleSize);
            assertNotNull(imageResource);
            assertNotNull(imageResource.getBitmap());

            // Make sure there's no scaling on the bitmap.
            final int bitmapWidth = imageResource.getBitmap().getWidth();
            final int bitmapHeight = imageResource.getBitmap().getHeight();
            assertEquals(options.getValue().outWidth, bitmapWidth);
            assertEquals(options.getValue().outHeight, bitmapHeight);
        } catch (final IOException e) {
            fail("IO exception while trying to load image resource");
        }
    }

    public void testLoadImageWithDownsampling() {
        final String uriString = ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                getContext().getPackageName() + "/" + R.drawable.ic_audio_light;
        final Uri uri = Uri.parse(uriString);
        final UriImageRequest imageRequest = new UriImageRequest(getContext(),
                new UriImageRequestDescriptor(uri, DOWNSAMPLE_IMAGE_SIZE, DOWNSAMPLE_IMAGE_SIZE,
                        false, true /* isStatic */, false /* cropToCircle */,
                        ImageUtils.DEFAULT_CIRCLE_BACKGROUND_COLOR /* circleBackgroundColor */,
                        ImageUtils.DEFAULT_CIRCLE_STROKE_COLOR /* circleStrokeColor */));
        try {
            final ImageResource imageResource = imageRequest.loadMediaBlocking(null);
            final ArgumentCaptor<BitmapFactory.Options> options =
                    ArgumentCaptor.forClass(BitmapFactory.Options.class);
            Mockito.verify(spyImageUtils).calculateInSampleSize(
                    options.capture(),
                    Matchers.eq(DOWNSAMPLE_IMAGE_SIZE), Matchers.eq(DOWNSAMPLE_IMAGE_SIZE));
            assertNotSame(1, options.getValue().inSampleSize);
            assertNotNull(imageResource);
            assertNotNull(imageResource.getBitmap());

            // Make sure there's down sampling on the bitmap.
            final int bitmapWidth = imageResource.getBitmap().getWidth();
            final int bitmapHeight = imageResource.getBitmap().getHeight();
            assertTrue(bitmapWidth >= DOWNSAMPLE_IMAGE_SIZE &&
                    bitmapHeight >= DOWNSAMPLE_IMAGE_SIZE &&
                    (bitmapWidth <= DOWNSAMPLE_IMAGE_SIZE * 4 ||
                    bitmapHeight <= DOWNSAMPLE_IMAGE_SIZE * 4));
        } catch (final IOException e) {
            fail("IO exception while trying to load image resource");
        }
    }
}
