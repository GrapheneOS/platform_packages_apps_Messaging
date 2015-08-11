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

package com.android.messaging.datamodel.data;

import android.graphics.Rect;
import android.net.Uri;

public class MediaPickerMessagePartData extends MessagePartData {
    private final Rect mStartRect;

    public MediaPickerMessagePartData(final Rect startRect, final String contentType,
            final Uri contentUri, final int width, final int height) {
        this(startRect, null /* messageText */, contentType, contentUri, width, height);
    }

   public MediaPickerMessagePartData(final Rect startRect, final String messageText,
            final String contentType, final Uri contentUri, final int width, final int height) {
        this(startRect, messageText, contentType, contentUri, width, height,
                false /*onlySingleAttachment*/);
    }

    public MediaPickerMessagePartData(final Rect startRect, final String contentType,
            final Uri contentUri, final int width, final int height,
            final boolean onlySingleAttachment) {
        this(startRect, null /* messageText */, contentType, contentUri, width, height,
                onlySingleAttachment);
    }

    public MediaPickerMessagePartData(final Rect startRect, final String messageText,
            final String contentType, final Uri contentUri, final int width, final int height,
            final boolean onlySingleAttachment) {
        super(messageText, contentType, contentUri, width, height, onlySingleAttachment);
        mStartRect = startRect;
    }

    /**
     * @return The starting rect to animate the attachment preview from in order to perform a smooth
     * transition
     */
    public Rect getStartRect() {
        return mStartRect;
    }

    /**
     * Modify the start rect of the attachment.
     */
    public void setStartRect(final Rect startRect) {
        mStartRect.set(startRect);
    }
}
