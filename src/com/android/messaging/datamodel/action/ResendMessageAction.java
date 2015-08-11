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
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.util.LogUtil;

/**
 * Action used to manually resend an outgoing message
 */
public class ResendMessageAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    /**
     * Manual send of existing message (no listener)
     */
    public static void resendMessage(final String messageId) {
        final ResendMessageAction action = new ResendMessageAction(messageId);
        action.start();
    }

    // Core parameters needed for all types of message
    private static final String KEY_MESSAGE_ID = "message_id";

    /**
     * Constructor used for retrying sending in the background (only message id available)
     */
    ResendMessageAction(final String messageId) {
        super();
        actionParameters.putString(KEY_MESSAGE_ID, messageId);
    }

    /**
     * Read message from database and change status to allow sending
     */
    @Override
    protected Object executeAction() {
        final String messageId = actionParameters.getString(KEY_MESSAGE_ID);

        final DatabaseWrapper db = DataModel.get().getDatabase();

        final MessageData message = BugleDatabaseOperations.readMessage(db, messageId);
        // Check message can be resent
        if (message != null && message.canResendMessage()) {
            final boolean isMms = message.getIsMms();
            long timestamp = System.currentTimeMillis();
            if (isMms) {
                // MMS expects timestamp rounded to nearest second
                timestamp = 1000 * ((timestamp + 500) / 1000);
            }

            LogUtil.i(TAG, "ResendMessageAction: Resending message " + messageId
                    + "; changed timestamp from " + message.getReceivedTimeStamp() + " to "
                    + timestamp);

            final ContentValues values = new ContentValues();
            values.put(MessageColumns.STATUS, MessageData.BUGLE_STATUS_OUTGOING_YET_TO_SEND);
            values.put(MessageColumns.RECEIVED_TIMESTAMP, timestamp);
            values.put(MessageColumns.SENT_TIMESTAMP, timestamp);
            values.put(MessageColumns.RETRY_START_TIMESTAMP, timestamp);

            // Row must exist as was just loaded above (on ActionService thread)
            BugleDatabaseOperations.updateMessageRow(db, message.getMessageId(), values);

            MessagingContentProvider.notifyMessagesChanged(message.getConversationId());

            // Whether we succeeded or failed we will check and maybe schedule some more work
            ProcessPendingMessagesAction.scheduleProcessPendingMessagesAction(false, this);

            return message;
        } else {
            String error = "ResendMessageAction: Cannot resend message " + messageId + "; ";
            if (message != null) {
                error += ("status = " + MessageData.getStatusDescription(message.getStatus()));
            } else {
                error += "not found in database";
            }
            LogUtil.e(TAG, error);
        }

        return null;
    }

    private ResendMessageAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<ResendMessageAction> CREATOR
            = new Parcelable.Creator<ResendMessageAction>() {
        @Override
        public ResendMessageAction createFromParcel(final Parcel in) {
            return new ResendMessageAction(in);
        }

        @Override
        public ResendMessageAction[] newArray(final int size) {
            return new ResendMessageAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
