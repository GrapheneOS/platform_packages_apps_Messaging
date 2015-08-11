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
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore.Images.Media;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.ConversationColumns;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseHelper.PartColumns;
import com.android.messaging.datamodel.DatabaseHelper.ParticipantColumns;
import com.android.messaging.datamodel.FakeCursor;
import com.android.messaging.datamodel.data.ConversationListItemData.ConversationListViewColumns;
import com.android.messaging.datamodel.data.ConversationMessageData.ConversationMessageViewColumns;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContactUtil;
import com.android.messaging.util.ContentType;

import java.util.Arrays;
import java.util.List;

/**
 * A factory for fake objects that can be useful for multiple tests.
 */
public class TestDataFactory {
    private final static String[] sConversationListCursorColumns = new String[] {
        ConversationListViewColumns._ID,
        ConversationListViewColumns.NAME,
        ConversationListViewColumns.ICON,
        ConversationListViewColumns.SNIPPET_TEXT,
        ConversationListViewColumns.PREVIEW_URI,
        ConversationListViewColumns.SORT_TIMESTAMP,
        ConversationListViewColumns.READ,
        ConversationListViewColumns.PREVIEW_CONTENT_TYPE,
        ConversationListViewColumns.MESSAGE_STATUS,
    };

    private final static String[] sContactCursorColumns = new String[] {
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.PHOTO_THUMBNAIL_URI,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            Phone.LOOKUP_KEY,
            Phone._ID,
            Phone.SORT_KEY_PRIMARY,
    };

    private final static String[] sFrequentContactCursorColumns = new String[] {
            Contacts._ID,
            Contacts.DISPLAY_NAME,
            Contacts.PHOTO_URI,
            Phone.LOOKUP_KEY,
    };

    private final static String[] sConversationMessageCursorColumns = new String[] {
        ConversationMessageViewColumns._ID,
        ConversationMessageViewColumns.CONVERSATION_ID,
        ConversationMessageViewColumns.PARTICIPANT_ID,
        ConversationMessageViewColumns.SENT_TIMESTAMP,
        ConversationMessageViewColumns.RECEIVED_TIMESTAMP,
        ConversationMessageViewColumns.STATUS,
        ConversationMessageViewColumns.SENDER_FULL_NAME,
        ConversationMessageViewColumns.SENDER_PROFILE_PHOTO_URI,
        ConversationMessageViewColumns.PARTS_IDS,
        ConversationMessageViewColumns.PARTS_CONTENT_TYPES,
        ConversationMessageViewColumns.PARTS_CONTENT_URIS,
        ConversationMessageViewColumns.PARTS_WIDTHS,
        ConversationMessageViewColumns.PARTS_HEIGHTS,
        ConversationMessageViewColumns.PARTS_TEXTS,
        ConversationMessageViewColumns.PARTS_COUNT
    };

    private final static String[] sGalleryCursorColumns = new String[] {
        Media._ID,
        Media.DATA,
        Media.WIDTH,
        Media.HEIGHT,
        Media.MIME_TYPE
    };

    public static FakeCursor getConversationListCursor() {
        final Object[][] cursorData = new Object[][] {
                new Object[] { Long.valueOf(1), "name1", "content://icon1",
                        "snippetText1", "content://snippetUri1", Long.valueOf(10), 1,
                        ContentType.IMAGE_JPEG, MessageData.BUGLE_STATUS_INCOMING_COMPLETE},
                new Object[] { Long.valueOf(2), "name2", "content://icon2",
                        "snippetText2", "content://snippetUri2", Long.valueOf(20) + 24*60*60*1000,
                        0, ContentType.IMAGE_JPEG, MessageData.BUGLE_STATUS_INCOMING_COMPLETE},
                new Object[] { Long.valueOf(3), "name3", "content://icon3",
                        "snippetText3", "content://snippetUri3", Long.valueOf(30) + 2*24*60*60*1000,
                        0, ContentType.IMAGE_JPEG, MessageData.BUGLE_STATUS_OUTGOING_COMPLETE}
        };
        return new FakeCursor(ConversationListItemData.PROJECTION, sConversationListCursorColumns,
                cursorData);
    }
    public static final int CONVERSATION_LIST_CURSOR_READ_MESSAGE_INDEX = 0;
    public static final int CONVERSATION_LIST_CURSOR_UNREAD_MESSAGE_INDEX = 1;

