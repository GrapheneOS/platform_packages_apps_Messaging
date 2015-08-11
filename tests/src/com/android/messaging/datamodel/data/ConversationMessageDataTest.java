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

import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.FakeCursor;
import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ConversationMessageData.ConversationMessageViewColumns;

@SmallTest
public class ConversationMessageDataTest extends BugleTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(getTestContext());
    }

    public void testBindFirstMessage() {
        final FakeCursor testCursor = TestDataFactory.getConversationMessageCursor();
        final ConversationMessageData data = new ConversationMessageData();
        testCursor.moveToFirst();
        data.bind(testCursor);
        // TODO: Add before checking in all bound fields...
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.STATUS, 0).equals(
                MessageData.BUGLE_STATUS_INCOMING_COMPLETE), data.getIsIncoming());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.SENDER_PROFILE_PHOTO_URI,
                0), data.getSenderProfilePhotoUri());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.SENDER_FULL_NAME, 0),
                data.getSenderFullName());
    }

    public void testBindTwice() {
        final FakeCursor testCursor = TestDataFactory.getConversationMessageCursor();
        final ConversationMessageData data = new ConversationMessageData();
        testCursor.moveToPosition(1);
        data.bind(testCursor);
        assertEquals(TestDataFactory.getMessageText(testCursor, 1), data.getText());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.RECEIVED_TIMESTAMP, 1),
                data.getReceivedTimeStamp());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.STATUS, 1).equals(
                MessageData.BUGLE_STATUS_INCOMING_COMPLETE), data.getIsIncoming());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.SENDER_PROFILE_PHOTO_URI,
                1), data.getSenderProfilePhotoUri());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.SENDER_FULL_NAME, 1),
                data.getSenderFullName());
        testCursor.moveToPosition(2);
        data.bind(testCursor);
        assertEquals(TestDataFactory.getMessageText(testCursor, 2), data.getText());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.RECEIVED_TIMESTAMP, 2),
                data.getReceivedTimeStamp());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.STATUS, 2).equals(
                MessageData.BUGLE_STATUS_INCOMING_COMPLETE), data.getIsIncoming());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.SENDER_PROFILE_PHOTO_URI,
                2), data.getSenderProfilePhotoUri());
        assertEquals(testCursor.getAt(ConversationMessageViewColumns.SENDER_FULL_NAME, 2),
                data.getSenderFullName());
    }

    public void testMessageClustering() {
        final FakeCursor testCursor = TestDataFactory.getConversationMessageCursor();
        final ConversationMessageData data = new ConversationMessageData();
        testCursor.moveToPosition(0);
        data.bind(testCursor);
        assertFalse(data.getCanClusterWithPreviousMessage());
        assertFalse(data.getCanClusterWithNextMessage());

        testCursor.moveToPosition(1);
        data.bind(testCursor);
        assertFalse(data.getCanClusterWithPreviousMessage());
        assertFalse(data.getCanClusterWithNextMessage());

        testCursor.moveToPosition(2);
        data.bind(testCursor);
        assertFalse(data.getCanClusterWithPreviousMessage());
        assertTrue(data.getCanClusterWithNextMessage());  // 2 and 3 can be clustered
        testCursor.moveToPosition(3);

        data.bind(testCursor);
        assertTrue(data.getCanClusterWithPreviousMessage());  // 2 and 3 can be clustered
        assertFalse(data.getCanClusterWithNextMessage());
    }
}
