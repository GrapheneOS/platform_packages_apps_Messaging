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

package com.android.messaging.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;

import com.android.messaging.util.exif.ExifInterface;

/**
 * A drawable that draws a bitmap in a flipped or rotated orientation without having to adjust the
 * bitmap
 */
public class OrientedBitmapDrawable extends BitmapDrawable {
    private final ExifInterface.OrientationParams mOrientationParams;
    private final Rect mDstRect;
    private int mCenterX;
    private int mCenterY;
    private boolean mApplyGravity;

    public static BitmapDrawable create(final int orientation, Resources res, Bitmap bitmap) {
        if (orientation <= ExifInterface.Orientation.TOP_LEFT) {
            // No need to adjust the bitmap, so just use a regular BitmapDrawable
            return new BitmapDrawable(res, bitmap);
        } else {
            // Create an oriented bitmap drawable
            return new OrientedBitmapDrawable(orientation, res, bitmap);
        }
    }

    private OrientedBitmapDrawable(final int orientation, Resources res, Bitmap bitmap) {
        super(res, bitmap);
        mOrientationParams = ExifInterface.getOrientationParams(orientation);
        mApplyGravity = true;
        mDstRect = new Rect();
    }

    @Override
    public int getIntrinsicWidth() {
        if (mOrientationParams.invertDimensions) {
            return super.getIntrinsicHeight();
        }
        return super.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        if (mOrientationParams.invertDimensions) {
            return super.getIntrinsicWidth();
        }
        return super.getIntrinsicHeight();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mApplyGravity = true;
    }

    @Override
    public void draw(Canvas canvas) {
        if (mApplyGravity) {
            Gravity.apply(getGravity(), getIntrinsicWidth(), getIntrinsicHeight(), getBounds(),
                    mDstRect);
            mCenterX = mDstRect.centerX();
            mCenterY = mDstRect.centerY();
            if (mOrientationParams.invertDimensions) {
                final Matrix matrix = new Matrix();
                matrix.setRotate(mOrientationParams.rotation, mCenterX, mCenterY);
                final RectF rotatedRect = new RectF(mDstRect);
                matrix.mapRect(rotatedRect);
                mDstRect.set((int) rotatedRect.left, (int) rotatedRect.top, (int) rotatedRect.right,
                        (int) rotatedRect.bottom);
            }

            mApplyGravity = false;
        }
        canvas.save();
        canvas.scale(mOrientationParams.scaleX, mOrientationParams.scaleY, mCenterX, mCenterY);
        canvas.rotate(mOrientationParams.rotation, mCenterX, mCenterY);
        canvas.drawBitmap(getBitmap(), (Rect) null, mDstRect, getPaint());
        canvas.restore();
    }
}
