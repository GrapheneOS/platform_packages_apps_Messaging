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
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.android.messaging.Factory;
import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.FakeCursor;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.ui.AsyncImageView;
import com.android.messaging.ui.UIIntentsImpl;
import com.android.messaging.ui.ViewTest;
import com.android.messaging.ui.conversationlist.ConversationListItemView;
import com.android.messaging.util.Dates;

import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;

@MediumTest
public class ConversationListItemViewTest extends ViewTest<ConversationListItemView> {

    @Mock private ConversationListItemView.HostInterface mockHost;
    private FakeCursor mCursor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getInstrumentation().getTargetContext();
        FakeFactory.register(context)
            .withDataModel(new FakeDataModel(context))
            .withUIIntents(new UIIntentsImpl());
        mCursor = TestDataFactory.getConversationListCursor();
    }


    protected void verifyLaunchedConversationForId(final String id,
            final ConversationListItemView conversationView) {
        // Must be a short click.
        final ArgumentMatcher<ConversationListItemData> itemDataIdMatcher =
                arg -> TextUtils.equals(id, arg.getConversationId());
        Mockito.verify(mockHost).onConversationClicked(
                Mockito.argThat(itemDataIdMatcher), Mockito.eq(false),
                Mockito.eq(conversationView));
    }

    protected void verifyContent(
            final ConversationListItemView view, final FakeCursor cursor, final int index) {
        /* ConversationQueryColumns.NAME */
        final String  conversationQueryColumnsName = "name";
        final String name = (String) cursor.getAt(conversationQueryColumnsName, index);

        /* ConversationQueryColumns.SNIPPET_TEXT */
        final String  conversationQueryColumnsSnippetText = "snippet_text";
        final String snippet = (String) cursor.getAt(conversationQueryColumnsSnippetText, index);

        /* ConversationQueryColumns.SORT_TIMESTAMP */
        final String  conversationQueryColumnsSortTimestamp = "sort_timestamp";
        final String timestamp = Dates.getConversationTimeString(
                (Long) cursor.getAt(conversationQueryColumnsSortTimestamp, index)).toString();

        final boolean unread = !isRead(cursor, index);
        verifyContent(view, name,  snippet, timestamp, unread);
    }

    protected void verifyContent(
            final ConversationListItemView view,
            final String conversationName,
            final String snippet,
            final String timestamp,
            final boolean unread) {
        final TextView conversationNameView =
                (TextView) view.findViewById(R.id.conversation_name);
        final TextView snippetTextView = (TextView) view.findViewById(R.id.conversation_snippet);
        final TextView timestampTextView = (TextView) view.findViewById(
                R.id.conversation_timestamp);
        final AsyncImageView imagePreviewView =
                (AsyncImageView) view.findViewById(R.id.conversation_image_preview);

        final Context context = Factory.get().getApplicationContext();
        assertNotNull(conversationNameView);
        assertEquals(conversationName, conversationNameView.getText());
        assertNotNull(snippetTextView);
        if (unread) {
            assertEquals(ConversationListItemView.UNREAD_SNIPPET_LINE_COUNT,
                    snippetTextView.getMaxLines());
            assertEquals(context.getResources().getColor(R.color.conversation_list_item_unread),
                    snippetTextView.getCurrentTextColor());
            assertEquals(context.getResources().getColor(R.color.conversation_list_item_unread),
                    conversationNameView.getCurrentTextColor());

        } else {
            assertEquals(ConversationListItemView.NO_UNREAD_SNIPPET_LINE_COUNT,
                    snippetTextView.getMaxLines());
            assertEquals(context.getResources().getColor(R.color.conversation_list_item_read),
                    snippetTextView.getCurrentTextColor());
            assertEquals(context.getResources().getColor(R.color.conversation_list_item_read),
                    conversationNameView.getCurrentTextColor());
        }

        assertEquals(View.VISIBLE, imagePreviewView.getVisibility());
        assertTrue(snippetTextView.getText().toString().contains(snippet));
        assertEquals(timestamp, timestampTextView.getText());
    }

    protected boolean isRead(final FakeCursor cursor, final int index) {
        return 1 == ((Integer) cursor.getAt("read", index)).intValue();
    }

    public void testBindUnread() {
        final ConversationListItemView view = getView();
        final int messageIndex = TestDataFactory.CONVERSATION_LIST_CURSOR_UNREAD_MESSAGE_INDEX;
        mCursor.moveToPosition(messageIndex);
        assertFalse(isRead(mCursor, messageIndex));
        view.bind(mCursor, mockHost);
        verifyContent(view, mCursor, messageIndex);
    }

    public void testBindRead() {
        final ConversationListItemView view = getView();

        final int messageIndex = TestDataFactory.CONVERSATION_LIST_CURSOR_READ_MESSAGE_INDEX;
        mCursor.moveToPosition(messageIndex);
        assertTrue(isRead(mCursor, messageIndex));
        view.bind(mCursor, mockHost);
        verifyContent(view, mCursor, messageIndex);
    }

    public void testClickLaunchesConversation() {
        final ConversationListItemView view = getView();
        final View swipeableContainer = view.findViewById(R.id.swipeableContainer);
        mCursor.moveToFirst();
        view.bind(mCursor, mockHost);
        swipeableContainer.performClick();
        verifyLaunchedConversationForId(
                mCursor.getAt("_id" /* ConversationQueryColumns._ID */, 0).toString(), view);
    }

    public void testBindTwice() {
        final ConversationListItemView view = getView();

        mCursor.moveToFirst();
        view.bind(mCursor, mockHost);

        mCursor.moveToNext();
        view.bind(mCursor, mockHost);
        verifyContent(view, mCursor, mCursor.getPosition());
    }

    @Override
    protected int getLayoutIdForView() {
        return R.layout.conversation_list_item_view;
    }
}
