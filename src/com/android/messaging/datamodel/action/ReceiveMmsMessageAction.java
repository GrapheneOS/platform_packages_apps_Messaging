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

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DataModelException;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.SyncManager;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.mmslib.pdu.PduHeaders;
import com.android.messaging.sms.DatabaseMessages;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.LogUtil;

import java.util.List;

/**
 * Action used to "receive" an incoming message
 */
public class ReceiveMmsMessageAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    private static final String KEY_SUB_ID = "sub_id";
    private static final String KEY_PUSH_DATA = "push_data";
    private static final String KEY_TRANSACTION_ID = "transaction_id";
    private static final String KEY_CONTENT_LOCATION = "content_location";

    /**
     * Create a message received from a particular number in a particular conversation
     */
    public ReceiveMmsMessageAction(final int subId, final byte[] pushData) {
        actionParameters.putInt(KEY_SUB_ID, subId);
        actionParameters.putByteArray(KEY_PUSH_DATA, pushData);
    }

    @Override
    protected Object executeAction() {
        final Context context = Factory.get().getApplicationContext();
        final int subId = actionParameters.getInt(KEY_SUB_ID, ParticipantData.DEFAULT_SELF_SUB_ID);
        final byte[] pushData = actionParameters.getByteArray(KEY_PUSH_DATA);
        final DatabaseWrapper db = DataModel.get().getDatabase();

        // Write received message to telephony DB
        MessageData message = null;
        final ParticipantData self = BugleDatabaseOperations.getOrCreateSelf(db, subId);

        final long received = System.currentTimeMillis();
        // Inform sync that message has been added at local received timestamp
        final SyncManager syncManager = DataModel.get().getSyncManager();
        syncManager.onNewMessageInserted(received);

        // TODO: Should use local time to set received time in MMS message
        final DatabaseMessages.MmsMessage mms = MmsUtils.processReceivedPdu(
                context, pushData, self.getSubId(), self.getNormalizedDestination());

        if (mms != null) {
            final List<String> recipients = MmsUtils.getRecipientsByThread(mms.mThreadId);
            String from = MmsUtils.getMmsSender(recipients, mms.getUri());
            if (from == null) {
                LogUtil.w(TAG, "Received an MMS without sender address; using unknown sender.");
                from = ParticipantData.getUnknownSenderDestination();
            }
            final ParticipantData rawSender = ParticipantData.getFromRawPhoneBySimLocale(
                    from, subId);
            final boolean blocked = BugleDatabaseOperations.isBlockedDestination(
                    db, rawSender.getNormalizedDestination());
            final boolean autoDownload = (!blocked && MmsUtils.allowMmsAutoRetrieve(subId));
            final String conversationId =
                    BugleDatabaseOperations.getOrCreateConversationFromThreadId(db, mms.mThreadId,
                            blocked, subId);

            final boolean messageInFocusedConversation =
                    DataModel.get().isFocusedConversation(conversationId);
            final boolean messageInObservableConversation =
                    DataModel.get().isNewMessageObservable(conversationId);

            // TODO: Also write these values to the telephony provider
            mms.mRead = messageInFocusedConversation;
            mms.mSeen = messageInObservableConversation || blocked;

            // Write received placeholder message to our DB
            db.beginTransaction();
            try {
                final String participantId =
                        BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, rawSender);
                final String selfId =
                        BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, self);

                message = MmsUtils.createMmsMessage(mms, conversationId, participantId, selfId,
                        (autoDownload ? MessageData.BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD :
                            MessageData.BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD));
                // Write the message
                BugleDatabaseOperations.insertNewMessageInTransaction(db, message);

                if (!autoDownload) {
                    BugleDatabaseOperations.updateConversationMetadataInTransaction(db,
                            conversationId, message.getMessageId(), message.getReceivedTimeStamp(),
                            blocked, true /* shouldAutoSwitchSelfId */);
                    final ParticipantData sender = ParticipantData .getFromId(
                            db, participantId);
                    BugleActionToasts.onMessageReceived(conversationId, sender, message);
                }
                // else update the conversation once we have downloaded final message (or failed)
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            // Update conversation if not immediately initiating a download
            if (!autoDownload) {
                MessagingContentProvider.notifyMessagesChanged(message.getConversationId());
                MessagingContentProvider.notifyPartsChanged();

                // Show a notification to let the user know a new message has arrived
                BugleNotifications.update(false/*silent*/, conversationId,
                        BugleNotifications.UPDATE_ALL);

                // Send the NotifyRespInd with DEFERRED status since no auto download
                actionParameters.putString(KEY_TRANSACTION_ID, mms.mTransactionId);
                actionParameters.putString(KEY_CONTENT_LOCATION, mms.mContentLocation);
                requestBackgroundWork();
            }

            LogUtil.i(TAG, "ReceiveMmsMessageAction: Received MMS message " + message.getMessageId()
                    + " in conversation " + message.getConversationId()
                    + ", uri = " + message.getSmsMessageUri());
        } else {
            LogUtil.e(TAG, "ReceiveMmsMessageAction: Skipping processing of incoming PDU");
        }

        ProcessPendingMessagesAction.scheduleProcessPendingMessagesAction(false, this);

        return message;
    }

    @Override
    protected Bundle doBackgroundWork() throws DataModelException {
        final Context context = Factory.get().getApplicationContext();
        final int subId = actionParameters.getInt(KEY_SUB_ID, ParticipantData.DEFAULT_SELF_SUB_ID);
        final String transactionId = actionParameters.getString(KEY_TRANSACTION_ID);
        final String contentLocation = actionParameters.getString(KEY_CONTENT_LOCATION);
        MmsUtils.sendNotifyResponseForMmsDownload(
                context,
                subId,
                MmsUtils.stringToBytes(transactionId, "UTF-8"),
                contentLocation,
                PduHeaders.STATUS_DEFERRED);
        // We don't need to return anything.
        return null;
    }

    private ReceiveMmsMessageAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<ReceiveMmsMessageAction> CREATOR
            = new Parcelable.Creator<ReceiveMmsMessageAction>() {
        @Override
        public ReceiveMmsMessageAction createFromParcel(final Parcel in) {
            return new ReceiveMmsMessageAction(in);
        }

        @Override
        public ReceiveMmsMessageAction[] newArray(final int size) {
            return new ReceiveMmsMessageAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
