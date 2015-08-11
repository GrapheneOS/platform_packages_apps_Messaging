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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.ViewSwitcher;

import com.android.messaging.R;

/**
 * Shows a tinted play pause button.
 */
public class AudioAttachmentPlayPauseButton extends ViewSwitcher {
    private ImageView mPlayButton;
    private ImageView mPauseButton;

    private boolean mShowAsIncoming;

    public AudioAttachmentPlayPauseButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPlayButton = (ImageView) findViewById(R.id.play_button);
        mPauseButton = (ImageView) findViewById(R.id.pause_button);
        updateAppearance();
    }

    public void setVisualStyle(final boolean showAsIncoming) {
        if (mShowAsIncoming != showAsIncoming) {
            mShowAsIncoming = showAsIncoming;
            updateAppearance();
        }
    }

    private void updateAppearance() {
        mPlayButton.setImageDrawable(ConversationDrawables.get()
                .getPlayButtonDrawable(mShowAsIncoming));
        mPauseButton.setImageDrawable(ConversationDrawables.get()
                .getPauseButtonDrawable(mShowAsIncoming));
    }
}
