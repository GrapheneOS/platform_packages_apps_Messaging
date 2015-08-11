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

package com.android.messaging.ui.conversationlist;

import android.content.Context;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.ui.FragmentTestCase;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.conversationlist.ConversationListFragment;
import com.android.messaging.ui.conversationlist.ConversationListFragment.ConversationListFragmentHost;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;


/**
 * Unit tests for {@link ConversationListFragment}.
 */
@LargeTest
public class ConversationListFragmentTest
    extends FragmentTestCase<ConversationListFragment> {

    @Mock protected ConversationListData mMockConversationListData;
    @Mock protected ConversationListFragmentHost mMockConversationHostListHost;
    @Mock protected UIIntents mMockUIIntents;
    protected FakeDataModel mFakeDataModel;

    public ConversationListFragmentTest() {
        super(ConversationListFragment.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final Context context = getInstrumentation().getTargetContext();
        mFakeDataModel = new FakeDataModel(context)
            .withConversationListData(mMockConversationListData);
        FakeFactory.register(context)
                .withDataModel(mFakeDataModel)
                .withUIIntents(mMockUIIntents);
    }

    /**
     * Helper that will do the 'binding' of ConversationListFragmentTest with ConversationListData
     * and leave fragment in 'ready' state.
     * @param cursor
     */
    private void loadWith(final Cursor cursor) {
        Mockito.when(mMockConversationListData.isBound(Matchers.anyString()))
            .thenReturn(true);

        final ConversationListFragment fragment = getFragment();
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fragment.setHost(mMockConversationHostListHost);
                getActivity().setFragment(fragment);
                Mockito.verify(mMockConversationListData).init(fragment.getLoaderManager(),
                        fragment.mListBinding);
                fragment.onConversationListCursorUpdated(mMockConversationListData, cursor);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    /**
     * Verifies that list view gets correctly populated given a cursor.
     */
    public void testLoadListView() {
        final Cursor cursor = TestDataFactory.getConversationListCursor();
        loadWith(cursor);
        final RecyclerView listView =
                (RecyclerView) getFragment().getView().findViewById(android.R.id.list);
        //assertEquals(cursor.getCount(), listView.getCount());
        assertEquals(cursor.getCount(), listView.getChildCount());
    }

    /**
     * Verifies that 'empty list' promo is rendered with an empty cursor.
     */
    public void testEmptyView() {
        loadWith(TestDataFactory.getEmptyConversationListCursor());
        final RecyclerView listView =
                (RecyclerView) getFragment().getView().findViewById(android.R.id.list);
        final View emptyMessageView =
                getFragment().getView().findViewById(R.id.no_conversations_view);
        assertEquals(View.VISIBLE, emptyMessageView.getVisibility());
        assertEquals(0, listView.getChildCount());
    }

    /**
     * Verifies that the button to start a new conversation works.
     */
    public void testStartNewConversation() {
        final Cursor cursor = TestDataFactory.getConversationListCursor();
        loadWith(cursor);
        final ImageView startNewConversationButton = (ImageView)
                getFragment().getView().findViewById(R.id.start_new_conversation_button);

        clickButton(startNewConversationButton);
        Mockito.verify(mMockConversationHostListHost).onCreateConversationClick();
    }
}
