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

package com.android.messaging.datamodel.action;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.LogUtil;

/**
 * Action used to mark all the messages in a conversation as read
 */
public class MarkAsReadAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    private static final String KEY_CONVERSATION_ID = "conversation_id";

    /**
     * Mark all the messages as read for a particular conversation.
     */
    public static void markAsRead(final String conversationId) {
        final MarkAsReadAction action = new MarkAsReadAction(conversationId);
        action.start();
    }

    private MarkAsReadAction(final String conversationId) {
        actionParameters.putString(KEY_CONVERSATION_ID, conversationId);
    }

    @Override
    protected Object executeAction() {
        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);

        // TODO: Consider doing this in background service to avoid delaying other actions
        final DatabaseWrapper db = DataModel.get().getDatabase();

        // Mark all messages in thread as read in telephony
        final long threadId = BugleDatabaseOperations.getThreadId(db, conversationId);
        if (threadId != -1) {
            MmsUtils.updateSmsReadStatus(threadId, Long.MAX_VALUE);
        }

        // Update local db
        db.beginTransaction();
        try {
            final ContentValues values = new ContentValues();
            values.put(MessageColumns.CONVERSATION_ID, conversationId);
            values.put(MessageColumns.READ, 1);
            values.put(MessageColumns.SEEN, 1);     // if they read it, they saw it

            final int count = db.update(DatabaseHelper.MESSAGES_TABLE, values,
                    "(" + MessageColumns.READ + " !=1 OR " +
                            MessageColumns.SEEN + " !=1 ) AND " +
                            MessageColumns.CONVERSATION_ID + "=?",
                    new String[] { conversationId });
            if (count > 0) {
                MessagingContentProvider.notifyMessagesChanged(conversationId);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        // After marking messages as read, update the notifications. This will
        // clear the now stale notifications.
        BugleNotifications.update(false/*silent*/, BugleNotifications.UPDATE_ALL);
        return null;
    }

    private MarkAsReadAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<MarkAsReadAction> CREATOR
            = new Parcelable.Creator<MarkAsReadAction>() {
        @Override
        public MarkAsReadAction createFromParcel(final Parcel in) {
            return new MarkAsReadAction(in);
        }

        @Override
        public MarkAsReadAction[] newArray(final int size) {
            return new MarkAsReadAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
