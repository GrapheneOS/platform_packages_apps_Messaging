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

import android.content.ContentProvider;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeContext;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.ConversationColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubActionService;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubActionService.StubActionServiceCallLog;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubConnectivityUtil;
import com.android.messaging.datamodel.action.ReadDraftDataAction.ReadDraftDataActionListener;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.util.ContentType;

import org.mockito.Mock;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

@SmallTest
public class ReadWriteDraftMessageActionTest extends BugleTestCase {

    @Mock ReadDraftDataActionListener mockListener;

    // TODO: Add test cases
    //  1. Make sure drafts can include attachments and multiple parts
    //  2. Make sure attachments get cleaned up appropriately
    //  3. Make sure messageId and partIds not reused (currently each draft is a new message).
    public void testWriteDraft() {
        final String draftMessage = "draftMessage";
        final long threadId = 1234567;
        final boolean senderBlocked = false;
        final String participantNumber = "5551234567";

        final DatabaseWrapper db = DataModel.get().getDatabase();

        final String conversationId = getOrCreateConversation(db, participantNumber, threadId,
                senderBlocked);
        final String selfId = getOrCreateSelfId(db);

        // Should clear/stub DB
        final ArrayList<StubActionServiceCallLog> calls = mService.getCalls();

        final MessageData message = MessageData.createDraftSmsMessage(conversationId, selfId,
                draftMessage);

        WriteDraftMessageAction.writeDraftMessage(conversationId, message);

        assertEquals("Failed to start service once for action", calls.size(), 1);
        assertTrue("Action not SaveDraftMessageAction",
                calls.get(0).action instanceof WriteDraftMessageAction);

        final Action save = calls.get(0).action;

        final Object result = save.executeAction();

        assertTrue("Expect row number string as result", result instanceof String);
        final String messageId = (String) result;

        // Should check DB
        final MessageData actual = BugleDatabaseOperations.readMessage(db, messageId);
        assertNotNull("Database missing draft", actual);
        assertEquals("Draft text changed", draftMessage, actual.getMessageText());
    }

    private static String getOrCreateSelfId(final DatabaseWrapper db) {
        db.beginTransaction();
        final String selfId = BugleDatabaseOperations.getOrCreateParticipantInTransaction(db,
                ParticipantData.getSelfParticipant(ParticipantData.DEFAULT_SELF_SUB_ID));
        db.setTransactionSuccessful();
        db.endTransaction();
        return selfId;
    }

    private static String getOrCreateConversation(final DatabaseWrapper db,
            final String participantNumber, final long threadId, final boolean senderBlocked) {
        final ArrayList<ParticipantData> participants =
                new ArrayList<ParticipantData>();
        participants.add(ParticipantData.getFromRawPhoneBySystemLocale(participantNumber));

        final String conversationId = BugleDatabaseOperations.getOrCreateConversation(db, threadId,
                senderBlocked, participants, false, false, null);
        assertNotNull("No conversation", conversationId);
        return conversationId;
    }

    public void testReadDraft() {
        final Object data = "data";
        final String draftMessage = "draftMessage";
        final long threadId = 1234567;
        final boolean senderBlocked = false;
        final String participantNumber = "5552345678";

        final DatabaseWrapper db = DataModel.get().getDatabase();

        final String conversationId = getOrCreateConversation(db, participantNumber, threadId,
                senderBlocked);
        final String selfId = getOrCreateSelfId(db);

        // Should clear/stub DB
        final ArrayList<StubActionServiceCallLog> calls = mService.getCalls();

        final MessageData message = MessageData.createDraftSmsMessage(conversationId, selfId,
                draftMessage);

        BugleDatabaseOperations.updateDraftMessageData(db, conversationId, message,
                BugleDatabaseOperations.UPDATE_MODE_ADD_DRAFT);

        final ActionMonitor monitor =
                ReadDraftDataAction.readDraftData(conversationId, null, data, mockListener);

        assertEquals("Unexpected number of calls to service", 1, calls.size());
        assertTrue("Action not of type ReadDraftMessageAction",
                calls.get(0).action instanceof ReadDraftDataAction);

        final Action read = calls.get(0).action;

        final Object result = read.executeAction();

        assertTrue(result instanceof ReadDraftDataAction.DraftData);
        final ReadDraftDataAction.DraftData draft = (ReadDraftDataAction.DraftData) result;

        assertEquals("Draft message text differs", draftMessage, draft.message.getMessageText());
        assertEquals("Draft self differs", selfId, draft.message.getSelfId());
        assertEquals("Draft conversation differs", conversationId,
                draft.conversation.getConversationId());
    }

