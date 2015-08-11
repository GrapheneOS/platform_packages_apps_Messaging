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

package com.android.messaging.ui.mediapicker;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.view.View;
import android.view.View.MeasureSpec;
import com.android.messaging.util.Assert;

import java.io.IOException;

/**
 * Contains shared code for SoftwareCameraPreview and HardwareCameraPreview, cannot use inheritance
 * because those classes must inherit from separate Views, so those classes delegate calls to this
 * helper class.  Specifics for each implementation are in CameraPreviewHost
 */
public class CameraPreview {
    public interface CameraPreviewHost {
        View getView();
        boolean isValid();
        void startPreview(final Camera camera) throws IOException;
        void onCameraPermissionGranted();

    }

    private int mCameraWidth = -1;
    private int mCameraHeight = -1;

    private final CameraPreviewHost mHost;

    public CameraPreview(final CameraPreviewHost host) {
        Assert.notNull(host);
        Assert.notNull(host.getView());
        mHost = host;
    }

    public void setSize(final Camera.Size size, final int orientation) {
        switch (orientation) {
            case 0:
            case 180:
                mCameraWidth = size.width;
                mCameraHeight = size.height;
                break;
            case 90:
            case 270:
            default:
                mCameraWidth = size.height;
                mCameraHeight = size.width;
        }
        mHost.getView().requestLayout();
    }

    public int getWidthMeasureSpec(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mCameraHeight >= 0) {
            final int width = View.MeasureSpec.getSize(widthMeasureSpec);
            return MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        } else {
            return widthMeasureSpec;
        }
    }

    public int getHeightMeasureSpec(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mCameraHeight >= 0) {
            final int orientation = getContext().getResources().getConfiguration().orientation;
            final int width = View.MeasureSpec.getSize(widthMeasureSpec);
            final float aspectRatio = (float) mCameraWidth / (float) mCameraHeight;
            int height;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                height = (int) (width * aspectRatio);
            } else {
                height = (int) (width / aspectRatio);
            }
            return View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        } else {
            return heightMeasureSpec;
        }
    }

    public void onVisibilityChanged(final int visibility) {
        if (CameraManager.hasCameraPermission()) {
            if (visibility == View.VISIBLE) {
                CameraManager.get().openCamera();
            } else {
                CameraManager.get().closeCamera();
            }
        }
    }

    public Context getContext() {
        return mHost.getView().getContext();
    }

    public void setOnTouchListener(final View.OnTouchListener listener) {
        mHost.getView().setOnTouchListener(listener);
    }

    public int getHeight() {
        return mHost.getView().getHeight();
    }

    public void onAttachedToWindow() {
        if (CameraManager.hasCameraPermission()) {
            CameraManager.get().openCamera();
        }
    }

    public void onDetachedFromWindow() {
        CameraManager.get().closeCamera();
    }

    public void onRestoreInstanceState() {
        if (CameraManager.hasCameraPermission()) {
            CameraManager.get().openCamera();
        }
    }

    public void onCameraPermissionGranted() {
        CameraManager.get().openCamera();
    }

    /**
     * @return True if the view is valid and prepared for the camera to start showing the preview
     */
    public boolean isValid() {
        return mHost.isValid();
    }

    /**
     * Starts the camera preview on the current surface.  Abstracts out the differences in API
     * from the CameraManager
     * @throws IOException Which is caught by the CameraManager to display an error
     */
    public void startPreview(final Camera camera) throws IOException {
        mHost.startPreview(camera);
    }
}
