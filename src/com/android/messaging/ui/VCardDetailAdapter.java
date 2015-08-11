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
package com.android.messaging.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;

import com.android.messaging.R;
import com.android.messaging.datamodel.media.VCardResourceEntry;
import com.android.messaging.datamodel.media.VCardResourceEntry.VCardResourceEntryDestinationItem;

import java.util.List;

/**
 * Displays a list of expandable contact cards shown in the VCardDetailActivity.
 */
public class VCardDetailAdapter extends BaseExpandableListAdapter {
    private final List<VCardResourceEntry> mVCards;
    private final LayoutInflater mInflater;

    public VCardDetailAdapter(final Context context, final List<VCardResourceEntry> vCards) {
        mVCards = vCards;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public Object getChild(final int groupPosition, final int childPosition) {
        return mVCards.get(groupPosition).getContactInfo().get(childPosition);
    }

    @Override
    public long getChildId(final int groupPosition, final int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(final int groupPosition, final int childPosition,
            final boolean isLastChild, final View convertView, final ViewGroup parent) {
        PersonItemView v;
        if (convertView == null) {
            v = instantiateView(parent);
        } else {
            v = (PersonItemView) convertView;
        }

        final VCardResourceEntryDestinationItem item = (VCardResourceEntryDestinationItem)
                getChild(groupPosition, childPosition);

        v.bind(item.getDisplayItem());
        return v;
    }

    @Override
    public int getChildrenCount(final int groupPosition) {
        return mVCards.get(groupPosition).getContactInfo().size();
    }

    @Override
    public Object getGroup(final int groupPosition) {
        return mVCards.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return mVCards.size();
    }

    @Override
    public long getGroupId(final int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(final int groupPosition, final boolean isExpanded,
            final View convertView, final ViewGroup parent) {
        PersonItemView v;
        if (convertView == null) {
            v = instantiateView(parent);
        } else {
            v = (PersonItemView) convertView;
        }

        final VCardResourceEntry item = (VCardResourceEntry) getGroup(groupPosition);
        v.bind(item.getDisplayItem());
        return v;
    }

    @Override
    public boolean isChildSelectable(final int groupPosition, final int childPosition) {
        return true;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private PersonItemView instantiateView(final ViewGroup parent) {
        final PersonItemView v = (PersonItemView) mInflater.inflate(R.layout.people_list_item_view,
                parent, false);
        v.setClickable(false);
        return v;
    }
}
