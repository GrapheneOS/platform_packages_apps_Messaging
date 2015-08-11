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
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.SectionIndexer;

import com.android.messaging.R;
import com.android.messaging.util.Assert;

public class ContactListAdapter extends CursorAdapter implements SectionIndexer {
    private final ContactListItemView.HostInterface mClivHostInterface;
    private final boolean mNeedAlphabetHeader;
    private ContactSectionIndexer mSectionIndexer;

    public ContactListAdapter(final Context context, final Cursor cursor,
            final ContactListItemView.HostInterface clivHostInterface,
            final boolean needAlphabetHeader) {
        super(context, cursor, 0);
        mClivHostInterface = clivHostInterface;
        mNeedAlphabetHeader = needAlphabetHeader;
        mSectionIndexer = new ContactSectionIndexer(cursor);
    }

    @Override
    public void bindView(final View view, final Context context, final Cursor cursor) {
        Assert.isTrue(view instanceof ContactListItemView);
        final ContactListItemView contactListItemView = (ContactListItemView) view;
        String alphabetHeader = null;
        if (mNeedAlphabetHeader) {
            final int position = cursor.getPosition();
            final int section = mSectionIndexer.getSectionForPosition(position);
            // Check if the position is the first in the section.
            if (mSectionIndexer.getPositionForSection(section) == position) {
                alphabetHeader = (String) mSectionIndexer.getSections()[section];
            }
        }
        contactListItemView.bind(cursor, mClivHostInterface, mNeedAlphabetHeader, alphabetHeader);
    }

    @Override
    public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
        final LayoutInflater layoutInflater = LayoutInflater.from(context);
        return layoutInflater.inflate(R.layout.contact_list_item_view, parent, false);
    }

    @Override
    public Cursor swapCursor(final Cursor newCursor) {
        mSectionIndexer = new ContactSectionIndexer(newCursor);
        return super.swapCursor(newCursor);
    }

    @Override
    public Object[] getSections() {
        return mSectionIndexer.getSections();
    }

    @Override
    public int getPositionForSection(final int sectionIndex) {
        return mSectionIndexer.getPositionForSection(sectionIndex);
    }

    @Override
    public int getSectionForPosition(final int position) {
        return mSectionIndexer.getSectionForPosition(position);
    }
}
