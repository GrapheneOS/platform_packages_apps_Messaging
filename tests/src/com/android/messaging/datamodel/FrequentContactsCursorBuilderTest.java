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
package com.android.messaging.datamodel;

import android.database.Cursor;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.util.ContactUtil;

@SmallTest
public class FrequentContactsCursorBuilderTest extends BugleTestCase {

    private void verifyBuiltCursor(final Cursor expected, final Cursor actual) {
        final int rowCount = expected.getCount();
        final int columnCount = expected.getColumnCount();
        assertEquals(rowCount, actual.getCount());
        assertEquals(columnCount, actual.getColumnCount());
        for (int i = 0; i < rowCount; i++) {
            expected.moveToPosition(i);
            actual.moveToPosition(i);
            assertEquals(expected.getLong(ContactUtil.INDEX_DATA_ID),
                    actual.getLong(ContactUtil.INDEX_DATA_ID));
            assertEquals(expected.getLong(ContactUtil.INDEX_CONTACT_ID),
                    actual.getLong(ContactUtil.INDEX_CONTACT_ID));
            assertEquals(expected.getString(ContactUtil.INDEX_LOOKUP_KEY),
                    actual.getString(ContactUtil.INDEX_LOOKUP_KEY));
            assertEquals(expected.getString(ContactUtil.INDEX_DISPLAY_NAME),
                    actual.getString(ContactUtil.INDEX_DISPLAY_NAME));
            assertEquals(expected.getString(ContactUtil.INDEX_PHOTO_URI),
                    actual.getString(ContactUtil.INDEX_PHOTO_URI));
            assertEquals(expected.getString(ContactUtil.INDEX_PHONE_EMAIL),
                    actual.getString(ContactUtil.INDEX_PHONE_EMAIL));
            assertEquals(expected.getInt(ContactUtil.INDEX_PHONE_EMAIL_TYPE),
                    actual.getInt(ContactUtil.INDEX_PHONE_EMAIL_TYPE));
            assertEquals(expected.getString(ContactUtil.INDEX_PHONE_EMAIL_LABEL),
                    actual.getString(ContactUtil.INDEX_PHONE_EMAIL_LABEL));
        }
    }

    public void testIncompleteBuild() {
        final FrequentContactsCursorBuilder builder = new FrequentContactsCursorBuilder();
        assertNull(builder.build());
        assertNull(builder.setFrequents(TestDataFactory.getStrequentContactsCursor()).build());
        builder.resetBuilder();
        assertNull(builder.build());
        assertNull(builder.setAllContacts(TestDataFactory.getAllContactListCursor()).build());
    }

    public void testBuildOnce() {
        final Cursor cursor = new FrequentContactsCursorBuilder()
            .setAllContacts(TestDataFactory.getAllContactListCursor())
            .setFrequents(TestDataFactory.getStrequentContactsCursor())
            .build();
        assertNotNull(cursor);
        verifyBuiltCursor(TestDataFactory.getFrequentContactListCursor(), cursor);
    }

    public void testBuildTwice() {
        final FrequentContactsCursorBuilder builder = new FrequentContactsCursorBuilder();
        final Cursor firstCursor = builder
            .setAllContacts(TestDataFactory.getAllContactListCursor())
            .setFrequents(TestDataFactory.getStrequentContactsCursor())
            .build();
        assertNotNull(firstCursor);
        builder.resetBuilder();
        assertNull(builder.build());

        final Cursor secondCursor = builder
                .setAllContacts(TestDataFactory.getAllContactListCursor())
                .setFrequents(TestDataFactory.getStrequentContactsCursor())
                .build();
        assertNotNull(firstCursor);
        verifyBuiltCursor(TestDataFactory.getFrequentContactListCursor(), secondCursor);
    }
}
