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
import android.net.Uri;

import com.android.messaging.util.Assert;
import com.android.messaging.util.AvatarUriUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AvatarGroupRequestDescriptor extends CompositeImageRequestDescriptor {
    private static final int MAX_GROUP_SIZE = 4;

    public AvatarGroupRequestDescriptor(final Uri uri, final int desiredWidth,
            final int desiredHeight) {
        this(convertToDescriptor(uri, desiredWidth, desiredHeight), desiredWidth, desiredHeight);
    }

    public AvatarGroupRequestDescriptor(final List<? extends ImageRequestDescriptor> descriptors,
            final int desiredWidth, final int desiredHeight) {
        super(descriptors, desiredWidth, desiredHeight);
        Assert.isTrue(descriptors.size() <= MAX_GROUP_SIZE);
    }

    private static List<? extends ImageRequestDescriptor> convertToDescriptor(final Uri uri,
            final int desiredWidth, final int desiredHeight) {
        final List<String> participantUriStrings = AvatarUriUtil.getGroupParticipantUris(uri);
        final List<AvatarRequestDescriptor> avatarDescriptors =
                new ArrayList<AvatarRequestDescriptor>(participantUriStrings.size());
        for (final String uriString : participantUriStrings) {
            final AvatarRequestDescriptor descriptor = new AvatarRequestDescriptor(
                    Uri.parse(uriString), desiredWidth, desiredHeight);
            avatarDescriptors.add(descriptor);
        }
        return avatarDescriptors;
    }

    @Override
    public CompositeImageRequest<?> buildBatchImageRequest(final Context context) {
        return new CompositeImageRequest<AvatarGroupRequestDescriptor>(context, this);
    }

    @Override
    public List<RectF> getChildRequestTargetRects() {
        return Arrays.asList(generateDestRectArray());
    }

    /**
     * Generates an array of {@link RectF} which represents where each of the individual avatar
     * should be located in the final group avatar image. The location of each avatar depends on
     * the size of the group and the size of the overall group avatar size.
     */
    private RectF[] generateDestRectArray() {
        final int groupSize = mDescriptors.size();
        final float width = desiredWidth;
        final float height = desiredHeight;
        final float halfWidth = width / 2F;
        final float halfHeight = height / 2F;
        final RectF[] destArray = new RectF[groupSize];
        switch (groupSize) {
            case 2:
                /**
                 * +-------+
                 * | 0 |   |
                 * +-------+
                 * |   | 1 |
                 * +-------+
                 *
                 * We want two circles which touches in the center. To get this we know that the
                 * diagonal of the overall group avatar is squareRoot(2) * w We also know that the
                 * two circles touches the at the center of the overall group avatar and the
                 * distance from the center of the circle to the corner of the group avatar is
                 * radius * squareRoot(2). Therefore, the following emerges.
                 *
                 * w * squareRoot(2) = 2 (radius + radius * squareRoot(2))
                 * Solving for radius we get:
                 * d = 2 * radius = ( squareRoot(2) / (squareRoot(2) + 1)) * w
                 * d = (2 - squareRoot(2)) * w
                 */
                final float diameter = (float) ((2 - Math.sqrt(2)) * width);
                destArray[0] = new RectF(0, 0, diameter, diameter);
                destArray[1] = new RectF(width - diameter, height - diameter, width, height);
                break;
            case 3:
                /**
                 * +-------+
                 * | | 0 | |
                 * +-------+
                 * | 1 | 2 |
                 * +-------+
                 *   i0
                 *   |\
                 * a | \ c
                 *   --- i2
                 *    b
                 *
                 * a = radius * squareRoot(3) due to the triangle being a 30-60-90 right triangle.
                 * b = radius of circle
                 * c = 2 * radius of circle
                 *
                 * All three of the images are circles and therefore image zero will not touch
                 * image one or image two. Move image zero down so it touches image one and image
                 * two. This can be done by keeping image zero in the center and moving it down
                 * slightly. The amount to move down can be calculated by solving a right triangle.
                 * We know that the center x of image two to the center x of image zero is the
                 * radius of the circle, this is the length of edge b. Also we know that the
                 * distance from image zero to image two's center is 2 * radius, edge c. From this
                 * we know that the distance from center y of image two to center y of image one,
                 * edge a, is equal to radius * squareRoot(3) due to this triangle being a 30-60-90
                 * right triangle.
                 */
                final float quarterWidth = width / 4F;
                final float threeQuarterWidth = 3 * quarterWidth;
                final float radius = height / 4F;
                final float imageTwoCenterY = height - radius;
                final float lengthOfEdgeA = (float) (radius * Math.sqrt(3));
                final float imageZeroCenterY = imageTwoCenterY - lengthOfEdgeA;
                final float imageZeroTop = imageZeroCenterY - radius;
                final float imageZeroBottom = imageZeroCenterY + radius;
                destArray[0] = new RectF(
                        quarterWidth, imageZeroTop, threeQuarterWidth, imageZeroBottom);
                destArray[1] = new RectF(0, halfHeight, halfWidth, height);
                destArray[2] = new RectF(halfWidth, halfHeight, width, height);
                break;
            default:
                /**
                 * +-------+
                 * | 0 | 1 |
                 * +-------+
                 * | 2 | 3 |
                 * +-------+
                 */
                destArray[0] = new RectF(0, 0, halfWidth, halfHeight);
                destArray[1] = new RectF(halfWidth, 0, width, halfHeight);
                destArray[2] = new RectF(0, halfHeight, halfWidth, height);
                destArray[3] = new RectF(halfWidth, halfHeight, width, height);
                break;
        }
        return destArray;
    }
}
