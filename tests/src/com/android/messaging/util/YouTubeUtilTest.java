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

package com.android.messaging.util;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;

/*
 * Class for testing YouTubeUtil.
 */
@SmallTest
public class YouTubeUtilTest extends BugleTestCase {
    public void testGetYoutubePreviewImageLink() {
        final String videoId = "dQw4w9WgXcQ";
        final String videoThumbnailUrl = YouTubeUtil.YOUTUBE_STATIC_THUMBNAIL_PREFIX + videoId
                + YouTubeUtil.YOUTUBE_STATIC_THUMBNAIL_END;

        // Check known valid youtube links to videos
        assertEquals(
                YouTubeUtil.getYoutubePreviewImageLink("http://www.youtube.com/watch?v=" + videoId),
                videoThumbnailUrl);
        assertEquals(
                YouTubeUtil.getYoutubePreviewImageLink("https://www.youtube.com/watch?v=" + videoId
                        + "&feature=youtu.be"), videoThumbnailUrl);
        assertEquals(
                YouTubeUtil.getYoutubePreviewImageLink("www.youtube.com/watch?v=" + videoId),
                videoThumbnailUrl);
        assertEquals(
                YouTubeUtil.getYoutubePreviewImageLink("http://www.youtube.com/embed/" + videoId),
                videoThumbnailUrl);
        assertEquals(YouTubeUtil.getYoutubePreviewImageLink("http://www.youtube.com/v/" + videoId),
                videoThumbnailUrl);
        assertEquals(
                YouTubeUtil.getYoutubePreviewImageLink("https://youtube.googleapis.com/v/"
                        + videoId), videoThumbnailUrl);
        assertEquals(
                YouTubeUtil.getYoutubePreviewImageLink("http://www.youtube.com/apiplayer?video_id="
                        + videoId), videoThumbnailUrl);
        // This is the type of links that are used as shares from YouTube and will be the most
        // likely case that we see
        assertEquals(YouTubeUtil.getYoutubePreviewImageLink("http://youtu.be/" + videoId),
                videoThumbnailUrl);

        // Try links that shouldn't work
        assertNull(YouTubeUtil.getYoutubePreviewImageLink("http://www.youtube.com"));
    }
}