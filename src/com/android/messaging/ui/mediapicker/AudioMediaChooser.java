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
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.util.OsUtil;

/**
 * Chooser which allows the user to record audio
 */
class AudioMediaChooser extends MediaChooser implements
        AudioRecordView.HostInterface {
    private View mEnabledView;
    private View mMissingPermissionView;

    AudioMediaChooser(final MediaPicker mediaPicker) {
        super(mediaPicker);
    }

    @Override
    public int getSupportedMediaTypes() {
        return MediaPicker.MEDIA_TYPE_AUDIO;
    }

    @Override
    public int getIconResource() {
        return R.drawable.ic_audio_light;
    }

    @Override
    public int getIconDescriptionResource() {
        return R.string.mediapicker_audioChooserDescription;
    }

    @Override
    public void onAudioRecorded(final MessagePartData item) {
        mMediaPicker.dispatchItemsSelected(item, true);
    }

    @Override
    public void setThemeColor(final int color) {
        if (mView != null) {
            ((AudioRecordView) mView).setThemeColor(color);
        }
    }

    @Override
    protected View createView(final ViewGroup container) {
        final LayoutInflater inflater = getLayoutInflater();
        final AudioRecordView view = (AudioRecordView) inflater.inflate(
                R.layout.mediapicker_audio_chooser,
                container /* root */,
                false /* attachToRoot */);
        view.setHostInterface(this);
        view.setThemeColor(mMediaPicker.getConversationThemeColor());
        mEnabledView = view.findViewById(R.id.mediapicker_enabled);
        mMissingPermissionView = view.findViewById(R.id.missing_permission_view);
        return view;
    }

    @Override
    int getActionBarTitleResId() {
        return R.string.mediapicker_audio_title;
    }

    @Override
    public boolean isHandlingTouch() {
        // Whenever the user is in the process of recording audio, we want to allow the user
        // to move the finger within the panel without interpreting that as dragging the media
        // picker panel.
        return ((AudioRecordView) mView).shouldHandleTouch();
    }

    @Override
    public void stopTouchHandling() {
        ((AudioRecordView) mView).stopTouchHandling();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mView != null) {
            ((AudioRecordView) mView).onPause();
        }
    }

    @Override
    protected void setSelected(final boolean selected) {
        super.setSelected(selected);
        if (selected && !OsUtil.hasRecordAudioPermission()) {
            requestRecordAudioPermission();
        }
    }

    private void requestRecordAudioPermission() {
        mMediaPicker.requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO },
                MediaPicker.RECORD_AUDIO_PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onRequestPermissionsResult(
            final int requestCode, final String permissions[], final int[] grantResults) {
        if (requestCode == MediaPicker.RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            final boolean permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            mEnabledView.setVisibility(permissionGranted ? View.VISIBLE : View.GONE);
            mMissingPermissionView.setVisibility(permissionGranted ? View.GONE : View.VISIBLE);
        }
    }
}
