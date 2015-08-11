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

import android.net.Uri;

import com.android.messaging.datamodel.action.UpdateMessagePartSizeAction;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.util.ImageUtils;

/**
 * Image descriptor attached to a message part.
 * Once image size is determined during loading this descriptor will update the db if necessary.
 */
public class MessagePartImageRequestDescriptor extends UriImageRequestDescriptor {
    private final String mMessagePartId;

    /**
     * Creates a new image request for a message part.
     */
    public MessagePartImageRequestDescriptor(final MessagePartData messagePart,
            final int desiredWidth, final int desiredHeight, boolean isStatic) {
        // Pull image parameters out of the MessagePart record
        this(messagePart.getPartId(), messagePart.getContentUri(), desiredWidth, desiredHeight,
                messagePart.getWidth(), messagePart.getHeight(), isStatic);
    }

    protected MessagePartImageRequestDescriptor(final String messagePartId, final Uri contentUri,
            final int desiredWidth, final int desiredHeight, final int sourceWidth,
            final int sourceHeight, boolean isStatic) {
        super(contentUri, desiredWidth, desiredHeight, sourceWidth, sourceHeight,
                true /* allowCompression */, isStatic, false /* cropToCircle */,
                ImageUtils.DEFAULT_CIRCLE_BACKGROUND_COLOR /* circleBackgroundColor */,
                ImageUtils.DEFAULT_CIRCLE_STROKE_COLOR /* circleStrokeColor */);
        mMessagePartId = messagePartId;
    }

    @Override
    public void updateSourceDimensions(final int updatedWidth, final int updatedHeight) {
        // If the dimensions of the image do not match then queue a DB update with new size.
        // Don't update if we don't have a part id, which happens if this part is loaded as
        // draft through actions such as share intent/message forwarding.
        if (mMessagePartId != null &&
                updatedWidth != MessagePartData.UNSPECIFIED_SIZE &&
                updatedHeight != MessagePartData.UNSPECIFIED_SIZE &&
                updatedWidth != sourceWidth && updatedHeight != sourceHeight) {
            UpdateMessagePartSizeAction.updateSize(mMessagePartId, updatedWidth, updatedHeight);
        }
    }
}
