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

import android.graphics.Color;
import android.net.Uri;
import android.net.Uri.Builder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.messaging.datamodel.data.ParticipantData;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper utility for creating {@link android.net.Uri}s to describe what avatar to fetch or
 * generate and will help verify and extract information from avatar {@link android.net.Uri}s.
 *
 * There are three types of avatar {@link android.net.Uri}.
 *
 * 1) Group Avatars - These are avatars which are used to represent a group conversation. Group
 * avatars uris are basically multiple avatar uri which can be any of the below types but not
 * another group avatar. The group avatars can hold anywhere from two to four avatars uri and can
 * be in any of the following format
 * messaging://avatar/g?p=<avatarUri>&p=<avatarUri2>
 * messaging://avatar/g?p=<avatarUri>&p=<avatarUri2>&p=<avatarUri3>
 * messaging://avatar/g?p=<avatarUri>&p=<avatarUri2>&p=<avatarUri3>&p=<avatarUri4>
 *
 * 2) Local Resource - A local resource avatar is use when there is a profile photo for the
 * participant. This can be any local resource.
 *
 * 3) Letter Tile - A letter tile is used when a participant has a name but no profile photo. A
 * letter tile will contain the first code point of the participant's name and a background color
 * based on the hash of the participant's full name. Letter tiles will be in the following format.
 * messaging://avatar/l?n=<fullName>
 *
 * 4) Default Avatars - These are avatars are used when the participant has no profile photo or
 * name. In these cases we use the default person icon with a color background. The color
 * background is based on a hash of the normalized phone number.
 *
 * 5) Default Background Avatars - This is a special case for Default Avatars where we use the
 * default background color for the default avatar.
 *
 * 6) SIM Selector Avatars - These are avatars used in the SIM selector. This may either be a
 * regular local resource avatar (2) or an avatar with a SIM identifier (i.e. SIM background with
 * a letter or a slot number).
 */
public class AvatarUriUtil {
    private static final int MAX_GROUP_PARTICIPANTS = 4;

    public static final String TYPE_GROUP_URI = "g";
    public static final String TYPE_LOCAL_RESOURCE_URI = "r";
    public static final String TYPE_LETTER_TILE_URI = "l";
    public static final String TYPE_DEFAULT_URI = "d";
    public static final String TYPE_DEFAULT_BACKGROUND_URI = "b";
    public static final String TYPE_SIM_SELECTOR_URI = "s";

    private static final String SCHEME = "messaging";
    private static final String AUTHORITY = "avatar";
    private static final String PARAM_NAME = "n";
    private static final String PARAM_PRIMARY_URI = "m";
    private static final String PARAM_FALLBACK_URI = "f";
    private static final String PARAM_PARTICIPANT = "p";
    private static final String PARAM_IDENTIFIER = "i";
    private static final String PARAM_SIM_COLOR = "c";
    private static final String PARAM_SIM_SELECTED = "s";
    private static final String PARAM_SIM_INCOMING = "g";

    public static final Uri DEFAULT_BACKGROUND_AVATAR = new Uri.Builder().scheme(SCHEME)
            .authority(AUTHORITY).appendPath(TYPE_DEFAULT_BACKGROUND_URI).build();

    private static final Uri BLANK_SIM_INDICATOR_INCOMING_URI = createSimIconUri("",
            false /* selected */, Color.TRANSPARENT, true /* incoming */);
    private static final Uri BLANK_SIM_INDICATOR_OUTGOING_URI = createSimIconUri("",
            false /* selected */, Color.TRANSPARENT, false /* incoming */);

    /**
     * Creates an avatar uri based on a list of ParticipantData. The list of participants may not
     * be null or empty. Depending on the size of the list either a group avatar uri will be create
     * or an individual's avatar will be created. This will never return a null uri.
     */
    public static Uri createAvatarUri(@NonNull final List<ParticipantData> participants) {
        Assert.notNull(participants);
        Assert.isTrue(!participants.isEmpty());

        if (participants.size() == 1) {
            return createAvatarUri(participants.get(0));
        }

        final int numParticipants = Math.min(participants.size(), MAX_GROUP_PARTICIPANTS);
        final ArrayList<Uri> avatarUris = new ArrayList<Uri>(numParticipants);
        for (int i = 0; i < numParticipants; i++) {
            avatarUris.add(createAvatarUri(participants.get(i)));
        }
        return AvatarUriUtil.joinAvatarUriToGroup(avatarUris);
    }

