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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;

import android.content.Context;
import android.media.MediaPlayer;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.EditText;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.ConversationData;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.DraftMessageData.CheckDraftForSendTask;
import com.android.messaging.datamodel.data.DraftMessageData.CheckDraftTaskCallback;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.ui.ViewTest;
import com.android.messaging.ui.conversation.ComposeMessageView.IComposeMessageViewHost;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.FakeMediaUtil;
import com.android.messaging.util.ImeUtil;

import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;

@MediumTest
public class ComposeMessageViewTest extends ViewTest<ComposeMessageView> {
    private Context mContext;

    @Mock protected DataModel mockDataModel;
    @Mock protected DraftMessageData mockDraftMessageData;
    @Mock protected BugleGservices mockBugleGservices;
    @Mock protected ImeUtil mockImeUtil;
    @Mock protected IComposeMessageViewHost mockIComposeMessageViewHost;
    @Mock protected MediaPlayer mockMediaPlayer;
    @Mock protected ConversationInputManager mockInputManager;
    @Mock protected ConversationData mockConversationData;

    Binding<ConversationData> mConversationBinding;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        FakeFactory.register(mContext)
                .withDataModel(mockDataModel)
                .withBugleGservices(mockBugleGservices)
                .withMediaUtil(new FakeMediaUtil(mockMediaPlayer));

        Mockito.doReturn(true).when(mockConversationData).isBound(Mockito.anyString());
        mConversationBinding = BindingBase.createBinding(this);
        mConversationBinding.bind(mockConversationData);
    }

    @Override
    protected ComposeMessageView getView() {
        final ComposeMessageView view = super.getView();
        view.setInputManager(mockInputManager);
        view.setConversationDataModel(BindingBase.createBindingReference(mConversationBinding));
        return view;
    }

    @Override
    protected int getLayoutIdForView() {
        return R.layout.compose_message_view;
    }

    public void testSend() {
        Mockito.when(mockDraftMessageData.getReadOnlyAttachments())
                .thenReturn(Collections.unmodifiableList(new ArrayList<MessagePartData>()));
        Mockito.when(mockDraftMessageData.getIsDefaultSmsApp()).thenReturn(true);
        Mockito.when(mockIComposeMessageViewHost.isReadyForAction()).thenReturn(true);
        final ComposeMessageView view = getView();

        final MessageData message = MessageData.createDraftSmsMessage("fake_id", "just_a_self_id",
                "Sample Message");

        Mockito.when(mockDraftMessageData.isBound(Matchers.anyString()))
                .thenReturn(true);
        Mockito.when(mockDraftMessageData.getMessageText()).thenReturn(message.getMessageText());
        Mockito.when(mockDraftMessageData.prepareMessageForSending(
                Matchers.<BindingBase<DraftMessageData>>any()))
                .thenReturn(message);
        Mockito.when(mockDraftMessageData.hasPendingAttachments()).thenReturn(false);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // Synchronously pass the draft check and callback.
                ((CheckDraftTaskCallback)invocation.getArguments()[2]).onDraftChecked(
                        mockDraftMessageData, CheckDraftForSendTask.RESULT_PASSED);
                return null;
            }
        }).when(mockDraftMessageData).checkDraftForAction(Mockito.anyBoolean(), Mockito.anyInt(),
                Mockito.<CheckDraftTaskCallback>any(),
                Mockito.<Binding<DraftMessageData>>any());

        view.bind(mockDraftMessageData, mockIComposeMessageViewHost);

        final EditText composeEditText = (EditText) view.findViewById(R.id.compose_message_text);
        final View sendButton = view.findViewById(R.id.send_message_button);

        view.requestDraftMessage(false);

        Mockito.verify(mockDraftMessageData).loadFromStorage(any(BindingBase.class),
                isNull(), Mockito.eq(false));

        view.onDraftChanged(mockDraftMessageData, DraftMessageData.ALL_CHANGED);

        assertEquals(message.getMessageText(), composeEditText.getText().toString());

        sendButton.performClick();
        Mockito.verify(mockIComposeMessageViewHost).sendMessage(
                Mockito.argThat(o -> {
                    assertEquals(message.getMessageText(), o.getMessageText());
                    return true;
                }));
    }

    public void testNotDefaultSms() {
        Mockito.when(mockDraftMessageData.getReadOnlyAttachments())
                .thenReturn(Collections.unmodifiableList(new ArrayList<MessagePartData>()));
        Mockito.when(mockDraftMessageData.getIsDefaultSmsApp()).thenReturn(false);
        Mockito.when(mockIComposeMessageViewHost.isReadyForAction()).thenReturn(false);
        final ComposeMessageView view = getView();

        final MessageData message = MessageData.createDraftSmsMessage("fake_id", "just_a_self_id",
                "Sample Message");

        Mockito.when(mockDraftMessageData.isBound(Matchers.anyString()))
                .thenReturn(true);
        Mockito.when(mockDraftMessageData.getMessageText()).thenReturn(message.getMessageText());
        Mockito.when(mockDraftMessageData.prepareMessageForSending(
                Matchers.<BindingBase<DraftMessageData>>any()))
                .thenReturn(message);
        Mockito.when(mockDraftMessageData.hasPendingAttachments()).thenReturn(false);

        view.bind(mockDraftMessageData, mockIComposeMessageViewHost);

        final EditText composeEditText = (EditText) view.findViewById(R.id.compose_message_text);
        final View sendButton = view.findViewById(R.id.send_message_button);

        view.requestDraftMessage(false);

        Mockito.verify(mockDraftMessageData).loadFromStorage(any(BindingBase.class),
                isNull(), Mockito.eq(false));

        view.onDraftChanged(mockDraftMessageData, DraftMessageData.ALL_CHANGED);

        assertEquals(message.getMessageText(), composeEditText.getText().toString());

        sendButton.performClick();
        Mockito.verify(mockIComposeMessageViewHost).warnOfMissingActionConditions(
                any(Boolean.class), any(Runnable.class));
    }
}
