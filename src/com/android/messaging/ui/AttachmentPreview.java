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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;

import com.android.messaging.R;
import com.android.messaging.annotation.VisibleForAnimation;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.MediaPickerMessagePartData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.MultiAttachmentLayout.OnAttachmentClickListener;
import com.android.messaging.ui.animation.PopupTransitionAnimation;
import com.android.messaging.ui.conversation.ComposeMessageView;
import com.android.messaging.ui.conversation.ConversationFragment;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ThreadUtil;
import com.android.messaging.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class AttachmentPreview extends ScrollView implements OnAttachmentClickListener {
    private FrameLayout mAttachmentView;
    private ComposeMessageView mComposeMessageView;
    private ImageButton mCloseButton;
    private int mAnimatedHeight = -1;
    private Animator mCloseGapAnimator;
    private boolean mPendingFirstUpdate;
    private Handler mHandler;
    private Runnable mHideRunnable;
    private boolean mPendingHideCanceled;

    private static final int CLOSE_BUTTON_REVEAL_STAGGER_MILLIS = 300;

    public AttachmentPreview(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCloseButton = (ImageButton) findViewById(R.id.close_button);
        mCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                mComposeMessageView.clearAttachments();
            }
        });

        mAttachmentView = (FrameLayout) findViewById(R.id.attachment_view);

        // The attachment preview is a scroll view so that it can show the bottom portion of the
        // attachment whenever the space is tight (e.g. when in landscape mode). Per design
        // request we'd like to make the attachment view always scrolled to the bottom.
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(final View v, final int left, final int top, final int right,
                    final int bottom, final int oldLeft, final int oldTop, final int oldRight,
                    final int oldBottom) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        final int childCount = getChildCount();
                        if (childCount > 0) {
                            final View lastChild = getChildAt(childCount - 1);
                            scrollTo(getScrollX(), lastChild.getBottom() - getHeight());
                        }
                    }
                });
            }
        });
        mPendingFirstUpdate = true;
    }

    public void setComposeMessageView(final ComposeMessageView composeMessageView) {
        mComposeMessageView = composeMessageView;
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mAnimatedHeight >= 0) {
            setMeasuredDimension(getMeasuredWidth(), mAnimatedHeight);
        }
    }

    private void cancelPendingHide() {
        mPendingHideCanceled = true;
    }

    public void hideAttachmentPreview() {
        if (getVisibility() != GONE) {
            UiUtils.revealOrHideViewWithAnimation(mCloseButton, GONE,
                    null /* onFinishRunnable */);
            startCloseGapAnimationOnAttachmentClear();

            if (mAttachmentView.getChildCount() > 0) {
                mPendingHideCanceled = false;
                final View viewToHide = mAttachmentView.getChildCount() > 1 ?
                        mAttachmentView : mAttachmentView.getChildAt(0);
                UiUtils.revealOrHideViewWithAnimation(viewToHide, INVISIBLE,
                        new Runnable() {
                            @Override
                            public void run() {
                                // Only hide if we are didn't get overruled by showing
                                if (!mPendingHideCanceled) {
                                    mAttachmentView.removeAllViews();
                                    setVisibility(GONE);
                                }
                            }
                        });
            } else {
                mAttachmentView.removeAllViews();
                setVisibility(GONE);
            }
        }
    }

    // returns true if we have attachments
    public boolean onAttachmentsChanged(final DraftMessageData draftMessageData) {
        final boolean isFirstUpdate = mPendingFirstUpdate;
        final List<MessagePartData> attachments = draftMessageData.getReadOnlyAttachments();
        final List<PendingAttachmentData> pendingAttachments =
                draftMessageData.getReadOnlyPendingAttachments();

        // Any change in attachments would invalidate the animated height animation.
        cancelCloseGapAnimation();
        mPendingFirstUpdate = false;

        final int combinedAttachmentCount = attachments.size() + pendingAttachments.size();
        mCloseButton.setContentDescription(getResources()
                .getQuantityString(R.plurals.attachment_preview_close_content_description,
                        combinedAttachmentCount));
        if (combinedAttachmentCount == 0) {
            mHideRunnable = new Runnable() {
                @Override
                public void run() {
                    mHideRunnable = null;
                    // Only start the hiding if there are still no attachments
                    if (attachments.size() + pendingAttachments.size() == 0) {
                        hideAttachmentPreview();
                    }
                }
            };
            if (draftMessageData.isSending()) {
                // Wait to hide until the message is ready to start animating
                // We'll execute immediately when the animation triggers
                mHandler.postDelayed(mHideRunnable,
                        ConversationFragment.MESSAGE_ANIMATION_MAX_WAIT);
            } else {
                // Run immediately when clearing attachments
                mHideRunnable.run();
            }
            return false;
        }

        cancelPendingHide();  // We're showing
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            mAttachmentView.setVisibility(VISIBLE);

            // Don't animate in the close button if this is the first update after view creation.
            // This is the initial draft load from database for pre-existing drafts.
            if (!isFirstUpdate) {
                // Reveal the close button after the view animates in.
                mCloseButton.setVisibility(INVISIBLE);
                ThreadUtil.getMainThreadHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.revealOrHideViewWithAnimation(mCloseButton, VISIBLE,
                                null /* onFinishRunnable */);
                    }
                }, UiUtils.MEDIAPICKER_TRANSITION_DURATION + CLOSE_BUTTON_REVEAL_STAGGER_MILLIS);
            }
        }

        // Merge the pending attachment list with real attachment.  Design would prefer these be
        // in LIFO order user can see added images past the 5th one but we also want them to be in
        // order and we want it to be WYSIWYG.
        final List<MessagePartData> combinedAttachments = new ArrayList<>();
        combinedAttachments.addAll(attachments);
        combinedAttachments.addAll(pendingAttachments);

        final LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        if (combinedAttachmentCount > 1) {
            MultiAttachmentLayout multiAttachmentLayout = null;
            Rect transitionRect = null;
            if (mAttachmentView.getChildCount() > 0) {
                final View firstChild = mAttachmentView.getChildAt(0);
                if (firstChild instanceof MultiAttachmentLayout) {
                    Assert.equals(1, mAttachmentView.getChildCount());
                    multiAttachmentLayout = (MultiAttachmentLayout) firstChild;
                    multiAttachmentLayout.bindAttachments(combinedAttachments,
                            null /* transitionRect */, combinedAttachmentCount);
                } else {
                    transitionRect = new Rect(firstChild.getLeft(), firstChild.getTop(),
                            firstChild.getRight(), firstChild.getBottom());
                }
            }
            if (multiAttachmentLayout == null) {
                multiAttachmentLayout = AttachmentPreviewFactory.createMultiplePreview(
                        getContext(), this);
                multiAttachmentLayout.bindAttachments(combinedAttachments, transitionRect,
                        combinedAttachmentCount);
                mAttachmentView.removeAllViews();
                mAttachmentView.addView(multiAttachmentLayout);
            }
        } else {
            final MessagePartData attachment = combinedAttachments.get(0);
            boolean shouldAnimate = true;
            if (mAttachmentView.getChildCount() > 0) {
                // If we are going from N->1 attachments, try to use the current bounds
                // bounds as the starting rect.
                shouldAnimate = false;
                final View firstChild = mAttachmentView.getChildAt(0);
                if (firstChild instanceof MultiAttachmentLayout &&
                        attachment instanceof MediaPickerMessagePartData) {
                    final View leftoverView = ((MultiAttachmentLayout) firstChild)
                            .findViewForAttachment(attachment);
                    if (leftoverView != null) {
                        final Rect currentRect = UiUtils.getMeasuredBoundsOnScreen(leftoverView);
                        if (!currentRect.isEmpty() &&
                                attachment instanceof MediaPickerMessagePartData) {
                            ((MediaPickerMessagePartData) attachment).setStartRect(currentRect);
                            shouldAnimate = true;
                        }
                    }
                }
            }
            mAttachmentView.removeAllViews();
            final View attachmentView = AttachmentPreviewFactory.createAttachmentPreview(
                    layoutInflater, attachment, mAttachmentView,
                    AttachmentPreviewFactory.TYPE_SINGLE, true /* startImageRequest */, this);
            if (attachmentView != null) {
                mAttachmentView.addView(attachmentView);
                if (shouldAnimate) {
                    tryAnimateViewIn(attachment, attachmentView);
                }
            }
        }
        return true;
    }

    public void onMessageAnimationStart() {
        if (mHideRunnable == null) {
            return;
        }

        // Run the hide animation at the same time as the message animation
        mHandler.removeCallbacks(mHideRunnable);
        setVisibility(View.INVISIBLE);
        mHideRunnable.run();
    }

    static void tryAnimateViewIn(final MessagePartData attachmentData, final View view) {
        if (attachmentData instanceof MediaPickerMessagePartData) {
            final Rect startRect = ((MediaPickerMessagePartData) attachmentData).getStartRect();
            new PopupTransitionAnimation(startRect, view).startAfterLayoutComplete();
        }
    }

    @VisibleForAnimation
    public void setAnimatedHeight(final int animatedHeight) {
        if (mAnimatedHeight != animatedHeight) {
            mAnimatedHeight = animatedHeight;
            requestLayout();
        }
    }

    /**
     * Kicks off an animation to animate the layout change for closing the gap between the
     * message list and the compose message box when the attachments are cleared.
     */
    private void startCloseGapAnimationOnAttachmentClear() {
        // Cancel existing animation.
        cancelCloseGapAnimation();
        mCloseGapAnimator = ObjectAnimator.ofInt(this, "animatedHeight", getHeight(), 0);
        mCloseGapAnimator.start();
    }

    private void cancelCloseGapAnimation() {
        if (mCloseGapAnimator != null) {
            mCloseGapAnimator.cancel();
            mCloseGapAnimator = null;
        }
        mAnimatedHeight = -1;
    }

    @Override
    public boolean onAttachmentClick(final MessagePartData attachment,
            final Rect viewBoundsOnScreen, final boolean longPress) {
        if (longPress) {
            mComposeMessageView.onAttachmentPreviewLongClicked();
            return true;
        }

        if (!(attachment instanceof PendingAttachmentData) && attachment.isImage()) {
            mComposeMessageView.displayPhoto(attachment.getContentUri(), viewBoundsOnScreen);
            return true;
        }
        return false;
    }
}
