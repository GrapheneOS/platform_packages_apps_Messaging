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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeContentProvider;
import com.android.messaging.FakeContext;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.DatabaseHelper.ParticipantColumns;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.ParticipantData.ParticipantsQuery;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.ContactUtil;

import org.junit.Assert;
import org.mockito.Mock;

/**
 * Utility class for testing ParticipantRefresh class for different scenarios.
 */
@SmallTest
public class ParticipantRefreshTest extends BugleTestCase {
    private FakeContext mContext;
    FakeFactory mFakeFactory;
    @Mock protected UIIntents mMockUIIntents;
    protected FakeDataModel mFakeDataModel;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mContext = new FakeContext(getTestContext());

        final ContentProvider provider = new MessagingContentProvider();
        provider.attachInfo(mContext, null);
        mContext.addContentProvider(MessagingContentProvider.AUTHORITY, provider);

        mFakeDataModel = new FakeDataModel(mContext);
        mFakeFactory = FakeFactory.registerWithFakeContext(getTestContext(), mContext)
                .withDataModel(mFakeDataModel)
                .withUIIntents(mMockUIIntents);
    }

    /**
     * Add some phonelookup result into take PhoneLookup content provider. This will be
     * used for doing phone lookup during participant refresh.
     */
    private void addPhoneLookup(final String phone, final Object[][] lookupResult) {
        final Uri uri = ContactUtil.lookupPhone(mContext, phone).getUri();
        final FakeContentProvider phoneLookup = new FakeContentProvider(mContext,
                uri, false);
        phoneLookup.addOverrideData(uri, null, null, ContactUtil.PhoneLookupQuery.PROJECTION,
                lookupResult);
        mFakeFactory.withProvider(uri, phoneLookup);
    }

    /**
     * Add some participant to test database.
     */
    private void addParticipant(final String normalizedDestination, final long contactId,
            final String name, final String photoUrl) {
        final DatabaseWrapper db = DataModel.get().getDatabase();
        final ContentValues values = new ContentValues();

        values.put(ParticipantColumns.NORMALIZED_DESTINATION, normalizedDestination);
        values.put(ParticipantColumns.CONTACT_ID, contactId);
        values.put(ParticipantColumns.FULL_NAME, name);
        values.put(ParticipantColumns.PROFILE_PHOTO_URI, photoUrl);

        db.beginTransaction();
        try {
            db.insert(DatabaseHelper.PARTICIPANTS_TABLE, null, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Verify that participant in the database has expected contacdtId, name and photoUrl fields.
     */
    private void verifyParticipant(final String normalizedDestination, final long contactId,
            final String name, final String photoUrl) {
        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();
        try {
            final String selection = ParticipantColumns.NORMALIZED_DESTINATION + "=?";
            final String[] selectionArgs = new String[] { normalizedDestination };

            final Cursor cursor = db.query(DatabaseHelper.PARTICIPANTS_TABLE,
                    ParticipantsQuery.PROJECTION, selection, selectionArgs, null, null, null);

            if (cursor == null || cursor.getCount() != 1) {
                Assert.fail("Should have participants for:" + normalizedDestination);
                return;
            }

            cursor.moveToFirst();
            final int currentContactId = cursor.getInt(ParticipantsQuery.INDEX_CONTACT_ID);
            final String currentName = cursor.getString(ParticipantsQuery.INDEX_FULL_NAME);
            final String currentPhotoUrl =
                    cursor.getString(ParticipantsQuery.INDEX_PROFILE_PHOTO_URI);
            if (currentContactId != contactId) {
                Assert.fail("Contact Id doesn't match. normalizedNumber=" + normalizedDestination +
                        " expected=" + contactId + " actual=" + currentContactId);
                return;
            }

            if (!TextUtils.equals(currentName, name)) {
                Assert.fail("Name doesn't match. normalizedNumber=" + normalizedDestination +
                        " expected=" + name + " actual=" + currentName);
                return;
            }

            if (!TextUtils.equals(currentPhotoUrl, photoUrl)) {
                Assert.fail("Contact Id doesn't match. normalizedNumber=" + normalizedDestination +
                        " expected=" + photoUrl + " actual=" + currentPhotoUrl);
                return;
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Verify that incremental refresh will resolve previously not resolved participants.
     */
    public void testIncrementalRefreshNotResolvedSingleMatch() {
        addParticipant("650-123-1233", ParticipantData.PARTICIPANT_CONTACT_ID_NOT_RESOLVED,
                null, null);
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_INCREMENTAL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that incremental refresh will resolve previously not resolved participants.
     */
    public void testIncrementalRefreshNotResolvedMultiMatch() {
        addParticipant("650-123-1233", ParticipantData.PARTICIPANT_CONTACT_ID_NOT_RESOLVED,
                null, null);
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null },
                { 2L, "Joe", "content://photo/joe", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_INCREMENTAL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that incremental refresh will not touch already-resolved participants.
     */
    public void testIncrementalRefreshResolvedSingleMatch() {
        addParticipant("650-123-1233", 1, "Joh", "content://photo/joh");
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_INCREMENTAL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that full refresh will correct already-resolved participants if needed
     */
    public void testFullRefreshResolvedSingleMatch() {
        addParticipant("650-123-1233", 1, "Joh", "content://photo/joh");
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_FULL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that incremental refresh will not touch participant that is marked as not found.
     */
    public void testIncrementalRefreshNotFound() {
        addParticipant("650-123-1233", ParticipantData.PARTICIPANT_CONTACT_ID_NOT_FOUND,
                null, null);
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_INCREMENTAL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that full refresh will resolve participant that is marked as not found.
     */
    public void testFullRefreshNotFound() {
        addParticipant("650-123-1233", ParticipantData.PARTICIPANT_CONTACT_ID_NOT_FOUND,
                null, null);
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_FULL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that refresh take consideration of current contact_id when having multiple matches.
     */
    public void testFullRefreshResolvedMultiMatch1() {
        addParticipant("650-123-1233", 1, "Joh", "content://photo/joh");
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null },
                { 2L, "Joe", "content://photo/joe", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_FULL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that refresh take consideration of current contact_id when having multiple matches.
     */
    public void testFullRefreshResolvedMultiMatch2() {
        addParticipant("650-123-1233", 2, "Joh", "content://photo/joh");
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null },
                { 2L, "Joe", "content://photo/joe", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_FULL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that refresh take first contact in case current contact_id no longer matches.
     */
    public void testFullRefreshResolvedMultiMatch3() {
        addParticipant("650-123-1233", 3, "Joh", "content://photo/joh");
        addPhoneLookup("650-123-1233", new Object[][] {
                { 1L, "John", "content://photo/john", "650-123-1233", null, null, null },
                { 2L, "Joe", "content://photo/joe", "650-123-1233", null, null, null }
        });

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_FULL);
        verifyParticipant("650-123-1233", 1, "John", "content://photo/john");
    }

    /**
     * Verify that refresh take first contact in case current contact_id no longer matches.
     */
    public void testFullRefreshResolvedBeforeButNotFoundNow() {
        addParticipant("650-123-1233", 1, "Joh", "content://photo/joh");
        addPhoneLookup("650-123-1233", new Object[][] {});

        ParticipantRefresh.refreshParticipants(ParticipantRefresh.REFRESH_MODE_FULL);
        verifyParticipant("650-123-1233", ParticipantData.PARTICIPANT_CONTACT_ID_NOT_FOUND,
                null, null);
    }
}
