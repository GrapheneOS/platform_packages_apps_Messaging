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
package com.android.messaging.datamodel.media;

import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.datamodel.data.PersonItemData;
import com.android.messaging.util.ContactUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.SafeAsyncTask;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntry.EmailData;
import com.android.vcard.VCardEntry.ImData;
import com.android.vcard.VCardEntry.NoteData;
import com.android.vcard.VCardEntry.OrganizationData;
import com.android.vcard.VCardEntry.PhoneData;
import com.android.vcard.VCardEntry.PostalData;
import com.android.vcard.VCardEntry.WebsiteData;
import com.android.vcard.VCardProperty;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds one entry item (i.e. a single contact) within a VCard resource. It is able to take
 * a VCardEntry and extract relevant information from it.
 */
public class VCardResourceEntry {
    public static final String PROPERTY_KIND = "KIND";

    public static final String KIND_LOCATION = "location";

    private final List<VCardResourceEntry.VCardResourceEntryDestinationItem> mContactInfo;
    private final Uri mAvatarUri;
    private final String mDisplayName;
    private final CustomVCardEntry mVCard;

    public VCardResourceEntry(final CustomVCardEntry vcard, final Uri avatarUri) {
        mContactInfo = getContactInfoFromVCardEntry(vcard);
        mDisplayName = getDisplayNameFromVCardEntry(vcard);
        mAvatarUri = avatarUri;
        mVCard = vcard;
    }

    void close() {
        // If the avatar image was temporarily saved in the scratch folder, remove that.
        if (MediaScratchFileProvider.isMediaScratchSpaceUri(mAvatarUri)) {
            SafeAsyncTask.executeOnThreadPool(new Runnable() {
                @Override
                public void run() {
                    Factory.get().getApplicationContext().getContentResolver().delete(
                            mAvatarUri, null, null);
                }
            });
        }
    }

    public String getKind() {
        VCardProperty kindProperty = mVCard.getProperty(PROPERTY_KIND);
        return kindProperty == null ? null : kindProperty.getRawValue();
    }

    public Uri getAvatarUri() {
        return mAvatarUri;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getDisplayAddress() {
        List<PostalData> postalList = mVCard.getPostalList();
        if (postalList == null || postalList.size() < 1) {
            return null;
        }

        return formatAddress(postalList.get(0));
    }

    public String getNotes() {
        List<NoteData> notes = mVCard.getNotes();
        if (notes == null || notes.size() == 0) {
            return null;
        }
        StringBuilder noteBuilder = new StringBuilder();
        for (NoteData note : notes) {
            noteBuilder.append(note.getNote());
        }
        return noteBuilder.toString();
    }

    /**
     * Returns a UI-facing representation that can be bound and consumed by the UI layer to display
     * this VCard resource entry.
     */
    public PersonItemData getDisplayItem() {
        return new PersonItemData() {
            @Override
            public Uri getAvatarUri() {
                return VCardResourceEntry.this.getAvatarUri();
            }

            @Override
            public String getDisplayName() {
                return VCardResourceEntry.this.getDisplayName();
            }

            @Override
            public String getDetails() {
                return null;
            }

            @Override
            public Intent getClickIntent() {
                return null;
            }

            @Override
            public long getContactId() {
                return ContactUtil.INVALID_CONTACT_ID;
            }

            @Override
            public String getLookupKey() {
                return null;
            }

            @Override
            public String getNormalizedDestination() {
                return null;
            }
        };
    }

    public List<VCardResourceEntry.VCardResourceEntryDestinationItem> getContactInfo() {
        return mContactInfo;
    }

    private static List<VCardResourceEntryDestinationItem> getContactInfoFromVCardEntry(
            final VCardEntry vcard) {
        final Resources resources = Factory.get().getApplicationContext().getResources();
        final List<VCardResourceEntry.VCardResourceEntryDestinationItem> retList =
                new ArrayList<VCardResourceEntry.VCardResourceEntryDestinationItem>();
        if (vcard.getPhoneList() != null) {
            for (final PhoneData phone : vcard.getPhoneList()) {
                final Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + phone.getNumber()));
                retList.add(new VCardResourceEntryDestinationItem(phone.getNumber(),
                        Phone.getTypeLabel(resources, phone.getType(), phone.getLabel()).toString(),
                        intent));
            }
        }

        if (vcard.getEmailList() != null) {
            for (final EmailData email : vcard.getEmailList()) {
                final Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:"));
                intent.putExtra(Intent.EXTRA_EMAIL, new String[] { email.getAddress() });
                retList.add(new VCardResourceEntryDestinationItem(email.getAddress(),
                        Phone.getTypeLabel(resources, email.getType(),
                                email.getLabel()).toString(), intent));
            }
        }

        if (vcard.getPostalList() != null) {
            for (final PostalData postalData : vcard.getPostalList()) {
                String type;
                try {
                    type = resources.
                            getStringArray(android.R.array.postalAddressTypes)
                            [postalData.getType() - 1];
                } catch (final NotFoundException ex) {
                    type = resources.getStringArray(android.R.array.postalAddressTypes)[2];
                } catch (final Exception e) {
                    LogUtil.e(LogUtil.BUGLE_TAG, "createContactItem postal Exception:" + e);
                    type = resources.getStringArray(android.R.array.postalAddressTypes)[2];
                }
                Intent intent = new Intent(Intent.ACTION_VIEW);
                final String address = formatAddress(postalData);
                try {
                    intent.setData(Uri.parse("geo:0,0?q=" + URLEncoder.encode(address, "UTF-8")));
                } catch (UnsupportedEncodingException e) {
                    intent = null;
                }

                retList.add(new VCardResourceEntryDestinationItem(address, type, intent));
            }
        }

