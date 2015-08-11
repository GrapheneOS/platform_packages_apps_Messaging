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

package com.android.messaging.ui.conversation;

import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import com.android.messaging.BugleTestCase;
import com.android.messaging.ui.contact.ContactPickerFragment;
import com.android.messaging.ui.conversation.ConversationActivityUiState;
import com.android.messaging.ui.conversation.ConversationActivityUiState.ConversationActivityUiStateHost;

import org.mockito.Mock;
import org.mockito.Mockito;

@SmallTest
public class ConversationActivityUiStateTest extends BugleTestCase {
    @Mock protected ConversationActivityUiStateHost mockListener;

    /**
     * Test the Ui state where we start off with the contact picker to pick the first contact.
     */
    public void testPickInitialContact() {
        final ConversationActivityUiState uiState = new ConversationActivityUiState(null);
        uiState.setHost(mockListener);
        assertTrue(uiState.shouldShowContactPickerFragment());
        assertFalse(uiState.shouldShowConversationFragment());
        assertEquals(ContactPickerFragment.MODE_PICK_INITIAL_CONTACT,
                uiState.getDesiredContactPickingMode());
        uiState.onGetOrCreateConversation("conversation1");
        Mockito.verify(mockListener, Mockito.times(1)).onConversationContactPickerUiStateChanged(
                Mockito.eq(ConversationActivityUiState.STATE_CONTACT_PICKER_ONLY_INITIAL_CONTACT),
                Mockito.eq(
                        ConversationActivityUiState.STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW),
                Mockito.anyBoolean());
        assertTrue(uiState.shouldShowContactPickerFragment());
        assertTrue(uiState.shouldShowConversationFragment());
        assertTrue(TextUtils.equals("conversation1", uiState.getConversationId()));
        assertEquals(ContactPickerFragment.MODE_CHIPS_ONLY,
                uiState.getDesiredContactPickingMode());
    }

    /**
     * Test the Ui state where we have both the chips view and the conversation view and we
     * start message compose.
     */
    public void testHybridUiStateStartCompose() {
        final ConversationActivityUiState uiState = new ConversationActivityUiState("conv1");
        uiState.testSetUiState(
                ConversationActivityUiState.STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW);
        uiState.setHost(mockListener);

        // Start message compose.
        uiState.onStartMessageCompose();
        Mockito.verify(mockListener, Mockito.times(1)).onConversationContactPickerUiStateChanged(
                Mockito.eq(
                        ConversationActivityUiState.STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW),
                Mockito.eq(ConversationActivityUiState.STATE_CONVERSATION_ONLY),
                Mockito.anyBoolean());
        assertFalse(uiState.shouldShowContactPickerFragment());
        assertTrue(uiState.shouldShowConversationFragment());
    }

    /**
     * Test the Ui state where we have both the chips view and the conversation view and we
     * try to add a participant.
     */
    public void testHybridUiStateAddParticipant() {
        final ConversationActivityUiState uiState = new ConversationActivityUiState("conv1");
        uiState.testSetUiState(
                ConversationActivityUiState.STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW);
        uiState.setHost(mockListener);

        uiState.onAddMoreParticipants();
        Mockito.verify(mockListener, Mockito.times(1)).onConversationContactPickerUiStateChanged(
                Mockito.eq(
                        ConversationActivityUiState.STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW),
                Mockito.eq(
                        ConversationActivityUiState.STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS),
                Mockito.anyBoolean());
        assertTrue(uiState.shouldShowContactPickerFragment());
        assertFalse(uiState.shouldShowConversationFragment());
        assertEquals(ContactPickerFragment.MODE_PICK_MORE_CONTACTS,
                uiState.getDesiredContactPickingMode());
    }

    /**
     * Test the Ui state where we are trying to add more participants and commit.
     */
    public void testCommitAddParticipant() {
        final ConversationActivityUiState uiState = new ConversationActivityUiState(null);
        uiState.testSetUiState(
                ConversationActivityUiState.STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS);
        uiState.setHost(mockListener);

        uiState.onGetOrCreateConversation("conversation1");

        // After adding more contacts, the terminal state is always conversation only (i.e. we
        // don't go back to hybrid mode).
        Mockito.verify(mockListener, Mockito.times(1)).onConversationContactPickerUiStateChanged(
                Mockito.eq(ConversationActivityUiState.STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS),
                Mockito.eq(ConversationActivityUiState.STATE_CONVERSATION_ONLY),
                Mockito.anyBoolean());
        assertFalse(uiState.shouldShowContactPickerFragment());
        assertTrue(uiState.shouldShowConversationFragment());
    }
}
