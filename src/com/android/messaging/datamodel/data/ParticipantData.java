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

import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.mms.MmsManager;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;

import com.android.ex.chips.RecipientEntry;
import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.ParticipantColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.sms.MmsSmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.TextUtil;

/**
 * A class that encapsulates all of the data for a specific participant in a conversation.
 */
public class ParticipantData implements Parcelable {
    // We always use -1 as default/invalid sub id although system may give us anything negative
    public static final int DEFAULT_SELF_SUB_ID = MmsManager.DEFAULT_SUB_ID;

    // This needs to be something apart from valid or DEFAULT_SELF_SUB_ID
    public static final int OTHER_THAN_SELF_SUB_ID = DEFAULT_SELF_SUB_ID - 1;

    // Active slot ids are non-negative. Using -1 to designate to inactive self participants.
    public static final int INVALID_SLOT_ID = -1;

    // TODO: may make sense to move this to common place?
    public static final long PARTICIPANT_CONTACT_ID_NOT_RESOLVED = -1;
    public static final long PARTICIPANT_CONTACT_ID_NOT_FOUND = -2;

    public static class ParticipantsQuery {
        public static final String[] PROJECTION = new String[] {
            ParticipantColumns._ID,
            ParticipantColumns.SUB_ID,
            ParticipantColumns.SIM_SLOT_ID,
            ParticipantColumns.NORMALIZED_DESTINATION,
            ParticipantColumns.SEND_DESTINATION,
            ParticipantColumns.DISPLAY_DESTINATION,
            ParticipantColumns.FULL_NAME,
            ParticipantColumns.FIRST_NAME,
            ParticipantColumns.PROFILE_PHOTO_URI,
            ParticipantColumns.CONTACT_ID,
            ParticipantColumns.LOOKUP_KEY,
            ParticipantColumns.BLOCKED,
            ParticipantColumns.SUBSCRIPTION_COLOR,
            ParticipantColumns.SUBSCRIPTION_NAME,
            ParticipantColumns.CONTACT_DESTINATION,
        };

        public static final int INDEX_ID                        = 0;
        public static final int INDEX_SUB_ID                    = 1;
        public static final int INDEX_SIM_SLOT_ID               = 2;
        public static final int INDEX_NORMALIZED_DESTINATION    = 3;
        public static final int INDEX_SEND_DESTINATION          = 4;
        public static final int INDEX_DISPLAY_DESTINATION       = 5;
        public static final int INDEX_FULL_NAME                 = 6;
        public static final int INDEX_FIRST_NAME                = 7;
        public static final int INDEX_PROFILE_PHOTO_URI         = 8;
        public static final int INDEX_CONTACT_ID                = 9;
        public static final int INDEX_LOOKUP_KEY                = 10;
        public static final int INDEX_BLOCKED                   = 11;
        public static final int INDEX_SUBSCRIPTION_COLOR        = 12;
        public static final int INDEX_SUBSCRIPTION_NAME         = 13;
        public static final int INDEX_CONTACT_DESTINATION       = 14;
    }

    /**
     * @return The MMS unknown sender participant entity
     */
    public static String getUnknownSenderDestination() {
        // This is a hard coded string rather than a localized one because we don't want it to
        // change when you change locale.
        return "\u02BCUNKNOWN_SENDER!\u02BC";
    }

    private String mParticipantId;
    private int mSubId;
    private int mSlotId;
    private String mNormalizedDestination;
    private String mSendDestination;
    private String mDisplayDestination;
    private String mContactDestination;
    private String mFullName;
    private String mFirstName;
    private String mProfilePhotoUri;
    private long mContactId;
    private String mLookupKey;
    private int mSubscriptionColor;
    private String mSubscriptionName;
    private boolean mIsEmailAddress;
    private boolean mBlocked;

    // Don't call constructor directly
    private ParticipantData() {
    }

