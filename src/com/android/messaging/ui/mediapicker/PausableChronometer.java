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
import android.os.SystemClock;
import android.util.AttributeSet;
import android.widget.Chronometer;

import com.android.messaging.ui.PlaybackStateView;

/**
 * A pausable Chronometer implementation. The default Chronometer in Android only stops the UI
 * from updating when you call stop(), but doesn't actually pause it. This implementation adds an
 * additional timestamp that tracks the timespan for the pause and compensate for that.
 */
public class PausableChronometer extends Chronometer implements PlaybackStateView {
    // Keeps track of how far long the Chronometer has been tracking when it's paused. We'd like
    // to start from this time the next time it's resumed.
    private long mTimeWhenPaused = 0;

    public PausableChronometer(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Reset the timer and start counting from zero.
     */
    @Override
    public void restart() {
        reset();
        start();
    }

    /**
     * Reset the timer to zero, but don't start it.
     */
    @Override
    public void reset() {
        stop();
        setBase(SystemClock.elapsedRealtime());
        mTimeWhenPaused = 0;
    }

    /**
     * Resume the timer after a previous pause.
     */
    @Override
    public void resume() {
        setBase(SystemClock.elapsedRealtime() - mTimeWhenPaused);
        start();
    }

    /**
     * Pause the timer.
     */
    @Override
    public void pause() {
        stop();
        mTimeWhenPaused = SystemClock.elapsedRealtime() - getBase();
    }
}
