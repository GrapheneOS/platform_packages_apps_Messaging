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
import android.net.Uri;

import com.android.messaging.util.Assert;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.UriUtil;

public class AvatarRequestDescriptor extends UriImageRequestDescriptor {
    final boolean isWearBackground;

    public AvatarRequestDescriptor(final Uri uri, final int desiredWidth,
            final int desiredHeight) {
        this(uri, desiredWidth, desiredHeight, true /* cropToCircle */);
    }

    public AvatarRequestDescriptor(final Uri uri, final int desiredWidth,
            final int desiredHeight, final boolean cropToCircle) {
        this(uri, desiredWidth, desiredHeight, cropToCircle, false /* isWearBackground */);
    }

    public AvatarRequestDescriptor(final Uri uri, final int desiredWidth,
            final int desiredHeight, boolean cropToCircle, boolean isWearBackground) {
        super(uri, desiredWidth, desiredHeight, false /* allowCompression */, true /* isStatic */,
                cropToCircle,
                ImageUtils.DEFAULT_CIRCLE_BACKGROUND_COLOR /* circleBackgroundColor */,
                ImageUtils.DEFAULT_CIRCLE_STROKE_COLOR /* circleStrokeColor */);
        Assert.isTrue(uri == null || UriUtil.isLocalResourceUri(uri) ||
                AvatarUriUtil.isAvatarUri(uri));
        this.isWearBackground = isWearBackground;
    }

    @Override
    public MediaRequest<ImageResource> buildSyncMediaRequest(final Context context) {
        final String avatarType = uri == null ? null : AvatarUriUtil.getAvatarType(uri);
        if (AvatarUriUtil.TYPE_SIM_SELECTOR_URI.equals(avatarType)) {
            return new SimSelectorAvatarRequest(context, this);
        } else {
            return new AvatarRequest(context, this);
        }
    }
}