    public static FakeCursor getEmptyConversationListCursor() {
        return new FakeCursor(ConversationListItemData.PROJECTION, sConversationListCursorColumns,
                new Object[][] {});
    }

    public static FakeCursor getConversationMessageCursor() {
        final Object[][] cursorData = new Object[][] {
                new Object[] { Long.valueOf(0), Long.valueOf(1), Long.valueOf(1),
                        Long.valueOf(10), Long.valueOf(10),
                        MessageData.BUGLE_STATUS_INCOMING_COMPLETE, "Alice", null,
                        "0", "text/plain", "''", -1, -1, "msg0", 1},
                new Object[] { Long.valueOf(1), Long.valueOf(1), Long.valueOf(2),
                        Long.valueOf(20), Long.valueOf(20),
                        MessageData.BUGLE_STATUS_OUTGOING_COMPLETE, "Bob", null,
                        "1", "text/plain", "''", -1, -1, "msg1", 1},
                new Object[] { Long.valueOf(2), Long.valueOf(1), Long.valueOf(1),
                        Long.valueOf(30), Long.valueOf(30),
                        MessageData.BUGLE_STATUS_OUTGOING_COMPLETE, "Alice", null,
                        "2", "contentType3", "'content://fakeUri3'", "0", "0", "msg1", 1},
                new Object[] { Long.valueOf(3), Long.valueOf(1), Long.valueOf(1),
                        Long.valueOf(40), Long.valueOf(40),
                        MessageData.BUGLE_STATUS_OUTGOING_COMPLETE, "Alice", null,
                        "3|4", "'contentType4'|'text/plain'", "'content://fakeUri4'|''", "0|-1", "0|-1", "''|'msg3'", 2},
        };
        return new FakeCursor(
                ConversationMessageData.getProjection(),
                sConversationMessageCursorColumns,
                cursorData);
    }

    public static String getMessageText(final FakeCursor messageCursor, final int row) {
        final String allPartsText = messageCursor.getAt(ConversationMessageViewColumns.PARTS_TEXTS, row)
                .toString();
        final int partsCount = (Integer) messageCursor.getAt(
                ConversationMessageViewColumns.PARTS_COUNT, row);
        final String messageId = messageCursor.getAt(
                ConversationMessageViewColumns._ID, row).toString();
        final List<MessagePartData> parts = ConversationMessageData.makeParts(
                messageCursor.getAt(ConversationMessageViewColumns.PARTS_IDS, row).toString(),
                messageCursor.getAt(ConversationMessageViewColumns.PARTS_CONTENT_TYPES, row).toString(),
                messageCursor.getAt(ConversationMessageViewColumns.PARTS_CONTENT_URIS, row).toString(),
                messageCursor.getAt(ConversationMessageViewColumns.PARTS_WIDTHS, row).toString(),
                messageCursor.getAt(ConversationMessageViewColumns.PARTS_HEIGHTS, row).toString(),
                messageCursor.getAt(ConversationMessageViewColumns.PARTS_TEXTS, row).toString(),
                partsCount,
                messageId);

        for (final MessagePartData part : parts) {
            if (part.isText()) {
                return part.getText();
            }
        }
        return null;
    }

    // Indexes where to find consecutive and non consecutive messages from same participant
    // (respect to index - 1).
    public static final int MESSAGE_WITH_SAME_PARTICIPANT_AS_PREVIOUS = 3;
    public static final int MESSAGE_WITH_DIFFERENT_PARTICIPANT_AS_PREVIOUS = 2;

