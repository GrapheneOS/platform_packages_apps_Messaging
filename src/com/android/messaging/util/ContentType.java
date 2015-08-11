/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

import android.webkit.MimeTypeMap;

public final class ContentType {
    public static String THREE_GPP_EXTENSION = "3gp";
    public static String VIDEO_MP4_EXTENSION = "mp4";
    // Default extension used when we don't know one.
    public static String DEFAULT_EXTENSION = "dat";

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_AUDIO = 2;
    public static final int TYPE_VCARD = 3;
    public static final int TYPE_OTHER = 4;

    public static final String ANY_TYPE          = "*/*";
    public static final String MMS_MESSAGE       = "application/vnd.wap.mms-message";
    // The phony content type for generic PDUs (e.g. ReadOrig.ind,
    // Notification.ind, Delivery.ind).
    public static final String MMS_GENERIC       = "application/vnd.wap.mms-generic";
    public static final String MMS_MULTIPART_MIXED   = "application/vnd.wap.multipart.mixed";
    public static final String MMS_MULTIPART_RELATED = "application/vnd.wap.multipart.related";
    public static final String MMS_MULTIPART_ALTERNATIVE =
            "application/vnd.wap.multipart.alternative";

    public static final String TEXT_PLAIN        = "text/plain";
    public static final String TEXT_HTML         = "text/html";
    public static final String TEXT_VCALENDAR    = "text/x-vCalendar";
    public static final String TEXT_VCARD        = "text/x-vCard";

    public static final String IMAGE_PREFIX      = "image/";
    public static final String IMAGE_UNSPECIFIED = "image/*";
    public static final String IMAGE_JPEG        = "image/jpeg";
    public static final String IMAGE_JPG         = "image/jpg";
    public static final String IMAGE_GIF         = "image/gif";
    public static final String IMAGE_WBMP        = "image/vnd.wap.wbmp";
    public static final String IMAGE_PNG         = "image/png";
    public static final String IMAGE_X_MS_BMP    = "image/x-ms-bmp";

    public static final String AUDIO_UNSPECIFIED = "audio/*";
    public static final String AUDIO_AAC         = "audio/aac";
    public static final String AUDIO_AMR         = "audio/amr";
    public static final String AUDIO_IMELODY     = "audio/imelody";
    public static final String AUDIO_MID         = "audio/mid";
    public static final String AUDIO_MIDI        = "audio/midi";
    public static final String AUDIO_MP3         = "audio/mp3";
    public static final String AUDIO_MPEG3       = "audio/mpeg3";
    public static final String AUDIO_MPEG        = "audio/mpeg";
    public static final String AUDIO_MPG         = "audio/mpg";
    public static final String AUDIO_MP4         = "audio/mp4";
    public static final String AUDIO_MP4_LATM    = "audio/mp4-latm";
    public static final String AUDIO_X_MID       = "audio/x-mid";
    public static final String AUDIO_X_MIDI      = "audio/x-midi";
    public static final String AUDIO_X_MP3       = "audio/x-mp3";
    public static final String AUDIO_X_MPEG3     = "audio/x-mpeg3";
    public static final String AUDIO_X_MPEG      = "audio/x-mpeg";
    public static final String AUDIO_X_MPG       = "audio/x-mpg";
    public static final String AUDIO_3GPP        = "audio/3gpp";
    public static final String AUDIO_X_WAV       = "audio/x-wav";
    public static final String AUDIO_OGG         = "application/ogg";

    public static final String MULTIPART_MIXED = "multipart/mixed";

    public static final String VIDEO_UNSPECIFIED = "video/*";
    public static final String VIDEO_3GP         = "video/3gp";
    public static final String VIDEO_3GPP        = "video/3gpp";
    public static final String VIDEO_3G2         = "video/3gpp2";
    public static final String VIDEO_H263        = "video/h263";
    public static final String VIDEO_M4V         = "video/m4v";
    public static final String VIDEO_MP4         = "video/mp4";
    public static final String VIDEO_MPEG        = "video/mpeg";
    public static final String VIDEO_MPEG4       = "video/mpeg4";
    public static final String VIDEO_WEBM        = "video/webm";

    public static final String APP_SMIL          = "application/smil";
    public static final String APP_WAP_XHTML     = "application/vnd.wap.xhtml+xml";
    public static final String APP_XHTML         = "application/xhtml+xml";

    public static final String APP_DRM_CONTENT   = "application/vnd.oma.drm.content";
    public static final String APP_DRM_MESSAGE   = "application/vnd.oma.drm.message";

    // This class should never be instantiated.
    private ContentType() {
    }

    public static boolean isTextType(final String contentType) {
        return TEXT_PLAIN.equals(contentType)
                || TEXT_HTML.equals(contentType)
                || APP_WAP_XHTML.equals(contentType);
    }

    public static boolean isMediaType(final String contentType) {
        return isImageType(contentType)
                || isVideoType(contentType)
                || isAudioType(contentType)
                || isVCardType(contentType);
    }

    public static boolean isImageType(final String contentType) {
        return (null != contentType) && contentType.startsWith(IMAGE_PREFIX);
    }

    public static boolean isAudioType(final String contentType) {
        return (null != contentType) &&
                (contentType.startsWith("audio/") || contentType.equalsIgnoreCase(AUDIO_OGG));
    }

    public static boolean isVideoType(final String contentType) {
        return (null != contentType) && contentType.startsWith("video/");
    }

    public static boolean isVCardType(final String contentType) {
        return (null != contentType) && contentType.equalsIgnoreCase(TEXT_VCARD);
    }

    public static boolean isDrmType(final String contentType) {
        return (null != contentType)
                && (contentType.equals(APP_DRM_CONTENT)
                        || contentType.equals(APP_DRM_MESSAGE));
    }

    public static boolean isUnspecified(final String contentType) {
        return (null != contentType) && contentType.endsWith("*");
    }

    /**
     * If the content type is a type which can be displayed in the conversation list as a preview.
     */
    public static boolean isConversationListPreviewableType(final String contentType) {
        return ContentType.isAudioType(contentType) || ContentType.isVideoType(contentType) ||
                ContentType.isImageType(contentType) || ContentType.isVCardType(contentType);
    }

    /**
     * Given a filename, look at the extension and try and determine the mime type.
     *
     * @param fileName a filename to determine the type from, such as img1231.jpg
     * @param contentTypeDefault type to use when the content type can't be determined from the file
     *      extension. It can be null or a type such as ContentType.IMAGE_UNSPECIFIED
     * @return Content type of the extension.
     */
    public static String getContentTypeFromExtension(final String fileName,
            final String contentTypeDefault) {
        final MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        final String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        String contentType = mimeTypeMap.getMimeTypeFromExtension(extension);
        if (contentType == null) {
            contentType = contentTypeDefault;
        }
        return contentType;
    }

    /**
     * Get the common file extension for a given content type
     * @param contentType The content type
     * @return The extension without the .
     */
    public static String getExtension(final String contentType) {
        if (VIDEO_MP4.equals(contentType)) {
            return VIDEO_MP4_EXTENSION;
        } else if (VIDEO_3GPP.equals(contentType)) {
            return THREE_GPP_EXTENSION;
        } else {
            return DEFAULT_EXTENSION;
        }
    }
}
