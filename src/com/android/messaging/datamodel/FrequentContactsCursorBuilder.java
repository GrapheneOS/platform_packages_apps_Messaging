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
import android.database.MatrixCursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.util.SimpleArrayMap;

import com.android.messaging.util.Assert;
import com.android.messaging.util.ContactUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * A cursor builder that takes the frequent contacts cursor and aggregate it with the all contacts
 * cursor to fill in contact details such as phone numbers and strip away invalid contacts.
 *
 * Because the frequent contact list depends on the loading of two cursors, it needs to temporarily
 * store the cursor that it receives with setFrequents() and setAllContacts() calls. Because it
 * doesn't know which one will be finished first, it always checks whether both cursors are ready
 * to pull data from and construct the aggregate cursor when it's ready to do so. Note that
 * this cursor builder doesn't assume ownership of the cursors passed in - it merely references
 * them and always does a isClosed() check before consuming them. The ownership still belongs to
 * the loader framework and the cursor may be closed when the UI is torn down.
 */
public class FrequentContactsCursorBuilder {
    private Cursor mAllContactsCursor;
    private Cursor mFrequentContactsCursor;

    /**
     * Sets the frequent contacts cursor as soon as it is loaded, or null if it's reset.
     * @return this builder instance for chained operations
     */
    public FrequentContactsCursorBuilder setFrequents(final Cursor frequentContactsCursor) {
        mFrequentContactsCursor = frequentContactsCursor;
        return this;
    }

    /**
     * Sets the all contacts cursor as soon as it is loaded, or null if it's reset.
     * @return this builder instance for chained operations
     */
    public FrequentContactsCursorBuilder setAllContacts(final Cursor allContactsCursor) {
        mAllContactsCursor = allContactsCursor;
        return this;
    }

    /**
     * Reset this builder. Must be called when the consumer resets its data.
     */
    public void resetBuilder() {
        mAllContactsCursor = null;
        mFrequentContactsCursor = null;
    }

