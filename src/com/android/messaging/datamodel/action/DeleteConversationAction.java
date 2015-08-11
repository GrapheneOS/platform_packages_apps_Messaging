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

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DataModelException;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;
import com.android.messaging.widget.WidgetConversationProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Action used to delete a conversation.
 */
public class DeleteConversationAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    public static void deleteConversation(final String conversationId, final long cutoffTimestamp) {
        final DeleteConversationAction action = new DeleteConversationAction(conversationId,
                cutoffTimestamp);
        action.start();
    }

    private static final String KEY_CONVERSATION_ID = "conversation_id";
    private static final String KEY_CUTOFF_TIMESTAMP = "cutoff_timestamp";

    private DeleteConversationAction(final String conversationId, final long cutoffTimestamp) {
        super();
        actionParameters.putString(KEY_CONVERSATION_ID, conversationId);
        // TODO: Should we set cuttoff timestamp to prevent us deleting new messages?
        actionParameters.putLong(KEY_CUTOFF_TIMESTAMP, cutoffTimestamp);
    }

    // Delete conversation from both the local DB and telephony in the background so sync cannot
    // run concurrently and incorrectly try to recreate the conversation's messages locally. The
    // telephony database can sometimes be quite slow to delete conversations, so we delete from
    // the local DB first, notify the UI, and then delete from telephony.
    @Override
    protected Bundle doBackgroundWork() throws DataModelException {
        final DatabaseWrapper db = DataModel.get().getDatabase();

        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        final long cutoffTimestamp = actionParameters.getLong(KEY_CUTOFF_TIMESTAMP);

        if (!TextUtils.isEmpty(conversationId)) {
            // First find the thread id for this conversation.
            final long threadId = BugleDatabaseOperations.getThreadId(db, conversationId);

            if (BugleDatabaseOperations.deleteConversation(db, conversationId, cutoffTimestamp)) {
                LogUtil.i(TAG, "DeleteConversationAction: Deleted local conversation "
                        + conversationId);

                BugleActionToasts.onConversationDeleted();

                // Remove notifications if necessary
                BugleNotifications.update(true /* silent */, null /* conversationId */,
                        BugleNotifications.UPDATE_MESSAGES);

                // We have changed the conversation list
                MessagingContentProvider.notifyConversationListChanged();

                // Notify the widget the conversation is deleted so it can go into its configure state.
                WidgetConversationProvider.notifyConversationDeleted(
                        Factory.get().getApplicationContext(),
                        conversationId);
            } else {
                LogUtil.w(TAG, "DeleteConversationAction: Could not delete local conversation "
                        + conversationId);
                return null;
            }

            // Now delete from telephony DB. MmsSmsProvider throws an exception if the thread id is
            // less than 0. If it's greater than zero, it will delete all messages with that thread
            // id, even if there's no corresponding row in the threads table.
            if (threadId >= 0) {
                final int count = MmsUtils.deleteThread(threadId, cutoffTimestamp);
                if (count > 0) {
                    LogUtil.i(TAG, "DeleteConversationAction: Deleted telephony thread "
                            + threadId + " (cutoffTimestamp = " + cutoffTimestamp + ")");
                } else {
                    LogUtil.w(TAG, "DeleteConversationAction: Could not delete thread from "
                            + "telephony: conversationId = " + conversationId + ", thread id = "
                            + threadId);
                }
            } else {
                LogUtil.w(TAG, "DeleteConversationAction: Local conversation " + conversationId
                        + " has an invalid telephony thread id; will delete messages individually");
                deleteConversationMessagesFromTelephony();
            }
        } else {
            LogUtil.e(TAG, "DeleteConversationAction: conversationId is empty");
        }

        return null;
    }

    /**
     * Deletes all the telephony messages for the local conversation being deleted.
     * <p>
     * This is a fallback used when the conversation is not associated with any telephony thread,
     * or its thread id is invalid (e.g. negative). This is not common, but can happen sometimes
     * (e.g. the Unknown Sender conversation). In the usual case of deleting a conversation, we
     * don't need this because the telephony provider automatically deletes messages when a thread
     * is deleted.
     */
    private void deleteConversationMessagesFromTelephony() {
        final DatabaseWrapper db = DataModel.get().getDatabase();
        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        Assert.notNull(conversationId);

        final List<Uri> messageUris = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.MESSAGES_TABLE,
                    new String[] { MessageColumns.SMS_MESSAGE_URI },
                    MessageColumns.CONVERSATION_ID + "=?",
                    new String[] { conversationId },
                    null, null, null);
            while (cursor.moveToNext()) {
                String messageUri = cursor.getString(0);
                try {
                    messageUris.add(Uri.parse(messageUri));
                } catch (Exception e) {
                    LogUtil.e(TAG, "DeleteConversationAction: Could not parse message uri "
                            + messageUri);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        for (Uri messageUri : messageUris) {
            int count = MmsUtils.deleteMessage(messageUri);
            if (count > 0) {
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "DeleteConversationAction: Deleted telephony message "
                            + messageUri);
                }
            } else {
                LogUtil.w(TAG, "DeleteConversationAction: Could not delete telephony message "
                        + messageUri);
            }
        }
    }

    @Override
    protected Object executeAction() {
        requestBackgroundWork();
        return null;
    }

    private DeleteConversationAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<DeleteConversationAction> CREATOR
            = new Parcelable.Creator<DeleteConversationAction>() {
        @Override
        public DeleteConversationAction createFromParcel(final Parcel in) {
            return new DeleteConversationAction(in);
        }

        @Override
        public DeleteConversationAction[] newArray(final int size) {
            return new DeleteConversationAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