    public void testReadDraftForNewConversation() {
        final Object data = "data";
        long threadId = 1234567;
        final boolean senderBlocked = false;
        long phoneNumber = 5557654567L;
        final String notConversationId = "ThisIsNotValidConversationId";

        final DatabaseWrapper db = DataModel.get().getDatabase();
        final String selfId = getOrCreateSelfId(db);

        // Unless set a new conversation should have a null draft message
        final MessageData blank = BugleDatabaseOperations.readDraftMessageData(db,
                notConversationId, selfId);
        assertNull(blank);

        String conversationId = null;
        do {
            conversationId = BugleDatabaseOperations.getExistingConversation(db,
                    threadId, senderBlocked);
            threadId++;
            phoneNumber++;
        }
        while(!TextUtils.isEmpty(conversationId));

        final ArrayList<ParticipantData> participants =
                new ArrayList<ParticipantData>();
        participants.add(ParticipantData.getFromRawPhoneBySystemLocale(Long.toString(phoneNumber)));

        conversationId = BugleDatabaseOperations.getOrCreateConversation(db, threadId,
                senderBlocked, participants, false, false, null);
        assertNotNull("No conversation", conversationId);

        final MessageData actual = BugleDatabaseOperations.readDraftMessageData(db, conversationId,
                selfId);
        assertNull(actual);

        // Should clear/stub DB
        final ArrayList<StubActionServiceCallLog> calls = mService.getCalls();

        final ActionMonitor monitor =
                ReadDraftDataAction.readDraftData(conversationId, null, data, mockListener);

        assertEquals("Unexpected number of calls to service", 1, calls.size());
        assertTrue("Action not of type ReadDraftMessageAction",
                calls.get(0).action instanceof ReadDraftDataAction);

        final Action read = calls.get(0).action;

        final Object result = read.executeAction();

        assertTrue(result instanceof ReadDraftDataAction.DraftData);
        final ReadDraftDataAction.DraftData draft = (ReadDraftDataAction.DraftData) result;

        assertEquals("Draft message text differs", "", draft.message.getMessageText());
        assertEquals("Draft self differs", selfId, draft.message.getSelfId());
        assertEquals("Draft conversation differs", conversationId,
                draft.conversation.getConversationId());
    }

    public void testWriteAndReadDraft() {
        final Object data = "data";
        final String draftMessage = "draftMessage";

        final DatabaseWrapper db = DataModel.get().getDatabase();
        final Cursor conversations = db.query(DatabaseHelper.CONVERSATIONS_TABLE,
                new String[] { ConversationColumns._ID, ConversationColumns.CURRENT_SELF_ID }, null,
                null, null /* groupBy */, null /* having */, null /* orderBy */);

        if (conversations.moveToFirst()) {
            final String conversationId = conversations.getString(0);
            final String selfId = getOrCreateSelfId(db);

            // Should clear/stub DB
            final ArrayList<StubActionServiceCallLog> calls = mService.getCalls();

            final MessageData message = MessageData.createDraftSmsMessage(conversationId, selfId,
                    draftMessage);

            WriteDraftMessageAction.writeDraftMessage(conversationId, message);

            assertEquals("Failed to start service once for action", calls.size(), 1);
            assertTrue("Action not SaveDraftMessageAction",
                    calls.get(0).action instanceof WriteDraftMessageAction);

            final Action save = calls.get(0).action;

            Object result = save.executeAction();

            assertTrue("Expect row number string as result", result instanceof String);

            // Should check DB

            final ActionMonitor monitor =
                    ReadDraftDataAction.readDraftData(conversationId, null, data,
                            mockListener);

            assertEquals("Expect two calls queued", 2, calls.size());
            assertTrue("Expect action", calls.get(1).action instanceof ReadDraftDataAction);

            final Action read = calls.get(1).action;

            result = read.executeAction();

            assertTrue(result instanceof ReadDraftDataAction.DraftData);
            final ReadDraftDataAction.DraftData draft = (ReadDraftDataAction.DraftData) result;

            assertEquals("Draft message text differs", draftMessage, draft.message.getMessageText());
            // The conversation's self id is used as the draft's self id.
            assertEquals("Draft self differs", conversations.getString(1),
                    draft.message.getSelfId());
            assertEquals("Draft conversation differs", conversationId,
                    draft.conversation.getConversationId());
        } else {
            fail("No conversations in database");
        }
    }

