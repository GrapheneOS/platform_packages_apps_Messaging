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

import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.widget.SectionIndexer;

import com.android.messaging.util.Assert;
import com.android.messaging.util.ContactUtil;
import com.android.messaging.util.LogUtil;

import java.util.ArrayList;

/**
 * Indexes contact alphabetical sections so we can report to the fast scrolling list view
 * where we are in the list when the user scrolls through the contact list, allowing us to show
 * alphabetical indicators for the fast scroller as well as list section headers.
 */
public class ContactSectionIndexer implements SectionIndexer {
    private String[] mSections;
    private ArrayList<Integer> mSectionStartingPositions;
    private static final String BLANK_HEADER_STRING = " ";

    public ContactSectionIndexer(final Cursor contactsCursor) {
        buildIndexer(contactsCursor);
    }

    @Override
    public Object[] getSections() {
        return mSections;
    }

    @Override
    public int getPositionForSection(final int sectionIndex) {
        if (mSectionStartingPositions.isEmpty()) {
            return 0;
        }
        // Clamp to the bounds of the section position array per Android API doc.
        return mSectionStartingPositions.get(
                Math.max(Math.min(sectionIndex, mSectionStartingPositions.size() - 1), 0));
    }

    @Override
    public int getSectionForPosition(final int position) {
        if (mSectionStartingPositions.isEmpty()) {
            return 0;
        }

        // Perform a binary search on the starting positions of the sections to the find the
        // section for the position.
        int left = 0;
        int right = mSectionStartingPositions.size() - 1;

        // According to getSectionForPosition()'s doc, we should always clamp the value when the
        // position is out of bound.
        if (position <= mSectionStartingPositions.get(left)) {
            return left;
        } else if (position >= mSectionStartingPositions.get(right)) {
            return right;
        }

        while (left <= right) {
            final int mid = (left + right) / 2;
            final int startingPos = mSectionStartingPositions.get(mid);
            final int nextStartingPos = mSectionStartingPositions.get(mid + 1);
            if (position >= startingPos && position < nextStartingPos) {
                return mid;
            } else if (position < startingPos) {
                right = mid - 1;
            } else if (position >= nextStartingPos) {
                left = mid + 1;
            }
        }
        Assert.fail("Invalid section indexer state: couldn't find section for pos " + position);
        return -1;
    }

    private boolean buildIndexerFromCursorExtras(final Cursor cursor) {
        if (cursor == null) {
            return false;
        }
        final Bundle cursorExtras = cursor.getExtras();
        if (cursorExtras == null) {
            return false;
        }
        final String[] sections = cursorExtras.getStringArray(
                Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
        final int[] counts = cursorExtras.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
        if (sections == null || counts == null) {
            return false;
        }

        if (sections.length != counts.length) {
            return false;
        }

        this.mSections = sections;
        mSectionStartingPositions = new ArrayList<Integer>(counts.length);
        int position = 0;
        for (int i = 0; i < counts.length; i++) {
            if (TextUtils.isEmpty(mSections[i])) {
                mSections[i] = BLANK_HEADER_STRING;
            } else if (!mSections[i].equals(BLANK_HEADER_STRING)) {
                mSections[i] = mSections[i].trim();
            }

            mSectionStartingPositions.add(position);
            position += counts[i];
        }
        return true;
    }

    private void buildIndexerFromDisplayNames(final Cursor cursor) {
        // Loop through the contact cursor and get the starting position for each first character.
        // The result is stored into two arrays, one for the section header (i.e. the first
        // character), and one for the starting position, which is guaranteed to be sorted in
        // ascending order.
        final ArrayList<String> sections = new ArrayList<String>();
        mSectionStartingPositions = new ArrayList<Integer>();
        if (cursor != null) {
            cursor.moveToPosition(-1);
            int currentPosition = 0;
            while (cursor.moveToNext()) {
                // The sort key is typically the contact's display name, so for example, a contact
                // named "Bob" will go into section "B". The Contacts provider generally uses a
                // a slightly more sophisticated heuristic, but as a fallback this is good enough.
                final String sortKey = cursor.getString(ContactUtil.INDEX_SORT_KEY);
                final String section = TextUtils.isEmpty(sortKey) ? BLANK_HEADER_STRING :
                    sortKey.substring(0, 1).toUpperCase();

                final int lastIndex = sections.size() - 1;
                final String currentSection = lastIndex >= 0 ? sections.get(lastIndex) : null;
                if (!TextUtils.equals(currentSection, section)) {
                    sections.add(section);
                    mSectionStartingPositions.add(currentPosition);
                }
                currentPosition++;
            }
        }
        mSections = new String[sections.size()];
        sections.toArray(mSections);
    }

    private void buildIndexer(final Cursor cursor) {
        // First check if we get indexer label extras from the contact provider; if not, fall back
        // to building from display names.
        if (!buildIndexerFromCursorExtras(cursor)) {
            LogUtil.w(LogUtil.BUGLE_TAG, "contact provider didn't provide contact label " +
                    "information, fall back to using display name!");
            buildIndexerFromDisplayNames(cursor);
        }
    }
}
