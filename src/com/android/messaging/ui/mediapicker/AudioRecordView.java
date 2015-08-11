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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.data.DraftMessageData.DraftMessageSubscriptionDataProvider;
import com.android.messaging.datamodel.data.MediaPickerMessagePartData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.MediaUtil;
import com.android.messaging.util.MediaUtil.OnCompletionListener;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.ThreadUtil;
import com.android.messaging.util.UiUtils;
import com.google.common.annotations.VisibleForTesting;

/**
 * Hosts an audio recorder with tap and hold to record functionality.
 */
public class AudioRecordView extends FrameLayout implements
        MediaRecorder.OnErrorListener,
        MediaRecorder.OnInfoListener {
    /**
     * An interface that communicates with the hosted AudioRecordView.
     */
    public interface HostInterface extends DraftMessageSubscriptionDataProvider {
        void onAudioRecorded(final MessagePartData item);
    }

    /** The initial state, the user may press and hold to start recording */
    private static final int MODE_IDLE = 1;

    /** The user has pressed the record button and we are playing the sound indicating the
     *  start of recording session. Don't record yet since we don't want the beeping sound
     *  to get into the recording. */
    private static final int MODE_STARTING = 2;

    /** When the user is actively recording */
    private static final int MODE_RECORDING = 3;

    /** When the user has finished recording, we need to record for some additional time. */
    private static final int MODE_STOPPING = 4;

    // Bug: 16020175: The framework's MediaRecorder would cut off the ending portion of the
    // recorded audio by about half a second. To mitigate this issue, we continue the recording
    // for some extra time before stopping it.
    private static final int AUDIO_RECORD_ENDING_BUFFER_MILLIS = 500;

    /**
     * The minimum duration of any recording. Below this threshold, it will be treated as if the
     * user clicked the record button and inform the user to tap and hold to record.
     */
    private static final int AUDIO_RECORD_MINIMUM_DURATION_MILLIS = 300;

    // For accessibility, the touchable record button is bigger than the record button visual.
    private ImageView mRecordButtonVisual;
    private View mRecordButton;
    private SoundLevels mSoundLevels;
    private TextView mHintTextView;
    private PausableChronometer mTimerTextView;
    private LevelTrackingMediaRecorder mMediaRecorder;
    private long mAudioRecordStartTimeMillis;

    private int mCurrentMode = MODE_IDLE;
    private HostInterface mHostInterface;
    private int mThemeColor;

    public AudioRecordView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mMediaRecorder = new LevelTrackingMediaRecorder();
    }

    public void setHostInterface(final HostInterface hostInterface) {
        mHostInterface = hostInterface;
    }

    @VisibleForTesting
    public void testSetMediaRecorder(final LevelTrackingMediaRecorder recorder) {
        mMediaRecorder = recorder;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSoundLevels = (SoundLevels) findViewById(R.id.sound_levels);
        mRecordButtonVisual = (ImageView) findViewById(R.id.record_button_visual);
        mRecordButton = findViewById(R.id.record_button);
        mHintTextView = (TextView) findViewById(R.id.hint_text);
        mTimerTextView = (PausableChronometer) findViewById(R.id.timer_text);
        mSoundLevels.setLevelSource(mMediaRecorder.getLevelSource());
        mRecordButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(final View v, final MotionEvent event) {
                final int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        onRecordButtonTouchDown();

                        // Don't let the record button handle the down event to let it fall through
                        // so that we can handle it for the entire panel in onTouchEvent(). This is
                        // done so that: 1) the user taps on the record button to start recording
                        // 2) the entire panel owns the touch event so we'd keep recording even
                        // if the user moves outside the button region.
                        return false;
                }
                return false;
            }
        });
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return shouldHandleTouch();

            case MotionEvent.ACTION_MOVE:
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return onRecordButtonTouchUp();
        }
        return super.onTouchEvent(event);
    }

    public void onPause() {
        // The conversation draft cannot take any updates when it's paused. Therefore, forcibly
        // stop recording on pause.
        stopRecording();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopRecording();
    }

    private boolean isRecording() {
        return mMediaRecorder.isRecording() && mCurrentMode == MODE_RECORDING;
    }

    public boolean shouldHandleTouch() {
        return mCurrentMode != MODE_IDLE;
    }

    public void stopTouchHandling() {
        setMode(MODE_IDLE);
        stopRecording();
    }

    private void setMode(final int mode) {
        if (mCurrentMode != mode) {
            mCurrentMode = mode;
            updateVisualState();
        }
    }

    private void updateVisualState() {
        switch (mCurrentMode) {
            case MODE_IDLE:
                mHintTextView.setVisibility(VISIBLE);
                mHintTextView.setTypeface(null, Typeface.NORMAL);
                mTimerTextView.setVisibility(GONE);
                mSoundLevels.setEnabled(false);
                mTimerTextView.stop();
                break;

            case MODE_RECORDING:
            case MODE_STOPPING:
                mHintTextView.setVisibility(GONE);
                mTimerTextView.setVisibility(VISIBLE);
                mSoundLevels.setEnabled(true);
                mTimerTextView.restart();
                break;

            case MODE_STARTING:
                break;  // No-Op.

            default:
                Assert.fail("invalid mode for AudioRecordView!");
                break;
        }
        updateRecordButtonAppearance();
    }

    public void setThemeColor(final int color) {
        mThemeColor = color;
        updateRecordButtonAppearance();
    }

    private void updateRecordButtonAppearance() {
        final Drawable foregroundDrawable = getResources().getDrawable(R.drawable.ic_mp_audio_mic);
        final GradientDrawable backgroundDrawable = ((GradientDrawable) getResources()
                .getDrawable(R.drawable.audio_record_control_button_background));
        if (isRecording()) {
            foregroundDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
            backgroundDrawable.setColor(mThemeColor);
        } else {
            foregroundDrawable.setColorFilter(mThemeColor, PorterDuff.Mode.SRC_ATOP);
            backgroundDrawable.setColor(Color.WHITE);
        }
        mRecordButtonVisual.setImageDrawable(foregroundDrawable);
        mRecordButtonVisual.setBackground(backgroundDrawable);
    }

    @VisibleForTesting
    boolean onRecordButtonTouchDown() {
        if (!mMediaRecorder.isRecording() && mCurrentMode == MODE_IDLE) {
            setMode(MODE_STARTING);
            playAudioStartSound(new OnCompletionListener() {
                @Override
                public void onCompletion() {
                    // Double-check the current mode before recording since the user may have
                    // lifted finger from the button before the beeping sound is played through.
                    final int maxSize = MmsConfig.get(mHostInterface.getConversationSelfSubId())
                            .getMaxMessageSize();
                    if (mCurrentMode == MODE_STARTING &&
                            mMediaRecorder.startRecording(AudioRecordView.this,
                                    AudioRecordView.this, maxSize)) {
                        setMode(MODE_RECORDING);
                    }
                }
            });
            mAudioRecordStartTimeMillis = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @VisibleForTesting
    boolean onRecordButtonTouchUp() {
        if (System.currentTimeMillis() - mAudioRecordStartTimeMillis <
                AUDIO_RECORD_MINIMUM_DURATION_MILLIS) {
            // The recording is too short, bolden the hint text to instruct the user to
            // "tap+hold" to record audio.
            final Uri outputUri = stopRecording();
            if (outputUri != null) {
                SafeAsyncTask.executeOnThreadPool(new Runnable() {
                    @Override
                    public void run() {
                        Factory.get().getApplicationContext().getContentResolver().delete(
                                outputUri, null, null);
                    }
                });
            }
            setMode(MODE_IDLE);
            mHintTextView.setTypeface(null, Typeface.BOLD);
        } else if (isRecording()) {
            // Record for some extra time to ensure the ending part is saved.
            setMode(MODE_STOPPING);
            ThreadUtil.getMainThreadHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    onFinishedRecording();
                }
            }, AUDIO_RECORD_ENDING_BUFFER_MILLIS);
        } else {
            setMode(MODE_IDLE);
        }
        return true;
    }

    private Uri stopRecording() {
        if (mMediaRecorder.isRecording()) {
            return mMediaRecorder.stopRecording();
        }
        return null;
    }

    @Override   // From MediaRecorder.OnInfoListener
    public void onInfo(final MediaRecorder mr, final int what, final int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            // Max size reached. Finish recording immediately.
            LogUtil.i(LogUtil.BUGLE_TAG, "Max size reached while recording audio");
            onFinishedRecording();
        } else {
            // These are unknown errors.
            onErrorWhileRecording(what, extra);
        }
    }

    @Override   // From MediaRecorder.OnErrorListener
    public void onError(final MediaRecorder mr, final int what, final int extra) {
        onErrorWhileRecording(what, extra);
    }

    private void onErrorWhileRecording(final int what, final int extra) {
        LogUtil.e(LogUtil.BUGLE_TAG, "Error occurred during audio recording what=" + what +
                ", extra=" + extra);
        UiUtils.showToastAtBottom(R.string.audio_recording_error);
        setMode(MODE_IDLE);
        stopRecording();
    }

    private void onFinishedRecording() {
        final Uri outputUri = stopRecording();
        if (outputUri != null) {
            final Rect startRect = new Rect();
            mRecordButtonVisual.getGlobalVisibleRect(startRect);
            final MediaPickerMessagePartData audioItem =
                    new MediaPickerMessagePartData(startRect,
                            ContentType.AUDIO_3GPP, outputUri, 0, 0);
            mHostInterface.onAudioRecorded(audioItem);
        }
        playAudioEndSound();
        setMode(MODE_IDLE);
    }

    private void playAudioStartSound(final OnCompletionListener completionListener) {
        MediaUtil.get().playSound(getContext(), R.raw.audio_initiate, completionListener);
    }

    private void playAudioEndSound() {
        MediaUtil.get().playSound(getContext(), R.raw.audio_end, null);
    }
}
