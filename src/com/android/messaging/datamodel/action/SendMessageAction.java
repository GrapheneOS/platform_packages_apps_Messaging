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
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;

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
import com.android.messaging.util.LogUtil;

import java.util.ArrayList;

/**
 * Action used to send an outgoing message. It writes MMS messages to the telephony db
 * ({@link InsertNewMessageAction}) writes SMS messages to the telephony db). It also
 * initiates the actual sending. It will all be used for re-sending a failed message.
 * NOTE: This action must queue a ProcessPendingMessagesAction when it is done (success or failure).
 * <p>
 * This class is public (not package-private) because the SMS/MMS (e.g. MmsUtils) classes need to
 * access the EXTRA_* fields for setting up the 'sent' pending intent.
 */
public class SendMessageAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    /**
     * Queue sending of existing message (can only be called during execute of action)
     */
    static boolean queueForSendInBackground(final String messageId,
            final Action processingAction) {
        final SendMessageAction action = new SendMessageAction();
        return action.queueAction(messageId, processingAction);
    }

    public static final boolean DEFAULT_DELIVERY_REPORT_MODE  = false;
    public static final int MAX_SMS_RETRY = 3;

    // Core parameters needed for all types of message
    private static final String KEY_MESSAGE_ID = "message_id";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_MESSAGE_URI = "message_uri";
    private static final String KEY_SUB_PHONE_NUMBER = "sub_phone_number";

    // For sms messages a few extra values are included in the bundle
    private static final String KEY_RECIPIENT = "recipient";
    private static final String KEY_RECIPIENTS = "recipients";
    private static final String KEY_SMS_SERVICE_CENTER = "sms_service_center";

    // Values we attach to the pending intent that's fired when the message is sent.
    // Only applicable when sending via the platform APIs on L+.
    public static final String KEY_SUB_ID = "sub_id";
    public static final String EXTRA_MESSAGE_ID = "message_id";
    public static final String EXTRA_UPDATED_MESSAGE_URI = "updated_message_uri";
    public static final String EXTRA_CONTENT_URI = "content_uri";
    public static final String EXTRA_RESPONSE_IMPORTANT = "response_important";

    /**
     * Constructor used for retrying sending in the background (only message id available)
     */
    private SendMessageAction() {
        super();
    }

    /**
     * Read message from database and queue actual sending
     */
    private boolean queueAction(final String messageId, final Action processingAction) {
        actionParameters.putString(KEY_MESSAGE_ID, messageId);

        final long timestamp = System.currentTimeMillis();
        final DatabaseWrapper db = DataModel.get().getDatabase();

        final MessageData message = BugleDatabaseOperations.readMessage(db, messageId);
        // Check message can be resent
        if (message != null && message.canSendMessage()) {
            final boolean isSms = (message.getProtocol() == MessageData.PROTOCOL_SMS);

            final ParticipantData self = BugleDatabaseOperations.getExistingParticipant(
                    db, message.getSelfId());
            final Uri messageUri = message.getSmsMessageUri();
            final String conversationId = message.getConversationId();

            // Update message status
            if (message.getYetToSend()) {
                // Initial sending of message
                message.markMessageSending(timestamp);
            } else {
                // Automatic resend of message
                message.markMessageResending(timestamp);
            }
            if (!updateMessageAndStatus(isSms, message, null /* messageUri */, false /*notify*/)) {
                // If message is missing in the telephony database we don't need to send it
                return false;
            }

            final ArrayList<String> recipients =
                    BugleDatabaseOperations.getRecipientsForConversation(db, conversationId);

            // Update action state with parameters needed for background sending
            actionParameters.putParcelable(KEY_MESSAGE_URI, messageUri);
            actionParameters.putParcelable(KEY_MESSAGE, message);
            actionParameters.putStringArrayList(KEY_RECIPIENTS, recipients);
            actionParameters.putInt(KEY_SUB_ID, self.getSubId());
            actionParameters.putString(KEY_SUB_PHONE_NUMBER, self.getNormalizedDestination());

            if (isSms) {
                final String smsc = BugleDatabaseOperations.getSmsServiceCenterForConversation(
                        db, conversationId);
                actionParameters.putString(KEY_SMS_SERVICE_CENTER, smsc);

                if (recipients.size() == 1) {
                    final String recipient = recipients.get(0);

                    actionParameters.putString(KEY_RECIPIENT, recipient);
                    // Queue actual sending for SMS
                    processingAction.requestBackgroundWork(this);

                    if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                        LogUtil.d(TAG, "SendMessageAction: Queued SMS message " + messageId
                                + " for sending");
                    }
                    return true;
                } else {
                    LogUtil.wtf(TAG, "Trying to resend a broadcast SMS - not allowed");
                }
            } else {
                // Queue actual sending for MMS
                processingAction.requestBackgroundWork(this);

                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "SendMessageAction: Queued MMS message " + messageId
                            + " for sending");
                }
                return true;
            }
        }

        return false;
    }


    /**
     * Never called
     */
    @Override
    protected Object executeAction() {
        Assert.fail("SendMessageAction must be queued rather than started");
        return null;
    }

    /**
     * Send message on background worker thread
     */
    @Override
    protected Bundle doBackgroundWork() {
        final MessageData message = actionParameters.getParcelable(KEY_MESSAGE);
        final String messageId = actionParameters.getString(KEY_MESSAGE_ID);
        Uri messageUri = actionParameters.getParcelable(KEY_MESSAGE_URI);
        Uri updatedMessageUri = null;
        final boolean isSms = message.getProtocol() == MessageData.PROTOCOL_SMS;
        final int subId = actionParameters.getInt(KEY_SUB_ID, ParticipantData.DEFAULT_SELF_SUB_ID);
        final String subPhoneNumber = actionParameters.getString(KEY_SUB_PHONE_NUMBER);

        LogUtil.i(TAG, "SendMessageAction: Sending " + (isSms ? "SMS" : "MMS") + " message "
                + messageId + " in conversation " + message.getConversationId());

        int status;
        int rawStatus = MessageData.RAW_TELEPHONY_STATUS_UNDEFINED;
        int resultCode = MessageData.UNKNOWN_RESULT_CODE;
        if (isSms) {
            Assert.notNull(messageUri);
            final String recipient = actionParameters.getString(KEY_RECIPIENT);
            final String messageText = message.getMessageText();
            final String smsServiceCenter = actionParameters.getString(KEY_SMS_SERVICE_CENTER);
            final boolean deliveryReportRequired = MmsUtils.isDeliveryReportRequired(subId);

            status = MmsUtils.sendSmsMessage(recipient, messageText, messageUri, subId,
                    smsServiceCenter, deliveryReportRequired);
        } else {
            final Context context = Factory.get().getApplicationContext();
            final ArrayList<String> recipients =
                    actionParameters.getStringArrayList(KEY_RECIPIENTS);
            if (messageUri == null) {
                final long timestamp = message.getReceivedTimeStamp();

                // Inform sync that message has been added at local received timestamp
                final SyncManager syncManager = DataModel.get().getSyncManager();
                syncManager.onNewMessageInserted(timestamp);

                // For MMS messages first need to write to telephony (resizing images if needed)
                updatedMessageUri = MmsUtils.insertSendingMmsMessage(context, recipients,
                        message, subId, subPhoneNumber, timestamp);
                if (updatedMessageUri != null) {
                    messageUri = updatedMessageUri;
                    // To prevent Sync seeing inconsistent state must write to DB on this thread
                    updateMessageUri(messageId, updatedMessageUri);

                    if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                        LogUtil.v(TAG, "SendMessageAction: Updated message " + messageId
                                + " with new uri " + messageUri);
                    }
                 }
            }
            if (messageUri != null) {
                // Actually send the MMS
                final Bundle extras = new Bundle();
                extras.putString(EXTRA_MESSAGE_ID, messageId);
                extras.putParcelable(EXTRA_UPDATED_MESSAGE_URI, updatedMessageUri);
                final MmsUtils.StatusPlusUri result = MmsUtils.sendMmsMessage(context, subId,
                        messageUri, extras);
                if (result == MmsUtils.STATUS_PENDING) {
                    // Async send, so no status yet
                    LogUtil.d(TAG, "SendMessageAction: Sending MMS message " + messageId
                            + " asynchronously; waiting for callback to finish processing");
                    return null;
                }
                status = result.status;
                rawStatus = result.rawStatus;
                resultCode = result.resultCode;
            } else {
                status = MmsUtils.MMS_REQUEST_MANUAL_RETRY;
            }
        }

        // When we fast-fail before calling the MMS lib APIs (e.g. airplane mode,
        // sending message is deleted).
        ProcessSentMessageAction.processMessageSentFastFailed(messageId, messageUri,
                updatedMessageUri, subId, isSms, status, rawStatus, resultCode);
        return null;
    }

    private void updateMessageUri(final String messageId, final Uri updatedMessageUri) {
        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();
        try {
            final ContentValues values = new ContentValues();
            values.put(MessageColumns.SMS_MESSAGE_URI, updatedMessageUri.toString());
            BugleDatabaseOperations.updateMessageRow(db, messageId, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    protected Object processBackgroundResponse(final Bundle response) {
        // Nothing to do here, post-send tasks handled by ProcessSentMessageAction
        return null;
    }

    /**
     * Update message status to reflect success or failure
     */
    @Override
    protected Object processBackgroundFailure() {
        final String messageId = actionParameters.getString(KEY_MESSAGE_ID);
        final MessageData message = actionParameters.getParcelable(KEY_MESSAGE);
        final boolean isSms = message.getProtocol() == MessageData.PROTOCOL_SMS;
        final int subId = actionParameters.getInt(KEY_SUB_ID, ParticipantData.DEFAULT_SELF_SUB_ID);
        final int resultCode = actionParameters.getInt(ProcessSentMessageAction.KEY_RESULT_CODE);
        final int httpStatusCode =
                actionParameters.getInt(ProcessSentMessageAction.KEY_HTTP_STATUS_CODE);

        ProcessSentMessageAction.processResult(messageId, null /* updatedMessageUri */,
                MmsUtils.MMS_REQUEST_MANUAL_RETRY, MessageData.RAW_TELEPHONY_STATUS_UNDEFINED,
                isSms, this, subId, resultCode, httpStatusCode);

        // Whether we succeeded or failed we will check and maybe schedule some more work
        ProcessPendingMessagesAction.scheduleProcessPendingMessagesAction(true, this);

        return null;
    }

    /**
     * Update the message status (and message itself if necessary)
     * @param isSms whether this is an SMS or MMS
     * @param message message to update
     * @param updatedMessageUri message uri for newly-inserted messages; null otherwise
     * @param clearSeen whether the message 'seen' status should be reset if error occurs
     */
    public static boolean updateMessageAndStatus(final boolean isSms, final MessageData message,
            final Uri updatedMessageUri, final boolean clearSeen) {
        final Context context = Factory.get().getApplicationContext();
        final DatabaseWrapper db = DataModel.get().getDatabase();

        // TODO: We're optimistically setting the type/box of outgoing messages to
        // 'SENT' even before they actually are. We should technically be using QUEUED or OUTBOX
        // instead, but if we do that, it's possible that the Messaging app will try to send them
        // as part of its clean-up logic that runs when it starts (http://b/18155366).
        //
        // We also use the wrong status when inserting queued SMS messages in
        // InsertNewMessageAction.insertBroadcastSmsMessage and insertSendingSmsMessage (should be
        // QUEUED or OUTBOX), and in MmsUtils.insertSendReq (should be OUTBOX).

        boolean updatedTelephony = true;
        int messageBox;
        int type;
        switch(message.getStatus()) {
            case MessageData.BUGLE_STATUS_OUTGOING_COMPLETE:
            case MessageData.BUGLE_STATUS_OUTGOING_DELIVERED:
                type = Sms.MESSAGE_TYPE_SENT;
                messageBox = Mms.MESSAGE_BOX_SENT;
                break;
            case MessageData.BUGLE_STATUS_OUTGOING_YET_TO_SEND:
            case MessageData.BUGLE_STATUS_OUTGOING_AWAITING_RETRY:
                type = Sms.MESSAGE_TYPE_SENT;
                messageBox = Mms.MESSAGE_BOX_SENT;
                break;
            case MessageData.BUGLE_STATUS_OUTGOING_SENDING:
            case MessageData.BUGLE_STATUS_OUTGOING_RESENDING:
                type = Sms.MESSAGE_TYPE_SENT;
                messageBox = Mms.MESSAGE_BOX_SENT;
                break;
            case MessageData.BUGLE_STATUS_OUTGOING_FAILED:
            case MessageData.BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER:
                type = Sms.MESSAGE_TYPE_FAILED;
                messageBox = Mms.MESSAGE_BOX_FAILED;
                break;
            default:
                type = Sms.MESSAGE_TYPE_ALL;
                messageBox = Mms.MESSAGE_BOX_ALL;
                break;
        }
        // First in the telephony DB
        if (isSms) {
            // Ignore update message Uri
            if (type != Sms.MESSAGE_TYPE_ALL) {
                if (!MmsUtils.updateSmsMessageSendingStatus(context, message.getSmsMessageUri(),
                        type, message.getReceivedTimeStamp())) {
                    message.markMessageFailed(message.getSentTimeStamp());
                    updatedTelephony = false;
                }
            }
        } else if (message.getSmsMessageUri() != null) {
            if (messageBox != Mms.MESSAGE_BOX_ALL) {
                if (!MmsUtils.updateMmsMessageSendingStatus(context, message.getSmsMessageUri(),
                        messageBox, message.getReceivedTimeStamp())) {
                    message.markMessageFailed(message.getSentTimeStamp());
                    updatedTelephony = false;
                }
            }
        }
        if (updatedTelephony) {
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "SendMessageAction: Updated " + (isSms ? "SMS" : "MMS")
                        + " message " + message.getMessageId()
                        + " in telephony (" + message.getSmsMessageUri() + ")");
            }
        } else {
            LogUtil.w(TAG, "SendMessageAction: Failed to update " + (isSms ? "SMS" : "MMS")
                    + " message " + message.getMessageId()
                    + " in telephony (" + message.getSmsMessageUri() + "); marking message failed");
        }

        // Update the local DB
        db.beginTransaction();
        try {
            if (updatedMessageUri != null) {
                // Update all message and part fields
                BugleDatabaseOperations.updateMessageInTransaction(db, message);
                BugleDatabaseOperations.refreshConversationMetadataInTransaction(
                        db, message.getConversationId(), false/* shouldAutoSwitchSelfId */,
                        false/*archived*/);
            } else {
                final ContentValues values = new ContentValues();
                values.put(MessageColumns.STATUS, message.getStatus());

                if (clearSeen) {
                    // When a message fails to send, the message needs to
                    // be unseen to be selected as an error notification.
                    values.put(MessageColumns.SEEN, 0);
                }
                values.put(MessageColumns.RECEIVED_TIMESTAMP, message.getReceivedTimeStamp());
                values.put(MessageColumns.RAW_TELEPHONY_STATUS, message.getRawTelephonyStatus());

                BugleDatabaseOperations.updateMessageRowIfExists(db, message.getMessageId(),
                        values);
            }
            db.setTransactionSuccessful();
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "SendMessageAction: Updated " + (isSms ? "SMS" : "MMS")
                        + " message " + message.getMessageId() + " in local db. Timestamp = "
                        + message.getReceivedTimeStamp());
            }
        } finally {
            db.endTransaction();
        }

        MessagingContentProvider.notifyMessagesChanged(message.getConversationId());
        if (updatedMessageUri != null) {
            MessagingContentProvider.notifyPartsChanged();
        }

        return updatedTelephony;
    }

    private SendMessageAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<SendMessageAction> CREATOR
            = new Parcelable.Creator<SendMessageAction>() {
        @Override
        public SendMessageAction createFromParcel(final Parcel in) {
            return new SendMessageAction(in);
        }

        @Override
        public SendMessageAction[] newArray(final int size) {
            return new SendMessageAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
