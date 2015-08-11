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
package com.android.messaging.ui.contact;

import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.view.View;
import android.widget.TextView;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.FakeCursor;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.data.ContactListItemData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.ui.ContactIconView;
import com.android.messaging.ui.ViewTest;

import org.mockito.Mock;
import org.mockito.Mockito;

public class ContactListItemViewTest extends ViewTest<ContactListItemView> {

    @Mock ContactListItemView.HostInterface mockHost;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getInstrumentation().getTargetContext();
        FakeFactory.register(context)
            .withDataModel(new FakeDataModel(context));
    }

    protected void verifyAddedContactForData(final ContactListItemData data,
            final ContactListItemView view) {
        Mockito.verify(mockHost).onContactListItemClicked(data, view);
    }

    protected void verifyContent(
            final ContactListItemView view,
            final String contactName,
            final String contactDetail,
            final String avatarUrl,
            final boolean showAvatar) {
        final TextView contactNameView = (TextView) view.findViewById(R.id.contact_name);
        final TextView contactDetailView = (TextView) view.findViewById(R.id.contact_details);
        final ContactIconView avatarView = (ContactIconView) view.findViewById(R.id.contact_icon);

        assertNotNull(contactNameView);
        assertEquals(contactName, contactNameView.getText());
        assertNotNull(contactDetail);
        assertEquals(contactDetail, contactDetailView.getText());
        assertNotNull(avatarView);
        if (showAvatar) {
            assertTrue(avatarView.mImageRequestBinding.isBound());
            assertEquals(View.VISIBLE, avatarView.getVisibility());
        } else {
            assertFalse(avatarView.mImageRequestBinding.isBound());
            assertEquals(View.INVISIBLE, avatarView.getVisibility());
        }
    }

    public void testBindFirstLevel() {
        final ContactListItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getAllContactListCursor();
        final int row = TestDataFactory.CONTACT_LIST_CURSOR_FIRST_LEVEL_CONTACT_INDEX;
        cursor.moveToPosition(row);
        view.bind(cursor, mockHost, false, null);
        verifyContent(view, (String) cursor.getAt(Contacts.DISPLAY_NAME, row),
                (String) cursor.getAt(Phone.NUMBER, row),
                (String) cursor.getAt(Contacts.PHOTO_THUMBNAIL_URI, row), true);
    }

    public void testBindSecondLevel() {
        final ContactListItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getAllContactListCursor();
        final int row = TestDataFactory.CONTACT_LIST_CURSOR_SECOND_LEVEL_CONTACT_INDEX;
        cursor.moveToPosition(row);
        view.bind(cursor, mockHost, false, null);
        verifyContent(view, (String) cursor.getAt(Contacts.DISPLAY_NAME, row),
                (String) cursor.getAt(Phone.NUMBER, row),
                (String) cursor.getAt(Contacts.PHOTO_THUMBNAIL_URI, row), false);
    }

    public void testClickAddedContact() {
        final ContactListItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getAllContactListCursor();
        cursor.moveToFirst();

        view.bind(cursor, mockHost, false, null);
        view.performClick();
        verifyAddedContactForData(view.mData, view);
    }

    public void testBindTwice() {
        final ContactListItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getAllContactListCursor();

        cursor.moveToFirst();
        view.bind(cursor, mockHost, false, null);

        cursor.moveToNext();
        view.bind(cursor, mockHost, false, null);
        verifyContent(view, (String) cursor.getAt(Contacts.DISPLAY_NAME, 1),
                (String) cursor.getAt(Phone.NUMBER, 1),
                (String) cursor.getAt(Contacts.PHOTO_THUMBNAIL_URI, 1), true);
    }

    @Override
    protected int getLayoutIdForView() {
        return R.layout.contact_list_item_view;
    }
}