    public void testUpdateDraft() {
        final String initialMessage = "initialMessage";
        final String draftMessage = "draftMessage";
        final long threadId = 1234567;
        final boolean senderBlocked = false;
        final String participantNumber = "5553456789";

        final DatabaseWrapper db = DataModel.get().getDatabase();

        final String conversationId = getOrCreateConversation(db, participantNumber, threadId,
                senderBlocked);
        final String selfId = getOrCreateSelfId(db);

        final ArrayList<StubActionServiceCallLog> calls = mService.getCalls();

        // Insert initial message
        MessageData initial = MessageData.createDraftSmsMessage(conversationId, selfId,
                initialMessage);

        BugleDatabaseOperations.updateDraftMessageData(db, conversationId, initial,
                BugleDatabaseOperations.UPDATE_MODE_ADD_DRAFT);

        initial = BugleDatabaseOperations.readDraftMessageData(db,
                conversationId, selfId);
        assertEquals("Initial text mismatch", initialMessage, initial.getMessageText());

        // Now update the draft
        final MessageData message = MessageData.createDraftSmsMessage(conversationId, selfId,
                draftMessage);
        WriteDraftMessageAction.writeDraftMessage(conversationId, message);

        assertEquals("Failed to start service once for action", calls.size(), 1);
        assertTrue("Action not SaveDraftMessageAction",
                calls.get(0).action instanceof WriteDraftMessageAction);

        final Action save = calls.get(0).action;

        final Object result = save.executeAction();

        assertTrue("Expect row number string as result", result instanceof String);

        // Check DB
        final MessageData actual =  BugleDatabaseOperations.readDraftMessageData(db,
                conversationId, selfId);
        assertNotNull("Database missing draft", actual);
        assertEquals("Draft text mismatch", draftMessage, actual.getMessageText());
        assertNull("Draft messageId should be null", actual.getMessageId());
    }

