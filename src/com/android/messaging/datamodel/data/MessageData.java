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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.Dates;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.OsUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageData implements Parcelable {
    private static final String[] sProjection = {
        MessageColumns._ID,
        MessageColumns.CONVERSATION_ID,
        MessageColumns.SENDER_PARTICIPANT_ID,
        MessageColumns.SELF_PARTICIPANT_ID,
        MessageColumns.SENT_TIMESTAMP,
        MessageColumns.RECEIVED_TIMESTAMP,
        MessageColumns.SEEN,
        MessageColumns.READ,
        MessageColumns.PROTOCOL,
        MessageColumns.STATUS,
        MessageColumns.SMS_MESSAGE_URI,
        MessageColumns.SMS_PRIORITY,
        MessageColumns.SMS_MESSAGE_SIZE,
        MessageColumns.MMS_SUBJECT,
        MessageColumns.MMS_TRANSACTION_ID,
        MessageColumns.MMS_CONTENT_LOCATION,
        MessageColumns.MMS_EXPIRY,
        MessageColumns.RAW_TELEPHONY_STATUS,
        MessageColumns.RETRY_START_TIMESTAMP,
    };

    private static final int INDEX_ID = 0;
    private static final int INDEX_CONVERSATION_ID = 1;
    private static final int INDEX_PARTICIPANT_ID = 2;
    private static final int INDEX_SELF_ID = 3;
    private static final int INDEX_SENT_TIMESTAMP = 4;
    private static final int INDEX_RECEIVED_TIMESTAMP = 5;
    private static final int INDEX_SEEN = 6;
    private static final int INDEX_READ = 7;
    private static final int INDEX_PROTOCOL = 8;
    private static final int INDEX_BUGLE_STATUS = 9;
    private static final int INDEX_SMS_MESSAGE_URI = 10;
    private static final int INDEX_SMS_PRIORITY = 11;
    private static final int INDEX_SMS_MESSAGE_SIZE = 12;
    private static final int INDEX_MMS_SUBJECT = 13;
    private static final int INDEX_MMS_TRANSACTION_ID = 14;
    private static final int INDEX_MMS_CONTENT_LOCATION = 15;
    private static final int INDEX_MMS_EXPIRY = 16;
    private static final int INDEX_RAW_TELEPHONY_STATUS = 17;
    private static final int INDEX_RETRY_START_TIMESTAMP = 18;

    // SQL statement to insert a "complete" message row (columns based on the projection above).
    private static final String INSERT_MESSAGE_SQL =
            "INSERT INTO " + DatabaseHelper.MESSAGES_TABLE + " ( "
                    + TextUtils.join(", ", Arrays.copyOfRange(sProjection, 1,
                            INDEX_RETRY_START_TIMESTAMP + 1))
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private String mMessageId;
    private String mConversationId;
    private String mParticipantId;
    private String mSelfId;
    private long mSentTimestamp;
    private long mReceivedTimestamp;
    private boolean mSeen;
    private boolean mRead;
    private int mProtocol;
    private Uri mSmsMessageUri;
    private int mSmsPriority;
    private long mSmsMessageSize;
    private String mMmsSubject;
    private String mMmsTransactionId;
    private String mMmsContentLocation;
    private long mMmsExpiry;
    private int mRawStatus;
    private int mStatus;
    private final ArrayList<MessagePartData> mParts;
    private long mRetryStartTimestamp;

    // PROTOCOL Values
    public static final int PROTOCOL_UNKNOWN = -1;              // Unknown type
    public static final int PROTOCOL_SMS = 0;                   // SMS message
    public static final int PROTOCOL_MMS = 1;                   // MMS message
    public static final int PROTOCOL_MMS_PUSH_NOTIFICATION = 2; // MMS WAP push notification

    // Bugle STATUS Values
    public static final int BUGLE_STATUS_UNKNOWN = 0;

    // Outgoing
    public static final int BUGLE_STATUS_OUTGOING_COMPLETE                = 1;
    public static final int BUGLE_STATUS_OUTGOING_DELIVERED               = 2;
    // Transitions to either YET_TO_SEND or SEND_AFTER_PROCESSING depending attachments.
    public static final int BUGLE_STATUS_OUTGOING_DRAFT                   = 3;
    public static final int BUGLE_STATUS_OUTGOING_YET_TO_SEND             = 4;
    public static final int BUGLE_STATUS_OUTGOING_SENDING                 = 5;
    public static final int BUGLE_STATUS_OUTGOING_RESENDING               = 6;
    public static final int BUGLE_STATUS_OUTGOING_AWAITING_RETRY          = 7;
    public static final int BUGLE_STATUS_OUTGOING_FAILED                  = 8;
    public static final int BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER = 9;

    // Incoming
    public static final int BUGLE_STATUS_INCOMING_COMPLETE                   = 100;
    public static final int BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD     = 101;
    public static final int BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD   = 102;
    public static final int BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING         = 103;
    public static final int BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD     = 104;
    public static final int BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING           = 105;
    public static final int BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED            = 106;
    public static final int BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE   = 107;

    public static final String getStatusDescription(int status) {
        switch (status) {
            case BUGLE_STATUS_UNKNOWN:
                return "UNKNOWN";
            case BUGLE_STATUS_OUTGOING_COMPLETE:
                return "OUTGOING_COMPLETE";
            case BUGLE_STATUS_OUTGOING_DELIVERED:
                return "OUTGOING_DELIVERED";
            case BUGLE_STATUS_OUTGOING_DRAFT:
                return "OUTGOING_DRAFT";
            case BUGLE_STATUS_OUTGOING_YET_TO_SEND:
                return "OUTGOING_YET_TO_SEND";
            case BUGLE_STATUS_OUTGOING_SENDING:
                return "OUTGOING_SENDING";
            case BUGLE_STATUS_OUTGOING_RESENDING:
                return "OUTGOING_RESENDING";
            case BUGLE_STATUS_OUTGOING_AWAITING_RETRY:
                return "OUTGOING_AWAITING_RETRY";
            case BUGLE_STATUS_OUTGOING_FAILED:
                return "OUTGOING_FAILED";
            case BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER:
                return "OUTGOING_FAILED_EMERGENCY_NUMBER";
            case BUGLE_STATUS_INCOMING_COMPLETE:
                return "INCOMING_COMPLETE";
            case BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD:
                return "INCOMING_YET_TO_MANUAL_DOWNLOAD";
            case BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD:
                return "INCOMING_RETRYING_MANUAL_DOWNLOAD";
            case BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING:
                return "INCOMING_MANUAL_DOWNLOADING";
            case BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD:
                return "INCOMING_RETRYING_AUTO_DOWNLOAD";
            case BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING:
                return "INCOMING_AUTO_DOWNLOADING";
            case BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED:
                return "INCOMING_DOWNLOAD_FAILED";
            case BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE:
                return "INCOMING_EXPIRED_OR_NOT_AVAILABLE";
            default:
                return String.valueOf(status) + " (check MessageData)";
        }
    }

    // All incoming messages expect to have status >= BUGLE_STATUS_FIRST_INCOMING
    public static final int BUGLE_STATUS_FIRST_INCOMING = BUGLE_STATUS_INCOMING_COMPLETE;

    // Detailed MMS failures. Most of the values are defined in PduHeaders. However, a few are
    // defined here instead. These are never returned in the MMS HTTP response, but are used
    // internally. The values here must not conflict with any of the existing PduHeader values.
    public static final int RAW_TELEPHONY_STATUS_UNDEFINED = MmsUtils.PDU_HEADER_VALUE_UNDEFINED;
    public static final int RAW_TELEPHONY_STATUS_MESSAGE_TOO_BIG = 10000;

    // Unknown result code for MMS sending/downloading. This is used as the default value
    // for result code returned from platform MMS API.
    public static final int UNKNOWN_RESULT_CODE = 0;

    /**
     * Create an "empty" message
     */
    public MessageData() {
        mParts = new ArrayList<MessagePartData>();
    }

    public static String[] getProjection() {
        return sProjection;
    }

    /**
     * Create a draft message for a particular conversation based on supplied content
     */
    public static MessageData createDraftMessage(final String conversationId,
            final String selfId, final MessageData content) {
        final MessageData message = new MessageData();
        message.mStatus = BUGLE_STATUS_OUTGOING_DRAFT;
        message.mProtocol = PROTOCOL_UNKNOWN;
        message.mConversationId = conversationId;
        message.mParticipantId = selfId;
        message.mReceivedTimestamp = System.currentTimeMillis();
        if (content == null) {
            message.mParts.add(MessagePartData.createTextMessagePart(""));
        } else {
            if (!TextUtils.isEmpty(content.mParticipantId)) {
                message.mParticipantId = content.mParticipantId;
            }
            if (!TextUtils.isEmpty(content.mMmsSubject)) {
                message.mMmsSubject = content.mMmsSubject;
            }
            for (final MessagePartData part : content.getParts()) {
                message.mParts.add(part);
            }
        }
        message.mSelfId = selfId;
        return message;
    }

    /**
     * Create a draft sms message for a particular conversation
     */
    public static MessageData createDraftSmsMessage(final String conversationId,
            final String selfId, final String messageText) {
        final MessageData message = new MessageData();
        message.mStatus = BUGLE_STATUS_OUTGOING_DRAFT;
        message.mProtocol = PROTOCOL_SMS;
        message.mConversationId = conversationId;
        message.mParticipantId = selfId;
        message.mSelfId = selfId;
        message.mParts.add(MessagePartData.createTextMessagePart(messageText));
        message.mReceivedTimestamp = System.currentTimeMillis();
        return message;
    }

    /**
     * Create a draft mms message for a particular conversation
     */
    public static MessageData createDraftMmsMessage(final String conversationId,
            final String selfId, final String messageText, final String subjectText) {
        final MessageData message = new MessageData();
        message.mStatus = BUGLE_STATUS_OUTGOING_DRAFT;
        message.mProtocol = PROTOCOL_MMS;
        message.mConversationId = conversationId;
        message.mParticipantId = selfId;
        message.mSelfId = selfId;
        message.mMmsSubject = subjectText;
        message.mReceivedTimestamp = System.currentTimeMillis();
        if (!TextUtils.isEmpty(messageText)) {
            message.mParts.add(MessagePartData.createTextMessagePart(messageText));
        }
        return message;
    }

    /**
     * Create a message received from a particular number in a particular conversation
     */
    public static MessageData createReceivedSmsMessage(final Uri uri, final String conversationId,
            final String participantId, final String selfId, final String messageText,
            final String subject, final long sent, final long recieved,
            final boolean seen, final boolean read) {
        final MessageData message = new MessageData();
        message.mSmsMessageUri = uri;
        message.mConversationId = conversationId;
        message.mParticipantId = participantId;
        message.mSelfId = selfId;
        message.mProtocol = PROTOCOL_SMS;
        message.mStatus = BUGLE_STATUS_INCOMING_COMPLETE;
        message.mMmsSubject = subject;
        message.mReceivedTimestamp = recieved;
        message.mSentTimestamp = sent;
        message.mParts.add(MessagePartData.createTextMessagePart(messageText));
        message.mSeen = seen;
        message.mRead = read;
        return message;
    }

    /**
     * Create a message not yet associated with a particular conversation
     */
    public static MessageData createSharedMessage(final String messageText,
            final String subjectText) {
        final MessageData message = new MessageData();
        message.mStatus = BUGLE_STATUS_OUTGOING_DRAFT;
        message.mMmsSubject = subjectText;
        if (!TextUtils.isEmpty(messageText)) {
            message.mParts.add(MessagePartData.createTextMessagePart(messageText));
        }
        return message;
    }

    /**
     * Create a message from Sms table fields
     */
    public static MessageData createSmsMessage(final String messageUri, final String participantId,
            final String selfId, final String conversationId, final int bugleStatus,
            final boolean seen, final boolean read, final long sent,
            final long recieved, final String messageText) {
        final MessageData message = new MessageData();
        message.mParticipantId = participantId;
        message.mSelfId = selfId;
        message.mConversationId = conversationId;
        message.mSentTimestamp = sent;
        message.mReceivedTimestamp = recieved;
        message.mSeen = seen;
        message.mRead = read;
        message.mProtocol = PROTOCOL_SMS;
        message.mStatus = bugleStatus;
        message.mSmsMessageUri = Uri.parse(messageUri);
        message.mParts.add(MessagePartData.createTextMessagePart(messageText));
        return message;
    }

    /**
     * Create a message from Mms table fields
     */
    public static MessageData createMmsMessage(final String messageUri, final String participantId,
            final String selfId, final String conversationId, final boolean isNotification,
            final int bugleStatus, final String contentLocation, final String transactionId,
            final int smsPriority, final String subject, final boolean seen, final boolean read,
            final long size, final int rawStatus, final long expiry, final long sent,
            final long received) {
        final MessageData message = new MessageData();
        message.mParticipantId = participantId;
        message.mSelfId = selfId;
        message.mConversationId = conversationId;
        message.mSentTimestamp = sent;
        message.mReceivedTimestamp = received;
        message.mMmsContentLocation = contentLocation;
        message.mMmsTransactionId = transactionId;
        message.mSeen = seen;
        message.mRead = read;
        message.mStatus = bugleStatus;
        message.mProtocol = (isNotification ? PROTOCOL_MMS_PUSH_NOTIFICATION : PROTOCOL_MMS);
        message.mSmsMessageUri = Uri.parse(messageUri);
        message.mSmsPriority = smsPriority;
        message.mSmsMessageSize = size;
        message.mMmsSubject = subject;
        message.mMmsExpiry = expiry;
        message.mRawStatus = rawStatus;
        if (bugleStatus == BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD ||
                bugleStatus == BUGLE_STATUS_OUTGOING_RESENDING) {
            // Set the retry start timestamp if this message is already in process of retrying
            // Either as autodownload is starting or sending already in progress (MMS update)
            message.mRetryStartTimestamp = received;
        }
        return message;
    }

    public void addPart(final MessagePartData part) {
        if (part instanceof PendingAttachmentData) {
            // Pending attachments may only be added to shared message data that's not associated
            // with any particular conversation, in order to store shared images.
            Assert.isTrue(mConversationId == null);
        }
        mParts.add(part);
    }

    public Iterable<MessagePartData> getParts() {
        return mParts;
    }

    public void bind(final Cursor cursor) {
        mMessageId = cursor.getString(INDEX_ID);
        mConversationId = cursor.getString(INDEX_CONVERSATION_ID);
        mParticipantId = cursor.getString(INDEX_PARTICIPANT_ID);
        mSelfId = cursor.getString(INDEX_SELF_ID);
        mSentTimestamp = cursor.getLong(INDEX_SENT_TIMESTAMP);
        mReceivedTimestamp = cursor.getLong(INDEX_RECEIVED_TIMESTAMP);
        mSeen = (cursor.getInt(INDEX_SEEN) != 0);
        mRead = (cursor.getInt(INDEX_READ) != 0);
        mProtocol = cursor.getInt(INDEX_PROTOCOL);
        mStatus = cursor.getInt(INDEX_BUGLE_STATUS);
        final String smsMessageUri = cursor.getString(INDEX_SMS_MESSAGE_URI);
        mSmsMessageUri = (smsMessageUri == null) ? null : Uri.parse(smsMessageUri);
        mSmsPriority = cursor.getInt(INDEX_SMS_PRIORITY);
        mSmsMessageSize = cursor.getLong(INDEX_SMS_MESSAGE_SIZE);
        mMmsExpiry = cursor.getLong(INDEX_MMS_EXPIRY);
        mRawStatus = cursor.getInt(INDEX_RAW_TELEPHONY_STATUS);
        mMmsSubject = cursor.getString(INDEX_MMS_SUBJECT);
        mMmsTransactionId = cursor.getString(INDEX_MMS_TRANSACTION_ID);
        mMmsContentLocation = cursor.getString(INDEX_MMS_CONTENT_LOCATION);
        mRetryStartTimestamp = cursor.getLong(INDEX_RETRY_START_TIMESTAMP);
    }

    /**
     * Bind to the draft message data for a conversation. The conversation's self id is used as
     * the draft's self id.
     */
    public void bindDraft(final Cursor cursor, final String conversationSelfId) {
        bind(cursor);
        mSelfId = conversationSelfId;
    }

    protected static String getParticipantId(final Cursor cursor) {
        return cursor.getString(INDEX_PARTICIPANT_ID);
    }

    public void populate(final ContentValues values) {
        values.put(MessageColumns.CONVERSATION_ID, mConversationId);
        values.put(MessageColumns.SENDER_PARTICIPANT_ID, mParticipantId);
        values.put(MessageColumns.SELF_PARTICIPANT_ID, mSelfId);
        values.put(MessageColumns.SENT_TIMESTAMP, mSentTimestamp);
        values.put(MessageColumns.RECEIVED_TIMESTAMP, mReceivedTimestamp);
        values.put(MessageColumns.SEEN, mSeen ? 1 : 0);
        values.put(MessageColumns.READ, mRead ? 1 : 0);
        values.put(MessageColumns.PROTOCOL, mProtocol);
        values.put(MessageColumns.STATUS, mStatus);
        final String smsMessageUri = ((mSmsMessageUri == null) ? null : mSmsMessageUri.toString());
        values.put(MessageColumns.SMS_MESSAGE_URI, smsMessageUri);
        values.put(MessageColumns.SMS_PRIORITY, mSmsPriority);
        values.put(MessageColumns.SMS_MESSAGE_SIZE, mSmsMessageSize);
        values.put(MessageColumns.MMS_EXPIRY, mMmsExpiry);
        values.put(MessageColumns.MMS_SUBJECT, mMmsSubject);
        values.put(MessageColumns.MMS_TRANSACTION_ID, mMmsTransactionId);
        values.put(MessageColumns.MMS_CONTENT_LOCATION, mMmsContentLocation);
        values.put(MessageColumns.RAW_TELEPHONY_STATUS, mRawStatus);
        values.put(MessageColumns.RETRY_START_TIMESTAMP, mRetryStartTimestamp);
    }

    /**
     * Note this is not thread safe so callers need to make sure they own the wrapper + statements
     * while they call this and use the returned value.
     */
    public SQLiteStatement getInsertStatement(final DatabaseWrapper db) {
        final SQLiteStatement insert = db.getStatementInTransaction(
                DatabaseWrapper.INDEX_INSERT_MESSAGE, INSERT_MESSAGE_SQL);
        insert.clearBindings();
        insert.bindString(INDEX_CONVERSATION_ID, mConversationId);
        insert.bindString(INDEX_PARTICIPANT_ID, mParticipantId);
        insert.bindString(INDEX_SELF_ID, mSelfId);
        insert.bindLong(INDEX_SENT_TIMESTAMP, mSentTimestamp);
        insert.bindLong(INDEX_RECEIVED_TIMESTAMP, mReceivedTimestamp);
        insert.bindLong(INDEX_SEEN, mSeen ? 1 : 0);
        insert.bindLong(INDEX_READ, mRead ? 1 : 0);
        insert.bindLong(INDEX_PROTOCOL, mProtocol);
        insert.bindLong(INDEX_BUGLE_STATUS, mStatus);
        if (mSmsMessageUri != null) {
            insert.bindString(INDEX_SMS_MESSAGE_URI, mSmsMessageUri.toString());
        }
        insert.bindLong(INDEX_SMS_PRIORITY, mSmsPriority);
        insert.bindLong(INDEX_SMS_MESSAGE_SIZE, mSmsMessageSize);
        insert.bindLong(INDEX_MMS_EXPIRY, mMmsExpiry);
        if (mMmsSubject != null) {
            insert.bindString(INDEX_MMS_SUBJECT, mMmsSubject);
        }
        if (mMmsTransactionId != null) {
            insert.bindString(INDEX_MMS_TRANSACTION_ID, mMmsTransactionId);
        }
        if (mMmsContentLocation != null) {
            insert.bindString(INDEX_MMS_CONTENT_LOCATION, mMmsContentLocation);
        }
        insert.bindLong(INDEX_RAW_TELEPHONY_STATUS, mRawStatus);
        insert.bindLong(INDEX_RETRY_START_TIMESTAMP, mRetryStartTimestamp);
        return insert;
    }

    public final String getMessageId() {
        return mMessageId;
    }

    public final String getConversationId() {
        return mConversationId;
    }

    public final String getParticipantId() {
        return mParticipantId;
    }

    public final String getSelfId() {
        return mSelfId;
    }

    public final long getSentTimeStamp() {
        return mSentTimestamp;
    }

    public final long getReceivedTimeStamp() {
        return mReceivedTimestamp;
    }

    public final String getFormattedReceivedTimeStamp() {
        return Dates.getMessageTimeString(mReceivedTimestamp).toString();
    }

    public final int getProtocol() {
        return mProtocol;
    }

    public final int getStatus() {
        return mStatus;
    }

    public final Uri getSmsMessageUri() {
        return mSmsMessageUri;
    }

    public final int getSmsPriority() {
        return mSmsPriority;
    }

    public final long getSmsMessageSize() {
        return mSmsMessageSize;
    }

    public final String getMmsSubject() {
        return mMmsSubject;
    }

    public final void setMmsSubject(final String subject) {
        mMmsSubject = subject;
    }

    public final String getMmsContentLocation() {
        return mMmsContentLocation;
    }

    public final String getMmsTransactionId() {
        return mMmsTransactionId;
    }

    public final boolean getMessageSeen() {
        return mSeen;
    }

    public final long getMmsExpiry() {
        return mMmsExpiry;
    }

    /**
     * For incoming MMS messages this returns the retrieve-status value
     * For sent MMS messages this returns the response-status value
     * See PduHeaders.java for possible values
     * Otherwise (SMS etc) this is RAW_TELEPHONY_STATUS_UNDEFINED
     */
    public final int getRawTelephonyStatus() {
        return mRawStatus;
    }

    public final void setMessageSeen(final boolean hasSeen) {
        mSeen = hasSeen;
    }

    public final boolean getInResendWindow(final long now) {
        final long maxAgeToResend = BugleGservices.get().getLong(
                BugleGservicesKeys.MESSAGE_RESEND_TIMEOUT_MS,
                BugleGservicesKeys.MESSAGE_RESEND_TIMEOUT_MS_DEFAULT);
        final long age = now - mRetryStartTimestamp;
        return age < maxAgeToResend;
    }

    public final boolean getInDownloadWindow(final long now) {
        final long maxAgeToRedownload = BugleGservices.get().getLong(
                BugleGservicesKeys.MESSAGE_DOWNLOAD_TIMEOUT_MS,
                BugleGservicesKeys.MESSAGE_DOWNLOAD_TIMEOUT_MS_DEFAULT);
        final long age = now - mRetryStartTimestamp;
        return age < maxAgeToRedownload;
    }

    static boolean getShowDownloadMessage(final int status) {
        if (OsUtil.isSecondaryUser()) {
            // Secondary users can't download mms's. Mms's are downloaded by bugle running as the
            // primary user.
            return false;
        }
        // Should show option for manual download iff status is manual download or failed
        return (status == BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED ||
                status == BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD ||
                // If debug is enabled, allow to download an expired or unavailable message.
                (DebugUtils.isDebugEnabled()
                        && status == BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE));
    }

    public boolean canDownloadMessage() {
        if (OsUtil.isSecondaryUser()) {
            // Secondary users can't download mms's. Mms's are downloaded by bugle running as the
            // primary user.
            return false;
        }
        // Can download iff status is retrying auto/manual downloading
        return (mStatus == BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD ||
                mStatus == BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD);
    }

    public boolean canRedownloadMessage() {
        if (OsUtil.isSecondaryUser()) {
            // Secondary users can't download mms's. Mms's are downloaded by bugle running as the
            // primary user.
            return false;
        }
        // Can redownload iff status is manual download not started or download failed
        return (mStatus == BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED ||
                mStatus == BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD ||
                // If debug is enabled, allow to download an expired or unavailable message.
                (DebugUtils.isDebugEnabled()
                        && mStatus == BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE));
    }

    static boolean getShowResendMessage(final int status) {
        // Should show option to resend iff status is failed
        return (status == BUGLE_STATUS_OUTGOING_FAILED);
    }

    static boolean getOneClickResendMessage(final int status, final int rawStatus) {
        // Should show option to resend iff status is failed
        return (status == BUGLE_STATUS_OUTGOING_FAILED
                && rawStatus == RAW_TELEPHONY_STATUS_UNDEFINED);
    }

    public boolean canResendMessage() {
        // Manual retry allowed only from failed
        return (mStatus == BUGLE_STATUS_OUTGOING_FAILED);
    }

    public boolean canSendMessage() {
        // Sending messages must be in yet_to_send or awaiting_retry state
        return (mStatus == BUGLE_STATUS_OUTGOING_YET_TO_SEND ||
                mStatus == BUGLE_STATUS_OUTGOING_AWAITING_RETRY);
    }

    public final boolean getYetToSend() {
        return (mStatus == BUGLE_STATUS_OUTGOING_YET_TO_SEND);
    }

    public final boolean getIsMms() {
        return mProtocol == MessageData.PROTOCOL_MMS
                || mProtocol == MessageData.PROTOCOL_MMS_PUSH_NOTIFICATION;
    }

    public static final boolean getIsMmsNotification(final int protocol) {
        return (protocol == MessageData.PROTOCOL_MMS_PUSH_NOTIFICATION);
    }

    public final boolean getIsMmsNotification() {
        return getIsMmsNotification(mProtocol);
    }

    public static final boolean getIsSms(final int protocol) {
        return protocol == (MessageData.PROTOCOL_SMS);
    }

    public final boolean getIsSms() {
        return getIsSms(mProtocol);
    }

    public static boolean getIsIncoming(final int status) {
        return (status >= MessageData.BUGLE_STATUS_FIRST_INCOMING);
    }

    public boolean getIsIncoming() {
        return getIsIncoming(mStatus);
    }

    public long getRetryStartTimestamp() {
        return mRetryStartTimestamp;
    }

    public final String getMessageText() {
        final String separator = System.getProperty("line.separator");
        final StringBuilder text = new StringBuilder();
        for (final MessagePartData part : mParts) {
            if (!part.isAttachment() && !TextUtils.isEmpty(part.getText())) {
                if (text.length() > 0) {
                    text.append(separator);
                }
                text.append(part.getText());
            }
        }
        return text.toString();
    }

    /**
     * Takes all captions from attachments and adds them as a prefix to the first text part or
     * appends a text part
     */
    public final void consolidateText() {
        final String separator = System.getProperty("line.separator");
        final StringBuilder captionText = new StringBuilder();
        MessagePartData firstTextPart = null;
        int firstTextPartIndex = -1;
        for (int i = 0; i < mParts.size(); i++) {
            final MessagePartData part = mParts.get(i);
            if (firstTextPart == null && !part.isAttachment()) {
                firstTextPart = part;
                firstTextPartIndex = i;
            }
            if (part.isAttachment() && !TextUtils.isEmpty(part.getText())) {
                if (captionText.length() > 0) {
                    captionText.append(separator);
                }
                captionText.append(part.getText());
            }
        }

        if (captionText.length() == 0) {
            // Nothing to consolidate
            return;
        }

        if (firstTextPart == null) {
            addPart(MessagePartData.createTextMessagePart(captionText.toString()));
        } else {
            final String partText = firstTextPart.getText();
            if (partText.length() > 0) {
                captionText.append(separator);
                captionText.append(partText);
            }
            mParts.set(firstTextPartIndex,
                    MessagePartData.createTextMessagePart(captionText.toString()));
        }
    }

    public final MessagePartData getFirstAttachment() {
        for (final MessagePartData part : mParts) {
            if (part.isAttachment()) {
                return part;
            }
        }
        return null;
    }

    /**
     * Updates the messageId for this message.
     * Can be used to reset the messageId prior to persisting (which will assign a new messageId)
     *  or can be called on a message that does not yet have a valid messageId to set it.
     */
    public void updateMessageId(final String messageId) {
        Assert.isTrue(TextUtils.isEmpty(messageId) || TextUtils.isEmpty(mMessageId));
        mMessageId = messageId;

        // TODO : This should probably also call updateMessageId on the message parts. We
        // may also want to make messages effectively immutable once they have a valid message id.
    }

    public final void updateSendingMessage(final String conversationId, final Uri messageUri,
            final long timestamp) {
        mConversationId = conversationId;
        mSmsMessageUri = messageUri;
        mRead = true;
        mSeen = true;
        mReceivedTimestamp = timestamp;
        mSentTimestamp = timestamp;
        mStatus = BUGLE_STATUS_OUTGOING_YET_TO_SEND;
        mRetryStartTimestamp = timestamp;
    }

    public final void markMessageManualResend(final long timestamp) {
        // Manual send updates timestamp and transitions back to initial sending status.
        mReceivedTimestamp = timestamp;
        mSentTimestamp = timestamp;
        mStatus = BUGLE_STATUS_OUTGOING_SENDING;
    }

    public final void markMessageSending(final long timestamp) {
        // Initial send
        mStatus = BUGLE_STATUS_OUTGOING_SENDING;
        mSentTimestamp = timestamp;
    }

    public final void markMessageResending(final long timestamp) {
        // Auto resend of message
        mStatus = BUGLE_STATUS_OUTGOING_RESENDING;
        mSentTimestamp = timestamp;
    }

    public final void markMessageSent(final long timestamp) {
        mSentTimestamp = timestamp;
        mStatus = BUGLE_STATUS_OUTGOING_COMPLETE;
    }

    public final void markMessageFailed(final long timestamp) {
        mSentTimestamp = timestamp;
        mStatus = BUGLE_STATUS_OUTGOING_FAILED;
    }

    public final void markMessageFailedEmergencyNumber(final long timestamp) {
        mSentTimestamp = timestamp;
        mStatus = BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER;
    }

    public final void markMessageNotSent(final long timestamp) {
        mSentTimestamp = timestamp;
        mStatus = BUGLE_STATUS_OUTGOING_AWAITING_RETRY;
    }

    public final void updateSizesForImageParts() {
        for (final MessagePartData part : getParts()) {
            part.decodeAndSaveSizeIfImage(false /* saveToStorage */);
        }
    }

    public final void setRetryStartTimestamp(final long timestamp) {
        mRetryStartTimestamp = timestamp;
    }

    public final void setRawTelephonyStatus(final int rawStatus) {
        mRawStatus = rawStatus;
    }

    public boolean hasContent() {
        return !TextUtils.isEmpty(mMmsSubject) ||
                getFirstAttachment() != null ||
                !TextUtils.isEmpty(getMessageText());
    }

    public final void bindSelfId(final String selfId) {
        mSelfId = selfId;
    }

    public final void bindParticipantId(final String participantId) {
        mParticipantId = participantId;
    }

    protected MessageData(final Parcel in) {
        mMessageId = in.readString();
        mConversationId = in.readString();
        mParticipantId = in.readString();
        mSelfId = in.readString();
        mSentTimestamp = in.readLong();
        mReceivedTimestamp = in.readLong();
        mSeen = (in.readInt() != 0);
        mRead = (in.readInt() != 0);
        mProtocol = in.readInt();
        mStatus = in.readInt();
        final String smsMessageUri = in.readString();
        mSmsMessageUri = (smsMessageUri == null ? null : Uri.parse(smsMessageUri));
        mSmsPriority = in.readInt();
        mSmsMessageSize = in.readLong();
        mMmsExpiry = in.readLong();
        mMmsSubject = in.readString();
        mMmsTransactionId = in.readString();
        mMmsContentLocation = in.readString();
        mRawStatus = in.readInt();
        mRetryStartTimestamp = in.readLong();

        // Read parts
        mParts = new ArrayList<MessagePartData>();
        final int partCount = in.readInt();
        for (int i = 0; i < partCount; i++) {
            mParts.add((MessagePartData) in.readParcelable(MessagePartData.class.getClassLoader()));
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(mMessageId);
        dest.writeString(mConversationId);
        dest.writeString(mParticipantId);
        dest.writeString(mSelfId);
        dest.writeLong(mSentTimestamp);
        dest.writeLong(mReceivedTimestamp);
        dest.writeInt(mRead ? 1 : 0);
        dest.writeInt(mSeen ? 1 : 0);
        dest.writeInt(mProtocol);
        dest.writeInt(mStatus);
        final String smsMessageUri = (mSmsMessageUri == null) ? null : mSmsMessageUri.toString();
        dest.writeString(smsMessageUri);
        dest.writeInt(mSmsPriority);
        dest.writeLong(mSmsMessageSize);
        dest.writeLong(mMmsExpiry);
        dest.writeString(mMmsSubject);
        dest.writeString(mMmsTransactionId);
        dest.writeString(mMmsContentLocation);
        dest.writeInt(mRawStatus);
        dest.writeLong(mRetryStartTimestamp);

        // Write parts
        dest.writeInt(mParts.size());
        for (final MessagePartData messagePartData : mParts) {
            dest.writeParcelable(messagePartData, flags);
        }
    }

    public static final Parcelable.Creator<MessageData> CREATOR
            = new Parcelable.Creator<MessageData>() {
        @Override
        public MessageData createFromParcel(final Parcel in) {
            return new MessageData(in);
        }

        @Override
        public MessageData[] newArray(final int size) {
            return new MessageData[size];
        }
    };

    @Override
    public String toString() {
        return toString(mMessageId, mParts);
    }

    public static String toString(String messageId, List<MessagePartData> parts) {
        StringBuilder sb = new StringBuilder();
        if (messageId != null) {
            sb.append(messageId);
            sb.append(": ");
        }
        for (MessagePartData part : parts) {
            sb.append(part.toString());
            sb.append(" ");
        }
        return sb.toString();
    }
}
