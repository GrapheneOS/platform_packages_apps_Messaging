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
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.SyncManager;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.RunsOnMainThread;
import com.android.messaging.util.LogUtil;

/**
 * Downloads an MMS message.
 * <p>
 * This class is public (not package-private) because the SMS/MMS (e.g. MmsUtils) classes need to
 * access the EXTRA_* fields for setting up the 'downloaded' pending intent.
 */
public class DownloadMmsAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    /**
     * Interface for DownloadMmsAction listeners
     */
    public interface DownloadMmsActionListener {
        @RunsOnMainThread
        abstract void onDownloadMessageStarting(final ActionMonitor monitor,
                final Object data, final MessageData message);
        @RunsOnMainThread
        abstract void onDownloadMessageSucceeded(final ActionMonitor monitor,
                final Object data, final MessageData message);
        @RunsOnMainThread
        abstract void onDownloadMessageFailed(final ActionMonitor monitor,
                final Object data, final MessageData message);
    }

    /**
     * Queue download of an mms notification message (can only be called during execute of action)
     */
    static boolean queueMmsForDownloadInBackground(final String messageId,
            final Action processingAction) {
        // When this method is being called, it is always from auto download
        final DownloadMmsAction action = new DownloadMmsAction();
        // This could queue nothing
        return action.queueAction(messageId, processingAction);
    }

    private static final String KEY_MESSAGE_ID = "message_id";
    private static final String KEY_CONVERSATION_ID = "conversation_id";
    private static final String KEY_PARTICIPANT_ID = "participant_id";
    private static final String KEY_CONTENT_LOCATION = "content_location";
    private static final String KEY_TRANSACTION_ID = "transaction_id";
    private static final String KEY_NOTIFICATION_URI = "notification_uri";
    private static final String KEY_SUB_ID = "sub_id";
    private static final String KEY_SUB_PHONE_NUMBER = "sub_phone_number";
    private static final String KEY_AUTO_DOWNLOAD = "auto_download";
    private static final String KEY_FAILURE_STATUS = "failure_status";
    private static final String KEY_EXPIRY = "expiry";

    // Values we attach to the pending intent that's fired when the message is downloaded.
    // Only applicable when downloading via the platform APIs on L+.
    public static final String EXTRA_MESSAGE_ID = "message_id";
    public static final String EXTRA_CONTENT_URI = "content_uri";
    public static final String EXTRA_NOTIFICATION_URI = "notification_uri";
    public static final String EXTRA_SUB_ID = "sub_id";
    public static final String EXTRA_SUB_PHONE_NUMBER = "sub_phone_number";
    public static final String EXTRA_TRANSACTION_ID = "transaction_id";
    public static final String EXTRA_CONTENT_LOCATION = "content_location";
    public static final String EXTRA_AUTO_DOWNLOAD = "auto_download";
    public static final String EXTRA_RECEIVED_TIMESTAMP = "received_timestamp";
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_PARTICIPANT_ID = "participant_id";
    public static final String EXTRA_STATUS_IF_FAILED = "status_if_failed";
    public static final String EXTRA_EXPIRY = "expiry";

    private DownloadMmsAction() {
        super();
    }

    @Override
    protected Object executeAction() {
        Assert.fail("DownloadMmsAction must be queued rather than started");
        return null;
    }

    protected boolean queueAction(final String messageId, final Action processingAction) {
        actionParameters.putString(KEY_MESSAGE_ID, messageId);

        final DatabaseWrapper db = DataModel.get().getDatabase();
        // Read the message from local db
        final MessageData message = BugleDatabaseOperations.readMessage(db, messageId);
        if (message != null && message.canDownloadMessage()) {
            final Uri notificationUri = message.getSmsMessageUri();
            final String conversationId = message.getConversationId();
            final int status = message.getStatus();

            final String selfId = message.getSelfId();
            final ParticipantData self = BugleDatabaseOperations
                    .getExistingParticipant(db, selfId);
            final int subId = self.getSubId();
            actionParameters.putInt(KEY_SUB_ID, subId);
            actionParameters.putString(KEY_CONVERSATION_ID, conversationId);
            actionParameters.putString(KEY_PARTICIPANT_ID, message.getParticipantId());
            actionParameters.putString(KEY_CONTENT_LOCATION, message.getMmsContentLocation());
            actionParameters.putString(KEY_TRANSACTION_ID, message.getMmsTransactionId());
            actionParameters.putParcelable(KEY_NOTIFICATION_URI, notificationUri);
            actionParameters.putBoolean(KEY_AUTO_DOWNLOAD, isAutoDownload(status));
            actionParameters.putLong(KEY_EXPIRY, message.getMmsExpiry());

            final long now = System.currentTimeMillis();
            if (message.getInDownloadWindow(now)) {
                // We can still retry
                actionParameters.putString(KEY_SUB_PHONE_NUMBER, self.getNormalizedDestination());

                final int downloadingStatus = getDownloadingStatus(status);
                // Update message status to indicate downloading.
                updateMessageStatus(notificationUri, messageId, conversationId,
                        downloadingStatus, MessageData.RAW_TELEPHONY_STATUS_UNDEFINED);
                // Pre-compute the next status when failed so we don't have to load from db again
                actionParameters.putInt(KEY_FAILURE_STATUS, getFailureStatus(downloadingStatus));

                // Actual download happens in background
                processingAction.requestBackgroundWork(this);

                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG,
                            "DownloadMmsAction: Queued download of MMS message " + messageId);
                }
                return true;
            } else {
                LogUtil.w(TAG, "DownloadMmsAction: Download of MMS message " + messageId
                        + " failed (outside download window)");

                // Retries depleted and we failed. Update the message status so we won't retry again
                updateMessageStatus(notificationUri, messageId, conversationId,
                        MessageData.BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED,
                        MessageData.RAW_TELEPHONY_STATUS_UNDEFINED);
                if (status == MessageData.BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD) {
                    // For auto download failure, we should send a DEFERRED NotifyRespInd
                    // to carrier to indicate we will manual download later
                    ProcessDownloadedMmsAction.sendDeferredRespStatus(
                            messageId, message.getMmsTransactionId(),
                            message.getMmsContentLocation(), subId);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find out the auto download state of this message based on its starting status
     *
     * @param status The starting status of the message.
     * @return True if this is a message doing auto downloading, false otherwise
     */
    private static boolean isAutoDownload(final int status) {
        switch (status) {
            case MessageData.BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD:
                return false;
            case MessageData.BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD:
                return true;
            default:
                Assert.fail("isAutoDownload: invalid input status " + status);
                return false;
        }
    }

    /**
     * Get the corresponding downloading status based on the starting status of the message
     *
     * @param status The starting status of the message.
     * @return The downloading status
     */
    private static int getDownloadingStatus(final int status) {
        switch (status) {
            case MessageData.BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD:
                return MessageData.BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING;
            case MessageData.BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD:
                return MessageData.BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING;
            default:
                Assert.fail("isAutoDownload: invalid input status " + status);
                return MessageData.BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING;
        }
    }

    /**
     * Get the corresponding failed status based on the current downloading status
     *
     * @param status The downloading status
     * @return The status the message should have if downloading failed
     */
    private static int getFailureStatus(final int status) {
        switch (status) {
            case MessageData.BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING:
                return MessageData.BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD;
            case MessageData.BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING:
                return MessageData.BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD;
            default:
                Assert.fail("isAutoDownload: invalid input status " + status);
                return MessageData.BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD;
        }
    }

    @Override
    protected Bundle doBackgroundWork() {
        final Context context = Factory.get().getApplicationContext();
        final int subId = actionParameters.getInt(KEY_SUB_ID);
        final String messageId = actionParameters.getString(KEY_MESSAGE_ID);
        final Uri notificationUri = actionParameters.getParcelable(KEY_NOTIFICATION_URI);
        final String subPhoneNumber = actionParameters.getString(KEY_SUB_PHONE_NUMBER);
        final String transactionId = actionParameters.getString(KEY_TRANSACTION_ID);
        final String contentLocation = actionParameters.getString(KEY_CONTENT_LOCATION);
        final boolean autoDownload = actionParameters.getBoolean(KEY_AUTO_DOWNLOAD);
        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        final String participantId = actionParameters.getString(KEY_PARTICIPANT_ID);
        final int statusIfFailed = actionParameters.getInt(KEY_FAILURE_STATUS);
        final long expiry = actionParameters.getLong(KEY_EXPIRY);

        final long receivedTimestampRoundedToSecond =
                1000 * ((System.currentTimeMillis() + 500) / 1000);

        LogUtil.i(TAG, "DownloadMmsAction: Downloading MMS message " + messageId
                + " (" + (autoDownload ? "auto" : "manual") + ")");

        // Bundle some values we'll need after the message is downloaded (via platform APIs)
        final Bundle extras = new Bundle();
        extras.putString(EXTRA_MESSAGE_ID, messageId);
        extras.putString(EXTRA_CONVERSATION_ID, conversationId);
        extras.putString(EXTRA_PARTICIPANT_ID, participantId);
        extras.putInt(EXTRA_STATUS_IF_FAILED, statusIfFailed);

        // Start the download
        final MmsUtils.StatusPlusUri status = MmsUtils.downloadMmsMessage(context,
                notificationUri, subId, subPhoneNumber, transactionId, contentLocation,
                autoDownload, receivedTimestampRoundedToSecond / 1000L, expiry / 1000L, extras);
        if (status == MmsUtils.STATUS_PENDING) {
            // Async download; no status yet
            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "DownloadMmsAction: Downloading MMS message " + messageId
                        + " asynchronously; waiting for pending intent to signal completion");
            }
        } else {
            // Inform sync that message has been added at local received timestamp
            final SyncManager syncManager = DataModel.get().getSyncManager();
            syncManager.onNewMessageInserted(receivedTimestampRoundedToSecond);
            // Handle downloaded message
            ProcessDownloadedMmsAction.processMessageDownloadFastFailed(messageId,
                    notificationUri, conversationId, participantId, contentLocation, subId,
                    subPhoneNumber, statusIfFailed, autoDownload, transactionId,
                    status.resultCode);
        }
        return null;
    }

    @Override
    protected Object processBackgroundResponse(final Bundle response) {
        // Nothing to do here; post-download actions handled by ProcessDownloadedMmsAction
        return null;
    }

    @Override
    protected Object processBackgroundFailure() {
        final String messageId = actionParameters.getString(KEY_MESSAGE_ID);
        final String transactionId = actionParameters.getString(KEY_TRANSACTION_ID);
        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        final String participantId = actionParameters.getString(KEY_PARTICIPANT_ID);
        final int statusIfFailed = actionParameters.getInt(KEY_FAILURE_STATUS);
        final int subId = actionParameters.getInt(KEY_SUB_ID);

        ProcessDownloadedMmsAction.processDownloadActionFailure(messageId,
                MmsUtils.MMS_REQUEST_MANUAL_RETRY, MessageData.RAW_TELEPHONY_STATUS_UNDEFINED,
                conversationId, participantId, statusIfFailed, subId, transactionId);

        return null;
    }

    static void updateMessageStatus(final Uri messageUri, final String messageId,
            final String conversationId, final int status, final int rawStatus) {
        final Context context = Factory.get().getApplicationContext();
        // Downloading status just kept in local DB but need to fix up telephony DB first
        if (status == MessageData.BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING ||
                status == MessageData.BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING) {
            MmsUtils.clearMmsStatus(context, messageUri);
        }
        // Then mark downloading status in our local DB
        final ContentValues values = new ContentValues();
        values.put(MessageColumns.STATUS, status);
        values.put(MessageColumns.RAW_TELEPHONY_STATUS, rawStatus);
        final DatabaseWrapper db = DataModel.get().getDatabase();
        BugleDatabaseOperations.updateMessageRowIfExists(db, messageId, values);

        MessagingContentProvider.notifyMessagesChanged(conversationId);
    }

    private DownloadMmsAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<DownloadMmsAction> CREATOR
            = new Parcelable.Creator<DownloadMmsAction>() {
        @Override
        public DownloadMmsAction createFromParcel(final Parcel in) {
            return new DownloadMmsAction(in);
        }

        @Override
        public DownloadMmsAction[] newArray(final int size) {
            return new DownloadMmsAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