    /**
     * Attempt to build the cursor records from the frequent and all contacts cursor if they
     * are both ready to be consumed.
     * @return the frequent contact cursor if built successfully, or null if it can't be built yet.
     */
    public Cursor build() {
        if (mFrequentContactsCursor != null && mAllContactsCursor != null) {
            Assert.isTrue(!mFrequentContactsCursor.isClosed());
            Assert.isTrue(!mAllContactsCursor.isClosed());

            // Frequent contacts cursor has one record per contact, plus it doesn't contain info
            // such as phone number and type. In order for the records to be usable by Bugle, we
            // would like to populate it with information from the all contacts cursor.
            final MatrixCursor retCursor = new MatrixCursor(ContactUtil.PhoneQuery.PROJECTION);

            // First, go through the frequents cursor and take note of all lookup keys and their
            // corresponding rank in the frequents list.
            final SimpleArrayMap<String, Integer> lookupKeyToRankMap =
                    new SimpleArrayMap<String, Integer>();
            int oldPosition = mFrequentContactsCursor.getPosition();
            int rank = 0;
            mFrequentContactsCursor.moveToPosition(-1);
            while (mFrequentContactsCursor.moveToNext()) {
                final String lookupKey = mFrequentContactsCursor.getString(
                        ContactUtil.INDEX_LOOKUP_KEY_FREQUENT);
                lookupKeyToRankMap.put(lookupKey, rank++);
            }
            mFrequentContactsCursor.moveToPosition(oldPosition);

            // Second, go through the all contacts cursor once and retrieve all information
            // (multiple phone numbers etc.) and store that in an array list. Since the all
            // contacts list only contains phone contacts, this step will ensure that we filter
            // out any invalid/email contacts in the frequents list.
            final ArrayList<Object[]> rows =
                    new ArrayList<Object[]>(mFrequentContactsCursor.getCount());
            oldPosition = mAllContactsCursor.getPosition();
            mAllContactsCursor.moveToPosition(-1);
            while (mAllContactsCursor.moveToNext()) {
                final String lookupKey = mAllContactsCursor.getString(ContactUtil.INDEX_LOOKUP_KEY);
                if (lookupKeyToRankMap.containsKey(lookupKey)) {
                    final Object[] row = new Object[ContactUtil.PhoneQuery.PROJECTION.length];
                    row[ContactUtil.INDEX_DATA_ID] =
                            mAllContactsCursor.getLong(ContactUtil.INDEX_DATA_ID);
                    row[ContactUtil.INDEX_CONTACT_ID] =
                            mAllContactsCursor.getLong(ContactUtil.INDEX_CONTACT_ID);
                    row[ContactUtil.INDEX_LOOKUP_KEY] =
                            mAllContactsCursor.getString(ContactUtil.INDEX_LOOKUP_KEY);
                    row[ContactUtil.INDEX_DISPLAY_NAME] =
                            mAllContactsCursor.getString(ContactUtil.INDEX_DISPLAY_NAME);
                    row[ContactUtil.INDEX_PHOTO_URI] =
                            mAllContactsCursor.getString(ContactUtil.INDEX_PHOTO_URI);
                    row[ContactUtil.INDEX_PHONE_EMAIL] =
                            mAllContactsCursor.getString(ContactUtil.INDEX_PHONE_EMAIL);
                    row[ContactUtil.INDEX_PHONE_EMAIL_TYPE] =
                            mAllContactsCursor.getInt(ContactUtil.INDEX_PHONE_EMAIL_TYPE);
                    row[ContactUtil.INDEX_PHONE_EMAIL_LABEL] =
                            mAllContactsCursor.getString(ContactUtil.INDEX_PHONE_EMAIL_LABEL);
                    rows.add(row);
                }
            }
            mAllContactsCursor.moveToPosition(oldPosition);

            // Now we have a list of rows containing frequent contacts in alphabetical order.
            // Therefore, sort all the rows according to their actual ranks in the frequents list.
            Collections.sort(rows, new Comparator<Object[]>() {
                @Override
                public int compare(final Object[] lhs, final Object[] rhs) {
                    final String lookupKeyLhs = (String) lhs[ContactUtil.INDEX_LOOKUP_KEY];
                    final String lookupKeyRhs = (String) rhs[ContactUtil.INDEX_LOOKUP_KEY];
                    Assert.isTrue(lookupKeyToRankMap.containsKey(lookupKeyLhs) &&
                            lookupKeyToRankMap.containsKey(lookupKeyRhs));
                    final int rankLhs = lookupKeyToRankMap.get(lookupKeyLhs);
                    final int rankRhs = lookupKeyToRankMap.get(lookupKeyRhs);
                    if (rankLhs < rankRhs) {
                        return -1;
                    } else if (rankLhs > rankRhs) {
                        return 1;
                    } else {
                        // Same rank, so it's two contact records for the same contact.
                        // Perform secondary sorting on the phone type. Always place
                        // mobile before everything else.
                        final int phoneTypeLhs = (int) lhs[ContactUtil.INDEX_PHONE_EMAIL_TYPE];
                        final int phoneTypeRhs = (int) rhs[ContactUtil.INDEX_PHONE_EMAIL_TYPE];
                        if (phoneTypeLhs == Phone.TYPE_MOBILE &&
                                phoneTypeRhs == Phone.TYPE_MOBILE) {
                            return 0;
                        } else if (phoneTypeLhs == Phone.TYPE_MOBILE) {
                            return -1;
                        } else if (phoneTypeRhs == Phone.TYPE_MOBILE) {
                            return 1;
                        } else {
                            // Use the default sort order, i.e. sort by phoneType value.
                            return phoneTypeLhs < phoneTypeRhs ? -1 :
                                    (phoneTypeLhs == phoneTypeRhs ? 0 : 1);
                        }
                    }
                }
            });

            // Finally, add all the rows to this cursor.
            for (final Object[] row : rows) {
                retCursor.addRow(row);
            }
            return retCursor;
        }
        return null;
    }
}