        if (vcard.getImList() != null) {
            for (final ImData imData : vcard.getImList()) {
                String type = null;
                try {
                    type = resources.
                            getString(Im.getProtocolLabelResource(imData.getProtocol()));
                } catch (final NotFoundException ex) {
                    // Do nothing since this implies an empty label.
                }
                retList.add(new VCardResourceEntryDestinationItem(imData.getAddress(), type, null));
            }
        }

        if (vcard.getOrganizationList() != null) {
            for (final OrganizationData organtization : vcard.getOrganizationList()) {
                String type = null;
                try {
                     type = resources.getString(Organization.getTypeLabelResource(
                                    organtization.getType()));
                } catch (final NotFoundException ex) {
                    //set other kind as "other"
                    type = resources.getStringArray(android.R.array.organizationTypes)[1];
                } catch (final Exception e) {
                    LogUtil.e(LogUtil.BUGLE_TAG, "createContactItem org Exception:" + e);
                    type = resources.getStringArray(android.R.array.organizationTypes)[1];
                }
                retList.add(new VCardResourceEntryDestinationItem(
                        organtization.getOrganizationName(), type, null));
            }
        }

        if (vcard.getWebsiteList() != null) {
            for (final WebsiteData web : vcard.getWebsiteList()) {
                if (web != null && TextUtils.isGraphic(web.getWebsite())){
                    String website = web.getWebsite();
                    if (!website.startsWith("http://") && !website.startsWith("https://")) {
                        // Prefix required for parsing to end up with a scheme and result in
                        // navigation
                        website = "http://" + website;
                    }
                    final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(website));
                    retList.add(new VCardResourceEntryDestinationItem(web.getWebsite(), null,
                            intent));
                }
            }
        }

        if (vcard.getBirthday() != null) {
             final String birthday = vcard.getBirthday();
             if (TextUtils.isGraphic(birthday)){
                 retList.add(new VCardResourceEntryDestinationItem(birthday,
                         resources.getString(R.string.vcard_detail_birthday_label), null));
             }
        }

        if (vcard.getNotes() != null) {
            for (final NoteData note : vcard.getNotes()) {
                 final ArrayMap<String, String> curChildMap = new ArrayMap<String, String>();
                 if (TextUtils.isGraphic(note.getNote())){
                     retList.add(new VCardResourceEntryDestinationItem(note.getNote(),
                             resources.getString(R.string.vcard_detail_notes_label), null));
                 }
             }
        }
        return retList;
    }

    private static String formatAddress(final PostalData postalData) {
        final StringBuilder sb = new StringBuilder();
        final String poBox = postalData.getPobox();
        if (!TextUtils.isEmpty(poBox)) {
            sb.append(poBox);
            sb.append(" ");
        }
        final String extendedAddress = postalData.getExtendedAddress();
        if (!TextUtils.isEmpty(extendedAddress)) {
            sb.append(extendedAddress);
            sb.append(" ");
        }
        final String street = postalData.getStreet();
        if (!TextUtils.isEmpty(street)) {
            sb.append(street);
            sb.append(" ");
        }
        final String localty = postalData.getLocalty();
        if (!TextUtils.isEmpty(localty)) {
            sb.append(localty);
            sb.append(" ");
        }
        final String region = postalData.getRegion();
        if (!TextUtils.isEmpty(region)) {
            sb.append(region);
            sb.append(" ");
        }
        final String postalCode = postalData.getPostalCode();
        if (!TextUtils.isEmpty(postalCode)) {
            sb.append(postalCode);
            sb.append(" ");
        }
        final String country = postalData.getCountry();
        if (!TextUtils.isEmpty(country)) {
            sb.append(country);
        }
        return sb.toString();
    }

    private static String getDisplayNameFromVCardEntry(final VCardEntry vcard) {
        String name = vcard.getDisplayName();
        if (name == null) {
            vcard.consolidateFields();
            name = vcard.getDisplayName();
        }
        return name;
    }

    /**
     * Represents one entry line (e.g. phone number and phone label) for a single contact. Each
     * VCardResourceEntry may hold one or more VCardResourceEntryDestinationItem's.
     */
    public static class VCardResourceEntryDestinationItem {
        private final String mDisplayDestination;
        private final String mDestinationType;
        private final Intent mClickIntent;
        public VCardResourceEntryDestinationItem(final String displayDestination,
                final String destinationType, final Intent clickIntent) {
            mDisplayDestination = displayDestination;
            mDestinationType = destinationType;
            mClickIntent = clickIntent;
        }

        /**
         * Returns a UI-facing representation that can be bound and consumed by the UI layer to
         * display this VCard resource destination entry.
         */
        public PersonItemData getDisplayItem() {
            return new PersonItemData() {
                @Override
                public Uri getAvatarUri() {
                    return null;
                }

                @Override
                public String getDisplayName() {
                    return mDisplayDestination;
                }

                @Override
                public String getDetails() {
                    return mDestinationType;
                }

                @Override
                public Intent getClickIntent() {
                    return mClickIntent;
                }

                @Override
                public long getContactId() {
                    return ContactUtil.INVALID_CONTACT_ID;
                }

                @Override
                public String getLookupKey() {
                    return null;
                }

                @Override
                public String getNormalizedDestination() {
                    return null;
                }
            };
        }
    }
}
