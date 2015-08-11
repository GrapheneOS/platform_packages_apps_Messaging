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

import android.net.Uri;
import android.text.TextUtils;

import com.android.ex.chips.RecipientEntry;

/**
 * An extension of RecipientEntry for Bugle's use since Bugle uses phone numbers to identify
 * participants / recipients instead of contact ids. This allows the user to send to multiple
 * phone numbers of the same contact.
 */
public class BugleRecipientEntry extends RecipientEntry {

    protected BugleRecipientEntry(final int entryType, final String displayName,
            final String destination, final int destinationType, final String destinationLabel,
            final long contactId, final Long directoryId, final long dataId,
            final Uri photoThumbnailUri, final boolean isFirstLevel, final boolean isValid,
            final String lookupKey) {
        super(entryType, displayName, destination, destinationType, destinationLabel, contactId,
                directoryId, dataId, photoThumbnailUri, isFirstLevel, isValid, lookupKey);
    }

    public static BugleRecipientEntry constructTopLevelEntry(final String displayName,
            final int displayNameSource, final String destination, final int destinationType,
            final String destinationLabel, final long contactId, final Long directoryId,
            final long dataId, final String thumbnailUriAsString, final boolean isValid,
            final String lookupKey) {
        return new BugleRecipientEntry(ENTRY_TYPE_PERSON, displayName, destination, destinationType,
                destinationLabel, contactId, directoryId, dataId, (thumbnailUriAsString != null
                ? Uri.parse(thumbnailUriAsString) : null), true, isValid, lookupKey);
    }

    public static BugleRecipientEntry constructSecondLevelEntry(final String displayName,
            final int displayNameSource, final String destination, final int destinationType,
            final String destinationLabel, final long contactId, final Long directoryId,
            final long dataId, final String thumbnailUriAsString, final boolean isValid,
            final String lookupKey) {
        return new BugleRecipientEntry(ENTRY_TYPE_PERSON, displayName, destination, destinationType,
                destinationLabel, contactId, directoryId, dataId, (thumbnailUriAsString != null
                ? Uri.parse(thumbnailUriAsString) : null), false, isValid, lookupKey);
    }

    @Override
    public boolean isSamePerson(final RecipientEntry entry) {
        return getDestination() != null && entry.getDestination() != null &&
                TextUtils.equals(getDestination(), entry.getDestination());
    }
}
