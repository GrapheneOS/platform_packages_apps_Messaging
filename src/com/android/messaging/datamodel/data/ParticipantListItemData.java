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
package com.android.messaging.datamodel.data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.android.messaging.datamodel.action.BugleActionToasts;
import com.android.messaging.datamodel.action.UpdateDestinationBlockedAction;
import com.android.messaging.util.AvatarUriUtil;

/**
 * Helps visualize a ParticipantData in a PersonItemView
 */
public class ParticipantListItemData extends PersonItemData {
    private final Uri mAvatarUri;
    private final String mDisplayName;
    private final String mDetails;
    private final long mContactId;
    private final String mLookupKey;
    private final String mNormalizedDestination;

    /**
     * Constructor. Takes necessary info from the incoming ParticipantData.
     */
    public ParticipantListItemData(final ParticipantData participant) {
        mAvatarUri = AvatarUriUtil.createAvatarUri(participant);
        mContactId = participant.getContactId();
        mLookupKey = participant.getLookupKey();
        mNormalizedDestination = participant.getNormalizedDestination();
        if (TextUtils.isEmpty(participant.getFullName())) {
            mDisplayName = participant.getSendDestination();
            mDetails = null;
        } else {
            mDisplayName = participant.getFullName();
            mDetails = (participant.isUnknownSender()) ? null : participant.getSendDestination();
        }
    }

    @Override
    public Uri getAvatarUri() {
        return mAvatarUri;
    }

    @Override
    public String getDisplayName() {
        return mDisplayName;
    }

    @Override
    public String getDetails() {
        return mDetails;
    }

    @Override
    public Intent getClickIntent() {
        return null;
    }

    @Override
    public long getContactId() {
        return mContactId;
    }

    @Override
    public String getLookupKey() {
        return mLookupKey;
    }

    @Override
    public String getNormalizedDestination() {
        return mNormalizedDestination;
    }

    public void unblock(final Context context) {
        UpdateDestinationBlockedAction.updateDestinationBlocked(
                mNormalizedDestination, false, null,
                BugleActionToasts.makeUpdateDestinationBlockedActionListener(context));
    }
}
