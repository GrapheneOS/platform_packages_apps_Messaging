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
package com.android.messaging.ui.conversation;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.messaging.R;
import com.android.messaging.annotation.VisibleForAnimation;
import com.android.messaging.datamodel.data.ConversationMessageBubbleData;
import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.util.UiUtils;

/**
 * Shows the message bubble for one conversation message. It is able to animate size changes
 * by morphing when the message content changes size.
 */
// TODO: Move functionality from ConversationMessageView into this class as appropriate
public class ConversationMessageBubbleView extends LinearLayout {
    private int mIntrinsicWidth;
    private int mMorphedWidth;
    private ObjectAnimator mAnimator;
    private boolean mShouldAnimateWidthChange;
    private final ConversationMessageBubbleData mData;
    private int mRunningStartWidth;
    private ViewGroup mBubbleBackground;

    public ConversationMessageBubbleView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mData = new ConversationMessageBubbleData();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBubbleBackground = (ViewGroup) findViewById(R.id.message_text_and_info);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int newIntrinsicWidth = getMeasuredWidth();
        if (mIntrinsicWidth == 0 && newIntrinsicWidth != mIntrinsicWidth) {
            if (mShouldAnimateWidthChange) {
                kickOffMorphAnimation(mIntrinsicWidth, newIntrinsicWidth);
            }
            mIntrinsicWidth = newIntrinsicWidth;
        }

        if (mMorphedWidth > 0) {
            mBubbleBackground.getLayoutParams().width = mMorphedWidth;
        } else {
            mBubbleBackground.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
        }
        mBubbleBackground.requestLayout();
    }

    @VisibleForAnimation
    public void setMorphWidth(final int width) {
        mMorphedWidth = width;
        requestLayout();
    }

    public void bind(final ConversationMessageData data) {
        final boolean changed = mData.bind(data);
        // Animate width change only when we are binding to the same message, so that we may
        // animate view size changes on the same message bubble due to things like status text
        // change.
        // Don't animate width change when the bubble contains attachments. Width animation is
        // only suitable for text-only messages (where the bubble size change due to status or
        // time stamp changes).
        mShouldAnimateWidthChange = !changed && !data.hasAttachments();
        if (mAnimator == null) {
            mMorphedWidth = 0;
        }
    }

    public void kickOffMorphAnimation(final int oldWidth, final int newWidth) {
        if (mAnimator != null) {
            mAnimator.setIntValues(mRunningStartWidth, newWidth);
            return;
        }
        mRunningStartWidth = oldWidth;
        mAnimator = ObjectAnimator.ofInt(this, "morphWidth", oldWidth, newWidth);
        mAnimator.setDuration(UiUtils.MEDIAPICKER_TRANSITION_DURATION);
        mAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mAnimator = null;
                mMorphedWidth = 0;
                // Allow the bubble to resize if, for example, the status text changed during
                // the animation.  This will snap to the bigger size if needed.  This is intentional
                // as animating immediately after looks really bad and switching layout params
                // during the original animation does not achieve the desired effect.
                mBubbleBackground.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
                mBubbleBackground.requestLayout();
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        mAnimator.start();
    }
}