    public static FakeCursor getConversationParticipantsCursor() {
        final String[] sConversationParticipantsCursorColumns = new String[] {
                ParticipantColumns._ID,
                ParticipantColumns.SUB_ID,
                ParticipantColumns.NORMALIZED_DESTINATION,
                ParticipantColumns.SEND_DESTINATION,
                ParticipantColumns.FULL_NAME,
                ParticipantColumns.FIRST_NAME,
                ParticipantColumns.PROFILE_PHOTO_URI,
        };

        final Object[][] cursorData = new Object[][] {
                new Object[] { 1, ParticipantData.OTHER_THAN_SELF_SUB_ID, "+15554567890",
                        "(555)456-7890", "alice in wonderland", "alice", "alice.png" },
                new Object[] { 2, ParticipantData.OTHER_THAN_SELF_SUB_ID, "+15551011121",
                        "(555)101-1121", "bob the baker", "bob", "bob.png"},
                new Object[] { 3, ParticipantData.OTHER_THAN_SELF_SUB_ID, "+15551314152",
                        "(555)131-4152", "charles in charge", "charles", "charles.png" },
        };

        return new FakeCursor(ParticipantData.ParticipantsQuery.PROJECTION,
                sConversationParticipantsCursorColumns, cursorData);
    }

    public static final int CONTACT_LIST_CURSOR_FIRST_LEVEL_CONTACT_INDEX = 0;
    public static final int CONTACT_LIST_CURSOR_SECOND_LEVEL_CONTACT_INDEX = 2;

    /**
     * Returns a cursor for the all contacts list consumable by ContactPickerFragment.
     */
    public static FakeCursor getAllContactListCursor() {
        final Object[][] cursorData = new Object[][] {
                new Object[] { Long.valueOf(0), "John Smith", "content://uri1",
                        "425-555-1234", Phone.TYPE_HOME, "", "0", Long.valueOf(0), 0 },
                new Object[] { Long.valueOf(1), "Sun Woo Kong", "content://uri2",
                        "425-555-1235", Phone.TYPE_MOBILE, "", "1", Long.valueOf(1), 1 },
                new Object[] { Long.valueOf(1), "Sun Woo Kong", "content://uri2",
                        "425-555-1238", Phone.TYPE_HOME, "", "1", Long.valueOf(2), 2 },
                new Object[] { Long.valueOf(2), "Anna Kinney", "content://uri3",
                        "425-555-1236", Phone.TYPE_MAIN, "", "3", Long.valueOf(3), 3 },
                new Object[] { Long.valueOf(3), "Mike Jones", "content://uri3",
                        "425-555-1236", Phone.TYPE_MAIN, "", "5", Long.valueOf(4), 4 },
        };
        return new FakeCursor(ContactUtil.PhoneQuery.PROJECTION, sContactCursorColumns,
                cursorData);
    }

    /**
     * Returns a cursor for the frequent contacts list consumable by ContactPickerFragment.
     * Note: make it so that this cursor is the generated result of getStrequentContactsCursor()
     * and getAllContactListCursor(), i.e., expand the entries in getStrequentContactsCursor()
     * with the details from getAllContactListCursor()
     */
    public static FakeCursor getFrequentContactListCursor() {
        final Object[][] cursorData = new Object[][] {
                new Object[] { Long.valueOf(2), "Anna Kinney", "content://uri3",
                        "425-555-1236", Phone.TYPE_MAIN, "", "3", Long.valueOf(3), 0 },
                new Object[] { Long.valueOf(1), "Sun Woo Kong", "content://uri2",
                        "425-555-1235", Phone.TYPE_MOBILE, "", "1", Long.valueOf(1), 1},
                new Object[] { Long.valueOf(1), "Sun Woo Kong", "content://uri2",
                        "425-555-1238", Phone.TYPE_HOME, "", "1", Long.valueOf(2), 2 },
                new Object[] { Long.valueOf(0), "John Smith", "content://uri1",
                        "425-555-1234", Phone.TYPE_HOME, "", "0", Long.valueOf(0), 3 },
        };
        return new FakeCursor(ContactUtil.PhoneQuery.PROJECTION, sContactCursorColumns,
                cursorData);
    }

    /**
     * Returns a strequent (starred + frequent) cursor (like the one produced by android contact
     * provider's CONTENT_STREQUENT_URI query) that's consumable by FrequentContactsCursorBuilder.
     */
    public static FakeCursor getStrequentContactsCursor() {
        final Object[][] cursorData = new Object[][] {
                new Object[] { Long.valueOf(0), "Anna Kinney", "content://uri1", "3" },
                new Object[] { Long.valueOf(1), "Sun Woo Kong", "content://uri2", "1" },
                new Object[] { Long.valueOf(2), "John Smith", "content://uri3", "0" },
                // Email-only entry that shouldn't be included in the result.
                new Object[] { Long.valueOf(3), "Email Contact", "content://uri4", "100" },
        };
        return new FakeCursor(ContactUtil.FrequentContactQuery.PROJECTION,
                sFrequentContactCursorColumns, cursorData);
    }

