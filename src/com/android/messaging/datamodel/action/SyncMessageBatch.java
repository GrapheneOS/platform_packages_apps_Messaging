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
import android.database.sqlite.SQLiteConstraintException;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.text.TextUtils;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.ConversationColumns;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.SyncManager.ThreadInfoCache;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.mmslib.pdu.PduHeaders;
import com.android.messaging.sms.DatabaseMessages.LocalDatabaseMessage;
import com.android.messaging.sms.DatabaseMessages.MmsMessage;
import com.android.messaging.sms.DatabaseMessages.SmsMessage;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Update local database with a batch of messages to add/delete in one transaction
 */
class SyncMessageBatch {
    private static final String TAG = LogUtil.BUGLE_TAG;

    // Variables used during executeAction
    private final HashSet<String> mConversationsToUpdate;
    // Cache of thread->conversationId map
    private final ThreadInfoCache mCache;

    // Set of SMS messages to add
    private final ArrayList<SmsMessage> mSmsToAdd;
    // Set of MMS messages to add
    private final ArrayList<MmsMessage> mMmsToAdd;
    // Set of local messages to delete
    private final ArrayList<LocalDatabaseMessage> mMessagesToDelete;

    SyncMessageBatch(final ArrayList<SmsMessage> smsToAdd,
            final ArrayList<MmsMessage> mmsToAdd,
            final ArrayList<LocalDatabaseMessage> messagesToDelete,
            final ThreadInfoCache cache) {
        mSmsToAdd = smsToAdd;
        mMmsToAdd = mmsToAdd;
        mMessagesToDelete = messagesToDelete;
        mCache = cache;
        mConversationsToUpdate = new HashSet<String>();
    }

    void updateLocalDatabase() {
        // Perform local database changes in one transaction
        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();
        try {
            // Store all the SMS messages
            for (final SmsMessage sms : mSmsToAdd) {
                storeSms(db, sms);
            }
            // Store all the MMS messages
            for (final MmsMessage mms : mMmsToAdd) {
                storeMms(db, mms);
            }
            // Keep track of conversations with messages deleted
            for (final LocalDatabaseMessage message : mMessagesToDelete) {
                mConversationsToUpdate.add(message.getConversationId());
            }
            // Batch delete local messages
            batchDelete(db, DatabaseHelper.MESSAGES_TABLE, MessageColumns._ID,
                    messageListToIds(mMessagesToDelete));

            for (final LocalDatabaseMessage message : mMessagesToDelete) {
                if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                    LogUtil.v(TAG, "SyncMessageBatch: Deleted message " + message.getLocalId()
                            + " for SMS/MMS " + message.getUri() + " with timestamp "
                            + message.getTimestampInMillis());
                }
            }

            // Update conversation state for imported messages, like snippet,
            updateConversations(db);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static String[] messageListToIds(final List<LocalDatabaseMessage> messagesToDelete) {
        final String[] ids = new String[messagesToDelete.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Long.toString(messagesToDelete.get(i).getLocalId());
        }
        return ids;
    }

    /**
     * Store the SMS message into local database.
     *
     * @param sms
     */
    private void storeSms(final DatabaseWrapper db, final SmsMessage sms) {
        if (sms.mBody == null) {
            LogUtil.w(TAG, "SyncMessageBatch: SMS " + sms.mUri + " has no body; adding empty one");
            // try to fix it
            sms.mBody = "";
        }

        if (TextUtils.isEmpty(sms.mAddress)) {
            LogUtil.e(TAG, "SyncMessageBatch: SMS has no address; using unknown sender");
            // try to fix it
            sms.mAddress = ParticipantData.getUnknownSenderDestination();
        }

        // TODO : We need to also deal with messages in a failed/retry state
        final boolean isOutgoing = sms.mType != Sms.MESSAGE_TYPE_INBOX;

        final String otherPhoneNumber = sms.mAddress;

        // A forced resync of all messages should still keep the archived states.
        // The database upgrade code notifies sync manager of this. We need to
        // honor the original customization to this conversation if created.
        final String conversationId = mCache.getOrCreateConversation(db, sms.mThreadId, sms.mSubId,
                DataModel.get().getSyncManager().getCustomizationForThread(sms.mThreadId));
        if (conversationId == null) {
            // Cannot create conversation for this message? This should not happen.
            LogUtil.e(TAG, "SyncMessageBatch: Failed to create conversation for SMS thread "
                    + sms.mThreadId);
            return;
        }
        final ParticipantData self = ParticipantData.getSelfParticipant(sms.getSubId());
        final String selfId =
                BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, self);
        final ParticipantData sender = isOutgoing ?
                self :
                ParticipantData.getFromRawPhoneBySimLocale(otherPhoneNumber, sms.getSubId());
        final String participantId = (isOutgoing ? selfId :
                BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, sender));

