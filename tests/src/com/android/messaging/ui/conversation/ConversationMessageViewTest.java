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

import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.Suppress;
import android.view.View;
import android.widget.TextView;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.FakeCursor;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.ui.ViewTest;
import com.android.messaging.ui.conversation.ConversationMessageView;
import com.android.messaging.ui.conversation.ConversationMessageView.ConversationMessageViewHost;
import com.android.messaging.util.Dates;

import org.mockito.Mock;

@MediumTest
public class ConversationMessageViewTest extends ViewTest<ConversationMessageView> {
    @Mock ConversationMessageViewHost mockHost;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(getInstrumentation().getTargetContext());
    }

    @Override
    protected ConversationMessageView getView() {
        final ConversationMessageView view = super.getView();
        view.setHost(mockHost);
        return view;
    }

    protected void verifyContent(final ConversationMessageView view, final String messageText,
            final boolean showTimestamp, final String timestampText) {

        final TextView messageTextView = (TextView) view.findViewById(R.id.message_text);
        final TextView statusTextView = (TextView) view.findViewById(R.id.message_status);

        assertNotNull(messageTextView);
        assertEquals(messageText, messageTextView.getText());

        if (showTimestamp) {
            assertEquals(View.VISIBLE, statusTextView.getVisibility());
            assertEquals(timestampText, statusTextView.getText());
        } else {
            assertEquals(View.GONE, statusTextView.getVisibility());
        }
    }

    public void testBind() {
        final ConversationMessageView view = getView();

        final FakeCursor cursor = TestDataFactory.getConversationMessageCursor();
        cursor.moveToFirst();

        view.bind(cursor);
        verifyContent(view, TestDataFactory.getMessageText(cursor, 0), true, Dates
                .getMessageTimeString((Long) cursor.getAt("received_timestamp", 0)).toString());
    }

    public void testBindTwice() {
        final ConversationMessageView view = getView();

        final FakeCursor cursor = TestDataFactory.getConversationMessageCursor();
        cursor.moveToFirst();
        view.bind(cursor);

        cursor.moveToNext();
        view.bind(cursor);
        verifyContent(view, TestDataFactory.getMessageText(cursor, 1), true, Dates
                .getMessageTimeString((Long) cursor.getAt("received_timestamp", 1)).toString());
    }

    public void testBindLast() {
        final ConversationMessageView view = getView();

        final FakeCursor cursor = TestDataFactory.getConversationMessageCursor();
        final int lastPos = cursor.getCount() - 1;
        cursor.moveToPosition(lastPos);

        view.bind(cursor);
        verifyContent(view, TestDataFactory.getMessageText(cursor, lastPos), true, Dates
                .getMessageTimeString((Long) cursor.getAt("received_timestamp", lastPos))
                .toString());
    }

    @Override
    protected int getLayoutIdForView() {
        return R.layout.conversation_message_view;
    }
}
