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

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.ConversationColumns;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseHelper.ParticipantColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.action.DeleteConversationAction;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContactUtil;
import com.android.messaging.util.Dates;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;

/**
 * Class wrapping the conversation list view used to display each item in conversation list
 */
public class ConversationListItemData {
    private String mConversationId;
    private String mName;
    private String mIcon;
    private boolean mIsRead;
    private long mTimestamp;
    private String mSnippetText;
    private Uri mPreviewUri;
    private String mPreviewContentType;
    private long mParticipantContactId;
    private String mParticipantLookupKey;
    private String mOtherParticipantNormalizedDestination;
    private String mSelfId;
    private int mParticipantCount;
    private boolean mNotificationEnabled;
    private String mNotificationSoundUri;
    private boolean mNotificationVibrate;
    private boolean mIncludeEmailAddress;
    private int mMessageStatus;
    private int mMessageRawTelephonyStatus;
    private boolean mShowDraft;
    private Uri mDraftPreviewUri;
    private String mDraftPreviewContentType;
    private String mDraftSnippetText;
    private boolean mIsArchived;
    private String mSubject;
    private String mDraftSubject;
    private String mSnippetSenderFirstName;
    private String mSnippetSenderDisplayDestination;
    private boolean mIsEnterprise;

    public ConversationListItemData() {
    }

    public void bind(final Cursor cursor) {
        bind(cursor, false);
    }

    public void bind(final Cursor cursor, final boolean ignoreDraft) {
        mConversationId = cursor.getString(INDEX_ID);
        mName = cursor.getString(INDEX_CONVERSATION_NAME);
        mIcon = cursor.getString(INDEX_CONVERSATION_ICON);
        mSnippetText = cursor.getString(INDEX_SNIPPET_TEXT);
        mTimestamp = cursor.getLong(INDEX_SORT_TIMESTAMP);
        mIsRead = cursor.getInt(INDEX_READ) == 1;
        final String previewUriString = cursor.getString(INDEX_PREVIEW_URI);
        mPreviewUri = TextUtils.isEmpty(previewUriString) ? null : Uri.parse(previewUriString);
        mPreviewContentType = cursor.getString(INDEX_PREVIEW_CONTENT_TYPE);
        mParticipantContactId = cursor.getLong(INDEX_PARTICIPANT_CONTACT_ID);
        mParticipantLookupKey = cursor.getString(INDEX_PARTICIPANT_LOOKUP_KEY);
        mOtherParticipantNormalizedDestination = cursor.getString(
                INDEX_OTHER_PARTICIPANT_NORMALIZED_DESTINATION);
        mSelfId = cursor.getString(INDEX_SELF_ID);
        mParticipantCount = cursor.getInt(INDEX_PARTICIPANT_COUNT);
        mNotificationEnabled = cursor.getInt(INDEX_NOTIFICATION_ENABLED) == 1;
        mNotificationSoundUri = cursor.getString(INDEX_NOTIFICATION_SOUND_URI);
        mNotificationVibrate = cursor.getInt(INDEX_NOTIFICATION_VIBRATION) == 1;
        mIncludeEmailAddress = cursor.getInt(INDEX_INCLUDE_EMAIL_ADDRESS) == 1;
        mMessageStatus = cursor.getInt(INDEX_MESSAGE_STATUS);
        mMessageRawTelephonyStatus = cursor.getInt(INDEX_MESSAGE_RAW_TELEPHONY_STATUS);
        if (!ignoreDraft) {
            mShowDraft = cursor.getInt(INDEX_SHOW_DRAFT) == 1;
            final String draftPreviewUriString = cursor.getString(INDEX_DRAFT_PREVIEW_URI);
            mDraftPreviewUri = TextUtils.isEmpty(draftPreviewUriString) ?
                    null : Uri.parse(draftPreviewUriString);
            mDraftPreviewContentType = cursor.getString(INDEX_DRAFT_PREVIEW_CONTENT_TYPE);
            mDraftSnippetText = cursor.getString(INDEX_DRAFT_SNIPPET_TEXT);
            mDraftSubject = cursor.getString(INDEX_DRAFT_SUBJECT_TEXT);
        } else {
            mShowDraft = false;
            mDraftPreviewUri = null;
            mDraftPreviewContentType = null;
            mDraftSnippetText = null;
            mDraftSubject = null;
        }

        mIsArchived = cursor.getInt(INDEX_ARCHIVE_STATUS) == 1;
        mSubject = cursor.getString(INDEX_SUBJECT_TEXT);
        mSnippetSenderFirstName = cursor.getString(INDEX_SNIPPET_SENDER_FIRST_NAME);
        mSnippetSenderDisplayDestination =
                cursor.getString(INDEX_SNIPPET_SENDER_DISPLAY_DESTINATION);
        mIsEnterprise = cursor.getInt(INDEX_IS_ENTERPRISE) == 1;
    }

