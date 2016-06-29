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

package com.android.messaging.datamodel;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import com.android.messaging.BugleApplication;
import com.android.messaging.R;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.DoesNotRunOnMainThread;
import com.android.messaging.util.LogUtil;
import com.google.common.annotations.VisibleForTesting;

/**
 * TODO: Open Issues:
 * - Should we be storing the draft messages in the regular messages table or should we have a
 *   separate table for drafts to keep the normal messages query as simple as possible?
 */

/**
 * Allows access to the SQL database.  This is package private.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "bugle_db";

    private static final int getDatabaseVersion(final Context context) {
        return Integer.parseInt(context.getResources().getString(R.string.database_version));
    }

    /** Table containing names of all other tables and views */
    private static final String MASTER_TABLE = "sqlite_master";
    /** Column containing the name of the tables and views */
    private static final String[] MASTER_COLUMNS = new String[] { "name", };

    // Table names
    public static final String CONVERSATIONS_TABLE = "conversations";
    public static final String MESSAGES_TABLE = "messages";
    public static final String PARTS_TABLE = "parts";
    public static final String PARTICIPANTS_TABLE = "participants";
    public static final String CONVERSATION_PARTICIPANTS_TABLE = "conversation_participants";

    // Views
    static final String DRAFT_PARTS_VIEW = "draft_parts_view";

    // Conversations table schema
    public static class ConversationColumns implements BaseColumns {
        /* SMS/MMS Thread ID from the system provider */
        public static final String SMS_THREAD_ID = "sms_thread_id";

        /* Display name for the conversation */
        public static final String NAME = "name";

        /* Latest Message ID for the read status to display in conversation list */
        public static final String LATEST_MESSAGE_ID = "latest_message_id";

        /* Latest text snippet for display in conversation list */
        public static final String SNIPPET_TEXT = "snippet_text";

        /* Latest text subject for display in conversation list, empty string if none exists */
        public static final String SUBJECT_TEXT = "subject_text";

        /* Preview Uri */
        public static final String PREVIEW_URI = "preview_uri";

        /* The preview uri's content type */
        public static final String PREVIEW_CONTENT_TYPE = "preview_content_type";

        /* If we should display the current draft snippet/preview pair or snippet/preview pair */
        public static final String SHOW_DRAFT = "show_draft";

        /* Latest draft text subject for display in conversation list, empty string if none exists*/
        public static final String DRAFT_SUBJECT_TEXT = "draft_subject_text";

        /* Latest draft text snippet for display, empty string if none exists */
        public static final String DRAFT_SNIPPET_TEXT = "draft_snippet_text";

        /* Draft Preview Uri, empty string if none exists */
        public static final String DRAFT_PREVIEW_URI = "draft_preview_uri";

        /* The preview uri's content type */
        public static final String DRAFT_PREVIEW_CONTENT_TYPE = "draft_preview_content_type";

        /* If this conversation is archived */
        public static final String ARCHIVE_STATUS = "archive_status";

        /* Timestamp for sorting purposes */
        public static final String SORT_TIMESTAMP = "sort_timestamp";

        /* Last read message timestamp */
        public static final String LAST_READ_TIMESTAMP = "last_read_timestamp";

        /* Avatar for the conversation. Could be for group of individual */
        public static final String ICON = "icon";

        /* Participant contact ID if this conversation has a single participant. -1 otherwise */
        public static final String PARTICIPANT_CONTACT_ID = "participant_contact_id";

        /* Participant lookup key if this conversation has a single participant. null otherwise */
        public static final String PARTICIPANT_LOOKUP_KEY = "participant_lookup_key";

        /*
         * Participant's normalized destination if this conversation has a single participant.
         * null otherwise.
         */
        public static final String OTHER_PARTICIPANT_NORMALIZED_DESTINATION =
                "participant_normalized_destination";

        /* Default self participant for the conversation */
        public static final String CURRENT_SELF_ID = "current_self_id";

        /* Participant count not including self (so will be 1 for 1:1 or bigger for group) */
        public static final String PARTICIPANT_COUNT = "participant_count";

        /* Should notifications be enabled for this conversation? */
        public static final String NOTIFICATION_ENABLED = "notification_enabled";

        /* Notification sound used for the conversation */
        public static final String NOTIFICATION_SOUND_URI = "notification_sound_uri";

        /* Should vibrations be enabled for the conversation's notification? */
        public static final String NOTIFICATION_VIBRATION = "notification_vibration";

        /* Conversation recipients include email address */
        public static final String INCLUDE_EMAIL_ADDRESS = "include_email_addr";

        // Record the last received sms's service center info if it indicates that the reply path
        // is present (TP-Reply-Path), so that we could use it for the subsequent message to send.
        // Refer to TS 23.040 D.6 and SmsMessageSender.java in Android Messaging app.
        public static final String SMS_SERVICE_CENTER = "sms_service_center";

        // A conversation is enterprise if one of the participant is a enterprise contact.
        public static final String IS_ENTERPRISE = "IS_ENTERPRISE";
    }

    // Conversation table SQL
    private static final String CREATE_CONVERSATIONS_TABLE_SQL =
            "CREATE TABLE " + CONVERSATIONS_TABLE + "("
                    + ConversationColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    // TODO : Int? Required not default?
                    + ConversationColumns.SMS_THREAD_ID + " INT DEFAULT(0), "
                    + ConversationColumns.NAME + " TEXT, "
                    + ConversationColumns.LATEST_MESSAGE_ID + " INT, "
                    + ConversationColumns.SNIPPET_TEXT + " TEXT, "
                    + ConversationColumns.SUBJECT_TEXT + " TEXT, "
                    + ConversationColumns.PREVIEW_URI + " TEXT, "
                    + ConversationColumns.PREVIEW_CONTENT_TYPE + " TEXT, "
                    + ConversationColumns.SHOW_DRAFT + " INT DEFAULT(0), "
                    + ConversationColumns.DRAFT_SNIPPET_TEXT + " TEXT, "
                    + ConversationColumns.DRAFT_SUBJECT_TEXT + " TEXT, "
                    + ConversationColumns.DRAFT_PREVIEW_URI + " TEXT, "
                    + ConversationColumns.DRAFT_PREVIEW_CONTENT_TYPE + " TEXT, "
                    + ConversationColumns.ARCHIVE_STATUS + " INT DEFAULT(0), "
                    + ConversationColumns.SORT_TIMESTAMP + " INT DEFAULT(0), "
                    + ConversationColumns.LAST_READ_TIMESTAMP + " INT DEFAULT(0), "
                    + ConversationColumns.ICON + " TEXT, "
                    + ConversationColumns.PARTICIPANT_CONTACT_ID + " INT DEFAULT ( "
                            + ParticipantData.PARTICIPANT_CONTACT_ID_NOT_RESOLVED + "), "
                    + ConversationColumns.PARTICIPANT_LOOKUP_KEY + " TEXT, "
                    + ConversationColumns.OTHER_PARTICIPANT_NORMALIZED_DESTINATION + " TEXT, "
                    + ConversationColumns.CURRENT_SELF_ID + " TEXT, "
                    + ConversationColumns.PARTICIPANT_COUNT + " INT DEFAULT(0), "
                    + ConversationColumns.NOTIFICATION_ENABLED + " INT DEFAULT(1), "
                    + ConversationColumns.NOTIFICATION_SOUND_URI + " TEXT, "
                    + ConversationColumns.NOTIFICATION_VIBRATION + " INT DEFAULT(1), "
                    + ConversationColumns.INCLUDE_EMAIL_ADDRESS + " INT DEFAULT(0), "
                    + ConversationColumns.SMS_SERVICE_CENTER + " TEXT ,"
                    + ConversationColumns.IS_ENTERPRISE + " INT DEFAULT(0)"
                    + ");";

    private static final String CONVERSATIONS_TABLE_SMS_THREAD_ID_INDEX_SQL =
            "CREATE INDEX index_" + CONVERSATIONS_TABLE + "_" + ConversationColumns.SMS_THREAD_ID
            + " ON " +  CONVERSATIONS_TABLE
            + "(" + ConversationColumns.SMS_THREAD_ID + ")";

    private static final String CONVERSATIONS_TABLE_ARCHIVE_STATUS_INDEX_SQL =
            "CREATE INDEX index_" + CONVERSATIONS_TABLE + "_" + ConversationColumns.ARCHIVE_STATUS
            + " ON " +  CONVERSATIONS_TABLE
            + "(" + ConversationColumns.ARCHIVE_STATUS + ")";

    private static final String CONVERSATIONS_TABLE_SORT_TIMESTAMP_INDEX_SQL =
            "CREATE INDEX index_" + CONVERSATIONS_TABLE + "_" + ConversationColumns.SORT_TIMESTAMP
            + " ON " +  CONVERSATIONS_TABLE
            + "(" + ConversationColumns.SORT_TIMESTAMP + ")";

    // Messages table schema
    public static class MessageColumns implements BaseColumns {
        /* conversation id that this message belongs to */
        public static final String CONVERSATION_ID = "conversation_id";

        /* participant which send this message */
        public static final String SENDER_PARTICIPANT_ID = "sender_id";

        /* This is bugle's internal status for the message */
        public static final String STATUS = "message_status";

        /* Type of message: SMS, MMS or MMS notification */
        public static final String PROTOCOL = "message_protocol";

        /* This is the time that the sender sent the message */
        public static final String SENT_TIMESTAMP = "sent_timestamp";

        /* Time that we received the message on this device */
        public static final String RECEIVED_TIMESTAMP = "received_timestamp";

        /* When the message has been seen by a user in a notification */
        public static final String SEEN = "seen";

        /* When the message has been read by a user */
        public static final String READ = "read";

        /* participant representing the sim which processed this message */
        public static final String SELF_PARTICIPANT_ID = "self_id";

        /*
         * Time when a retry is initiated. This is used to compute the retry window
         * when we retry sending/downloading a message.
         */
        public static final String RETRY_START_TIMESTAMP = "retry_start_timestamp";

        // Columns which map to the SMS provider

        /* Message ID from the platform provider */
        public static final String SMS_MESSAGE_URI = "sms_message_uri";

        /* The message priority for MMS message */
        public static final String SMS_PRIORITY = "sms_priority";

        /* The message size for MMS message */
        public static final String SMS_MESSAGE_SIZE = "sms_message_size";

        /* The subject for MMS message */
        public static final String MMS_SUBJECT = "mms_subject";

        /* Transaction id for MMS notificaiton */
        public static final String MMS_TRANSACTION_ID = "mms_transaction_id";

        /* Content location for MMS notificaiton */
        public static final String MMS_CONTENT_LOCATION = "mms_content_location";

        /* The expiry time (ms) for MMS message */
        public static final String MMS_EXPIRY = "mms_expiry";

        /* The detailed status (RESPONSE_STATUS or RETRIEVE_STATUS) for MMS message */
        public static final String RAW_TELEPHONY_STATUS = "raw_status";
    }

    // Messages table SQL
    private static final String CREATE_MESSAGES_TABLE_SQL =
            "CREATE TABLE " + MESSAGES_TABLE + " ("
                    + MessageColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + MessageColumns.CONVERSATION_ID + " INT, "
                    + MessageColumns.SENDER_PARTICIPANT_ID + " INT, "
                    + MessageColumns.SENT_TIMESTAMP + " INT DEFAULT(0), "
                    + MessageColumns.RECEIVED_TIMESTAMP + " INT DEFAULT(0), "
                    + MessageColumns.PROTOCOL + " INT DEFAULT(0), "
                    + MessageColumns.STATUS + " INT DEFAULT(0), "
                    + MessageColumns.SEEN + " INT DEFAULT(0), "
                    + MessageColumns.READ + " INT DEFAULT(0), "
                    + MessageColumns.SMS_MESSAGE_URI + " TEXT, "
                    + MessageColumns.SMS_PRIORITY + " INT DEFAULT(0), "
                    + MessageColumns.SMS_MESSAGE_SIZE + " INT DEFAULT(0), "
                    + MessageColumns.MMS_SUBJECT + " TEXT, "
                    + MessageColumns.MMS_TRANSACTION_ID + " TEXT, "
                    + MessageColumns.MMS_CONTENT_LOCATION + " TEXT, "
                    + MessageColumns.MMS_EXPIRY + " INT DEFAULT(0), "
                    + MessageColumns.RAW_TELEPHONY_STATUS + " INT DEFAULT(0), "
                    + MessageColumns.SELF_PARTICIPANT_ID + " INT, "
                    + MessageColumns.RETRY_START_TIMESTAMP + " INT DEFAULT(0), "
                    + "FOREIGN KEY (" + MessageColumns.CONVERSATION_ID + ") REFERENCES "
                    + CONVERSATIONS_TABLE + "(" + ConversationColumns._ID + ") ON DELETE CASCADE "
                    + "FOREIGN KEY (" + MessageColumns.SENDER_PARTICIPANT_ID + ") REFERENCES "
                    + PARTICIPANTS_TABLE + "(" + ParticipantColumns._ID + ") ON DELETE SET NULL "
                    + "FOREIGN KEY (" + MessageColumns.SELF_PARTICIPANT_ID + ") REFERENCES "
                    + PARTICIPANTS_TABLE + "(" + ParticipantColumns._ID + ") ON DELETE SET NULL "
                    + ");";

    // Primary sort index for messages table : by conversation id, status, received timestamp.
    private static final String MESSAGES_TABLE_SORT_INDEX_SQL =
            "CREATE INDEX index_" + MESSAGES_TABLE + "_sort ON " +  MESSAGES_TABLE + "("
                    + MessageColumns.CONVERSATION_ID + ", "
                    + MessageColumns.STATUS + ", "
                    + MessageColumns.RECEIVED_TIMESTAMP + ")";

    private static final String MESSAGES_TABLE_STATUS_SEEN_INDEX_SQL =
            "CREATE INDEX index_" + MESSAGES_TABLE + "_status_seen ON " +  MESSAGES_TABLE + "("
                    + MessageColumns.STATUS + ", "
                    + MessageColumns.SEEN + ")";

    // Parts table schema
    // A part may contain text or a media url, but not both.
    public static class PartColumns implements BaseColumns {
        /* message id that this part belongs to */
        public static final String MESSAGE_ID = "message_id";

        /* conversation id that this part belongs to */
        public static final String CONVERSATION_ID = "conversation_id";

        /* text for this part */
        public static final String TEXT = "text";

        /* content uri for this part */
        public static final String CONTENT_URI = "uri";

        /* content type for this part */
        public static final String CONTENT_TYPE = "content_type";

        /* cached width for this part (for layout while loading) */
        public static final String WIDTH = "width";

        /* cached height for this part (for layout while loading) */
        public static final String HEIGHT = "height";

        /* de-normalized copy of timestamp from the messages table.  This is populated
         * via an insert trigger on the parts table.
         */
        public static final String TIMESTAMP = "timestamp";
    }

    // Message part table SQL
    private static final String CREATE_PARTS_TABLE_SQL =
            "CREATE TABLE " + PARTS_TABLE + "("
                    + PartColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + PartColumns.MESSAGE_ID + " INT,"
                    + PartColumns.TEXT + " TEXT,"
                    + PartColumns.CONTENT_URI + " TEXT,"
                    + PartColumns.CONTENT_TYPE + " TEXT,"
                    + PartColumns.WIDTH + " INT DEFAULT("
                    + MessagingContentProvider.UNSPECIFIED_SIZE + "),"
                    + PartColumns.HEIGHT + " INT DEFAULT("
                    + MessagingContentProvider.UNSPECIFIED_SIZE + "),"
                    + PartColumns.TIMESTAMP + " INT, "
                    + PartColumns.CONVERSATION_ID + " INT NOT NULL,"
                    + "FOREIGN KEY (" + PartColumns.MESSAGE_ID + ") REFERENCES "
                    + MESSAGES_TABLE + "(" + MessageColumns._ID + ") ON DELETE CASCADE "
                    + "FOREIGN KEY (" + PartColumns.CONVERSATION_ID + ") REFERENCES "
                    + CONVERSATIONS_TABLE + "(" + ConversationColumns._ID + ") ON DELETE CASCADE "
                    + ");";

    public static final String CREATE_PARTS_TRIGGER_SQL =
            "CREATE TRIGGER " + PARTS_TABLE + "_TRIGGER" + " AFTER INSERT ON " + PARTS_TABLE
            + " FOR EACH ROW "
            + " BEGIN UPDATE " + PARTS_TABLE
            + " SET " + PartColumns.TIMESTAMP + "="
            + " (SELECT received_timestamp FROM " + MESSAGES_TABLE + " WHERE " + MESSAGES_TABLE
            + "." + MessageColumns._ID + "=" + "NEW." + PartColumns.MESSAGE_ID + ")"
            + " WHERE " + PARTS_TABLE + "." + PartColumns._ID + "=" + "NEW." + PartColumns._ID
            + "; END";

    public static final String CREATE_MESSAGES_TRIGGER_SQL =
            "CREATE TRIGGER " + MESSAGES_TABLE + "_TRIGGER" + " AFTER UPDATE OF "
            + MessageColumns.RECEIVED_TIMESTAMP + " ON " + MESSAGES_TABLE
            + " FOR EACH ROW BEGIN UPDATE " + PARTS_TABLE + " SET " + PartColumns.TIMESTAMP
            + " = NEW." + MessageColumns.RECEIVED_TIMESTAMP + " WHERE " + PARTS_TABLE + "."
            + PartColumns.MESSAGE_ID + " = NEW." + MessageColumns._ID
            + "; END;";

    // Primary sort index for parts table : by message_id
    private static final String PARTS_TABLE_MESSAGE_INDEX_SQL =
            "CREATE INDEX index_" + PARTS_TABLE + "_message_id ON " + PARTS_TABLE + "("
                    + PartColumns.MESSAGE_ID + ")";

    // Participants table schema
    public static class ParticipantColumns implements BaseColumns {
        /* The subscription id for the sim associated with this self participant.
         * Introduced in L. For earlier versions will always be default_sub_id (-1).
         * For multi sim devices (or cases where the sim was changed) single device
         * may have several different sub_id values */
        public static final String SUB_ID = "sub_id";

        /* The slot of the active SIM (inserted in the device) for this self-participant. If the
         * self-participant doesn't correspond to any active SIM, this will be
         * {@link android.telephony.SubscriptionManager#INVALID_SLOT_ID}.
         * The column is ignored for all non-self participants.
         */
        public static final String SIM_SLOT_ID = "sim_slot_id";

        /* The phone number stored in a standard E164 format if possible.  This is unique for a
         * given participant.  We can't handle multiple participants with the same phone number
         * since we don't know which of them a message comes from. This can also be an email
         * address, in which case this is the same as the displayed address */
        public static final String NORMALIZED_DESTINATION = "normalized_destination";

        /* The phone number as originally supplied and used for dialing. Not necessarily in E164
         * format or unique */
        public static final String SEND_DESTINATION = "send_destination";

        /* The user-friendly formatting of the phone number according to the region setting of
         * the device when the row was added. */
        public static final String DISPLAY_DESTINATION = "display_destination";

        /* A string with this participant's full name or a pretty printed phone number */
        public static final String FULL_NAME = "full_name";

        /* A string with just this participant's first name */
        public static final String FIRST_NAME = "first_name";

        /* A local URI to an asset for the icon for this participant */
        public static final String PROFILE_PHOTO_URI = "profile_photo_uri";

        /* Contact id for matching local contact for this participant */
        public static final String CONTACT_ID = "contact_id";

        /* String that contains hints on how to find contact information in a contact lookup */
        public static final String LOOKUP_KEY = "lookup_key";

        /* If this participant is blocked */
        public static final String BLOCKED = "blocked";

        /* The color of the subscription (FOR SELF PARTICIPANTS ONLY) */
        public static final String SUBSCRIPTION_COLOR = "subscription_color";

        /* The name of the subscription (FOR SELF PARTICIPANTS ONLY) */
        public static final String SUBSCRIPTION_NAME = "subscription_name";

        /* The exact destination stored in Contacts for this participant */
        public static final String CONTACT_DESTINATION = "contact_destination";
    }

    // Participants table SQL
    private static final String CREATE_PARTICIPANTS_TABLE_SQL =
            "CREATE TABLE " + PARTICIPANTS_TABLE + "("
                    + ParticipantColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + ParticipantColumns.SUB_ID + " INT DEFAULT("
                    + ParticipantData.OTHER_THAN_SELF_SUB_ID + "),"
                    + ParticipantColumns.SIM_SLOT_ID + " INT DEFAULT("
                    + ParticipantData.INVALID_SLOT_ID + "),"
                    + ParticipantColumns.NORMALIZED_DESTINATION + " TEXT,"
                    + ParticipantColumns.SEND_DESTINATION + " TEXT,"
                    + ParticipantColumns.DISPLAY_DESTINATION + " TEXT,"
                    + ParticipantColumns.FULL_NAME + " TEXT,"
                    + ParticipantColumns.FIRST_NAME + " TEXT,"
                    + ParticipantColumns.PROFILE_PHOTO_URI + " TEXT, "
                    + ParticipantColumns.CONTACT_ID + " INT DEFAULT( "
                    + ParticipantData.PARTICIPANT_CONTACT_ID_NOT_RESOLVED + "), "
                    + ParticipantColumns.LOOKUP_KEY + " STRING, "
                    + ParticipantColumns.BLOCKED + " INT DEFAULT(0), "
                    + ParticipantColumns.SUBSCRIPTION_NAME + " TEXT, "
                    + ParticipantColumns.SUBSCRIPTION_COLOR + " INT DEFAULT(0), "
                    + ParticipantColumns.CONTACT_DESTINATION + " TEXT, "
                    + "UNIQUE (" + ParticipantColumns.NORMALIZED_DESTINATION + ", "
                    + ParticipantColumns.SUB_ID + ") ON CONFLICT FAIL" + ");";

    private static final String CREATE_SELF_PARTICIPANT_SQL =
            "INSERT INTO " + PARTICIPANTS_TABLE
            + " ( " +  ParticipantColumns.SUB_ID + " ) VALUES ( %s )";

    static String getCreateSelfParticipantSql(int subId) {
        return String.format(CREATE_SELF_PARTICIPANT_SQL, subId);
    }

    // Conversation Participants table schema - contains a list of participants excluding the user
    // in a given conversation.
    public static class ConversationParticipantsColumns implements BaseColumns {
        /* participant id of someone in this conversation */
        public static final String PARTICIPANT_ID = "participant_id";

        /* conversation id that this participant belongs to */
        public static final String CONVERSATION_ID = "conversation_id";
    }

    // Conversation Participants table SQL
    private static final String CREATE_CONVERSATION_PARTICIPANTS_TABLE_SQL =
            "CREATE TABLE " + CONVERSATION_PARTICIPANTS_TABLE + "("
                    + ConversationParticipantsColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + ConversationParticipantsColumns.CONVERSATION_ID + " INT,"
                    + ConversationParticipantsColumns.PARTICIPANT_ID + " INT,"
                    + "UNIQUE (" + ConversationParticipantsColumns.CONVERSATION_ID + ","
                    + ConversationParticipantsColumns.PARTICIPANT_ID + ") ON CONFLICT FAIL, "
                    + "FOREIGN KEY (" + ConversationParticipantsColumns.CONVERSATION_ID + ") "
                    + "REFERENCES " + CONVERSATIONS_TABLE + "(" + ConversationColumns._ID + ")"
                    + " ON DELETE CASCADE "
                    + "FOREIGN KEY (" + ConversationParticipantsColumns.PARTICIPANT_ID + ")"
                    + " REFERENCES " + PARTICIPANTS_TABLE + "(" + ParticipantColumns._ID + "));";

    // Primary access pattern for conversation participants is to look them up for a specific
    // conversation.
    private static final String CONVERSATION_PARTICIPANTS_TABLE_CONVERSATION_ID_INDEX_SQL =
            "CREATE INDEX index_" + CONVERSATION_PARTICIPANTS_TABLE + "_"
                    + ConversationParticipantsColumns.CONVERSATION_ID
                    + " ON " +  CONVERSATION_PARTICIPANTS_TABLE
                    + "(" + ConversationParticipantsColumns.CONVERSATION_ID + ")";

    // View for getting parts which are for draft messages.
    static final String DRAFT_PARTS_VIEW_SQL = "CREATE VIEW " +
            DRAFT_PARTS_VIEW + " AS SELECT "
            + PARTS_TABLE + '.' + PartColumns._ID
            + " as " + PartColumns._ID + ", "
            + PARTS_TABLE + '.' + PartColumns.MESSAGE_ID
            + " as " + PartColumns.MESSAGE_ID + ", "
            + PARTS_TABLE + '.' + PartColumns.TEXT
            + " as " + PartColumns.TEXT + ", "
            + PARTS_TABLE + '.' + PartColumns.CONTENT_URI
            + " as " + PartColumns.CONTENT_URI + ", "
            + PARTS_TABLE + '.' + PartColumns.CONTENT_TYPE
            + " as " + PartColumns.CONTENT_TYPE + ", "
            + PARTS_TABLE + '.' + PartColumns.WIDTH
            + " as " + PartColumns.WIDTH + ", "
            + PARTS_TABLE + '.' + PartColumns.HEIGHT
            + " as " + PartColumns.HEIGHT + ", "
            + MESSAGES_TABLE + '.' + MessageColumns.CONVERSATION_ID
            + " as " + MessageColumns.CONVERSATION_ID + " "
            + " FROM " + MESSAGES_TABLE + " LEFT JOIN " + PARTS_TABLE + " ON ("
            + MESSAGES_TABLE + "." + MessageColumns._ID
            + "=" + PARTS_TABLE + "." + PartColumns.MESSAGE_ID + ")"
            // Exclude draft messages from main view
            + " WHERE " + MESSAGES_TABLE + "." + MessageColumns.STATUS
            + " = " + MessageData.BUGLE_STATUS_OUTGOING_DRAFT;

    // List of all our SQL tables
    private static final String[] CREATE_TABLE_SQLS = new String[] {
        CREATE_CONVERSATIONS_TABLE_SQL,
        CREATE_MESSAGES_TABLE_SQL,
        CREATE_PARTS_TABLE_SQL,
        CREATE_PARTICIPANTS_TABLE_SQL,
        CREATE_CONVERSATION_PARTICIPANTS_TABLE_SQL,
    };

    // List of all our indices
    private static final String[] CREATE_INDEX_SQLS = new String[] {
        CONVERSATIONS_TABLE_SMS_THREAD_ID_INDEX_SQL,
        CONVERSATIONS_TABLE_ARCHIVE_STATUS_INDEX_SQL,
        CONVERSATIONS_TABLE_SORT_TIMESTAMP_INDEX_SQL,
        MESSAGES_TABLE_SORT_INDEX_SQL,
        MESSAGES_TABLE_STATUS_SEEN_INDEX_SQL,
        PARTS_TABLE_MESSAGE_INDEX_SQL,
        CONVERSATION_PARTICIPANTS_TABLE_CONVERSATION_ID_INDEX_SQL,
    };

    // List of all our SQL triggers
    private static final String[] CREATE_TRIGGER_SQLS = new String[] {
            CREATE_PARTS_TRIGGER_SQL,
            CREATE_MESSAGES_TRIGGER_SQL,
    };

    // List of all our views
    private static final String[] CREATE_VIEW_SQLS = new String[] {
        ConversationListItemData.getConversationListViewSql(),
        ConversationImagePartsView.getCreateSql(),
        DRAFT_PARTS_VIEW_SQL,
    };

    private static final Object sLock = new Object();
    private final Context mApplicationContext;
    private static DatabaseHelper sHelperInstance;      // Protected by sLock.

    private final Object mDatabaseWrapperLock = new Object();
    private DatabaseWrapper mDatabaseWrapper;           // Protected by mDatabaseWrapperLock.
    private final DatabaseUpgradeHelper mUpgradeHelper = new DatabaseUpgradeHelper();

    /**
     * Get a (singleton) instance of {@link DatabaseHelper}, creating one if there isn't one yet.
     * This is the only public method for getting a new instance of the class.
     * @param context Should be the application context (or something that will live for the
     * lifetime of the application).
     * @return The current (or a new) DatabaseHelper instance.
     */
    public static DatabaseHelper getInstance(final Context context) {
        synchronized (sLock) {
            if (sHelperInstance == null) {
                sHelperInstance = new DatabaseHelper(context);
            }
            return sHelperInstance;
        }
    }

    /**
     * Private constructor, used from {@link #getInstance()}.
     * @param context Should be the application context (or something that will live for the
     * lifetime of the application).
     */
    private DatabaseHelper(final Context context) {
        super(context, DATABASE_NAME, null, getDatabaseVersion(context), null);
        mApplicationContext = context;
    }

    /**
     * Test method that always instantiates a new DatabaseHelper instance. This should
     * be used ONLY by the tests and never by the real application.
     * @param context Test context.
     * @return Brand new DatabaseHelper instance.
     */
    @VisibleForTesting
    static DatabaseHelper getNewInstanceForTest(final Context context) {
        Assert.isEngBuild();
        Assert.isTrue(BugleApplication.isRunningTests());
        return new DatabaseHelper(context);
    }

    /**
     * Get the (singleton) instance of @{link DatabaseWrapper}.
     * <p>The database is always opened as a writeable database.
     * @return The current (or a new) DatabaseWrapper instance.
     */
    @DoesNotRunOnMainThread
    DatabaseWrapper getDatabase() {
        // We prevent the main UI thread from accessing the database here since we have to allow
        // public access to this class to enable sub-packages to access data.
        Assert.isNotMainThread();

        synchronized (mDatabaseWrapperLock) {
            if (mDatabaseWrapper == null) {
                mDatabaseWrapper = new DatabaseWrapper(mApplicationContext, getWritableDatabase());
            }
            return mDatabaseWrapper;
        }
    }

    @Override
    public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        mUpgradeHelper.onDowngrade(db, oldVersion, newVersion);
    }

    /**
     * Drops and recreates all tables.
     */
    public static void rebuildTables(final SQLiteDatabase db) {
        // Drop tables first, then views, and indices.
        dropAllTables(db);
        dropAllViews(db);
        dropAllIndexes(db);
        dropAllTriggers(db);

        // Recreate the whole database.
        createDatabase(db);
    }

    /**
     * Drop and rebuild a given view.
     */
    static void rebuildView(final SQLiteDatabase db, final String viewName,
            final String createViewSql) {
        dropView(db, viewName, true /* throwOnFailure */);
        db.execSQL(createViewSql);
    }

    private static void dropView(final SQLiteDatabase db, final String viewName,
            final boolean throwOnFailure) {
        final String dropPrefix = "DROP VIEW IF EXISTS ";
        try {
            db.execSQL(dropPrefix + viewName);
        } catch (final SQLException ex) {
            if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.DEBUG)) {
                LogUtil.d(LogUtil.BUGLE_TAG, "unable to drop view " + viewName + " "
                        + ex);
            }

            if (throwOnFailure) {
                throw ex;
            }
        }
    }

    public static void rebuildAllViews(final DatabaseWrapper db) {
        for (final String sql : DatabaseHelper.CREATE_VIEW_SQLS) {
            db.execSQL(sql);
        }
    }

    /**
     * Drops all user-defined tables from the given database.
     */
    private static void dropAllTables(final SQLiteDatabase db) {
        final Cursor tableCursor =
                db.query(MASTER_TABLE, MASTER_COLUMNS, "type='table'", null, null, null, null);
        if (tableCursor != null) {
            try {
                final String dropPrefix = "DROP TABLE IF EXISTS ";
                while (tableCursor.moveToNext()) {
                    final String tableName = tableCursor.getString(0);

                    // Skip special tables
                    if (tableName.startsWith("android_") || tableName.startsWith("sqlite_")) {
                        continue;
                    }
                    try {
                        db.execSQL(dropPrefix + tableName);
                    } catch (final SQLException ex) {
                        if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.DEBUG)) {
                            LogUtil.d(LogUtil.BUGLE_TAG, "unable to drop table " + tableName + " "
                                    + ex);
                        }
                    }
                }
            } finally {
                tableCursor.close();
            }
        }
    }

    /**
     * Drops all user-defined triggers from the given database.
     */
    private static void dropAllTriggers(final SQLiteDatabase db) {
        final Cursor triggerCursor =
                db.query(MASTER_TABLE, MASTER_COLUMNS, "type='trigger'", null, null, null, null);
        if (triggerCursor != null) {
            try {
                final String dropPrefix = "DROP TRIGGER IF EXISTS ";
                while (triggerCursor.moveToNext()) {
                    final String triggerName = triggerCursor.getString(0);

                    // Skip special tables
                    if (triggerName.startsWith("android_") || triggerName.startsWith("sqlite_")) {
                        continue;
                    }
                    try {
                        db.execSQL(dropPrefix + triggerName);
                    } catch (final SQLException ex) {
                        if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.DEBUG)) {
                            LogUtil.d(LogUtil.BUGLE_TAG, "unable to drop trigger " + triggerName +
                                    " " + ex);
                        }
                    }
                }
            } finally {
                triggerCursor.close();
            }
        }
    }

    /**
     * Drops all user-defined views from the given database.
     */
    public static void dropAllViews(final SQLiteDatabase db) {
        final Cursor viewCursor =
                db.query(MASTER_TABLE, MASTER_COLUMNS, "type='view'", null, null, null, null);
        if (viewCursor != null) {
            try {
                while (viewCursor.moveToNext()) {
                    final String viewName = viewCursor.getString(0);
                    dropView(db, viewName, false /* throwOnFailure */);
                }
            } finally {
                viewCursor.close();
            }
        }
    }

    /**
     * Drops all user-defined views from the given database.
     */
    private static void dropAllIndexes(final SQLiteDatabase db) {
        final Cursor indexCursor =
                db.query(MASTER_TABLE, MASTER_COLUMNS, "type='index'", null, null, null, null);
        if (indexCursor != null) {
            try {
                final String dropPrefix = "DROP INDEX IF EXISTS ";
                while (indexCursor.moveToNext()) {
                    final String indexName = indexCursor.getString(0);
                    try {
                        db.execSQL(dropPrefix + indexName);
                    } catch (final SQLException ex) {
                        if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.DEBUG)) {
                            LogUtil.d(LogUtil.BUGLE_TAG, "unable to drop index " + indexName + " "
                                    + ex);
                        }
                    }
                }
            } finally {
                indexCursor.close();
            }
        }
    }

    private static void createDatabase(final SQLiteDatabase db) {
        for (final String sql : CREATE_TABLE_SQLS) {
            db.execSQL(sql);
        }

        for (final String sql : CREATE_INDEX_SQLS) {
            db.execSQL(sql);
        }

        for (final String sql : CREATE_VIEW_SQLS) {
            db.execSQL(sql);
        }

        for (final String sql : CREATE_TRIGGER_SQLS) {
            db.execSQL(sql);
        }

        // Enable foreign key constraints
        db.execSQL("PRAGMA foreign_keys=ON;");

        // Add the default self participant. The default self will be assigned a proper slot id
        // during participant refresh.
        db.execSQL(getCreateSelfParticipantSql(ParticipantData.DEFAULT_SELF_SUB_ID));

        DataModel.get().onCreateTables(db);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createDatabase(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mUpgradeHelper.doOnUpgrade(db, oldVersion, newVersion);
    }
}
