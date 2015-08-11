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
import com.android.messaging.datamodel.FakeCursor;
import com.android.messaging.datamodel.DatabaseHelper.ParticipantColumns;
import com.android.messaging.datamodel.data.ConversationParticipantsData;
import com.android.messaging.datamodel.data.ParticipantData;

@SmallTest
public class ConversationParticipantsDataTest extends BugleTestCase {
    public void testBindParticipants() {
        final FakeCursor testCursor = TestDataFactory.getConversationParticipantsCursor();
        final ConversationParticipantsData data = new ConversationParticipantsData();
        data.bind(testCursor);

        assertEquals(data.getParticipantListExcludingSelf().size(), testCursor.getCount());
        final ParticipantData participant2 = data.getParticipantById("2");
        assertNotNull(participant2);
        assertEquals(participant2.getFirstName(), testCursor.getAt(
                ParticipantColumns.FIRST_NAME, 1) );
        assertEquals(participant2.getSendDestination(), testCursor.getAt(
                ParticipantColumns.SEND_DESTINATION, 1));
    }
}
