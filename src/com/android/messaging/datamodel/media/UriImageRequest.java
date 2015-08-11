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

import android.content.Context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Serves local content URI based image requests.
 */
public class UriImageRequest<D extends UriImageRequestDescriptor> extends ImageRequest<D> {
    public UriImageRequest(final Context context, final D descriptor) {
        super(context, descriptor);
    }

    @Override
    protected InputStream getInputStreamForResource() throws FileNotFoundException {
        return mContext.getContentResolver().openInputStream(mDescriptor.uri);
    }

    @Override
    protected ImageResource loadMediaInternal(List<MediaRequest<ImageResource>> chainedTasks)
            throws IOException {
        final ImageResource resource = super.loadMediaInternal(chainedTasks);
        // Check if the caller asked for compression. If so, chain an encoding task if possible.
        if (mDescriptor.allowCompression && chainedTasks != null) {
            @SuppressWarnings("unchecked")
            final MediaRequest<ImageResource> chainedTask = (MediaRequest<ImageResource>)
                    resource.getMediaEncodingRequest(this);
            if (chainedTask != null) {
                chainedTasks.add(chainedTask);
                // Don't cache decoded image resource since we'll perform compression and cache
                // the compressed resource.
                if (resource instanceof DecodedImageResource) {
                    ((DecodedImageResource) resource).setCacheable(false);
                }
            }
        }
        return resource;
    }
}
