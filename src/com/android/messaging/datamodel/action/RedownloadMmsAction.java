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

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.util.LogUtil;

/**
 * Action to manually start an MMS download (after failed or manual mms download)
 */
public class RedownloadMmsAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;
    private static final int REQUEST_CODE_PENDING_INTENT = 102;

    /**
     * Download an MMS message
     */
    public static void redownloadMessage(final String messageId) {
        final RedownloadMmsAction action = new RedownloadMmsAction(messageId);
        action.start();
    }

    /**
     * Get a pending intent of for downloading an MMS
     */
    public static PendingIntent getPendingIntentForRedownloadMms(
            final Context context, final String messageId) {
        final Action action = new RedownloadMmsAction(messageId);
        return ActionService.makeStartActionPendingIntent(context,
                action, REQUEST_CODE_PENDING_INTENT, false /*launchesAnActivity*/);
    }

    // Core parameters needed for all types of message
    private static final String KEY_MESSAGE_ID = "message_id";

    /**
     * Constructor used for retrying sending in the background (only message id available)
     */
    RedownloadMmsAction(final String messageId) {
        super();
        actionParameters.putString(KEY_MESSAGE_ID, messageId);
    }

    /**
     * Read message from database and change status to allow downloading
     */
    @Override
    protected Object executeAction() {
        final String messageId = actionParameters.getString(KEY_MESSAGE_ID);

        final DatabaseWrapper db = DataModel.get().getDatabase();

        MessageData message = BugleDatabaseOperations.readMessage(db, messageId);
        // Check message can be redownloaded
        if (message != null && message.canRedownloadMessage()) {
            final long timestamp = System.currentTimeMillis();

            final ContentValues values = new ContentValues(2);
            values.put(DatabaseHelper.MessageColumns.STATUS,
                    MessageData.BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD);
            values.put(DatabaseHelper.MessageColumns.RETRY_START_TIMESTAMP, timestamp);

            // Row must exist as was just loaded above (on ActionService thread)
            BugleDatabaseOperations.updateMessageRow(db, message.getMessageId(), values);

            MessagingContentProvider.notifyMessagesChanged(message.getConversationId());

            // Whether we succeeded or failed we will check and maybe schedule some more work
            ProcessPendingMessagesAction.scheduleProcessPendingMessagesAction(false, this);
        } else {
            message = null;
            LogUtil.e(LogUtil.BUGLE_TAG,
                    "Attempt to download a missing or un-redownloadable message");
        }
        // Immediately update the notifications in case we came from the download action from a
        // heads-up notification. This will dismiss the heads-up notification.
        BugleNotifications.update(false/*silent*/, BugleNotifications.UPDATE_ALL);
        return message;
    }

    private RedownloadMmsAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<RedownloadMmsAction> CREATOR
            = new Parcelable.Creator<RedownloadMmsAction>() {
        @Override
        public RedownloadMmsAction createFromParcel(final Parcel in) {
            return new RedownloadMmsAction(in);
        }

        @Override
        public RedownloadMmsAction[] newArray(final int size) {
            return new RedownloadMmsAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