    public static ParticipantData getFromCursor(final Cursor cursor) {
        final ParticipantData pd = new ParticipantData();
        pd.mParticipantId = cursor.getString(ParticipantsQuery.INDEX_ID);
        pd.mSubId = cursor.getInt(ParticipantsQuery.INDEX_SUB_ID);
        pd.mSlotId = cursor.getInt(ParticipantsQuery.INDEX_SIM_SLOT_ID);
        pd.mNormalizedDestination = cursor.getString(
                ParticipantsQuery.INDEX_NORMALIZED_DESTINATION);
        pd.mSendDestination = cursor.getString(ParticipantsQuery.INDEX_SEND_DESTINATION);
        pd.mDisplayDestination = cursor.getString(ParticipantsQuery.INDEX_DISPLAY_DESTINATION);
        pd.mContactDestination = cursor.getString(ParticipantsQuery.INDEX_CONTACT_DESTINATION);
        pd.mFullName = cursor.getString(ParticipantsQuery.INDEX_FULL_NAME);
        pd.mFirstName = cursor.getString(ParticipantsQuery.INDEX_FIRST_NAME);
        pd.mProfilePhotoUri = cursor.getString(ParticipantsQuery.INDEX_PROFILE_PHOTO_URI);
        pd.mContactId = cursor.getLong(ParticipantsQuery.INDEX_CONTACT_ID);
        pd.mLookupKey = cursor.getString(ParticipantsQuery.INDEX_LOOKUP_KEY);
        pd.mIsEmailAddress = MmsSmsUtils.isEmailAddress(pd.mSendDestination);
        pd.mBlocked = cursor.getInt(ParticipantsQuery.INDEX_BLOCKED) != 0;
        pd.mSubscriptionColor = cursor.getInt(ParticipantsQuery.INDEX_SUBSCRIPTION_COLOR);
        pd.mSubscriptionName = cursor.getString(ParticipantsQuery.INDEX_SUBSCRIPTION_NAME);
        pd.maybeSetupUnknownSender();
        return pd;
    }

