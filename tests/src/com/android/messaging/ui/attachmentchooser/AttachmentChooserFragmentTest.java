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

package com.android.messaging.ui.attachmentchooser;

import android.app.Fragment;
import android.test.suitebuilder.annotation.LargeTest;
import android.widget.CheckBox;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.ui.FragmentTestCase;
import com.android.messaging.ui.TestActivity;
import com.android.messaging.ui.TestActivity.FragmentEventListener;
import com.android.messaging.ui.attachmentchooser.AttachmentChooserFragment;
import com.android.messaging.ui.attachmentchooser.AttachmentGridItemView;
import com.android.messaging.ui.attachmentchooser.AttachmentGridView;
import com.android.messaging.ui.attachmentchooser.AttachmentChooserFragment.AttachmentChooserFragmentHost;
import com.android.messaging.ui.conversationlist.ConversationListFragment;

import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Unit tests for {@link ConversationListFragment}.
 */
@LargeTest
public class AttachmentChooserFragmentTest extends FragmentTestCase<AttachmentChooserFragment> {

    @Mock protected DataModel mockDataModel;
    @Mock protected DraftMessageData mockDraftMessageData;
    @Mock protected AttachmentChooserFragmentHost mockHost;

    private static final String CONVERSATION_ID = "cid";

    /** A custom argument matcher that checks whether the set argument passed in is a set
     * with identical attachment data as the given set.
     */
    private class IsSetOfGivenAttachments implements ArgumentMatcher<Set<MessagePartData>> {
        private final Set<MessagePartData> mGivenParts;
        public IsSetOfGivenAttachments(final Set<MessagePartData> givenParts) {
            mGivenParts = givenParts;
        }

        @Override
        public boolean matches(final Set<MessagePartData> actualSet) {
            if (actualSet.size() != mGivenParts.size()) {
                return false;
            }
            return mGivenParts.containsAll(actualSet) && actualSet.containsAll(mGivenParts);
        }
     }

    public AttachmentChooserFragmentTest() {
        super(AttachmentChooserFragment.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(this.getInstrumentation().getTargetContext())
            .withDataModel(mockDataModel);
    }

    private void loadWith(final List<MessagePartData> attachments) {
        Mockito.when(mockDraftMessageData.isBound(Matchers.anyString()))
            .thenReturn(true);
        Mockito.doReturn(mockDraftMessageData)
            .when(mockDataModel)
            .createDraftMessageData(Mockito.anyString());
        Mockito.doReturn(attachments)
            .when(mockDraftMessageData)
            .getReadOnlyAttachments();
        Mockito.when(mockDataModel.createDraftMessageData(
                Matchers.anyString()))
            .thenReturn(mockDraftMessageData);

        // Create fragment synchronously to avoid need for volatile, synchronization etc.
        final AttachmentChooserFragment fragment = getFragment();
        // Binding to model happens when attaching fragment to activity, so hook into test
        // activity to do so.
        getActivity().setFragmentEventListener(new FragmentEventListener() {
            @Override
            public void onAttachFragment(final Fragment attachedFragment) {
                if (fragment == attachedFragment) {
                    fragment.setConversationId(CONVERSATION_ID);
                }
            }
        });

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fragment.setHost(mockHost);
                getActivity().setFragment(fragment);
                Mockito.verify(mockDataModel).createDraftMessageData(
                        Mockito.matches(CONVERSATION_ID));
                Mockito.verify(mockDraftMessageData).loadFromStorage(
                        Matchers.eq(fragment.mBinding), Matchers.eq((MessageData) null),
                        Matchers.eq(false));
            }
        });
        // Now load the cursor
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fragment.onDraftChanged(mockDraftMessageData, DraftMessageData.ALL_CHANGED);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testUnselect() {
        final List<MessagePartData> attachments = TestDataFactory.getTestDraftAttachments();
        loadWith(attachments);
        final AttachmentGridView attachmentGridView = (AttachmentGridView)
                getFragment().getView().findViewById(R.id.grid);
        assertEquals("bad view count", attachments.size(),
                attachmentGridView.getAdapter().getCount());

        final AttachmentGridItemView itemView = (AttachmentGridItemView)
                attachmentGridView.getChildAt(0);
        assertEquals(attachmentGridView, itemView.testGetHostInterface());
        final CheckBox checkBox = (CheckBox) itemView.findViewById(R.id.checkbox);
        assertEquals(true, checkBox.isChecked());
        assertEquals(true, attachmentGridView.isItemSelected(itemView.mAttachmentData));
        clickButton(checkBox);
        assertEquals(false, checkBox.isChecked());
        assertEquals(false, attachmentGridView.isItemSelected(itemView.mAttachmentData));

        final AttachmentGridItemView itemView2 = (AttachmentGridItemView)
                attachmentGridView.getChildAt(1);
        final CheckBox checkBox2 = (CheckBox) itemView2.findViewById(R.id.checkbox);
        clickButton(checkBox2);

        getFragment().confirmSelection();
        final MessagePartData[] attachmentsToRemove = new MessagePartData[] {
                itemView.mAttachmentData, itemView2.mAttachmentData };
        Mockito.verify(mockDraftMessageData).removeExistingAttachments(Matchers.argThat(
                new IsSetOfGivenAttachments(new HashSet<>(Arrays.asList(attachmentsToRemove)))));
        Mockito.verify(mockDraftMessageData).saveToStorage(Matchers.eq(getFragment().mBinding));
        Mockito.verify(mockHost).onConfirmSelection();
    }
}
