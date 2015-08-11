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

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.SafeAsyncTask;

import java.io.FileNotFoundException;

class MmsVideoRecorder extends MediaRecorder {
    private static final float VIDEO_OVERSHOOT_SLOP = .85F;

    private static final int BITS_PER_BYTE = 8;

    // We think user will expect to be able to record videos at least this long
    private static final long MIN_DURATION_LIMIT_SECONDS = 25;

    /** The uri where video is being recorded to */
    private Uri mTempVideoUri;

    /** The settings used for video recording */
    private final CamcorderProfile mCamcorderProfile;

    public MmsVideoRecorder(final Camera camera, final int cameraIndex, final int orientation,
            final int maxMessageSize)
            throws FileNotFoundException {
        mCamcorderProfile =
                CamcorderProfile.get(cameraIndex, CamcorderProfile.QUALITY_LOW);
        mTempVideoUri = MediaScratchFileProvider.buildMediaScratchSpaceUri(
                ContentType.getExtension(getContentType()));

        // The video recorder can sometimes return a file that's larger than the max we
        // say we can handle. Try to handle that overshoot by specifying an 85% limit.
        final long sizeLimit = (long) (maxMessageSize * VIDEO_OVERSHOOT_SLOP);

        // The QUALITY_LOW profile might not be low enough to allow for video of a reasonable
        // minimum duration.  Adjust a/v bitrates to allow at least MIN_DURATION_LIMIT video
        // to be recorded.
        int audioBitRate = mCamcorderProfile.audioBitRate;
        int videoBitRate = mCamcorderProfile.videoBitRate;
        final double initialDurationLimit = sizeLimit * BITS_PER_BYTE
                / (double) (audioBitRate + videoBitRate);
        if (initialDurationLimit < MIN_DURATION_LIMIT_SECONDS) {
            // Reduce the suggested bitrates.  These bitrates are only requests, if implementation
            // can't actually hit these goals it will still record video at higher rate and stop when
            // it hits the size limit.
            final double bitRateAdjustmentFactor = initialDurationLimit / MIN_DURATION_LIMIT_SECONDS;
            audioBitRate *= bitRateAdjustmentFactor;
            videoBitRate *= bitRateAdjustmentFactor;
        }

        setCamera(camera);
        setOrientationHint(orientation);
        setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        setVideoSource(MediaRecorder.VideoSource.CAMERA);
        setOutputFormat(mCamcorderProfile.fileFormat);
        setOutputFile(
                Factory.get().getApplicationContext().getContentResolver().openFileDescriptor(
                        mTempVideoUri, "w").getFileDescriptor());

        // Copy settings from CamcorderProfile to MediaRecorder
        setAudioEncodingBitRate(audioBitRate);
        setAudioChannels(mCamcorderProfile.audioChannels);
        setAudioEncoder(mCamcorderProfile.audioCodec);
        setAudioSamplingRate(mCamcorderProfile.audioSampleRate);
        setVideoEncodingBitRate(videoBitRate);
        setVideoEncoder(mCamcorderProfile.videoCodec);
        setVideoFrameRate(mCamcorderProfile.videoFrameRate);
        setVideoSize(
                mCamcorderProfile.videoFrameWidth, mCamcorderProfile.videoFrameHeight);
        setMaxFileSize(sizeLimit);
    }

    Uri getVideoUri() {
        return mTempVideoUri;
    }

    int getVideoWidth() {
        return mCamcorderProfile.videoFrameWidth;
    }

    int getVideoHeight() {
        return mCamcorderProfile.videoFrameHeight;
    }

    void cleanupTempFile() {
        final Uri tempUri = mTempVideoUri;
        SafeAsyncTask.executeOnThreadPool(new Runnable() {
            @Override
            public void run() {
                Factory.get().getApplicationContext().getContentResolver().delete(
                        tempUri, null, null);
            }
        });
        mTempVideoUri = null;
    }

    String getContentType() {
        if (mCamcorderProfile.fileFormat == OutputFormat.MPEG_4) {
            return ContentType.VIDEO_MP4;
        } else {
            // 3GPP is the only other video format with a constant in OutputFormat
            return ContentType.VIDEO_3GPP;
        }
    }
}
