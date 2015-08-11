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
package com.android.messaging.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.action.MarkAsSeenAction;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.ConversationIdSet;
import com.android.messaging.util.LogUtil;

// NotificationReceiver is used to handle delete intents from notifications. When a user
// clears all notifications or swipes a bugle notification away, the intent we pass in as
// the delete intent will get handled here.
public class NotificationReceiver extends BroadcastReceiver {
    // Logging
    public static final String TAG = LogUtil.BUGLE_TAG;
    public static final boolean VERBOSE = false;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (VERBOSE) {
            LogUtil.v(TAG, "NotificationReceiver.onReceive: intent " + intent);
        }
        if (intent.getAction().equals(UIIntents.ACTION_RESET_NOTIFICATIONS)) {
            final String conversationIdSetString =
                    intent.getStringExtra(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID_SET);
            final int notificationTargets = intent.getIntExtra(
                    UIIntents.UI_INTENT_EXTRA_NOTIFICATIONS_UPDATE, BugleNotifications.UPDATE_ALL);
            if (conversationIdSetString == null) {
                BugleNotifications.markAllMessagesAsSeen();
            } else {
                for (final String conversationId :
                        ConversationIdSet.createSet(conversationIdSetString)) {
                    MarkAsSeenAction.markAsSeen(conversationId);
                    BugleNotifications.resetLastMessageDing(conversationId);
                }
            }
        }
    }
}