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

package com.android.messaging.util;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.test.suitebuilder.annotation.LargeTest;
import android.text.TextUtils;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeFactory;

import org.junit.Assert;

import java.util.ArrayList;

/*
 * Class for testing ContactUtil.
 */
@LargeTest
public class ContactUtilTest extends BugleTestCase {
    private static final String TEST_NAME_PREFIX = "BugleTest:";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // TODO: This test will actually mess with contacts on your phone.
        // Ideally we would use a fake content provider to give us contact data...
        FakeFactory.registerWithoutFakeContext(getTestContext());

        // add test contacts.
        addTestContact("John", "650-123-1233", "john@gmail.com", false);
        addTestContact("Joe", "(650)123-1233", "joe@gmail.com", false);
        addTestContact("Jim", "650 123 1233", "jim@gmail.com", false);
        addTestContact("Samantha", "650-123-1235", "samantha@gmail.com", true);
        addTestContact("Adrienne", "650-123-1236", "adrienne@gmail.com", true);
    }

    @Override
    protected void tearDown() throws Exception {
        deleteTestContacts();
        super.tearDown();
    }

    /**
     * Add a test contact based on contact name, phone and email.
     */
    private void addTestContact(
            final String name, final String phone, final String email, final boolean starred)
            throws Exception {
        final ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build());

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        TEST_NAME_PREFIX + name).build());

        if (phone != null) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build());
        }

        if (email != null) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE,
                            ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                    .build());
        }

        mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);

        // Star the whole contact if needed.
        if (starred) {
            final ContentValues values = new ContentValues();
            values.put(Contacts.STARRED, 1);
            getContext().getContentResolver().update(Contacts.CONTENT_URI, values,
                    Contacts.DISPLAY_NAME + "= ?", new String[] { TEST_NAME_PREFIX + name });
        }
    }

    /**
     * Remove test contacts added during test setup.
     */
    private void deleteTestContacts() {
        final Uri contactUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI,
                Uri.encode(TEST_NAME_PREFIX));
        final Cursor cur =
                mContext.getContentResolver().query(contactUri, null, null, null, null);
        try {
            if (cur.moveToFirst()) {
                do {
                    final String lookupKey = cur.getString(cur.getColumnIndex(Contacts.LOOKUP_KEY));
                    final Uri uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
                    mContext.getContentResolver().delete(uri, null, null);
                } while (cur.moveToNext());
            }
        } catch (final Exception e) {
            System.out.println(e.getStackTrace());
        }
    }

    /**
     * Verify ContactUtil.getPhone will return all phones, including the ones added for test.
     */
    public void ingoredTestGetPhones() {
        final Cursor cur = ContactUtil.getPhones(getContext())
                .performSynchronousQuery();

        LogUtil.i(LogUtil.BUGLE_TAG, "testGetPhones: Number of phones on the device:" +
                cur.getCount());

        verifyCursorContains(cur, TEST_NAME_PREFIX + "John");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Joe");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Jim");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Samantha");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Adrienne");
    }

    /**
     * Verify ContactUtil.filterPhone will work on name based matches.
     */
    public void ingoredTestFilterPhonesByName() {
        final Cursor cur = ContactUtil.filterPhones(getContext(), TEST_NAME_PREFIX)
                .performSynchronousQuery();

        if (cur.getCount() != 5) {
            Assert.fail("Cursor should have size of 5");
            return;
        }

        verifyCursorContains(cur, TEST_NAME_PREFIX + "John");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Joe");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Jim");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Samantha");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Adrienne");
    }

    /**
     * Verify ContactUtil.filterPhone will work on partial number matches.
     */
    public void ingoredTestFilterPhonesByPartialNumber() {
        final String[] filters = new String[] { "650123", "650-123", "(650)123", "650 123" };

        for (final String filter : filters) {
            final Cursor cur = ContactUtil.filterPhones(getContext(), filter)
                    .performSynchronousQuery();

            LogUtil.i(LogUtil.BUGLE_TAG, "testFilterPhonesByPartialNumber: Number of phones:" +
                    cur.getCount());

            verifyCursorContains(cur, TEST_NAME_PREFIX + "John");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Joe");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Jim");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Samantha");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Adrienne");
        }
    }

    /**
     * Verify ContactUtil.filterPhone will work on full number matches.
     */
    public void ingoredTestFilterPhonesByFullNumber() {
        final String[] filters = new String[] {
                "6501231233", "650-123-1233", "(650)123-1233", "650 123 1233" };

        for (final String filter : filters) {
            final Cursor cur = ContactUtil.filterPhones(getContext(), filter)
                    .performSynchronousQuery();

            LogUtil.i(LogUtil.BUGLE_TAG, "testFilterPhonesByFullNumber: Number of phones:" +
                    cur.getCount());

            verifyCursorContains(cur, TEST_NAME_PREFIX + "John");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Joe");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Jim");
        }
    }

    /**
     * Verify ContactUtil.lookPhone will work on number including area code.
     */
    public void ingoredTestLookupPhoneWithAreaCode() {
        final String[] filters = new String[] {
                "6501231233", "650-123-1233", "(650)123-1233", "650 123 1233" };

        for (final String filter : filters) {
            final Cursor cur = ContactUtil.lookupPhone(getContext(), filter)
                    .performSynchronousQuery();

            LogUtil.i(LogUtil.BUGLE_TAG, "testLookupPhoneWithAreaCode: Number of phones:" +
                    cur.getCount());

            verifyCursorContains(cur, TEST_NAME_PREFIX + "John");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Joe");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Jim");
        }
    }

    /**
     * Verify ContactUtil.lookPhone will work on number without area code.
     */
    public void ingoredTestLookupPhoneWithoutAreaCode() {
        final String[] filters = new String[] {
                "1231233", "123-1233", "123 1233" };

        for (final String filter : filters) {
            final Cursor cur = ContactUtil.lookupPhone(getContext(), filter)
                    .performSynchronousQuery();

            LogUtil.i(LogUtil.BUGLE_TAG, "testLookupPhoneWithoutAreaCode: Number of phones:" +
                    cur.getCount());

            verifyCursorContains(cur, TEST_NAME_PREFIX + "John");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Joe");
            verifyCursorContains(cur, TEST_NAME_PREFIX + "Jim");
        }
    }

    public void ingoredTestGetFrequentPhones() {
        final Cursor cur = ContactUtil.getFrequentContacts(getContext())
                .performSynchronousQuery();

        LogUtil.i(LogUtil.BUGLE_TAG, "testGetFrequentPhones: Number of phones on the device:" +
                cur.getCount());

        verifyCursorContains(cur, TEST_NAME_PREFIX + "Samantha");
        verifyCursorContains(cur, TEST_NAME_PREFIX + "Adrienne");
    }

    /**
     * Verify ContactUtil.filterEmails will work on partial email.
     */
    public void ingoredTestFilterEmails() {
        final Cursor cur = ContactUtil.filterEmails(getContext(), "john@")
                .performSynchronousQuery();

        LogUtil.i(LogUtil.BUGLE_TAG, "testFilterEmails: Number of emails:" +
                cur.getCount());

        verifyCursorContains(cur, TEST_NAME_PREFIX + "John");
    }

    /**
     * Verify ContactUtil.lookupEmail will work on full email.
     */
    public void ingoredTestLookupEmail() {
        final Cursor cur = ContactUtil.lookupEmail(getContext(), "john@gmail.com")
                .performSynchronousQuery();

        LogUtil.i(LogUtil.BUGLE_TAG, "testLookupEmail: Number of emails:" +
                cur.getCount());

        verifyCursorContains(cur, TEST_NAME_PREFIX + "John");
    }

    /**
     * Utility method to check whether cursor contains a particular contact.
     */
    private void verifyCursorContains(final Cursor cursor, final String nameToVerify) {
        if (cursor.moveToFirst()) {
            do {
                final String name = cursor.getString(ContactUtil.INDEX_DISPLAY_NAME);
                if (TextUtils.equals(name, nameToVerify)) {
                    return;
                }
            } while (cursor.moveToNext());
        }
        Assert.fail("Cursor should have " + nameToVerify);
    }
}
