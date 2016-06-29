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
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.support.v4.text.BidiFormatter;
import android.support.v4.text.TextDirectionHeuristicsCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.ex.chips.DropdownChipLayouter;
import com.android.ex.chips.RecipientEntry;
import com.android.messaging.R;
import com.android.messaging.datamodel.data.ContactListItemData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.ui.ContactIconView;
import com.android.messaging.util.Assert;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.ContactRecipientEntryUtils;
import com.android.messaging.util.ContactUtil;

/**
 * An implementation for {@link DropdownChipLayouter}. Layouts the dropdown
 * list in the ContactRecipientAutoCompleteView in Material style.
 */
public class ContactDropdownLayouter extends DropdownChipLayouter {
    private final ContactListItemView.HostInterface mClivHostInterface;

    public ContactDropdownLayouter(final LayoutInflater inflater, final Context context,
            final ContactListItemView.HostInterface clivHostInterface) {
        super(inflater, context);
        mClivHostInterface = new ContactListItemView.HostInterface() {

            @Override
            public void onContactListItemClicked(final ContactListItemData item,
                    final ContactListItemView view) {
                // The chips UI will handle auto-complete item click events, so No-op here.
            }

            @Override
            public boolean isContactSelected(final ContactListItemData item) {
                // In chips drop down we don't show any selected checkmark per design.
                return false;
            }
        };
    }

    /**
     * Bind a drop down view to a RecipientEntry. We'd like regular dropdown items (BASE_RECIPIENT)
     * to behave the same as regular ContactListItemViews, while using the chips library's
     * item styling for alternates dropdown items (happens when you click on a chip).
     */
    @Override
    public View bindView(final View convertView, final ViewGroup parent, final RecipientEntry entry,
            final int position, AdapterType type, final String substring,
            final StateListDrawable deleteDrawable) {
        if (type != AdapterType.BASE_RECIPIENT) {
            if (type == AdapterType.SINGLE_RECIPIENT) {
                // Treat single recipients the same way as alternates. The base implementation of
                // single recipients would try to simplify the destination by tokenizing. We'd
                // like to always show the full destination address per design request.
                type = AdapterType.RECIPIENT_ALTERNATES;
            }
            return super.bindView(convertView, parent, entry, position, type, substring,
                    deleteDrawable);
        }

        // Default to show all the information
        // RTL : To format contact name and detail if they happen to be phone numbers.
        final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        final String displayName = bidiFormatter.unicodeWrap(
                ContactRecipientEntryUtils.getDisplayNameForContactList(entry),
                TextDirectionHeuristicsCompat.LTR);
        final String destination = bidiFormatter.unicodeWrap(
                ContactRecipientEntryUtils.formatDestination(entry),
                TextDirectionHeuristicsCompat.LTR);
        final View itemView = reuseOrInflateView(convertView, parent, type);

        // Bold the string that is matched.
        final CharSequence[] styledResults =
                getStyledResults(substring, displayName, destination);

        Assert.isTrue(itemView instanceof ContactListItemView);
        final ContactListItemView contactListItemView = (ContactListItemView) itemView;
        contactListItemView.setImageClickHandlerDisabled(true);
        boolean isWorkContact = ContactUtil.isEnterpriseContactId(entry.getContactId());
        contactListItemView.bind(entry, styledResults[0], styledResults[1],
                mClivHostInterface, (type == AdapterType.SINGLE_RECIPIENT), isWorkContact);
        return itemView;
    }

    @Override
    protected void bindIconToView(boolean showImage, RecipientEntry entry, ImageView view,
            AdapterType type) {
        if (showImage && view instanceof ContactIconView) {
            final ContactIconView contactView = (ContactIconView) view;
            // These show contact cards by default, but that isn't what we want here
            contactView.setImageClickHandlerDisabled(true);
            final Uri avatarUri = AvatarUriUtil.createAvatarUri(
                    ParticipantData.getFromRecipientEntry(entry));
            contactView.setImageResourceUri(avatarUri);
        } else {
            super.bindIconToView(showImage, entry, view, type);
        }
    }

    @Override
    protected int getItemLayoutResId(AdapterType type) {
        switch (type) {
            case BASE_RECIPIENT:
                return R.layout.contact_list_item_view;
            case RECIPIENT_ALTERNATES:
                return R.layout.chips_alternates_dropdown_item;
            default:
                return R.layout.chips_alternates_dropdown_item;
        }
    }

    @Override
    protected int getAlternateItemLayoutResId(AdapterType type) {
        return getItemLayoutResId(type);
    }
}
