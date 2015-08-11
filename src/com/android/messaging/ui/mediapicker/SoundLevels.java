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

import android.animation.ObjectAnimator;
import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.messaging.R;
import com.android.messaging.util.LogUtil;

/**
 * This view draws circular sound levels. By default the sound levels are black, unless
 * otherwise defined via {@link #mPrimaryLevelPaint}.
 */
public class SoundLevels extends View {
    private static final String TAG = LogUtil.BUGLE_TAG;
    private static final boolean DEBUG = false;

    private boolean mCenterDefined;
    private int mCenterX;
    private int mCenterY;

    // Paint for the main level meter, most closely follows the mic.
    private final Paint mPrimaryLevelPaint;

    // The minimum size of the levels as a percentage of the max, that is the size when volume is 0.
    private final float mMinimumLevel;

    // The minimum size of the levels, that is the size when volume is 0.
    private final float mMinimumLevelSize;

    // The maximum size of the levels, that is the size when volume is 100.
    private final float mMaximumLevelSize;

    // Generates clock ticks for the animation using the global animation loop.
    private final TimeAnimator mSpeechLevelsAnimator;

    private float mCurrentVolume;

    // Indicates whether we should be animating the sound level.
    private boolean mIsEnabled;

    // Input level is pulled from here.
    private AudioLevelSource mLevelSource;

    public SoundLevels(final Context context) {
        this(context, null);
    }

    public SoundLevels(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SoundLevels(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        // Safe source, replaced with system one when attached.
        mLevelSource = new AudioLevelSource();
        mLevelSource.setSpeechLevel(0);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SoundLevels,
                defStyle, 0);

        mMaximumLevelSize = a.getDimensionPixelOffset(
                R.styleable.SoundLevels_maxLevelRadius, 0);
        mMinimumLevelSize = a.getDimensionPixelOffset(
                R.styleable.SoundLevels_minLevelRadius, 0);
        mMinimumLevel = mMinimumLevelSize / mMaximumLevelSize;

        mPrimaryLevelPaint = new Paint();
        mPrimaryLevelPaint.setColor(
                a.getColor(R.styleable.SoundLevels_primaryColor, Color.BLACK));
        mPrimaryLevelPaint.setFlags(Paint.ANTI_ALIAS_FLAG);

        a.recycle();

        // This animator generates ticks that invalidate the
        // view so that the animation is synced with the global animation loop.
        // TODO: We could probably remove this in favor of using postInvalidateOnAnimation
        // which might improve things further.
        mSpeechLevelsAnimator = new TimeAnimator();
        mSpeechLevelsAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        mSpeechLevelsAnimator.setTimeListener(new TimeListener() {
            @Override
            public void onTimeUpdate(final TimeAnimator animation, final long totalTime,
                    final long deltaTime) {
                invalidate();
            }
        });
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        if (!mIsEnabled) {
            return;
        }

        if (!mCenterDefined) {
            // One time computation here, because we can't rely on getWidth() to be computed at
            // constructor time or in onFinishInflate :(.
            mCenterX = getWidth() / 2;
            mCenterY = getWidth() / 2;
            mCenterDefined = true;
        }

        final int level = mLevelSource.getSpeechLevel();
        // Either ease towards the target level, or decay away from it depending on whether
        // its higher or lower than the current.
        if (level > mCurrentVolume) {
            mCurrentVolume = mCurrentVolume + ((level - mCurrentVolume) / 4);
        } else {
            mCurrentVolume = mCurrentVolume * 0.95f;
        }

        final float radius = mMinimumLevel + (1f - mMinimumLevel) * mCurrentVolume / 100;
        mPrimaryLevelPaint.setStyle(Style.FILL);
        canvas.drawCircle(mCenterX, mCenterY, radius * mMaximumLevelSize, mPrimaryLevelPaint);
    }

    public void setLevelSource(final AudioLevelSource source) {
        if (DEBUG) {
            Log.d(TAG, "Speech source set.");
        }
        mLevelSource = source;
    }

    private void startSpeechLevelsAnimator() {
        if (DEBUG) {
            Log.d(TAG, "startAnimator()");
        }
        if (!mSpeechLevelsAnimator.isStarted()) {
            mSpeechLevelsAnimator.start();
        }
    }

    private void stopSpeechLevelsAnimator() {
        if (DEBUG) {
            Log.d(TAG, "stopAnimator()");
        }
        if (mSpeechLevelsAnimator.isStarted()) {
            mSpeechLevelsAnimator.end();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopSpeechLevelsAnimator();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (enabled == mIsEnabled) {
            return;
        }
        if (DEBUG) {
            Log.d("TAG", "setEnabled: " + enabled);
        }
        super.setEnabled(enabled);
        mIsEnabled = enabled;
        setKeepScreenOn(enabled);
        updateSpeechLevelsAnimatorState();
    }

    private void updateSpeechLevelsAnimatorState() {
        if (mIsEnabled) {
            startSpeechLevelsAnimator();
        } else {
            stopSpeechLevelsAnimator();
        }
    }

    /**
     * This is required to make the View findable by uiautomator
     */
    @Override
    public void onInitializeAccessibilityNodeInfo(final AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SoundLevels.class.getCanonicalName());
    }

    /**
     * Set the alpha level of the sound circles.
     */
    public void setPrimaryColorAlpha(final int alpha) {
        mPrimaryLevelPaint.setAlpha(alpha);
    }
}
