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

import android.content.Context;
import android.net.Uri;

import com.android.messaging.datamodel.data.MessagePartData;

public class MessagePartVideoThumbnailRequestDescriptor extends MessagePartImageRequestDescriptor {
    public MessagePartVideoThumbnailRequestDescriptor(MessagePartData messagePart) {
        super(messagePart, ImageRequest.UNSPECIFIED_SIZE, ImageRequest.UNSPECIFIED_SIZE, false);
    }

    public MessagePartVideoThumbnailRequestDescriptor(Uri uri) {
        super(null, uri, ImageRequest.UNSPECIFIED_SIZE, ImageRequest.UNSPECIFIED_SIZE,
                ImageRequest.UNSPECIFIED_SIZE, ImageRequest.UNSPECIFIED_SIZE, false);
    }

    @Override
    public MediaRequest<ImageResource> buildSyncMediaRequest(final Context context) {
        return new VideoThumbnailRequest(context, this);
    }
}
