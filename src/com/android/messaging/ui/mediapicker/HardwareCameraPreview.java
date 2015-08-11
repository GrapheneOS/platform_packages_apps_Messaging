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
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;

import java.io.IOException;

/**
 * A hardware accelerated preview texture for the camera.  This is the preferred CameraPreview
 * because it animates smoother.  When hardware acceleration isn't available, SoftwareCameraPreview
 * is used.
 *
 * There is a significant amount of duplication between HardwareCameraPreview and
 * SoftwareCameraPreview which we can't easily share due to a lack of multiple inheritance, The
 * implementations of the shared methods are delegated to CameraPreview
 */
public class HardwareCameraPreview extends TextureView implements CameraPreview.CameraPreviewHost {
    private CameraPreview mPreview;

    public HardwareCameraPreview(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mPreview = new CameraPreview(this);
        setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surfaceTexture, final int i, final int i2) {
                CameraManager.get().setSurface(mPreview);
            }

            @Override
            public void onSurfaceTextureSizeChanged(final SurfaceTexture surfaceTexture, final int i, final int i2) {
                CameraManager.get().setSurface(mPreview);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(final SurfaceTexture surfaceTexture) {
                CameraManager.get().setSurface(null);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(final SurfaceTexture surfaceTexture) {
                CameraManager.get().setSurface(mPreview);
            }
        });
    }

    @Override
    protected void onVisibilityChanged(final View changedView, final int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        mPreview.onVisibilityChanged(visibility);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPreview.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mPreview.onAttachedToWindow();
    }

    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        super.onRestoreInstanceState(state);
        mPreview.onRestoreInstanceState();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        widthMeasureSpec = mPreview.getWidthMeasureSpec(widthMeasureSpec, heightMeasureSpec);
        heightMeasureSpec = mPreview.getHeightMeasureSpec(widthMeasureSpec, heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public boolean isValid() {
        return getSurfaceTexture() != null;
    }

    @Override
    public void startPreview(final Camera camera) throws IOException {
        camera.setPreviewTexture(getSurfaceTexture());
    }

    @Override
    public void onCameraPermissionGranted() {
        mPreview.onCameraPermissionGranted();
    }
}
