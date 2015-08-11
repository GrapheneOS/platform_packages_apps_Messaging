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

import com.android.messaging.R;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.media.BindableMediaRequest;
import com.android.messaging.datamodel.media.MediaRequest;
import com.android.messaging.datamodel.media.MediaResourceManager;
import com.android.messaging.datamodel.media.MediaResourceManager.MediaResourceLoadListener;
import com.android.messaging.datamodel.media.VCardRequestDescriptor;
import com.android.messaging.datamodel.media.VCardResource;
import com.android.messaging.datamodel.media.VCardResourceEntry;
import com.android.messaging.util.Assert;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.ContactUtil;

import java.util.List;

/**
 * Data class for visualizing and loading data for a VCard contact.
 */
public class VCardContactItemData extends PersonItemData
        implements MediaResourceLoadListener<VCardResource> {
    private final Context mContext;
    private final Uri mVCardUri;
    private String mDetails;
    private final Binding<BindableMediaRequest<VCardResource>> mBinding =
            BindingBase.createBinding(this);
    private VCardResource mVCardResource;

    private static final Uri sDefaultAvatarUri =
            AvatarUriUtil.createAvatarUri(null, null, null, null);

    /**
     * Constructor. This parses data from the given MessagePartData describing the vcard
     */
    public VCardContactItemData(final Context context, final MessagePartData messagePartData) {
        this(context, messagePartData.getContentUri());
        Assert.isTrue(messagePartData.isVCard());
    }

    /**
     * Constructor. This parses data from the given VCard Uri
     */
    public VCardContactItemData(final Context context, final Uri vCardUri) {
        mContext = context;
        mDetails = mContext.getString(R.string.loading_vcard);
        mVCardUri = vCardUri;
    }

    @Override
    public Uri getAvatarUri() {
        if (hasValidVCard()) {
            final List<VCardResourceEntry> vcards = mVCardResource.getVCards();
            Assert.isTrue(vcards.size() > 0);
            if (vcards.size() == 1) {
                return vcards.get(0).getAvatarUri();
            }
        }
        return sDefaultAvatarUri;
    }

    @Override
    public String getDisplayName() {
        if (hasValidVCard()) {
            final List<VCardResourceEntry> vcards = mVCardResource.getVCards();
            Assert.isTrue(vcards.size() > 0);
            if (vcards.size() == 1) {
                return vcards.get(0).getDisplayName();
            } else {
                return mContext.getResources().getQuantityString(
                        R.plurals.vcard_multiple_display_name, vcards.size(), vcards.size());
            }
        }
        return null;
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

    public VCardResource getVCardResource() {
        return hasValidVCard() ? mVCardResource : null;
    }

    public Uri getVCardUri() {
        return hasValidVCard() ? mVCardUri : null;
    }

    public boolean hasValidVCard() {
        return isBound() && mVCardResource != null;
    }

    @Override
    public void bind(final String bindingId) {
        super.bind(bindingId);

        // Bind and request the VCard from media resource manager.
        mBinding.bind(new VCardRequestDescriptor(mVCardUri).buildAsyncMediaRequest(mContext, this));
        MediaResourceManager.get().requestMediaResourceAsync(mBinding.getData());
    }

    @Override
    public void unbind(final String bindingId) {
        super.unbind(bindingId);
        mBinding.unbind();
        if (mVCardResource != null) {
            mVCardResource.release();
            mVCardResource = null;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof VCardContactItemData)) {
            return false;
        }

        final VCardContactItemData lhs = (VCardContactItemData) o;
        return mVCardUri.equals(lhs.mVCardUri);
    }

    @Override
    public void onMediaResourceLoaded(final MediaRequest<VCardResource> request,
            final VCardResource resource, final boolean isCached) {
        Assert.isTrue(mVCardResource == null);
        mBinding.ensureBound();
        mDetails = mContext.getString(R.string.vcard_tap_hint);
        mVCardResource = resource;
        mVCardResource.addRef();
        notifyDataUpdated();
    }

    @Override
    public void onMediaResourceLoadError(final MediaRequest<VCardResource> request,
            final Exception exception) {
        mBinding.ensureBound();
        mDetails = mContext.getString(R.string.failed_loading_vcard);
        notifyDataFailed(exception);
    }
}
