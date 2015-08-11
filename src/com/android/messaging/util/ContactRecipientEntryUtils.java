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

import android.net.Uri;
import android.provider.ContactsContract.DisplayNameSources;
import android.text.TextUtils;

import com.android.ex.chips.RecipientEntry;
import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.BugleRecipientEntry;
import com.android.messaging.datamodel.data.ParticipantData;

/**
 * Provides utility methods around creating RecipientEntry instance specific to Bugle's needs.
 */
public class ContactRecipientEntryUtils {
    /**
     * A special contact id for generated contacts with no display name (number only) and avatar.
     * By default, the chips UI doesn't load any avatar for chips with no display name, or where
     * the display name is the same as phone number (which is true for unknown contacts).
     * Since Bugle always generate a default avatar for all contacts, this is used to replace
     * those default generated chips with a phone number and no avatars.
     */
    private static final long CONTACT_ID_NUMBER_WITH_AVATAR = -1000;

    /**
     * A generated special contact which says "Send to xxx" in the contact list, which allows
     * a user to direct send an SMS to a number that was manually typed in.
     */
    private static final long CONTACT_ID_SENDTO_DESTINATION = -1001;

    /**
     * Construct a special "Send to xxx" entry for a given destination.
     */
    public static RecipientEntry constructSendToDestinationEntry(final String destination) {
        return constructSpecialRecipientEntry(destination, CONTACT_ID_SENDTO_DESTINATION);
    }

    /**
     * Construct a generated contact entry but with rendered avatar.
     */
    public static RecipientEntry constructNumberWithAvatarEntry(final String destination) {
        return constructSpecialRecipientEntry(destination, CONTACT_ID_NUMBER_WITH_AVATAR);
    }

    private static RecipientEntry constructSpecialRecipientEntry(final String destination,
            final long contactId) {
        // For the send-to-destination (e.g. "Send to xxx" in the auto-complete drop-down)
        // we want to show a default avatar with a static background so that it doesn't flicker
        // as the user types.
        final Uri avatarUri = contactId == CONTACT_ID_SENDTO_DESTINATION ?
                AvatarUriUtil.DEFAULT_BACKGROUND_AVATAR : null;
        return BugleRecipientEntry.constructTopLevelEntry(null, DisplayNameSources.STRUCTURED_NAME,
                destination, RecipientEntry.INVALID_DESTINATION_TYPE, null, contactId,
                null, contactId, avatarUri, true, null);
    }

    /**
     * Gets the display name for contact list only. For most cases this is the same as the normal
     * contact name, but there are cases where these two differ. For example, for the
     * send to typed number item, we'd like to show "Send to xxx" in the contact list. However,
     * when this item is actually added to the chips edit box, we would like to show just the
     * phone number (i.e. no display name).
     */
    public static String getDisplayNameForContactList(final RecipientEntry entry) {
        if (entry.getContactId() == CONTACT_ID_SENDTO_DESTINATION) {
            return Factory.get().getApplicationContext().getResources().getString(
                    R.string.contact_list_send_to_text, formatDestination(entry));
        } else if (!TextUtils.isEmpty(entry.getDisplayName())) {
            return entry.getDisplayName();
        } else {
            return formatDestination(entry);
        }
    }

    public static String formatDestination(final RecipientEntry entry) {
        return PhoneUtils.getDefault().formatForDisplay(entry.getDestination());
    }

    /**
     * Returns true if the given entry has only avatar and number
     */
    public static boolean isAvatarAndNumberOnlyContact(final RecipientEntry entry) {
        return entry.getContactId() == CONTACT_ID_NUMBER_WITH_AVATAR;
    }

    /**
     * Returns true if the given entry is a special send to number item.
     */
    public static boolean isSendToDestinationContact(final RecipientEntry entry) {
        return entry.getContactId() == CONTACT_ID_SENDTO_DESTINATION;
    }

    /**
     * Returns true if the given participant is a special send to number item.
     */
    public static boolean isSendToDestinationContact(final ParticipantData participant) {
        return participant.getContactId() == CONTACT_ID_SENDTO_DESTINATION;
    }
}
