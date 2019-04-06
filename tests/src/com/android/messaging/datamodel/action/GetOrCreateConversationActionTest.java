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

import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.test.mock.MockContentProvider;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeContext;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubActionService;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubActionService.StubActionServiceCallLog;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction.GetOrCreateConversationActionListener;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction.GetOrCreateConversationActionMonitor;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.sms.MmsUtils;

import org.mockito.Mock;

import java.util.ArrayList;

@SmallTest
public class GetOrCreateConversationActionTest extends BugleTestCase {

    @Mock GetOrCreateConversationActionListener mockListener;

    public void testGetOrCreateConversation() {
        final DatabaseWrapper db = DataModel.get().getDatabase();

        final ArrayList<String> recipients = new ArrayList<String>();
        recipients.add("5551234567");
        recipients.add("5551234568");

        // Generate a list of partially formed participants
        final ArrayList<ParticipantData> participants = new
                ArrayList<ParticipantData>();


        for (final String recipient : recipients) {
            participants.add(ParticipantData.getFromRawPhoneBySystemLocale(recipient));
        }

        // Test that we properly stubbed the SMS provider to return a thread id
        final long threadId = MmsUtils.getOrCreateThreadId(mContext, recipients);
        assertEquals(TestDataFactory.SMS_MMS_THREAD_ID_CURSOR_VALUE, threadId);

        ArrayList<StubActionServiceCallLog> calls = mService.getCalls();

        GetOrCreateConversationActionMonitor monitor =
                GetOrCreateConversationAction.getOrCreateConversation(participants, null,
                        mockListener);

        assertEquals("Failed to start service once for action", calls.size(), 1);
        assertTrue("Action not GetOrCreateConversationAction", calls.get(0).action instanceof
                GetOrCreateConversationAction);

        GetOrCreateConversationAction action = (GetOrCreateConversationAction)
                calls.get(0).action;

        Object result = action.executeAction();

        assertTrue(result instanceof String);

        // Make sure that we created a new conversation
        // TODO(rtenneti): Investigate why blankId is 4 more than NUM_TEST_CONVERSATIONS.
        assertEquals(TestDataFactory.NUM_TEST_CONVERSATIONS+4, Integer.parseInt((String)result));

        // Now get the conversation that we just created again
        monitor = GetOrCreateConversationAction.getOrCreateConversation(participants, null,
                        mockListener);

        calls = mService.getCalls();
        assertEquals("Failed to start service for second action", calls.size(), 2);
        assertTrue("Action not GetOrCreateConversationAction", calls.get(1).action instanceof
                GetOrCreateConversationAction);
        action = (GetOrCreateConversationAction)calls.get(1).action;
        result = action.executeAction();

        assertTrue(result instanceof String);

        final String conversationId = (String) result;

        // Make sure that we found the same conversation id
        // TODO(rtenneti): Investigate why blankId is 4 more than NUM_TEST_CONVERSATIONS.
        assertEquals(TestDataFactory.NUM_TEST_CONVERSATIONS+4, Integer.parseInt((String)result));

        final ArrayList<ParticipantData> conversationParticipants =
                BugleDatabaseOperations.getParticipantsForConversation(db, conversationId);

        assertEquals("Participant count mismatch", recipients.size(),
                conversationParticipants.size());
        for(final ParticipantData participant : conversationParticipants) {
            assertTrue(recipients.contains(participant.getSendDestination()));
        }

        final Uri conversationParticipantsUri =
                MessagingContentProvider.buildConversationParticipantsUri(conversationId);
        final Cursor cursor = mContext.getContentResolver().query(conversationParticipantsUri,
                ParticipantData.ParticipantsQuery.PROJECTION, null, null, null);

        int countSelf = 0;
        while(cursor.moveToNext()) {
            final ParticipantData participant = ParticipantData.getFromCursor(cursor);
            if (participant.isSelf()) {
                countSelf++;
            } else {
                assertTrue(recipients.contains(participant.getSendDestination()));
            }
        }
        cursor.close();
        assertEquals("Expect one self participant in conversations", 1, countSelf);
        assertEquals("Cursor count mismatch", recipients.size(), cursor.getCount() - countSelf);

        final String realId = BugleDatabaseOperations.getExistingConversation(db, threadId, false);
        assertEquals("Conversation already exists", realId, conversationId);
    }

    private FakeContext mContext;
    private StubActionService mService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mContext = new FakeContext(getTestContext());

        final MockContentProvider mockProvider = new MockContentProvider() {
            @Override
            public Cursor query(final Uri uri, final String[] projection, final String selection,
                    final String[] selectionArgs, final String sortOrder) {
                return TestDataFactory.getSmsMmsThreadIdCursor();
            }
        };

        mContext.addContentProvider("mms-sms", mockProvider);
        final MessagingContentProvider provider = new MessagingContentProvider();
        final ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = MessagingContentProvider.AUTHORITY;
        provider.attachInfo(mContext, providerInfo);
        mContext.addContentProvider(MessagingContentProvider.AUTHORITY, provider);

        mService = new StubActionService();
        final FakeDataModel fakeDataModel = new FakeDataModel(mContext)
            .withActionService(mService);
        FakeFactory.registerWithFakeContext(getTestContext(), mContext)
                .withDataModel(fakeDataModel);
        provider.setDatabaseForTest(fakeDataModel.getDatabase());
    }
}