    public static final int SMS_MMS_THREAD_ID_CURSOR_VALUE = 123456789;

    public static FakeCursor getSmsMmsThreadIdCursor() {
        final String[] ID_PROJECTION = { BaseColumns._ID };
        final Object[][] cursorData = new Object[][] {
                new Object[] { Long.valueOf(SMS_MMS_THREAD_ID_CURSOR_VALUE) },
        };
        return new FakeCursor(ID_PROJECTION, ID_PROJECTION, cursorData);
    }

    public static FakeCursor getGalleryGridCursor() {
        final Object[][] cursorData = new Object[][] {
                new Object[] { Long.valueOf(0), "/sdcard/image1", 100, 100, "image/jpeg" },
                new Object[] { Long.valueOf(1), "/sdcard/image2", 200, 200, "image/png" },
                new Object[] { Long.valueOf(2), "/sdcard/image3", 300, 300, "image/jpeg" },
        };
        return new FakeCursor(GalleryGridItemData.IMAGE_PROJECTION, sGalleryCursorColumns,
                cursorData);
    }

    public static final int NUM_TEST_CONVERSATIONS = 10;

    /**
     * Create test data in our db.
     *
     * Ideally this will create more realistic data with more variety.
     */
    public static void createTestData(final SQLiteDatabase db) {
        BugleDatabaseOperations.clearParticipantIdCache();

        // Timestamp for 1 day ago
        final long yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

        final ContentValues conversationValues = new ContentValues();
        for (int i = 1; i <= NUM_TEST_CONVERSATIONS; i++) {
            conversationValues.put(ConversationColumns.NAME, "Conversation " + i);
            final long conversationId = db.insert(DatabaseHelper.CONVERSATIONS_TABLE, null,
                    conversationValues);

            final ContentValues messageValues = new ContentValues();
            for (int m = 1; m <= 25; m++) {
                // Move forward ten minutes per conversation, 1 minute per message.
                final long messageTime = yesterday + (i * 10 * 60 * 1000) + (m * 60 * 1000);
                messageValues.put(MessageColumns.RECEIVED_TIMESTAMP, messageTime);
                messageValues.put(MessageColumns.CONVERSATION_ID, conversationId);
                messageValues.put(MessageColumns.SENDER_PARTICIPANT_ID,
                        Math.abs(("" + messageTime).hashCode()) % 2);
                final long messageId = db.insert(DatabaseHelper.MESSAGES_TABLE, null, messageValues);

                // Create a text part for this message
                final ContentValues partValues = new ContentValues();
                partValues.put(PartColumns.MESSAGE_ID, messageId);
                partValues.put(PartColumns.CONVERSATION_ID, conversationId);
                partValues.put(PartColumns.TEXT, "Conversation: " + conversationId +
                        " Message: " + m);
                db.insert(DatabaseHelper.PARTS_TABLE, null, partValues);

                // Update the snippet for this conversation to the latest message inserted
                conversationValues.clear();
                conversationValues.put(ConversationColumns.LATEST_MESSAGE_ID, messageId);
                final int updatedCount = db.update(DatabaseHelper.CONVERSATIONS_TABLE,
                        conversationValues,
                        "_id=?", new String[]{String.valueOf(conversationId)});
                Assert.isTrue(updatedCount == 1);
            }
        }
    }

    public static List<MessagePartData> getTestDraftAttachments() {
        final MessagePartData[] retParts = new MessagePartData[] {
                new MessagePartData(ContentType.IMAGE_JPEG, Uri.parse("content://image"),
                        100, 100),
                new MessagePartData(ContentType.VIDEO_3GPP, Uri.parse("content://video"),
                        100, 100),
                new MessagePartData(ContentType.TEXT_VCARD, Uri.parse("content://vcard"),
                        0, 0),
                new MessagePartData(ContentType.AUDIO_3GPP, Uri.parse("content://audio"),
                        0, 0)
        };
        return Arrays.asList(retParts);
    }
}
