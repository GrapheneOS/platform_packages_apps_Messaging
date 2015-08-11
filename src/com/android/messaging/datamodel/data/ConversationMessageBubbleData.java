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

import android.text.TextUtils;

/**
 * Holds data for conversation message bubble which keeps track of whether it's been bound to
 * a new message.
 */
public class ConversationMessageBubbleData {
    private String mMessageId;

    /**
     * Binds to ConversationMessageData instance.
     * @return true if we are binding to a different message, false if we are binding to the
     *         same message (e.g. in order to update the status text)
     */
    public boolean bind(final ConversationMessageData data) {
        final boolean changed = !TextUtils.equals(mMessageId, data.getMessageId());
        mMessageId = data.getMessageId();
        return changed;
    }
}