        final int bugleStatus = bugleStatusForSms(isOutgoing, sms.mType, sms.mStatus);

        final MessageData message = MessageData.createSmsMessage(
                sms.mUri,
                participantId,
                selfId,
                conversationId,
                bugleStatus,
                sms.mSeen,
                sms.mRead,
                sms.mTimestampSentInMillis,
                sms.mTimestampInMillis,
                sms.mBody);

        // Inserting sms content into messages table
        try {
            BugleDatabaseOperations.insertNewMessageInTransaction(db, message);
        } catch (SQLiteConstraintException e) {
            rethrowSQLiteConstraintExceptionWithDetails(e, db, sms.mUri, sms.mThreadId,
                    conversationId, selfId, participantId);
        }

        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "SyncMessageBatch: Inserted new message " + message.getMessageId()
                    + " for SMS " + message.getSmsMessageUri() + " received at "
                    + message.getReceivedTimeStamp());
        }

        // Keep track of updated conversation for later updating the conversation snippet, etc.
        mConversationsToUpdate.add(conversationId);
    }

    public static int bugleStatusForSms(final boolean isOutgoing, final int type,
            final int status) {
        int bugleStatus = MessageData.BUGLE_STATUS_UNKNOWN;
        // For a message we sync either
        if (isOutgoing) {
            // Outgoing message not yet been sent
            if (type == Telephony.Sms.MESSAGE_TYPE_FAILED ||
                    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                    type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
                    (type == Telephony.Sms.MESSAGE_TYPE_SENT &&
                     status == Telephony.Sms.STATUS_FAILED)) {
                // Not sent counts as failed and available for manual resend
                bugleStatus = MessageData.BUGLE_STATUS_OUTGOING_FAILED;
            } else if (status == Sms.STATUS_COMPLETE) {
                bugleStatus = MessageData.BUGLE_STATUS_OUTGOING_DELIVERED;
            } else {
                // Otherwise outgoing message is complete
                bugleStatus = MessageData.BUGLE_STATUS_OUTGOING_COMPLETE;
            }
        } else {
            // All incoming SMS messages are complete
            bugleStatus = MessageData.BUGLE_STATUS_INCOMING_COMPLETE;
        }
        return bugleStatus;
    }

    /**
     * Store the MMS message into local database
     *
     * @param mms
     */
    private void storeMms(final DatabaseWrapper db, final MmsMessage mms) {
        if (mms.mParts.size() < 1) {
            LogUtil.w(TAG, "SyncMessageBatch: MMS " + mms.mUri + " has no parts");
        }

        // TODO : We need to also deal with messages in a failed/retry state
        final boolean isOutgoing = mms.mType != Mms.MESSAGE_BOX_INBOX;
        final boolean isNotification = (mms.mMmsMessageType ==
                PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND);

        final String senderId = mms.mSender;

        // A forced resync of all messages should still keep the archived states.
        // The database upgrade code notifies sync manager of this. We need to
        // honor the original customization to this conversation if created.
        final String conversationId = mCache.getOrCreateConversation(db, mms.mThreadId, mms.mSubId,
                DataModel.get().getSyncManager().getCustomizationForThread(mms.mThreadId));
        if (conversationId == null) {
            LogUtil.e(TAG, "SyncMessageBatch: Failed to create conversation for MMS thread "
                    + mms.mThreadId);
            return;
        }
        final ParticipantData self = ParticipantData.getSelfParticipant(mms.getSubId());
        final String selfId =
                BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, self);
        final ParticipantData sender = isOutgoing ?
                self : ParticipantData.getFromRawPhoneBySimLocale(senderId, mms.getSubId());
        final String participantId = (isOutgoing ? selfId :
                BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, sender));

        final int bugleStatus = MmsUtils.bugleStatusForMms(isOutgoing, isNotification, mms.mType);

        // Import message and all of the parts.
        // TODO : For now we are importing these in the order we found them in the MMS
        // database. Ideally we would load and parse the SMIL which describes how the parts relate
        // to one another.

        // TODO: Need to set correct status on message
        final MessageData message = MmsUtils.createMmsMessage(mms, conversationId, participantId,
                selfId, bugleStatus);

        // Inserting mms content into messages table
        try {
            BugleDatabaseOperations.insertNewMessageInTransaction(db, message);
        } catch (SQLiteConstraintException e) {
            rethrowSQLiteConstraintExceptionWithDetails(e, db, mms.mUri, mms.mThreadId,
                    conversationId, selfId, participantId);
        }

        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "SyncMessageBatch: Inserted new message " + message.getMessageId()
                    + " for MMS " + message.getSmsMessageUri() + " received at "
                    + message.getReceivedTimeStamp());
        }

        // Keep track of updated conversation for later updating the conversation snippet, etc.
        mConversationsToUpdate.add(conversationId);
    }

    // TODO: Remove this after we no longer see this crash (b/18375758)
    private static void rethrowSQLiteConstraintExceptionWithDetails(SQLiteConstraintException e,
            DatabaseWrapper db, String messageUri, long threadId, String conversationId,
            String selfId, String senderId) {
        // Add some extra debug information to the exception for tracking down b/18375758.
        // The default detail message for SQLiteConstraintException tells us that a foreign
        // key constraint failed, but not which one! Messages have foreign keys to 3 tables:
        // conversations, participants (self), participants (sender). We'll query each one
        // to determine which one(s) violated the constraint, and then throw a new exception
        // with those details.

        String foundConversationId = null;
        Cursor cursor = null;
        try {
            // Look for an existing conversation in the db with the conversation id
            cursor = db.rawQuery("SELECT " + ConversationColumns._ID
                    + " FROM " + DatabaseHelper.CONVERSATIONS_TABLE
                    + " WHERE " + ConversationColumns._ID + "=" + conversationId,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                Assert.isTrue(cursor.getCount() == 1);
                foundConversationId = cursor.getString(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        ParticipantData foundSelfParticipant =
                BugleDatabaseOperations.getExistingParticipant(db, selfId);
        ParticipantData foundSenderParticipant =
                BugleDatabaseOperations.getExistingParticipant(db, senderId);

        String errorMsg = "SQLiteConstraintException while inserting message for " + messageUri
                + "; conversation id from getOrCreateConversation = " + conversationId
                + " (lookup thread = " + threadId + "), found conversation id = "
                + foundConversationId + ", found self participant = "
                + LogUtil.sanitizePII(foundSelfParticipant.getNormalizedDestination())
                + " (lookup id = " + selfId + "), found sender participant = "
                + LogUtil.sanitizePII(foundSenderParticipant.getNormalizedDestination())
                + " (lookup id = " + senderId + ")";
        throw new RuntimeException(errorMsg, e);
    }

    /**
     * Use the tracked latest message info to update conversations, including
     * latest chat message and sort timestamp.
     */
    private void updateConversations(final DatabaseWrapper db) {
        for (final String conversationId : mConversationsToUpdate) {
            if (BugleDatabaseOperations.deleteConversationIfEmptyInTransaction(db,
                    conversationId)) {
                continue;
            }

            final boolean archived = mCache.isArchived(conversationId);
            // Always attempt to auto-switch conversation self id for sync/import case.
            BugleDatabaseOperations.maybeRefreshConversationMetadataInTransaction(db,
                    conversationId, true /*shouldAutoSwitchSelfId*/, archived /*keepArchived*/);
        }
    }


    /**
     * Batch delete database rows by matching a column with a list of values, usually some
     * kind of IDs.
     *
     * @param table
     * @param column
     * @param ids
     * @return Total number of deleted messages
     */
    private static int batchDelete(final DatabaseWrapper db, final String table,
            final String column, final String[] ids) {
        int totalDeleted = 0;
        final int totalIds = ids.length;
        for (int start = 0; start < totalIds; start += MmsUtils.MAX_IDS_PER_QUERY) {
            final int end = Math.min(start + MmsUtils.MAX_IDS_PER_QUERY, totalIds); //excluding
            final int count = end - start;
            final String batchSelection = String.format(
                    Locale.US,
                    "%s IN %s",
                    column,
                    MmsUtils.getSqlInOperand(count));
            final String[] batchSelectionArgs = Arrays.copyOfRange(ids, start, end);
            final int deleted = db.delete(
                    table,
                    batchSelection,
                    batchSelectionArgs);
            totalDeleted += deleted;
        }
        return totalDeleted;
    }
}