    /**
     * Joins together a list of valid avatar uri into a group uri.The list of participants may not
     * be null or empty. If a lit of one is given then the first element will be return back
     * instead of a group avatar uri. All uris in the list must be a valid avatar uri. This will
     * never return a null uri.
     */
    public static Uri joinAvatarUriToGroup(@NonNull final List<Uri> avatarUris) {
        Assert.notNull(avatarUris);
        Assert.isTrue(!avatarUris.isEmpty());

        if (avatarUris.size() == 1) {
            final Uri firstAvatar = avatarUris.get(0);
            Assert.isTrue(AvatarUriUtil.isAvatarUri(firstAvatar));
            return firstAvatar;
        }

        final Builder builder = new Builder();
        builder.scheme(SCHEME);
        builder.authority(AUTHORITY);
        builder.appendPath(TYPE_GROUP_URI);
        final int numParticipants = Math.min(avatarUris.size(), MAX_GROUP_PARTICIPANTS);
        for (int i = 0; i < numParticipants; i++) {
            final Uri uri = avatarUris.get(i);
            Assert.notNull(uri);
            Assert.isTrue(UriUtil.isLocalResourceUri(uri) || AvatarUriUtil.isAvatarUri(uri));
            builder.appendQueryParameter(PARAM_PARTICIPANT, uri.toString());
        }
        return builder.build();
    }

    /**
     * Creates an avatar uri based on ParticipantData which may not be null and expected to have
     * profilePhotoUri, fullName and normalizedDestination populated. This will never return a null
     * uri.
     */
    public static Uri createAvatarUri(@NonNull final ParticipantData participant) {
        Assert.notNull(participant);
        final String photoUriString = participant.getProfilePhotoUri();
        final Uri profilePhotoUri = (photoUriString == null) ? null : Uri.parse(photoUriString);
        final String name = participant.getFullName();
        final String destination = participant.getNormalizedDestination();
        final String contactLookupKey = participant.getLookupKey();
        return createAvatarUri(profilePhotoUri, name, destination, contactLookupKey);
    }

    /**
     * Creates an avatar uri based on a the input data.
     */
    public static Uri createAvatarUri(final Uri profilePhotoUri, final CharSequence name,
            final String defaultIdentifier, final String contactLookupKey) {
        Uri generatedUri;
        if (!TextUtils.isEmpty(name) && isValidFirstCharacter(name)) {
            generatedUri = AvatarUriUtil.fromName(name, contactLookupKey);
        } else {
            final String identifier = TextUtils.isEmpty(contactLookupKey)
                    ? defaultIdentifier : contactLookupKey;
            generatedUri = AvatarUriUtil.fromIdentifier(identifier);
        }

        if (profilePhotoUri != null) {
            if (UriUtil.isLocalResourceUri(profilePhotoUri)) {
                return fromLocalResourceWithFallback(profilePhotoUri, generatedUri);
            } else {
                return profilePhotoUri;
            }
        } else {
            return generatedUri;
        }
    }

    public static boolean isValidFirstCharacter(final CharSequence name) {
        final char c = name.charAt(0);
        return c != '+';
    }

    /**
     * Creates an avatar URI used for the SIM selector.
     * @param participantData the self participant data for an <i>active</i> SIM
     * @param slotIdentifier when null, this will simply use a regular avatar; otherwise, the
     *        first letter of slotIdentifier will be used for the icon.
     * @param selected is this the currently selected SIM?
     * @param incoming is this for an incoming message or outgoing message?
     */
    public static Uri createAvatarUri(@NonNull final ParticipantData participantData,
            @Nullable final String slotIdentifier, final boolean selected, final boolean incoming) {
        Assert.notNull(participantData);
        Assert.isTrue(participantData.isActiveSubscription());
        Assert.isTrue(!TextUtils.isEmpty(slotIdentifier) ||
                !TextUtils.isEmpty(participantData.getProfilePhotoUri()));
        if (TextUtils.isEmpty(slotIdentifier)) {
            return createAvatarUri(participantData);
        }

        return createSimIconUri(slotIdentifier, selected, participantData.getSubscriptionColor(),
                incoming);
    }

    private static Uri createSimIconUri(final String slotIdentifier, final boolean selected,
            final int subColor, final boolean incoming) {
        final Builder builder = new Builder();
        builder.scheme(SCHEME);
        builder.authority(AUTHORITY);
        builder.appendPath(TYPE_SIM_SELECTOR_URI);
        builder.appendQueryParameter(PARAM_IDENTIFIER, slotIdentifier);
        builder.appendQueryParameter(PARAM_SIM_COLOR, String.valueOf(subColor));
        builder.appendQueryParameter(PARAM_SIM_SELECTED, String.valueOf(selected));
        builder.appendQueryParameter(PARAM_SIM_INCOMING, String.valueOf(incoming));
        return builder.build();
    }

