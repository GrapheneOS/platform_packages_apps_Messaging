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

import android.net.Uri;
import android.text.TextUtils;

public class YouTubeUtil {
    private static final String YOUTUBE_HOST_1 = "www.youtube.com";
    private static final String YOUTUBE_HOST_2 = "youtube.com";
    private static final String YOUTUBE_HOST_3 = "m.youtube.com";
    private static final String YOUTUBE_HOST_4 = "youtube.googleapis.com";
    private static final String YOUTUBE_HOST_5 = "youtu.be";

    private static final String YOUTUBE_PATH_1 = "/watch";
    private static final String YOUTUBE_PATH_2 = "/embed/";
    private static final String YOUTUBE_PATH_3 = "/v/";
    private static final String YOUTUBE_PATH_4 = "/apiplayer";

    public static final String YOUTUBE_STATIC_THUMBNAIL_PREFIX = "https://img.youtube.com/vi/";
    public static final String YOUTUBE_STATIC_THUMBNAIL_END = "/hqdefault.jpg";

    public static String getYoutubePreviewImageLink(String urlString) {
        // Types of youtube urls:
        // 1.) http://www.youtube.com/watch?v=VIDEOID
        // 2.) http://www.youtube.com/embed/VIDEOID
        // 3.) http://www.youtube.com/v/VIDEOID
        // 3a.) https://youtube.googleapis.com/v/VIDEOID
        // 4.) http://www.youtube.com/apiplayer?video_id=VIDEO_ID
        // 5.) http://youtu.be/VIDEOID
        if (!urlString.startsWith("http")) {
            // Apparently the url is not an RFC 2396 compliant uri without the port
            urlString = "http://" + urlString;
        }
        final Uri uri = Uri.parse(urlString);
        final String host = uri.getHost();
        if (YOUTUBE_HOST_1.equalsIgnoreCase(host)
                || YOUTUBE_HOST_2.equalsIgnoreCase(host)
                || YOUTUBE_HOST_3.equalsIgnoreCase(host)
                || YOUTUBE_HOST_4.equalsIgnoreCase(host)
                || YOUTUBE_HOST_5.equalsIgnoreCase(host)) {
            final String videoId = getYouTubeVideoId(uri);
            if (!TextUtils.isEmpty(videoId)) {
                return YOUTUBE_STATIC_THUMBNAIL_PREFIX + videoId + YOUTUBE_STATIC_THUMBNAIL_END;
            }
            return null;
        }
        return null;
    }

    private static String getYouTubeVideoId(Uri uri) {
        final String urlPath = uri.getPath();

        if (TextUtils.isEmpty(urlPath)) {
            // There is no path so no need to continue.
            return null;
        }
        // Case 1
        if (urlPath.startsWith(YOUTUBE_PATH_1)) {
            return uri.getQueryParameter("v");
        }
        // Case 2
        if (urlPath.startsWith(YOUTUBE_PATH_2)) {
            return getVideoIdFromPath(YOUTUBE_PATH_2, urlPath);
        }
        // Case 3
        if (urlPath.startsWith(YOUTUBE_PATH_3)) {
            return getVideoIdFromPath(YOUTUBE_PATH_3, urlPath);
        }
        // Case 4
        if (urlPath.startsWith(YOUTUBE_PATH_4)) {
            return uri.getQueryParameter("video_id");
        }
        // Case 5
        if (YOUTUBE_HOST_5.equalsIgnoreCase(uri.getHost())) {
            return getVideoIdFromPath("/", urlPath);
        }
        return null;
    }

    private static String getVideoIdFromPath(String prefixSubstring, String urlPath) {
        return urlPath.substring(prefixSubstring.length());
    }

}
