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

import com.android.messaging.R;
import com.android.messaging.ui.CustomHeaderPagerListViewHolder;
import com.android.messaging.ui.contact.ContactListItemView.HostInterface;

/**
 * Holds the all contacts view for the contact picker's view pager.
 */
public class AllContactsListViewHolder extends CustomHeaderPagerListViewHolder {
    public AllContactsListViewHolder(final Context context, final HostInterface clivHostInterface) {
        super(context, new ContactListAdapter(context, null, clivHostInterface,
                true /* needAlphabetHeader */));
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.all_contacts_list_view;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.contact_picker_all_contacts_tab_title;
    }

    @Override
    protected int getEmptyViewResId() {
        return R.id.empty_view;
    }

    @Override
    protected int getListViewResId() {
        return R.id.all_contacts_list;
    }

    @Override
    protected int getEmptyViewTitleResId() {
        return R.string.contact_list_empty_text;
    }

    @Override
    protected int getEmptyViewImageResId() {
        return R.drawable.ic_oobe_freq_list;
    }
}