    public String getConversationId() {
        return mConversationId;
    }

    public String getName() {
        return mName;
    }

    public String getIcon() {
        return mIcon;
    }

    public boolean getIsRead() {
        return mIsRead;
    }

    public String getFormattedTimestamp() {
        return Dates.getConversationTimeString(mTimestamp).toString();
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public String getSnippetText() {
        return mSnippetText;
    }

    public Uri getPreviewUri() {
        return mPreviewUri;
    }

    public String getPreviewContentType() {
        return mPreviewContentType;
    }

    /**
      * @see ConversationColumns#PARTICIPANT_CONTACT_ID
      * @return the contact id of the participant if it is a 1:1 conversation, -1 for group.
      */
    public long getParticipantContactId() {
        return mParticipantContactId;
    }

    /**
     * @see ConversationColumns#IS_ENTERPRISE
     * @return whether the conversation is enterprise.
     */
    public boolean isEnterprise() {
        return mIsEnterprise;
    }

    public String getParticipantLookupKey() {
        return mParticipantLookupKey;
    }

    public String getOtherParticipantNormalizedDestination() {
        return mOtherParticipantNormalizedDestination;
    }

    public String getSelfId() {
        return mSelfId;
    }

    public int getParticipantCount() {
        return mParticipantCount;
    }

    public boolean getIsGroup() {
        // Participant count excludes self
        return (mParticipantCount > 1);
    }

    public boolean getIncludeEmailAddress() {
        return mIncludeEmailAddress;
    }

    public boolean getNotificationEnabled() {
        return mNotificationEnabled;
    }

    public String getNotificationSoundUri() {
        return mNotificationSoundUri;
    }

    public boolean getNotifiationVibrate() {
        return mNotificationVibrate;
    }

    public final boolean getIsFailedStatus() {
        return (mMessageStatus == MessageData.BUGLE_STATUS_OUTGOING_FAILED ||
                mMessageStatus == MessageData.BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER ||
                mMessageStatus == MessageData.BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED ||
                mMessageStatus == MessageData.BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE);
    }

    public final boolean getIsSendRequested() {
        return (mMessageStatus == MessageData.BUGLE_STATUS_OUTGOING_YET_TO_SEND ||
                mMessageStatus == MessageData.BUGLE_STATUS_OUTGOING_AWAITING_RETRY ||
                mMessageStatus == MessageData.BUGLE_STATUS_OUTGOING_SENDING ||
                mMessageStatus == MessageData.BUGLE_STATUS_OUTGOING_RESENDING);
    }

    public boolean getIsMessageTypeOutgoing() {
        return !MessageData.getIsIncoming(mMessageStatus);
    }

    public int getMessageRawTelephonyStatus() {
        return mMessageRawTelephonyStatus;
    }

    public int getMessageStatus() {
        return mMessageStatus;
    }

    public boolean getShowDraft() {
        return mShowDraft;
    }

    public String getDraftSnippetText() {
        return mDraftSnippetText;
    }

    public Uri getDraftPreviewUri() {
        return mDraftPreviewUri;
    }

    public String getDraftPreviewContentType() {
        return mDraftPreviewContentType;
    }

    public boolean getIsArchived() {
        return mIsArchived;
    }

    public String getSubject() {
        return mSubject;
    }

    public String getDraftSubject() {
        return mDraftSubject;
    }

    public String getSnippetSenderName() {
        if (!TextUtils.isEmpty(mSnippetSenderFirstName)) {
            return mSnippetSenderFirstName;
        }
        return mSnippetSenderDisplayDestination;
    }

    public void deleteConversation() {
        DeleteConversationAction.deleteConversation(mConversationId, mTimestamp);
    }

    /**
     * Get the name of the view for this data item
     */
    public static final String getConversationListView() {
        return CONVERSATION_LIST_VIEW;
    }

    public static final String getConversationListViewSql() {
        return CONVERSATION_LIST_VIEW_SQL;
    }

    private static final String CONVERSATION_LIST_VIEW = "conversation_list_view";

    private static final String CONVERSATION_LIST_VIEW_PROJECTION =
            DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns._ID
            + " as " + ConversationListViewColumns._ID + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.NAME
            + " as " + ConversationListViewColumns.NAME + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.CURRENT_SELF_ID
            + " as " + ConversationListViewColumns.CURRENT_SELF_ID + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.ARCHIVE_STATUS
            + " as " + ConversationListViewColumns.ARCHIVE_STATUS + ", "
            + DatabaseHelper.MESSAGES_TABLE + '.' + MessageColumns.READ
            + " as " + ConversationListViewColumns.READ + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.ICON
            + " as " + ConversationListViewColumns.ICON + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.PARTICIPANT_CONTACT_ID
            + " as " + ConversationListViewColumns.PARTICIPANT_CONTACT_ID + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.PARTICIPANT_LOOKUP_KEY
            + " as " + ConversationListViewColumns.PARTICIPANT_LOOKUP_KEY + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.'
                    + ConversationColumns.OTHER_PARTICIPANT_NORMALIZED_DESTINATION
            + " as " + ConversationListViewColumns.OTHER_PARTICIPANT_NORMALIZED_DESTINATION + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.SORT_TIMESTAMP
            + " as " + ConversationListViewColumns.SORT_TIMESTAMP + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.SHOW_DRAFT
            + " as " + ConversationListViewColumns.SHOW_DRAFT + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.DRAFT_SNIPPET_TEXT
            + " as " + ConversationListViewColumns.DRAFT_SNIPPET_TEXT + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.DRAFT_PREVIEW_URI
            + " as " + ConversationListViewColumns.DRAFT_PREVIEW_URI + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.DRAFT_SUBJECT_TEXT
            + " as " + ConversationListViewColumns.DRAFT_SUBJECT_TEXT + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.'
                    + ConversationColumns.DRAFT_PREVIEW_CONTENT_TYPE
            + " as " + ConversationListViewColumns.DRAFT_PREVIEW_CONTENT_TYPE + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.PREVIEW_URI
            + " as " + ConversationListViewColumns.PREVIEW_URI + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.PREVIEW_CONTENT_TYPE
            + " as " + ConversationListViewColumns.PREVIEW_CONTENT_TYPE + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.PARTICIPANT_COUNT
            + " as " + ConversationListViewColumns.PARTICIPANT_COUNT + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.NOTIFICATION_ENABLED
            + " as " + ConversationListViewColumns.NOTIFICATION_ENABLED + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.NOTIFICATION_SOUND_URI
            + " as " + ConversationListViewColumns.NOTIFICATION_SOUND_URI + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.NOTIFICATION_VIBRATION
            + " as " + ConversationListViewColumns.NOTIFICATION_VIBRATION + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' +
                    ConversationColumns.INCLUDE_EMAIL_ADDRESS
            + " as " + ConversationListViewColumns.INCLUDE_EMAIL_ADDRESS + ", "
            + DatabaseHelper.MESSAGES_TABLE + '.' + MessageColumns.STATUS
            + " as " + ConversationListViewColumns.MESSAGE_STATUS + ", "
            + DatabaseHelper.MESSAGES_TABLE + '.' + MessageColumns.RAW_TELEPHONY_STATUS
            + " as " + ConversationListViewColumns.MESSAGE_RAW_TELEPHONY_STATUS + ", "
            + DatabaseHelper.MESSAGES_TABLE + '.' + MessageColumns._ID
            + " as " + ConversationListViewColumns.MESSAGE_ID + ", "
            + DatabaseHelper.PARTICIPANTS_TABLE + '.' + ParticipantColumns.FIRST_NAME
            + " as " + ConversationListViewColumns.SNIPPET_SENDER_FIRST_NAME + ", "
            + DatabaseHelper.PARTICIPANTS_TABLE + '.' + ParticipantColumns.DISPLAY_DESTINATION
            + " as " + ConversationListViewColumns.SNIPPET_SENDER_DISPLAY_DESTINATION + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.IS_ENTERPRISE
            + " as " + ConversationListViewColumns.IS_ENTERPRISE;

    private static final String JOIN_PARTICIPANTS =
            " LEFT JOIN " + DatabaseHelper.PARTICIPANTS_TABLE + " ON ("
            + DatabaseHelper.MESSAGES_TABLE + '.' + MessageColumns.SENDER_PARTICIPANT_ID
            + '=' + DatabaseHelper.PARTICIPANTS_TABLE + '.' + DatabaseHelper.ParticipantColumns._ID
            + ") ";

    // View that makes latest message read flag available with rest of conversation data.
    private static final String CONVERSATION_LIST_VIEW_SQL = "CREATE VIEW " +
            CONVERSATION_LIST_VIEW + " AS SELECT "
            + CONVERSATION_LIST_VIEW_PROJECTION + ", "
            // Snippet not part of the base projection shared with search view
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.SNIPPET_TEXT
            + " as " + ConversationListViewColumns.SNIPPET_TEXT + ", "
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' + ConversationColumns.SUBJECT_TEXT
            + " as " + ConversationListViewColumns.SUBJECT_TEXT + " "
            + " FROM " + DatabaseHelper.CONVERSATIONS_TABLE
            + " LEFT JOIN " + DatabaseHelper.MESSAGES_TABLE + " ON ("
            + DatabaseHelper.CONVERSATIONS_TABLE + '.' +  ConversationColumns.LATEST_MESSAGE_ID
            + '=' + DatabaseHelper.MESSAGES_TABLE + '.' + MessageColumns._ID + ") "
            + JOIN_PARTICIPANTS
            + "ORDER BY " + DatabaseHelper.CONVERSATIONS_TABLE + '.'
            + ConversationColumns.SORT_TIMESTAMP + " DESC";

    public static class ConversationListViewColumns implements BaseColumns {
        public static final String _ID = ConversationColumns._ID;
        static final String NAME = ConversationColumns.NAME;
        static final String ARCHIVE_STATUS = ConversationColumns.ARCHIVE_STATUS;
        static final String READ = MessageColumns.READ;
        static final String SORT_TIMESTAMP = ConversationColumns.SORT_TIMESTAMP;
        static final String PREVIEW_URI = ConversationColumns.PREVIEW_URI;
        static final String PREVIEW_CONTENT_TYPE = ConversationColumns.PREVIEW_CONTENT_TYPE;
        static final String SNIPPET_TEXT = ConversationColumns.SNIPPET_TEXT;
        static final String SUBJECT_TEXT = ConversationColumns.SUBJECT_TEXT;
        static final String ICON = ConversationColumns.ICON;
        static final String SHOW_DRAFT = ConversationColumns.SHOW_DRAFT;
        static final String DRAFT_SUBJECT_TEXT = ConversationColumns.DRAFT_SUBJECT_TEXT;
        static final String DRAFT_PREVIEW_URI = ConversationColumns.DRAFT_PREVIEW_URI;
        static final String DRAFT_PREVIEW_CONTENT_TYPE =
                ConversationColumns.DRAFT_PREVIEW_CONTENT_TYPE;
        static final String DRAFT_SNIPPET_TEXT = ConversationColumns.DRAFT_SNIPPET_TEXT;
        static final String PARTICIPANT_CONTACT_ID = ConversationColumns.PARTICIPANT_CONTACT_ID;
        static final String PARTICIPANT_LOOKUP_KEY = ConversationColumns.PARTICIPANT_LOOKUP_KEY;
        static final String OTHER_PARTICIPANT_NORMALIZED_DESTINATION =
                ConversationColumns.OTHER_PARTICIPANT_NORMALIZED_DESTINATION;
        static final String CURRENT_SELF_ID = ConversationColumns.CURRENT_SELF_ID;
        static final String PARTICIPANT_COUNT = ConversationColumns.PARTICIPANT_COUNT;
        static final String NOTIFICATION_ENABLED = ConversationColumns.NOTIFICATION_ENABLED;
        static final String NOTIFICATION_SOUND_URI = ConversationColumns.NOTIFICATION_SOUND_URI;
        static final String NOTIFICATION_VIBRATION = ConversationColumns.NOTIFICATION_VIBRATION;
        static final String INCLUDE_EMAIL_ADDRESS =
                ConversationColumns.INCLUDE_EMAIL_ADDRESS;
        static final String MESSAGE_STATUS = MessageColumns.STATUS;
        static final String MESSAGE_RAW_TELEPHONY_STATUS = MessageColumns.RAW_TELEPHONY_STATUS;
        static final String MESSAGE_ID = "message_id";
        static final String SNIPPET_SENDER_FIRST_NAME = "snippet_sender_first_name";
        static final String SNIPPET_SENDER_DISPLAY_DESTINATION =
                "snippet_sender_display_destination";
        static final String IS_ENTERPRISE = ConversationColumns.IS_ENTERPRISE;
    }

    public static final String[] PROJECTION = {
        ConversationListViewColumns._ID,
        ConversationListViewColumns.NAME,
        ConversationListViewColumns.ICON,
        ConversationListViewColumns.SNIPPET_TEXT,
        ConversationListViewColumns.SORT_TIMESTAMP,
        ConversationListViewColumns.READ,
        ConversationListViewColumns.PREVIEW_URI,
        ConversationListViewColumns.PREVIEW_CONTENT_TYPE,
        ConversationListViewColumns.PARTICIPANT_CONTACT_ID,
        ConversationListViewColumns.PARTICIPANT_LOOKUP_KEY,
        ConversationListViewColumns.OTHER_PARTICIPANT_NORMALIZED_DESTINATION,
        ConversationListViewColumns.PARTICIPANT_COUNT,
        ConversationListViewColumns.CURRENT_SELF_ID,
        ConversationListViewColumns.NOTIFICATION_ENABLED,
        ConversationListViewColumns.NOTIFICATION_SOUND_URI,
        ConversationListViewColumns.NOTIFICATION_VIBRATION,
        ConversationListViewColumns.INCLUDE_EMAIL_ADDRESS,
        ConversationListViewColumns.MESSAGE_STATUS,
        ConversationListViewColumns.SHOW_DRAFT,
        ConversationListViewColumns.DRAFT_PREVIEW_URI,
        ConversationListViewColumns.DRAFT_PREVIEW_CONTENT_TYPE,
        ConversationListViewColumns.DRAFT_SNIPPET_TEXT,
        ConversationListViewColumns.ARCHIVE_STATUS,
        ConversationListViewColumns.MESSAGE_ID,
        ConversationListViewColumns.SUBJECT_TEXT,
        ConversationListViewColumns.DRAFT_SUBJECT_TEXT,
        ConversationListViewColumns.MESSAGE_RAW_TELEPHONY_STATUS,
        ConversationListViewColumns.SNIPPET_SENDER_FIRST_NAME,
        ConversationListViewColumns.SNIPPET_SENDER_DISPLAY_DESTINATION,
        ConversationListViewColumns.IS_ENTERPRISE,
    };

    private static final int INDEX_ID = 0;
    private static final int INDEX_CONVERSATION_NAME = 1;
    private static final int INDEX_CONVERSATION_ICON = 2;
    private static final int INDEX_SNIPPET_TEXT = 3;
    private static final int INDEX_SORT_TIMESTAMP = 4;
    private static final int INDEX_READ = 5;
    private static final int INDEX_PREVIEW_URI = 6;
    private static final int INDEX_PREVIEW_CONTENT_TYPE = 7;
    private static final int INDEX_PARTICIPANT_CONTACT_ID = 8;
    private static final int INDEX_PARTICIPANT_LOOKUP_KEY = 9;
    private static final int INDEX_OTHER_PARTICIPANT_NORMALIZED_DESTINATION = 10;
    private static final int INDEX_PARTICIPANT_COUNT = 11;
    private static final int INDEX_SELF_ID = 12;
    private static final int INDEX_NOTIFICATION_ENABLED = 13;
    private static final int INDEX_NOTIFICATION_SOUND_URI = 14;
    private static final int INDEX_NOTIFICATION_VIBRATION = 15;
    private static final int INDEX_INCLUDE_EMAIL_ADDRESS = 16;
    private static final int INDEX_MESSAGE_STATUS = 17;
    private static final int INDEX_SHOW_DRAFT = 18;
    private static final int INDEX_DRAFT_PREVIEW_URI = 19;
    private static final int INDEX_DRAFT_PREVIEW_CONTENT_TYPE = 20;
    private static final int INDEX_DRAFT_SNIPPET_TEXT = 21;
    private static final int INDEX_ARCHIVE_STATUS = 22;
    private static final int INDEX_MESSAGE_ID = 23;
    private static final int INDEX_SUBJECT_TEXT = 24;
    private static final int INDEX_DRAFT_SUBJECT_TEXT = 25;
    private static final int INDEX_MESSAGE_RAW_TELEPHONY_STATUS = 26;
    private static final int INDEX_SNIPPET_SENDER_FIRST_NAME = 27;
    private static final int INDEX_SNIPPET_SENDER_DISPLAY_DESTINATION = 28;
    private static final int INDEX_IS_ENTERPRISE = 29;

    private static final String DIVIDER_TEXT = ", ";

    public static boolean hasAnyEnterpriseContact(
             final List<ParticipantData> participants) {
         for (final ParticipantData participant : participants) {
             if (ContactUtil.isEnterpriseContactId(participant.getContactId())) {
                 return true;
             }
         }
         return false;
     }

    /**
     * Get a conversation from the local DB based on the conversation id.
     *
     * @param dbWrapper       The database
     * @param conversationId  The conversation Id to read
     * @return The existing conversation or null
     */
    public static ConversationListItemData getExistingConversation(final DatabaseWrapper dbWrapper,
            final String conversationId) {
        ConversationListItemData conversation = null;

        // Look for an existing conversation in the db with this conversation id
        Cursor cursor = null;
        try {
            // TODO: Should we be able to read a row from just the conversation table?
            cursor = dbWrapper.query(getConversationListView(),
                    PROJECTION,
                    ConversationColumns._ID + "=?",
                    new String[] { conversationId },
                    null, null, null);
            Assert.inRange(cursor.getCount(), 0, 1);
            if (cursor.moveToFirst()) {
                conversation = new ConversationListItemData();
                conversation.bind(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return conversation;
    }

    public static String generateConversationName(final List<ParticipantData>
            participants) {
        if (participants.size() == 1) {
            // Prefer full name over first name for 1:1 conversation
            return participants.get(0).getDisplayName(true);
        }

        final ArrayList<String> participantNames = new ArrayList<String>();
        for (final ParticipantData participant : participants) {
            // Prefer first name over full name for group conversation
            participantNames.add(participant.getDisplayName(false));
        }

        final Joiner joiner = Joiner.on(DIVIDER_TEXT).skipNulls();
        return joiner.join(participantNames);
    }

}