    public static Uri getBlankSimIndicatorUri(final boolean incoming) {
        return incoming ? BLANK_SIM_INDICATOR_INCOMING_URI : BLANK_SIM_INDICATOR_OUTGOING_URI;
    }

    /**
     * Creates an avatar uri from the given local resource Uri, followed by a fallback Uri in case
     * the local resource one could not be loaded.
     */
    private static Uri fromLocalResourceWithFallback(@NonNull final Uri profilePhotoUri,
            @NonNull Uri fallbackUri) {
        Assert.notNull(profilePhotoUri);
        Assert.notNull(fallbackUri);
        final Builder builder = new Builder();
        builder.scheme(SCHEME);
        builder.authority(AUTHORITY);
        builder.appendPath(TYPE_LOCAL_RESOURCE_URI);
        builder.appendQueryParameter(PARAM_PRIMARY_URI, profilePhotoUri.toString());
        builder.appendQueryParameter(PARAM_FALLBACK_URI, fallbackUri.toString());
        return builder.build();
    }

    private static Uri fromName(@NonNull final CharSequence name, final String contactLookupKey) {
        Assert.notNull(name);
        final Builder builder = new Builder();
        builder.scheme(SCHEME);
        builder.authority(AUTHORITY);
        builder.appendPath(TYPE_LETTER_TILE_URI);
        final String nameString = String.valueOf(name);
        builder.appendQueryParameter(PARAM_NAME, nameString);
        final String identifier =
                TextUtils.isEmpty(contactLookupKey) ? nameString : contactLookupKey;
        builder.appendQueryParameter(PARAM_IDENTIFIER, identifier);
        return builder.build();
    }

    private static Uri fromIdentifier(@NonNull final String identifier) {
        final Builder builder = new Builder();
        builder.scheme(SCHEME);
        builder.authority(AUTHORITY);
        builder.appendPath(TYPE_DEFAULT_URI);
        builder.appendQueryParameter(PARAM_IDENTIFIER, identifier);
        return builder.build();
    }

    public static boolean isAvatarUri(@NonNull final Uri uri) {
        Assert.notNull(uri);
        return uri != null && TextUtils.equals(SCHEME, uri.getScheme()) &&
                TextUtils.equals(AUTHORITY, uri.getAuthority());
    }

    public static String getAvatarType(@NonNull final Uri uri) {
        Assert.notNull(uri);
        final List<String> path = uri.getPathSegments();
        return path.isEmpty() ? null : path.get(0);
    }

    public static String getIdentifier(@NonNull final Uri uri) {
        Assert.notNull(uri);
        return uri.getQueryParameter(PARAM_IDENTIFIER);
    }

    public static String getName(@NonNull final Uri uri) {
        Assert.notNull(uri);
        return uri.getQueryParameter(PARAM_NAME);
    }

    public static List<String> getGroupParticipantUris(@NonNull final Uri uri) {
        Assert.notNull(uri);
        return uri.getQueryParameters(PARAM_PARTICIPANT);
    }

    public static int getSimColor(@NonNull final Uri uri) {
        Assert.notNull(uri);
        return Integer.valueOf(uri.getQueryParameter(PARAM_SIM_COLOR));
    }

    public static boolean getSimSelected(@NonNull final Uri uri) {
        Assert.notNull(uri);
        return Boolean.valueOf(uri.getQueryParameter(PARAM_SIM_SELECTED));
    }

    public static boolean getSimIncoming(@NonNull final Uri uri) {
        Assert.notNull(uri);
        return Boolean.valueOf(uri.getQueryParameter(PARAM_SIM_INCOMING));
    }

    public static Uri getPrimaryUri(@NonNull final Uri uri) {
        Assert.notNull(uri);
        final String primaryUriString = uri.getQueryParameter(PARAM_PRIMARY_URI);
        return primaryUriString == null ? null : Uri.parse(primaryUriString);
    }

    public static Uri getFallbackUri(@NonNull final Uri uri) {
        Assert.notNull(uri);
        final String fallbackUriString = uri.getQueryParameter(PARAM_FALLBACK_URI);
        return fallbackUriString == null ? null : Uri.parse(fallbackUriString);
    }
}
