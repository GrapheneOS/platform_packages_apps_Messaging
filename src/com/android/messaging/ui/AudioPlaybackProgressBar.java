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

import android.animation.ObjectAnimator;
import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.content.Context;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ProgressBar;

/**
 * Shows a styled progress bar that is synchronized with the playback state of an audio attachment.
 */
public class AudioPlaybackProgressBar extends ProgressBar implements PlaybackStateView {
    private long mDurationInMillis;
    private final TimeAnimator mUpdateAnimator;
    private long mCumulativeTime = 0;
    private long mCurrentPlayStartTime = 0;
    private boolean mIncoming = false;

    public AudioPlaybackProgressBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        mUpdateAnimator = new TimeAnimator();
        mUpdateAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        mUpdateAnimator.setTimeListener(new TimeListener() {
            @Override
            public void onTimeUpdate(final TimeAnimator animation, final long totalTime,
                    final long deltaTime) {
                int progress = 0;
                if (mDurationInMillis > 0) {
                    progress = (int) (((mCumulativeTime + SystemClock.elapsedRealtime() -
                            mCurrentPlayStartTime) * 1.0f / mDurationInMillis) * 100);
                    progress = Math.max(Math.min(progress, 100), 0);
                }
                setProgress(progress);
            }
        });
        updateAppearance();
    }

    /**
     * Sets the duration of the audio that's being played, in milliseconds.
     */
    public void setDuration(final long durationInMillis) {
        mDurationInMillis = durationInMillis;
    }

    @Override
    public void restart() {
        reset();
        resume();
    }

    @Override
    public void reset() {
        stopUpdateTicks();
        setProgress(0);
        mCumulativeTime = 0;
        mCurrentPlayStartTime = 0;
    }

    @Override
    public void resume() {
        mCurrentPlayStartTime = SystemClock.elapsedRealtime();
        startUpdateTicks();
    }

    @Override
    public void pause() {
        mCumulativeTime += SystemClock.elapsedRealtime() - mCurrentPlayStartTime;
        stopUpdateTicks();
    }

    private void startUpdateTicks() {
        if (!mUpdateAnimator.isStarted()) {
            mUpdateAnimator.start();
        }
    }

    private void stopUpdateTicks() {
        if (mUpdateAnimator.isStarted()) {
            mUpdateAnimator.end();
        }
    }

    private void updateAppearance() {
        final Drawable drawable =
                ConversationDrawables.get().getAudioProgressDrawable(mIncoming);
        final ClipDrawable clipDrawable = new ClipDrawable(drawable, Gravity.START,
                ClipDrawable.HORIZONTAL);
        setProgressDrawable(clipDrawable);
        setBackground(ConversationDrawables.get()
                .getAudioProgressBackgroundDrawable(mIncoming));
    }

    public void setVisualStyle(final boolean incoming) {
        if (mIncoming != incoming) {
            mIncoming = incoming;
            updateAppearance();
        }
    }
}
