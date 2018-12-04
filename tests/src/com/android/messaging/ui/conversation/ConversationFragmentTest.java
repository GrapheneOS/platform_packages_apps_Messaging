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

import android.app.Activity;
import android.app.Fragment;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.MemoryCacheManager;
import com.android.messaging.datamodel.data.ConversationData;
import com.android.messaging.datamodel.data.ConversationData.ConversationDataListener;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.datamodel.media.MediaResourceManager;
import com.android.messaging.ui.FragmentTestCase;
import com.android.messaging.ui.PlainTextEditText;
import com.android.messaging.ui.TestActivity.FragmentEventListener;
import com.android.messaging.ui.conversation.ConversationFragment.ConversationFragmentHost;
import com.android.messaging.ui.conversationlist.ConversationListFragment;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.ImeUtil;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;


/**
 * Unit tests for {@link ConversationListFragment}.
 */
@LargeTest
public class ConversationFragmentTest extends FragmentTestCase<ConversationFragment> {

    @Mock protected DataModel mockDataModel;
    @Mock protected ConversationData mockConversationData;
    @Mock protected DraftMessageData mockDraftMessageData;
    @Mock protected MediaResourceManager mockMediaResourceManager;
    @Mock protected BugleGservices mockBugleGservices;
    @Mock protected ConversationFragmentHost mockHost;
    @Mock protected MemoryCacheManager mockMemoryCacheManager;

    private ImeUtil mSpiedImeUtil;

    private static final String CONVERSATION_ID = "cid";


    public ConversationFragmentTest() {
        super(ConversationFragment.class);
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      ImeUtil.clearInstance();
      mSpiedImeUtil = Mockito.spy(new ImeUtil());
      FakeFactory.register(this.getInstrumentation().getTargetContext())
          .withDataModel(mockDataModel)
          .withBugleGservices(mockBugleGservices)
          .withMemoryCacheManager(mockMemoryCacheManager);
    }

    /**
     * Helper that will do the 'binding' of ConversationFragmentTest with ConversationData and
     * leave fragment in 'ready' state.
     * @param cursor
     */
    private void loadWith(final Cursor cursor) {
        Mockito.when(mockDraftMessageData.isBound(Matchers.anyString()))
            .thenReturn(true);
        Mockito.when(mockConversationData.isBound(Matchers.anyString()))
            .thenReturn(true);
        Mockito.doReturn(mockDraftMessageData)
            .when(mockDataModel)
            .createDraftMessageData(Mockito.anyString());
        Mockito.when(mockDataModel.createConversationData(
                Matchers.any(Activity.class),
                Matchers.any(ConversationDataListener.class),
                Matchers.anyString()))
            .thenReturn(mockConversationData);

        // Create fragment synchronously to avoid need for volatile, synchronization etc.
        final ConversationFragment fragment = getFragment();
        // Binding to model happens when attaching fragment to activity, so hook into test
        // activity to do so.
        getActivity().setFragmentEventListener(new FragmentEventListener() {
            @Override
            public void onAttachFragment(final Fragment attachedFragment) {
                if (fragment == attachedFragment) {
                    fragment.setConversationInfo(getActivity(), CONVERSATION_ID, null);
                }
            }
        });

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fragment.setHost(mockHost);
                getActivity().setFragment(fragment);
                Mockito.verify(mockDataModel).createConversationData(
                        getActivity(), fragment, CONVERSATION_ID);
                Mockito.verify(mockConversationData).init(fragment.getLoaderManager(),
                        fragment.mBinding);
            }
        });
        // Wait for initial layout pass to work around crash in recycler view
        getInstrumentation().waitForIdleSync();
        // Now load the cursor
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fragment.onConversationMessagesCursorUpdated(mockConversationData, cursor, null,
                        false);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    /**
     * Verifies that list view gets correctly populated given a cursor.
     */
    public void testLoadListView() {
        final Cursor cursor = TestDataFactory.getConversationMessageCursor();
        loadWith(cursor);
        final RecyclerView listView =
                (RecyclerView) getFragment().getView().findViewById(android.R.id.list);
        assertEquals("bad cursor", cursor.getCount(), listView.getAdapter().getItemCount());
        assertEquals("bad cursor count", cursor.getCount(), listView.getChildCount());
    }

    public void testClickComposeMessageView() {
        final Cursor cursor = TestDataFactory.getConversationMessageCursor();
        loadWith(cursor);

        final PlainTextEditText composeEditText = (PlainTextEditText) getFragment().getView()
                .findViewById(R.id.compose_message_text);
        setFocus(composeEditText, false);
        Mockito.verify(mockHost, Mockito.never()).onStartComposeMessage();
        setFocus(composeEditText, true);
        Mockito.verify(mockHost).onStartComposeMessage();
    }
}
