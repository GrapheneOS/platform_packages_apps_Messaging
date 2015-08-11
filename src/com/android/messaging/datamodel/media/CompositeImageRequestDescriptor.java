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
import android.graphics.RectF;

import com.google.common.base.Joiner;

import java.util.List;

public abstract class CompositeImageRequestDescriptor extends ImageRequestDescriptor {
    protected final List<? extends ImageRequestDescriptor> mDescriptors;
    private final String mKey;

    public CompositeImageRequestDescriptor(final List<? extends ImageRequestDescriptor> descriptors,
            final int desiredWidth, final int desiredHeight) {
        super(desiredWidth, desiredHeight);
        mDescriptors = descriptors;

        final String[] keyParts = new String[descriptors.size()];
        for (int i = 0; i < descriptors.size(); i++) {
            keyParts[i] = descriptors.get(i).getKey();
        }
        mKey = Joiner.on(",").skipNulls().join(keyParts);
    }

    /**
     * Gets a key that uniquely identify all the underlying image resource to be loaded (e.g. Uri or
     * file path).
     */
    @Override
    public String getKey() {
        return mKey;
    }

    public List<? extends ImageRequestDescriptor> getChildRequestDescriptors(){
        return mDescriptors;
    }

    public abstract List<RectF> getChildRequestTargetRects();
    public abstract CompositeImageRequest<?> buildBatchImageRequest(final Context context);

    @Override
    public MediaRequest<ImageResource> buildSyncMediaRequest(final Context context) {
        return buildBatchImageRequest(context);
    }
}
