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
import android.net.Uri;

import com.android.ex.chips.PhotoManager;
import com.android.ex.chips.RecipientEntry;
import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.media.AvatarRequestDescriptor;
import com.android.messaging.datamodel.media.BindableMediaRequest;
import com.android.messaging.datamodel.media.ImageResource;
import com.android.messaging.datamodel.media.MediaRequest;
import com.android.messaging.datamodel.media.MediaResourceManager;
import com.android.messaging.datamodel.media.MediaResourceManager.MediaResourceLoadListener;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.ThreadUtil;

/**
 * An implementation of {@link PhotoManager} that hooks up the chips UI's photos with our own
 * {@link MediaResourceManager} for retrieving and caching contact avatars.
 */
public class ContactRecipientPhotoManager implements PhotoManager {
    private static final String IMAGE_BYTES_REQUEST_STATIC_BINDING_ID = "imagebytes";
    private final Context mContext;
    private final int mIconSize;
    private final ContactListItemView.HostInterface mClivHostInterface;

    public ContactRecipientPhotoManager(final Context context,
            final ContactListItemView.HostInterface clivHostInterface) {
        mContext = context;
        mIconSize = context.getResources().getDimensionPixelSize(
                R.dimen.compose_message_chip_height) - context.getResources().getDimensionPixelSize(
                        R.dimen.compose_message_chip_padding) * 2;
        mClivHostInterface = clivHostInterface;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void populatePhotoBytesAsync(final RecipientEntry entry,
            final PhotoManagerCallback callback) {
        // Post all media resource request to the main thread.
        ThreadUtil.getMainThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                final Uri avatarUri = AvatarUriUtil.createAvatarUri(
                        ParticipantData.getFromRecipientEntry(entry));
                final AvatarRequestDescriptor descriptor =
                        new AvatarRequestDescriptor(avatarUri, mIconSize, mIconSize);
                final BindableMediaRequest<ImageResource> req = descriptor.buildAsyncMediaRequest(
                        mContext,
                        new MediaResourceLoadListener<ImageResource>() {
                    @Override
                    public void onMediaResourceLoaded(final MediaRequest<ImageResource> request,
                            final ImageResource resource, final boolean isCached) {
                        entry.setPhotoBytes(resource.getBytes());
                        callback.onPhotoBytesAsynchronouslyPopulated();
                    }

                    @Override
                    public void onMediaResourceLoadError(final MediaRequest<ImageResource> request,
                            final Exception exception) {
                        LogUtil.e(LogUtil.BUGLE_TAG, "Photo bytes loading failed due to " +
                                exception + " request key=" + request.getKey());

                        // Fall back to the default avatar image.
                        callback.onPhotoBytesAsyncLoadFailed();
                    }});

                // Statically bind the request since it's not bound to any specific piece of UI.
                req.bind(IMAGE_BYTES_REQUEST_STATIC_BINDING_ID);

                Factory.get().getMediaResourceManager().requestMediaResourceAsync(req);
            }
        });
    }
}