    public void testBugleDatabaseDraftOperations() {
        final String initialMessage = "initialMessage";
        final String draftMessage = "draftMessage";
        final long threadId = 1234599;
        final boolean senderBlocked = false;
        final String participantNumber = "5553456798";
        final String subject = "subject here";

        final DatabaseWrapper db = DataModel.get().getDatabase();

        final String conversationId = getOrCreateConversation(db, participantNumber, threadId,
                senderBlocked);
        final String selfId = getOrCreateSelfId(db);

        final String text = "This is some text";
        final Uri mOutputUri = MediaScratchFileProvider.buildMediaScratchSpaceUri("txt");
        OutputStream outputStream = null;
        try {
            outputStream = mContext.getContentResolver().openOutputStream(mOutputUri);
            outputStream.write(text.getBytes());
        } catch (final FileNotFoundException e) {
            fail("Cannot open output file");
        } catch (final IOException e) {
            fail("Cannot write output file");
        }

        final MessageData initial =
                MessageData.createDraftMmsMessage(conversationId, selfId, initialMessage, subject);
        initial.addPart(MessagePartData.createMediaMessagePart(ContentType.MULTIPART_MIXED,
                mOutputUri, 0, 0));

        final String initialMessageId = BugleDatabaseOperations.updateDraftMessageData(db,
                conversationId, initial, BugleDatabaseOperations.UPDATE_MODE_ADD_DRAFT);
        assertNotNull(initialMessageId);

        final MessageData initialDraft = BugleDatabaseOperations.readMessage(db, initialMessageId);
        assertNotNull(initialDraft);
        int cnt = 0;
        for(final MessagePartData part : initialDraft.getParts()) {
            if (part.isAttachment()) {
                assertEquals(part.getContentUri(), mOutputUri);
            } else {
                assertEquals(part.getText(), initialMessage);
            }
            cnt++;
        }
        assertEquals("Wrong number of parts", 2, cnt);

        InputStream inputStream = null;
        try {
            inputStream = mContext.getContentResolver().openInputStream(mOutputUri);
            final byte[] buffer = new byte[256];
            final int read = inputStream.read(buffer);
            assertEquals(read, text.getBytes().length);
        } catch (final FileNotFoundException e) {
            fail("Cannot open input file");
        } catch (final IOException e) {
            fail("Cannot read input file");
        }

        final String moreText = "This is some more text";
        final Uri mAnotherUri = MediaScratchFileProvider.buildMediaScratchSpaceUri("txt");
        outputStream = null;
        try {
            outputStream = mContext.getContentResolver().openOutputStream(mAnotherUri);
            outputStream.write(moreText.getBytes());
        } catch (final FileNotFoundException e) {
            fail("Cannot open output file");
        } catch (final IOException e) {
            fail("Cannot write output file");
        }

        final MessageData another =
                MessageData.createDraftMmsMessage(conversationId, selfId, draftMessage, subject);
        another.addPart(MessagePartData.createMediaMessagePart(ContentType.MMS_MULTIPART_MIXED,
                mAnotherUri, 0, 0));

        final String anotherMessageId = BugleDatabaseOperations.updateDraftMessageData(db,
                conversationId, another, BugleDatabaseOperations.UPDATE_MODE_ADD_DRAFT);
        assertNotNull(anotherMessageId);

        final MessageData anotherDraft = BugleDatabaseOperations.readMessage(db, anotherMessageId);
        assertNotNull(anotherDraft);
        cnt = 0;
        for(final MessagePartData part : anotherDraft.getParts()) {
            if (part.isAttachment()) {
                assertEquals(part.getContentUri(), mAnotherUri);
            } else {
                assertEquals(part.getText(), draftMessage);
            }
            cnt++;
        }
        assertEquals("Wrong number of parts", 2, cnt);

        inputStream = null;
        try {
            inputStream = mContext.getContentResolver().openInputStream(mOutputUri);
            assertNull("Original draft content should have been deleted", inputStream);
        } catch (final FileNotFoundException e) {
        }
        inputStream = null;
        try {
            inputStream = mContext.getContentResolver().openInputStream(mAnotherUri);
            final byte[] buffer = new byte[256];
            final int read = inputStream.read(buffer);
            assertEquals(read, moreText.getBytes().length);
        } catch (final FileNotFoundException e) {
            fail("Cannot open input file");
        } catch (final IOException e) {
            fail("Cannot read input file");
        }

        final MessageData last = null;
        final String lastPartId = BugleDatabaseOperations.updateDraftMessageData(db,
                conversationId, last, BugleDatabaseOperations.UPDATE_MODE_ADD_DRAFT);
        assertNull(lastPartId);

        inputStream = null;
        try {
            inputStream = mContext.getContentResolver().openInputStream(mAnotherUri);
            assertNull("Original draft content should have been deleted", inputStream);
        } catch (final FileNotFoundException e) {
        }

    }

    private StubActionService mService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        final FakeContext context = new FakeContext(getTestContext());

        final ContentProvider bugleProvider = new MessagingContentProvider();
        final ProviderInfo bugleProviderInfo = new ProviderInfo();
        bugleProviderInfo.authority = MessagingContentProvider.AUTHORITY;
        bugleProvider.attachInfo(mContext, bugleProviderInfo);
        context.addContentProvider(MessagingContentProvider.AUTHORITY, bugleProvider);
        final ContentProvider mediaProvider = new MediaScratchFileProvider();
        final ProviderInfo mediaProviderInfo = new ProviderInfo();
        mediaProviderInfo.authority = MediaScratchFileProvider.AUTHORITY;
        mediaProvider.attachInfo(mContext, mediaProviderInfo);
        context.addContentProvider(MediaScratchFileProvider.AUTHORITY, mediaProvider);

        mService = new StubActionService();
        final FakeDataModel fakeDataModel = new FakeDataModel(context)
                .withActionService(mService)
                .withConnectivityUtil(new StubConnectivityUtil(context));
        FakeFactory.registerWithFakeContext(getTestContext(), context)
                .withDataModel(fakeDataModel);

    }
}