    public static ParticipantData getFromId(final DatabaseWrapper dbWrapper,
            final String participantId) {
        Cursor cursor = null;
        try {
            cursor = dbWrapper.query(DatabaseHelper.PARTICIPANTS_TABLE,
                    ParticipantsQuery.PROJECTION,
                    ParticipantColumns._ID + " =?",
                    new String[] { participantId }, null, null, null);

            if (cursor.moveToFirst()) {
                return ParticipantData.getFromCursor(cursor);
            } else {
                return null;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static ParticipantData getFromRecipientEntry(final RecipientEntry recipientEntry) {
        final ParticipantData pd = new ParticipantData();
        pd.mParticipantId = null;
        pd.mSubId = OTHER_THAN_SELF_SUB_ID;
        pd.mSlotId = INVALID_SLOT_ID;
        pd.mSendDestination = TextUtil.replaceUnicodeDigits(recipientEntry.getDestination());
        pd.mIsEmailAddress = MmsSmsUtils.isEmailAddress(pd.mSendDestination);
        pd.mNormalizedDestination = pd.mIsEmailAddress ?
                pd.mSendDestination :
                PhoneUtils.getDefault().getCanonicalBySystemLocale(pd.mSendDestination);
        pd.mDisplayDestination = pd.mIsEmailAddress ?
                pd.mNormalizedDestination :
                PhoneUtils.getDefault().formatForDisplay(pd.mNormalizedDestination);
        pd.mFullName = recipientEntry.getDisplayName();
        pd.mFirstName = null;
        pd.mProfilePhotoUri = (recipientEntry.getPhotoThumbnailUri() == null) ? null :
                recipientEntry.getPhotoThumbnailUri().toString();
        pd.mContactId = recipientEntry.getContactId();
        if (pd.mContactId < 0) {
            // ParticipantData only supports real contact ids (>=0) based on faith that the contacts
            // provider will continue to only use non-negative ids.  The UI uses contactId < 0 for
            // special handling. We convert those to 'not resolved'
            pd.mContactId = PARTICIPANT_CONTACT_ID_NOT_RESOLVED;
        }
        pd.mLookupKey = recipientEntry.getLookupKey();
        pd.mBlocked = false;
        pd.mSubscriptionColor = Color.TRANSPARENT;
        pd.mSubscriptionName = null;
        pd.maybeSetupUnknownSender();
        return pd;
    }

    // Shared code for getFromRawPhoneBySystemLocale and getFromRawPhoneBySimLocale
    private static ParticipantData getFromRawPhone(final String phoneNumber) {
        Assert.isTrue(phoneNumber != null);
        final ParticipantData pd = new ParticipantData();
        pd.mParticipantId = null;
        pd.mSubId = OTHER_THAN_SELF_SUB_ID;
        pd.mSlotId = INVALID_SLOT_ID;
        pd.mSendDestination = TextUtil.replaceUnicodeDigits(phoneNumber);
        pd.mIsEmailAddress = MmsSmsUtils.isEmailAddress(pd.mSendDestination);
        pd.mFullName = null;
        pd.mFirstName = null;
        pd.mProfilePhotoUri = null;
        pd.mContactId = PARTICIPANT_CONTACT_ID_NOT_RESOLVED;
        pd.mLookupKey = null;
        pd.mBlocked = false;
        pd.mSubscriptionColor = Color.TRANSPARENT;
        pd.mSubscriptionName = null;
        return pd;
    }

    /**
     * Get an instance from a raw phone number and using system locale to normalize it.
     *
     * Use this when creating a participant that is for displaying UI and not associated
     * with a specific SIM. For example, when creating a conversation using user entered
     * phone number.
     *
     * @param phoneNumber The raw phone number
     * @return instance
     */
    public static ParticipantData getFromRawPhoneBySystemLocale(final String phoneNumber) {
        final ParticipantData pd = getFromRawPhone(phoneNumber);
        pd.mNormalizedDestination = pd.mIsEmailAddress ?
                pd.mSendDestination :
                PhoneUtils.getDefault().getCanonicalBySystemLocale(pd.mSendDestination);
        pd.mDisplayDestination = pd.mIsEmailAddress ?
                pd.mNormalizedDestination :
                PhoneUtils.getDefault().formatForDisplay(pd.mNormalizedDestination);
        pd.maybeSetupUnknownSender();
        return pd;
    }

    /**
     * Get an instance from a raw phone number and using SIM or system locale to normalize it.
     *
     * Use this when creating a participant that is associated with a specific SIM. For example,
     * the sender of a received message or the recipient of a sending message that is already
     * targeted at a specific SIM.
     *
     * @param phoneNumber The raw phone number
     * @return instance
     */
    public static ParticipantData getFromRawPhoneBySimLocale(
            final String phoneNumber, final int subId) {
        final ParticipantData pd = getFromRawPhone(phoneNumber);
        pd.mNormalizedDestination = pd.mIsEmailAddress ?
                pd.mSendDestination :
                PhoneUtils.get(subId).getCanonicalBySimLocale(pd.mSendDestination);
        pd.mDisplayDestination = pd.mIsEmailAddress ?
                pd.mNormalizedDestination :
                PhoneUtils.getDefault().formatForDisplay(pd.mNormalizedDestination);
        pd.maybeSetupUnknownSender();
        return pd;
    }

    public static ParticipantData getSelfParticipant(final int subId) {
        Assert.isTrue(subId != OTHER_THAN_SELF_SUB_ID);
        final ParticipantData pd = new ParticipantData();
        pd.mParticipantId = null;
        pd.mSubId = subId;
        pd.mSlotId = INVALID_SLOT_ID;
        pd.mIsEmailAddress = false;
        pd.mSendDestination = null;
        pd.mNormalizedDestination = null;
        pd.mDisplayDestination = null;
        pd.mFullName = null;
        pd.mFirstName = null;
        pd.mProfilePhotoUri = null;
        pd.mContactId = PARTICIPANT_CONTACT_ID_NOT_RESOLVED;
        pd.mLookupKey = null;
        pd.mBlocked = false;
        pd.mSubscriptionColor = Color.TRANSPARENT;
        pd.mSubscriptionName = null;
        return pd;
    }

    private void maybeSetupUnknownSender() {
        if (isUnknownSender()) {
            // Because your locale may change, we setup the display string for the unknown sender
            // on the fly rather than relying on the version in the database.
            final Resources resources = Factory.get().getApplicationContext().getResources();
            mDisplayDestination = resources.getString(R.string.unknown_sender);
            mFullName = mDisplayDestination;
        }
    }

    public String getNormalizedDestination() {
        return mNormalizedDestination;
    }

    public String getSendDestination() {
        return mSendDestination;
    }

    public String getDisplayDestination() {
        return mDisplayDestination;
    }

    public String getContactDestination() {
        return mContactDestination;
    }

    public String getFullName() {
        return mFullName;
    }

    public String getFirstName() {
        return mFirstName;
    }

    public String getDisplayName(final boolean preferFullName) {
        if (preferFullName) {
            // Prefer full name over first name
            if (!TextUtils.isEmpty(mFullName)) {
                return mFullName;
            }
            if (!TextUtils.isEmpty(mFirstName)) {
                return mFirstName;
            }
        } else {
            // Prefer first name over full name
            if (!TextUtils.isEmpty(mFirstName)) {
                return mFirstName;
            }
            if (!TextUtils.isEmpty(mFullName)) {
                return mFullName;
            }
        }

        // Fallback to the display destination
        if (!TextUtils.isEmpty(mDisplayDestination)) {
            return mDisplayDestination;
        }

        return Factory.get().getApplicationContext().getResources().getString(
                R.string.unknown_sender);
    }

    public String getProfilePhotoUri() {
        return mProfilePhotoUri;
    }

    public long getContactId() {
        return mContactId;
    }

    public String getLookupKey() {
        return mLookupKey;
    }

    public boolean updatePhoneNumberForSelfIfChanged() {
        final String phoneNumber =
                PhoneUtils.get(mSubId).getCanonicalForSelf(true/*allowOverride*/);
        boolean changed = false;
        if (isSelf() && !TextUtils.equals(phoneNumber, mNormalizedDestination)) {
            mNormalizedDestination = phoneNumber;
            mSendDestination = phoneNumber;
            mDisplayDestination = mIsEmailAddress ?
                    phoneNumber :
                    PhoneUtils.getDefault().formatForDisplay(phoneNumber);
            changed = true;
        }
        return changed;
    }

    public boolean updateSubscriptionInfoForSelfIfChanged(final SubscriptionInfo subscriptionInfo) {
        boolean changed = false;
        if (isSelf()) {
            if (subscriptionInfo == null) {
                // The subscription is inactive. Check if the participant is still active.
                if (isActiveSubscription()) {
                    mSlotId = INVALID_SLOT_ID;
                    mSubscriptionColor = Color.TRANSPARENT;
                    mSubscriptionName = "";
                    changed = true;
                }
            } else {
                final int slotId = subscriptionInfo.getSimSlotIndex();
                final int color = subscriptionInfo.getIconTint();
                final CharSequence name = subscriptionInfo.getDisplayName();
                if (mSlotId != slotId || mSubscriptionColor != color || mSubscriptionName != name) {
                    mSlotId = slotId;
                    mSubscriptionColor = color;
                    mSubscriptionName = name.toString();
                    changed = true;
                }
            }
        }
        return changed;
    }

    public void setFullName(final String fullName) {
        mFullName = fullName;
    }

    public void setFirstName(final String firstName) {
        mFirstName = firstName;
    }

    public void setProfilePhotoUri(final String profilePhotoUri) {
        mProfilePhotoUri = profilePhotoUri;
    }

    public void setContactId(final long contactId) {
        mContactId = contactId;
    }

    public void setLookupKey(final String lookupKey) {
        mLookupKey = lookupKey;
    }

    public void setSendDestination(final String destination) {
        mSendDestination = destination;
    }

    public void setContactDestination(final String destination) {
        mContactDestination = destination;
    }

    public int getSubId() {
        return mSubId;
    }

    /**
     * @return whether this sub is active. Note that {@link ParticipantData#DEFAULT_SELF_SUB_ID} is
     *         is considered as active if there is any active SIM.
     */
    public boolean isActiveSubscription() {
        return mSlotId != INVALID_SLOT_ID;
    }

    public boolean isDefaultSelf() {
        return mSubId == ParticipantData.DEFAULT_SELF_SUB_ID;
    }

    public int getSlotId() {
        return mSlotId;
    }

    /**
     * Slot IDs in the subscription manager is zero-based, but we want to show it
     * as 1-based in UI.
     */
    public int getDisplaySlotId() {
        return getSlotId() + 1;
    }

    public int getSubscriptionColor() {
        Assert.isTrue(isActiveSubscription());
        // Force the alpha channel to 0xff to ensure the returned color is solid.
        return mSubscriptionColor | 0xff000000;
    }

    public String getSubscriptionName() {
        Assert.isTrue(isActiveSubscription());
        return mSubscriptionName;
    }

    public String getId() {
        return mParticipantId;
    }

    public boolean isSelf() {
        return (mSubId != OTHER_THAN_SELF_SUB_ID);
    }

    public boolean isEmail() {
        return mIsEmailAddress;
    }

    public boolean isContactIdResolved() {
        return (mContactId != PARTICIPANT_CONTACT_ID_NOT_RESOLVED);
    }

    public boolean isBlocked() {
        return mBlocked;
    }

    public boolean isUnknownSender() {
        final String unknownSender = ParticipantData.getUnknownSenderDestination();
        return (TextUtils.equals(mSendDestination, unknownSender));
    }

    public ContentValues toContentValues() {
        final ContentValues values = new ContentValues();
        values.put(ParticipantColumns.SUB_ID, mSubId);
        values.put(ParticipantColumns.SIM_SLOT_ID, mSlotId);
        values.put(DatabaseHelper.ParticipantColumns.SEND_DESTINATION, mSendDestination);

        if (!isUnknownSender()) {
            values.put(DatabaseHelper.ParticipantColumns.DISPLAY_DESTINATION, mDisplayDestination);
            values.put(DatabaseHelper.ParticipantColumns.NORMALIZED_DESTINATION,
                    mNormalizedDestination);
            values.put(ParticipantColumns.FULL_NAME, mFullName);
            values.put(ParticipantColumns.FIRST_NAME, mFirstName);
        }

        values.put(ParticipantColumns.PROFILE_PHOTO_URI, mProfilePhotoUri);
        values.put(ParticipantColumns.CONTACT_ID, mContactId);
        values.put(ParticipantColumns.LOOKUP_KEY, mLookupKey);
        values.put(ParticipantColumns.BLOCKED, mBlocked);
        values.put(ParticipantColumns.SUBSCRIPTION_COLOR, mSubscriptionColor);
        values.put(ParticipantColumns.SUBSCRIPTION_NAME, mSubscriptionName);
        return values;
    }

    public ParticipantData(final Parcel in) {
        mParticipantId = in.readString();
        mSubId = in.readInt();
        mSlotId = in.readInt();
        mNormalizedDestination = in.readString();
        mSendDestination = in.readString();
        mDisplayDestination = in.readString();
        mFullName = in.readString();
        mFirstName = in.readString();
        mProfilePhotoUri = in.readString();
        mContactId = in.readLong();
        mLookupKey = in.readString();
        mIsEmailAddress = in.readInt() != 0;
        mBlocked = in.readInt() != 0;
        mSubscriptionColor = in.readInt();
        mSubscriptionName = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(mParticipantId);
        dest.writeInt(mSubId);
        dest.writeInt(mSlotId);
        dest.writeString(mNormalizedDestination);
        dest.writeString(mSendDestination);
        dest.writeString(mDisplayDestination);
        dest.writeString(mFullName);
        dest.writeString(mFirstName);
        dest.writeString(mProfilePhotoUri);
        dest.writeLong(mContactId);
        dest.writeString(mLookupKey);
        dest.writeInt(mIsEmailAddress ? 1 : 0);
        dest.writeInt(mBlocked ? 1 : 0);
        dest.writeInt(mSubscriptionColor);
        dest.writeString(mSubscriptionName);
    }

    public static final Parcelable.Creator<ParticipantData> CREATOR
    = new Parcelable.Creator<ParticipantData>() {
        @Override
        public ParticipantData createFromParcel(final Parcel in) {
            return new ParticipantData(in);
        }

        @Override
        public ParticipantData[] newArray(final int size) {
            return new ParticipantData[size];
        }
    };
}
