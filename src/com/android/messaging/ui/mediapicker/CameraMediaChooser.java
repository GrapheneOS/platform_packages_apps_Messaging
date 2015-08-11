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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.Uri;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.Chronometer;
import android.widget.ImageButton;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.MediaPickerMessagePartData;
import com.android.messaging.ui.mediapicker.CameraManager.MediaCallback;
import com.android.messaging.ui.mediapicker.camerafocus.RenderOverlay;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.UiUtils;

/**
 * Chooser which allows the user to take pictures or video without leaving the current app/activity
 */
class CameraMediaChooser extends MediaChooser implements
        CameraManager.CameraManagerListener {
    private CameraPreview.CameraPreviewHost mCameraPreviewHost;
    private ImageButton mFullScreenButton;
    private ImageButton mSwapCameraButton;
    private ImageButton mSwapModeButton;
    private ImageButton mCaptureButton;
    private ImageButton mCancelVideoButton;
    private Chronometer mVideoCounter;
    private boolean mVideoCancelled;
    private int mErrorToast;
    private View mEnabledView;
    private View mMissingPermissionView;

    CameraMediaChooser(final MediaPicker mediaPicker) {
        super(mediaPicker);
    }

    @Override
    public int getSupportedMediaTypes() {
        if (CameraManager.get().hasAnyCamera()) {
            return MediaPicker.MEDIA_TYPE_IMAGE | MediaPicker.MEDIA_TYPE_VIDEO;
        } else {
            return MediaPicker.MEDIA_TYPE_NONE;
        }
    }

    @Override
    public View destroyView() {
        CameraManager.get().closeCamera();
        CameraManager.get().setListener(null);
        CameraManager.get().setSubscriptionDataProvider(null);
        return super.destroyView();
    }

    @Override
    protected View createView(final ViewGroup container) {
        CameraManager.get().setListener(this);
        CameraManager.get().setSubscriptionDataProvider(this);
        CameraManager.get().setVideoMode(false);
        final LayoutInflater inflater = getLayoutInflater();
        final CameraMediaChooserView view = (CameraMediaChooserView) inflater.inflate(
                R.layout.mediapicker_camera_chooser,
                container /* root */,
                false /* attachToRoot */);
        mCameraPreviewHost = (CameraPreview.CameraPreviewHost) view.findViewById(
                R.id.camera_preview);
        mCameraPreviewHost.getView().setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent motionEvent) {
                if (CameraManager.get().isVideoMode()) {
                    // Prevent the swipe down in video mode because video is always captured in
                    // full screen
                    return true;
                }

                return false;
            }
        });

        final View shutterVisual = view.findViewById(R.id.camera_shutter_visual);

        mFullScreenButton = (ImageButton) view.findViewById(R.id.camera_fullScreen_button);
        mFullScreenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                mMediaPicker.setFullScreen(true);
            }
        });

        mSwapCameraButton = (ImageButton) view.findViewById(R.id.camera_swapCamera_button);
        mSwapCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                CameraManager.get().swapCamera();
            }
        });

        mCaptureButton = (ImageButton) view.findViewById(R.id.camera_capture_button);
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final float heightPercent = Math.min(mMediaPicker.getViewPager().getHeight() /
                        (float) mCameraPreviewHost.getView().getHeight(), 1);

                if (CameraManager.get().isRecording()) {
                    CameraManager.get().stopVideo();
                } else {
                    final CameraManager.MediaCallback callback = new CameraManager.MediaCallback() {
                        @Override
                        public void onMediaReady(
                                final Uri uriToVideo, final String contentType,
                                final int width, final int height) {
                            mVideoCounter.stop();
                            if (mVideoCancelled || uriToVideo == null) {
                                mVideoCancelled = false;
                            } else {
                                final Rect startRect = new Rect();
                                // It's possible to throw out the chooser while taking the
                                // picture/video.  In that case, still use the attachment, just
                                // skip the startRect
                                if (mView != null) {
                                    mView.getGlobalVisibleRect(startRect);
                                }
                                mMediaPicker.dispatchItemsSelected(
                                        new MediaPickerMessagePartData(startRect, contentType,
                                                uriToVideo, width, height),
                                        true /* dismissMediaPicker */);
                            }
                            updateViewState();
                        }

                        @Override
                        public void onMediaFailed(final Exception exception) {
                            UiUtils.showToastAtBottom(R.string.camera_media_failure);
                            updateViewState();
                        }

                        @Override
                        public void onMediaInfo(final int what) {
                            if (what == MediaCallback.MEDIA_NO_DATA) {
                                UiUtils.showToastAtBottom(R.string.camera_media_failure);
                            }
                            updateViewState();
                        }
                    };
                    if (CameraManager.get().isVideoMode()) {
                        CameraManager.get().startVideo(callback);
                        mVideoCounter.setBase(SystemClock.elapsedRealtime());
                        mVideoCounter.start();
                        updateViewState();
                    } else {
                        showShutterEffect(shutterVisual);
                        CameraManager.get().takePicture(heightPercent, callback);
                        updateViewState();
                    }
                }
            }
        });

        mSwapModeButton = (ImageButton) view.findViewById(R.id.camera_swap_mode_button);
        mSwapModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                final boolean isSwitchingToVideo = !CameraManager.get().isVideoMode();
                if (isSwitchingToVideo && !OsUtil.hasRecordAudioPermission()) {
                    requestRecordAudioPermission();
                } else {
                    onSwapMode();
                }
            }
        });

        mCancelVideoButton = (ImageButton) view.findViewById(R.id.camera_cancel_button);
        mCancelVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                mVideoCancelled = true;
                CameraManager.get().stopVideo();
                mMediaPicker.dismiss(true);
            }
        });

        mVideoCounter = (Chronometer) view.findViewById(R.id.camera_video_counter);

        CameraManager.get().setRenderOverlay((RenderOverlay) view.findViewById(R.id.focus_visual));

        mEnabledView = view.findViewById(R.id.mediapicker_enabled);
        mMissingPermissionView = view.findViewById(R.id.missing_permission_view);

        // Must set mView before calling updateViewState because it operates on mView
        mView = view;
        updateViewState();
        updateForPermissionState(CameraManager.hasCameraPermission());
        return view;
    }

    @Override
    public int getIconResource() {
        return R.drawable.ic_camera_light;
    }

    @Override
    public int getIconDescriptionResource() {
        return R.string.mediapicker_cameraChooserDescription;
    }

    /**
     * Updates the view when entering or leaving full-screen camera mode
     * @param fullScreen
     */
    @Override
    void onFullScreenChanged(final boolean fullScreen) {
        super.onFullScreenChanged(fullScreen);
        if (!fullScreen && CameraManager.get().isVideoMode()) {
            CameraManager.get().setVideoMode(false);
        }
        updateViewState();
    }

    /**
     * Initializes the control to a default state when it is opened / closed
     * @param open True if the control is opened
     */
    @Override
    void onOpenedChanged(final boolean open) {
        super.onOpenedChanged(open);
        updateViewState();
    }

    @Override
    protected void setSelected(final boolean selected) {
        super.setSelected(selected);
        if (selected) {
            if (CameraManager.hasCameraPermission()) {
                // If an error occurred before the chooser was selected, show it now
                showErrorToastIfNeeded();
            } else {
                requestCameraPermission();
            }
        }
    }

    private void requestCameraPermission() {
        mMediaPicker.requestPermissions(new String[] { Manifest.permission.CAMERA },
                MediaPicker.CAMERA_PERMISSION_REQUEST_CODE);
    }

    private void requestRecordAudioPermission() {
        mMediaPicker.requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO },
                MediaPicker.RECORD_AUDIO_PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onRequestPermissionsResult(
            final int requestCode, final String permissions[], final int[] grantResults) {
        if (requestCode == MediaPicker.CAMERA_PERMISSION_REQUEST_CODE) {
            final boolean permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            updateForPermissionState(permissionGranted);
            if (permissionGranted) {
                mCameraPreviewHost.onCameraPermissionGranted();
            }
        } else if (requestCode == MediaPicker.RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            Assert.isFalse(CameraManager.get().isVideoMode());
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Switch to video mode
                onSwapMode();
            } else {
                // Stay in still-photo mode
            }
        }
    }

    private void updateForPermissionState(final boolean granted) {
        // onRequestPermissionsResult can sometimes get called before createView().
        if (mEnabledView == null) {
            return;
        }

        mEnabledView.setVisibility(granted ? View.VISIBLE : View.GONE);
        mMissingPermissionView.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean canSwipeDown() {
        if (CameraManager.get().isVideoMode()) {
            return true;
        }
        return super.canSwipeDown();
    }

    /**
     * Handles an error from the camera manager by showing the appropriate error message to the user
     * @param errorCode One of the CameraManager.ERROR_* constants
     * @param e The exception which caused the error, if any
     */
    @Override
    public void onCameraError(final int errorCode, final Exception e) {
        switch (errorCode) {
            case CameraManager.ERROR_OPENING_CAMERA:
            case CameraManager.ERROR_SHOWING_PREVIEW:
                mErrorToast = R.string.camera_error_opening;
                break;
            case CameraManager.ERROR_INITIALIZING_VIDEO:
                mErrorToast = R.string.camera_error_video_init_fail;
                updateViewState();
                break;
            case CameraManager.ERROR_STORAGE_FAILURE:
                mErrorToast = R.string.camera_error_storage_fail;
                updateViewState();
                break;
            case CameraManager.ERROR_TAKING_PICTURE:
                mErrorToast = R.string.camera_error_failure_taking_picture;
                break;
            default:
                mErrorToast = R.string.camera_error_unknown;
                LogUtil.w(LogUtil.BUGLE_TAG, "Unknown camera error:" + errorCode);
                break;
        }
        showErrorToastIfNeeded();
    }

    private void showErrorToastIfNeeded() {
        if (mErrorToast != 0 && mSelected) {
            UiUtils.showToastAtBottom(mErrorToast);
            mErrorToast = 0;
        }
    }

    @Override
    public void onCameraChanged() {
        updateViewState();
    }

    private void onSwapMode() {
        CameraManager.get().setVideoMode(!CameraManager.get().isVideoMode());
        if (CameraManager.get().isVideoMode()) {
            mMediaPicker.setFullScreen(true);

            // For now we start recording immediately
            mCaptureButton.performClick();
        }
        updateViewState();
    }

    private void showShutterEffect(final View shutterVisual) {
        final float maxAlpha = getContext().getResources().getFraction(
                R.fraction.camera_shutter_max_alpha, 1 /* base */, 1 /* pBase */);

        // Divide by 2 so each half of the animation adds up to the full duration
        final int animationDuration = getContext().getResources().getInteger(
                R.integer.camera_shutter_duration) / 2;

        final AnimationSet animation = new AnimationSet(false /* shareInterpolator */);
        final Animation alphaInAnimation = new AlphaAnimation(0.0f, maxAlpha);
        alphaInAnimation.setDuration(animationDuration);
        animation.addAnimation(alphaInAnimation);

        final Animation alphaOutAnimation = new AlphaAnimation(maxAlpha, 0.0f);
        alphaOutAnimation.setStartOffset(animationDuration);
        alphaOutAnimation.setDuration(animationDuration);
        animation.addAnimation(alphaOutAnimation);

        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(final Animation animation) {
                shutterVisual.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(final Animation animation) {
                shutterVisual.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(final Animation animation) {
            }
        });
        shutterVisual.startAnimation(animation);
    }

    /** Updates the state of the buttons and overlays based on the current state of the view */
    private void updateViewState() {
        if (mView == null) {
            return;
        }

        final Context context = getContext();
        if (context == null) {
            // Context is null if the fragment was already removed from the activity
            return;
        }
        final boolean fullScreen = mMediaPicker.isFullScreen();
        final boolean videoMode = CameraManager.get().isVideoMode();
        final boolean isRecording = CameraManager.get().isRecording();
        final boolean isCameraAvailable = isCameraAvailable();
        final Camera.CameraInfo cameraInfo = CameraManager.get().getCameraInfo();
        final boolean frontCamera = cameraInfo != null && cameraInfo.facing ==
                Camera.CameraInfo.CAMERA_FACING_FRONT;

        mView.setSystemUiVisibility(
                fullScreen ? View.SYSTEM_UI_FLAG_LOW_PROFILE :
                View.SYSTEM_UI_FLAG_VISIBLE);

        mFullScreenButton.setVisibility(!fullScreen ? View.VISIBLE : View.GONE);
        mFullScreenButton.setEnabled(isCameraAvailable);
        mSwapCameraButton.setVisibility(
                fullScreen && !isRecording && CameraManager.get().hasFrontAndBackCamera() ?
                        View.VISIBLE : View.GONE);
        mSwapCameraButton.setImageResource(frontCamera ?
                R.drawable.ic_camera_front_light :
                R.drawable.ic_camera_rear_light);
        mSwapCameraButton.setEnabled(isCameraAvailable);

        mCancelVideoButton.setVisibility(isRecording ? View.VISIBLE : View.GONE);
        mVideoCounter.setVisibility(isRecording ? View.VISIBLE : View.GONE);

        mSwapModeButton.setImageResource(videoMode ?
                R.drawable.ic_mp_camera_small_light :
                R.drawable.ic_mp_video_small_light);
        mSwapModeButton.setContentDescription(context.getString(videoMode ?
                R.string.camera_switch_to_still_mode : R.string.camera_switch_to_video_mode));
        mSwapModeButton.setVisibility(isRecording ? View.GONE : View.VISIBLE);
        mSwapModeButton.setEnabled(isCameraAvailable);

        if (isRecording) {
            mCaptureButton.setImageResource(R.drawable.ic_mp_capture_stop_large_light);
            mCaptureButton.setContentDescription(context.getString(
                    R.string.camera_stop_recording));
        } else if (videoMode) {
            mCaptureButton.setImageResource(R.drawable.ic_mp_video_large_light);
            mCaptureButton.setContentDescription(context.getString(
                    R.string.camera_start_recording));
        } else {
            mCaptureButton.setImageResource(R.drawable.ic_checkmark_large_light);
            mCaptureButton.setContentDescription(context.getString(
                    R.string.camera_take_picture));
        }
        mCaptureButton.setEnabled(isCameraAvailable);
    }

    @Override
    int getActionBarTitleResId() {
        return 0;
    }

    /**
     * Returns if the camera is currently ready camera is loaded and not taking a picture.
     * otherwise we should avoid taking another picture, swapping camera or recording video.
     */
    private boolean isCameraAvailable() {
        return CameraManager.get().isCameraAvailable();
    }
}
