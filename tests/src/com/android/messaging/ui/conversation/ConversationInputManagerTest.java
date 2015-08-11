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

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.EditText;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.ConversationData;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.SubscriptionListData;
import com.android.messaging.ui.conversation.ConversationInputManager.ConversationInputHost;
import com.android.messaging.ui.conversation.ConversationInputManager.ConversationInputSink;
import com.android.messaging.ui.mediapicker.MediaPicker;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.ImeUtil;
import com.android.messaging.util.ImeUtil.ImeStateHost;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

@SmallTest
public class ConversationInputManagerTest extends BugleTestCase {
    @Spy protected ImeUtil spyImeUtil;
    @Mock protected BugleGservices mockBugleGservices;
    @Mock protected FragmentManager mockFragmentManager;
    @Mock protected ConversationInputHost mockConversationInputHost;
    @Mock protected ConversationInputSink mockConversationInputSink;
    @Mock protected ImeStateHost mockImeStateHost;
    @Mock protected ConversationData mockConversationData;
    @Mock protected DraftMessageData mockDraftMessageData;
    @Mock protected MediaPicker mockMediaPicker;
    @Mock protected SubscriptionListData mockSubscriptionListData;
    @Mock protected FragmentTransaction mockFragmentTransaction;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(getTestContext())
                .withBugleGservices(mockBugleGservices);
        spyImeUtil = Mockito.spy(new ImeUtil());
        ImeUtil.set(spyImeUtil);
    }

    private ConversationInputManager initNewInputManager(final Bundle savedState) {
        // Set up the mocks.
        Mockito.when(mockConversationInputHost.getSimSelectorView())
                .thenReturn(new SimSelectorView(getTestContext(), null));
        Mockito.when(mockConversationInputHost.createMediaPicker()).thenReturn(mockMediaPicker);
        Mockito.when(mockConversationInputSink.getComposeEditText())
                .thenReturn(new EditText(getTestContext()));
        Mockito.doReturn(mockFragmentTransaction).when(mockFragmentTransaction).replace(
                Mockito.eq(R.id.mediapicker_container), Mockito.any(MediaPicker.class),
                Mockito.anyString());
        Mockito.when(mockFragmentManager.findFragmentByTag(MediaPicker.FRAGMENT_TAG))
                .thenReturn(null);
        Mockito.when(mockFragmentManager.beginTransaction()).thenReturn(mockFragmentTransaction);
        Mockito.when(mockSubscriptionListData.hasData()).thenReturn(true);
        Mockito.when(mockConversationData.getSubscriptionListData())
                .thenReturn(mockSubscriptionListData);
        Mockito.doReturn(true).when(mockConversationData).isBound(Mockito.anyString());
        Mockito.doReturn(true).when(mockDraftMessageData).isBound(Mockito.anyString());

        final Binding<ConversationData> dataBinding = BindingBase.createBinding(this);
        dataBinding.bind(mockConversationData);
        final Binding<DraftMessageData> draftBinding = BindingBase.createBinding(this);
        draftBinding.bind(mockDraftMessageData);
        final ConversationInputManager inputManager = new ConversationInputManager(getTestContext(),
                mockConversationInputHost, mockConversationInputSink, mockImeStateHost,
                mockFragmentManager, dataBinding, draftBinding, savedState);
        return inputManager;
    }

    public void testShowHideInputs() {
        final ConversationInputManager inputManager = initNewInputManager(new Bundle());
        Mockito.when(mockMediaPicker.isOpen()).thenReturn(true);
        inputManager.showHideMediaPicker(true /* show */, true /* animate */);
        Mockito.verify(mockFragmentTransaction).replace(
                Mockito.eq(R.id.mediapicker_container), Mockito.any(MediaPicker.class),
                Mockito.anyString());
        Mockito.verify(mockMediaPicker).open(Mockito.anyInt(), Mockito.eq(true /* animate */));

        assertEquals(true, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(false, inputManager.isImeKeyboardVisible());

        Mockito.when(mockMediaPicker.isOpen()).thenReturn(false);
        inputManager.showHideMediaPicker(false /* show */, true /* animate */);
        Mockito.verify(mockMediaPicker).dismiss(Mockito.eq(true /* animate */));

        assertEquals(false, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(false, inputManager.isImeKeyboardVisible());
    }

    public void testShowTwoInputsSequentially() {
        // First show the media picker, then show the IME keyboard.
        final ConversationInputManager inputManager = initNewInputManager(new Bundle());
        Mockito.when(mockMediaPicker.isOpen()).thenReturn(true);
        inputManager.showHideMediaPicker(true /* show */, true /* animate */);

        assertEquals(true, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(false, inputManager.isImeKeyboardVisible());

        Mockito.when(mockMediaPicker.isOpen()).thenReturn(false);
        inputManager.showHideImeKeyboard(true /* show */, true /* animate */);

        assertEquals(false, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(true, inputManager.isImeKeyboardVisible());
    }

    public void testOnKeyboardShow() {
        final ConversationInputManager inputManager = initNewInputManager(new Bundle());
        Mockito.when(mockMediaPicker.isOpen()).thenReturn(true);
        inputManager.showHideMediaPicker(true /* show */, true /* animate */);

        assertEquals(true, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(false, inputManager.isImeKeyboardVisible());

        Mockito.when(mockMediaPicker.isOpen()).thenReturn(false);
        inputManager.testNotifyImeStateChanged(true /* imeOpen */);

        assertEquals(false, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(true, inputManager.isImeKeyboardVisible());
    }

    public void testRestoreState() {
        final ConversationInputManager inputManager = initNewInputManager(new Bundle());
        Mockito.when(mockMediaPicker.isOpen()).thenReturn(true);
        inputManager.showHideMediaPicker(true /* show */, true /* animate */);

        assertEquals(true, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(false, inputManager.isImeKeyboardVisible());

        Bundle savedInstanceState = new Bundle();
        inputManager.onSaveInputState(savedInstanceState);

        // Now try to restore the state
        final ConversationInputManager restoredInputManager =
                initNewInputManager(savedInstanceState);

        // Make sure the state is preserved.
        assertEquals(true, restoredInputManager.isMediaPickerVisible());
        assertEquals(false, restoredInputManager.isSimSelectorVisible());
        assertEquals(false, restoredInputManager.isImeKeyboardVisible());
    }

    public void testBackPress() {
        final ConversationInputManager inputManager = initNewInputManager(new Bundle());
        Mockito.when(mockMediaPicker.isOpen()).thenReturn(true);
        inputManager.showHideMediaPicker(true /* show */, true /* animate */);

        assertEquals(true, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(false, inputManager.isImeKeyboardVisible());

        Mockito.when(mockMediaPicker.isOpen()).thenReturn(false);
        assertEquals(true, inputManager.onBackPressed());

        assertEquals(false, inputManager.isMediaPickerVisible());
        assertEquals(false, inputManager.isSimSelectorVisible());
        assertEquals(false, inputManager.isImeKeyboardVisible());
    }
}
