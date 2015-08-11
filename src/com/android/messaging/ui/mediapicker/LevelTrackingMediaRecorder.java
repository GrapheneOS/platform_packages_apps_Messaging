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

import android.media.MediaRecorder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;

import java.io.IOException;

/**
 * Wraps around the functionalities of MediaRecorder, performs routine setup for audio recording
 * and updates the audio level to be displayed in UI.
 *
 * During the start and end of a recording session, we kick off a thread that polls for audio
 * levels, and updates the thread-safe AudioLevelSource instance. Consumers may bind to the
 * sound level by either polling from the level source, or register for a level change callback
 * on the level source object. In Bugle, the UI element (SoundLevels) polls for the sound level
 * on the UI thread by using animation ticks and invalidating itself.
 *
 * Aside from tracking sound levels, this also encapsulates the functionality to save the file
 * to the scratch space. The saved file is returned by calling stopRecording().
 */
public class LevelTrackingMediaRecorder {
    // We refresh sound level every 100ms during a recording session.
    private static final int REFRESH_INTERVAL_MILLIS = 100;

    // The native amplitude returned from MediaRecorder ranges from 0~32768 (unfortunately, this
    // is not a constant that's defined anywhere, but the framework's Recorder app is using the
    // same hard-coded number). Therefore, a constant is needed in order to make it 0~100.
    private static final int MAX_AMPLITUDE_FACTOR = 32768 / 100;

    // We want to limit the max audio file size by the max message size allowed by MmsConfig,
    // plus multiplied by this fudge ratio to guarantee that we don't go over limit.
    private static final float MAX_SIZE_RATIO = 0.8f;

    // Default recorder settings for Bugle.
    // TODO: Do we want these to be tweakable?
    private static final int MEDIA_RECORDER_AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private static final int MEDIA_RECORDER_OUTPUT_FORMAT = MediaRecorder.OutputFormat.THREE_GPP;
    private static final int MEDIA_RECORDER_AUDIO_ENCODER = MediaRecorder.AudioEncoder.AMR_NB;

    private final AudioLevelSource mLevelSource;
    private Thread mRefreshLevelThread;
    private MediaRecorder mRecorder;
    private Uri mOutputUri;
    private ParcelFileDescriptor mOutputFD;

    public LevelTrackingMediaRecorder() {
        mLevelSource = new AudioLevelSource();
    }

    public AudioLevelSource getLevelSource() {
        return mLevelSource;
    }

    /**
     * @return if we are currently in a recording session.
     */
    public boolean isRecording() {
        return mRecorder != null;
    }

    /**
     * Start a new recording session.
     * @return true if a session is successfully started; false if something went wrong or if
     *         we are already recording.
     */
    public boolean startRecording(final MediaRecorder.OnErrorListener errorListener,
            final MediaRecorder.OnInfoListener infoListener, int maxSize) {
        synchronized (LevelTrackingMediaRecorder.class) {
            if (mRecorder == null) {
                mOutputUri = MediaScratchFileProvider.buildMediaScratchSpaceUri(
                        ContentType.THREE_GPP_EXTENSION);
                mRecorder = new MediaRecorder();
                try {
                    // The scratch space file is a Uri, however MediaRecorder
                    // API only accepts absolute FD's. Therefore, get the
                    // FileDescriptor from the content resolver to ensure the
                    // directory is created and get the file path to output the
                    // audio to.
                    maxSize *= MAX_SIZE_RATIO;
                    mOutputFD = Factory.get().getApplicationContext()
                            .getContentResolver().openFileDescriptor(mOutputUri, "w");
                    mRecorder.setAudioSource(MEDIA_RECORDER_AUDIO_SOURCE);
                    mRecorder.setOutputFormat(MEDIA_RECORDER_OUTPUT_FORMAT);
                    mRecorder.setAudioEncoder(MEDIA_RECORDER_AUDIO_ENCODER);
                    mRecorder.setOutputFile(mOutputFD.getFileDescriptor());
                    mRecorder.setMaxFileSize(maxSize);
                    mRecorder.setOnErrorListener(errorListener);
                    mRecorder.setOnInfoListener(infoListener);
                    mRecorder.prepare();
                    mRecorder.start();
                    startTrackingSoundLevel();
                    return true;
                } catch (final Exception e) {
                    // There may be a device failure or I/O failure, record the error but
                    // don't fail.
                    LogUtil.e(LogUtil.BUGLE_TAG, "Something went wrong when starting " +
                            "media recorder. " + e);
                    UiUtils.showToastAtBottom(R.string.audio_recording_start_failed);
                    stopRecording();
                }
            } else {
                Assert.fail("Trying to start a new recording session while already recording!");
            }
            return false;
        }
    }

    /**
     * Stop the current recording session.
     * @return the Uri of the output file, or null if not currently recording.
     */
    public Uri stopRecording() {
        synchronized (LevelTrackingMediaRecorder.class) {
            if (mRecorder != null) {
                try {
                    mRecorder.stop();
                } catch (final RuntimeException ex) {
                    // This may happen when the recording is too short, so just drop the recording
                    // in this case.
                    LogUtil.w(LogUtil.BUGLE_TAG, "Something went wrong when stopping " +
                            "media recorder. " + ex);
                    if (mOutputUri != null) {
                        final Uri outputUri = mOutputUri;
                        SafeAsyncTask.executeOnThreadPool(new Runnable() {
                            @Override
                            public void run() {
                                Factory.get().getApplicationContext().getContentResolver().delete(
                                        outputUri, null, null);
                            }
                        });
                        mOutputUri = null;
                    }
                } finally {
                    mRecorder.release();
                    mRecorder = null;
                }
            } else {
                Assert.fail("Not currently recording!");
                return null;
            }
        }

        if (mOutputFD != null) {
            try {
                mOutputFD.close();
            } catch (final IOException e) {
                // Nothing to do
            }
            mOutputFD = null;
        }

        stopTrackingSoundLevel();
        return mOutputUri;
    }

    private int getAmplitude() {
        synchronized (LevelTrackingMediaRecorder.class) {
            if (mRecorder != null) {
                final int maxAmplitude = mRecorder.getMaxAmplitude() / MAX_AMPLITUDE_FACTOR;
                return Math.min(maxAmplitude, 100);
            } else {
                return 0;
            }
        }
    }

    private void startTrackingSoundLevel() {
        stopTrackingSoundLevel();
        mRefreshLevelThread = new Thread() {
            @Override
            public void run() {
                try {
                    while (true) {
                        synchronized (LevelTrackingMediaRecorder.class) {
                            if (mRecorder != null) {
                                mLevelSource.setSpeechLevel(getAmplitude());
                            } else {
                                // The recording session is over, finish the thread.
                                return;
                            }
                        }
                        Thread.sleep(REFRESH_INTERVAL_MILLIS);
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        mRefreshLevelThread.start();
    }

    private void stopTrackingSoundLevel() {
        if (mRefreshLevelThread != null && mRefreshLevelThread.isAlive()) {
            mRefreshLevelThread.interrupt();
            mRefreshLevelThread = null;
        }
    }
}
