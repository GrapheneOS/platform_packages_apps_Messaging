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
package com.android.messaging.ui.conversationlist;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnItemTouchListener;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.messaging.R;
import com.android.messaging.util.Assert;
import com.android.messaging.util.UiUtils;

/**
 * Animation and touch helper class for Conversation List swipe.
 */
public class ConversationListSwipeHelper implements OnItemTouchListener {
    private static final int UNIT_SECONDS = 1000;
    private static final boolean ANIMATING = true;

    private static final float ERROR_FACTOR_MULTIPLIER = 1.2f;
    private static final float PERCENTAGE_OF_WIDTH_TO_DISMISS = 0.4f;
    private static final float FLING_PERCENTAGE_OF_WIDTH_TO_DISMISS = 0.05f;

    private static final int SWIPE_DIRECTION_NONE = 0;
    private static final int SWIPE_DIRECTION_LEFT = 1;
    private static final int SWIPE_DIRECTION_RIGHT = 2;

    private final RecyclerView mRecyclerView;
    private final long mDefaultRestoreAnimationDuration;
    private final long mDefaultDismissAnimationDuration;
    private final long mMaxTranslationAnimationDuration;
    private final int mTouchSlop;
    private final int mMinimumFlingVelocity;
    private final int mMaximumFlingVelocity;

    /* Valid throughout a single gesture. */
    private VelocityTracker mVelocityTracker;
    private float mInitialX;
    private float mInitialY;
    private boolean mIsSwiping;
    private ConversationListItemView mListItemView;

    public ConversationListSwipeHelper(final RecyclerView recyclerView) {
        mRecyclerView = recyclerView;

        final Context context = mRecyclerView.getContext();
        final Resources res = context.getResources();
        mDefaultRestoreAnimationDuration = res.getInteger(R.integer.swipe_duration_ms);
        mDefaultDismissAnimationDuration = res.getInteger(R.integer.swipe_duration_ms);
        mMaxTranslationAnimationDuration = res.getInteger(R.integer.swipe_duration_ms);

        final ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mTouchSlop = viewConfiguration.getScaledPagingTouchSlop();
        mMaximumFlingVelocity = Math.min(
                viewConfiguration.getScaledMaximumFlingVelocity(),
                res.getInteger(R.integer.swipe_max_fling_velocity_px_per_s));
        mMinimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
    }

    @Override
    public boolean onInterceptTouchEvent(final RecyclerView recyclerView, final MotionEvent event) {
        if (event.getPointerCount() > 1) {
            // Ignore subsequent pointers.
            return false;
        }

        // We are not yet tracking a swipe gesture. Begin detection by spying on
        // touch events bubbling down to our children.
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!hasGestureSwipeTarget()) {
                    onGestureStart();

                    mVelocityTracker.addMovement(event);
                    mInitialX = event.getX();
                    mInitialY = event.getY();

                    final View viewAtPoint = mRecyclerView.findChildViewUnder(mInitialX, mInitialY);
                    final ConversationListItemView child = (ConversationListItemView) viewAtPoint;
                    if (viewAtPoint instanceof ConversationListItemView &&
                            child != null && child.isSwipeAnimatable()) {
                        // Begin detecting swipe on the target for the rest of the gesture.
                        mListItemView = child;
                        if (mListItemView.isAnimating()) {
                            mListItemView = null;
                        }
                    } else {
                        mListItemView = null;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (hasValidGestureSwipeTarget()) {
                    mVelocityTracker.addMovement(event);

                    final int historicalCount = event.getHistorySize();
                    // First consume the historical events, then consume the current ones.
                    for (int i = 0; i < historicalCount + 1; i++) {
                        float currX;
                        float currY;
                        if (i < historicalCount) {
                            currX = event.getHistoricalX(i);
                            currY = event.getHistoricalY(i);
                        } else {
                            currX = event.getX();
                            currY = event.getY();
                        }
                        final float deltaX = currX - mInitialX;
                        final float deltaY = currY - mInitialY;
                        final float absDeltaX = Math.abs(deltaX);
                        final float absDeltaY = Math.abs(deltaY);

                        if (!mIsSwiping && absDeltaY > mTouchSlop
                                && absDeltaY > (ERROR_FACTOR_MULTIPLIER * absDeltaX)) {
                            // Stop detecting swipe for the remainder of this gesture.
                            onGestureEnd();
                            return false;
                        }

                        if (absDeltaX > mTouchSlop) {
                            // Swipe detected. Return true so we can handle the gesture in
                            // onTouchEvent.
                            mIsSwiping = true;

                            // We don't want to suddenly jump the slop distance.
                            mInitialX = event.getX();
                            mInitialY = event.getY();

                            onSwipeGestureStart(mListItemView);
                            return true;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (hasGestureSwipeTarget()) {
                    onGestureEnd();
                }
                break;
        }

        // Start intercepting touch events from children if we detect a swipe.
        return mIsSwiping;
    }

    @Override
    public void onTouchEvent(final RecyclerView recyclerView, final MotionEvent event) {
        // We should only be here if we intercepted the touch due to swipe.
        Assert.isTrue(mIsSwiping);

        // We are now tracking a swipe gesture.
        mVelocityTracker.addMovement(event);

        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_MOVE:
                if (hasValidGestureSwipeTarget()) {
                    mListItemView.setSwipeTranslationX(event.getX() - mInitialX);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (hasValidGestureSwipeTarget()) {
                    final float maxVelocity = mMaximumFlingVelocity;
                    mVelocityTracker.computeCurrentVelocity(UNIT_SECONDS, maxVelocity);
                    final float velocityX = getLastComputedXVelocity();

                    final float translationX = mListItemView.getSwipeTranslationX();

                    int swipeDirection = SWIPE_DIRECTION_NONE;
                    if (translationX != 0) {
                        swipeDirection =
                                translationX > 0 ? SWIPE_DIRECTION_RIGHT : SWIPE_DIRECTION_LEFT;
                    } else if (velocityX != 0) {
                        swipeDirection =
                                velocityX > 0 ? SWIPE_DIRECTION_RIGHT : SWIPE_DIRECTION_LEFT;
                    }

                    final boolean fastEnough = isTargetSwipedFastEnough();
                    final boolean farEnough = isTargetSwipedFarEnough();

                    final boolean shouldDismiss =  (fastEnough || farEnough);

                    if (shouldDismiss) {
                        if (fastEnough) {
                            animateDismiss(mListItemView, velocityX);
                        } else {
                            animateDismiss(mListItemView, swipeDirection);
                        }
                    } else {
                        animateRestore(mListItemView, velocityX);
                    }

                    onSwipeGestureEnd(mListItemView,
                            shouldDismiss ? swipeDirection : SWIPE_DIRECTION_NONE);
                } else {
                    onGestureEnd();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (hasValidGestureSwipeTarget()) {
                    animateRestore(mListItemView, 0f);
                    onSwipeGestureEnd(mListItemView, SWIPE_DIRECTION_NONE);
                } else {
                    onGestureEnd();
                }
                break;
        }
    }


    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }

    /**
     * We have started to intercept a series of touch events.
     */
    private void onGestureStart() {
        mIsSwiping = false;
        // Work around bug in RecyclerView that sends two identical ACTION_DOWN
        // events to #onInterceptTouchEvent.
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.clear();
    }

    /**
     * The series of touch events has been detected as a swipe.
     *
     * Now that the gesture is a swipe, we will begin translating the view of the
     * given viewHolder.
     */
    private void onSwipeGestureStart(final ConversationListItemView itemView) {
        mRecyclerView.getParent().requestDisallowInterceptTouchEvent(true);
        setHardwareAnimatingLayerType(itemView, ANIMATING);
        itemView.setAnimating(true);
    }

    /**
     * The current swipe gesture is complete.
     */
    private void onSwipeGestureEnd(final ConversationListItemView itemView,
            final int swipeDirection) {
        if (swipeDirection == SWIPE_DIRECTION_RIGHT || swipeDirection == SWIPE_DIRECTION_LEFT) {
            itemView.onSwipeComplete();
        }

        // Balances out onSwipeGestureStart.
        itemView.setAnimating(false);

        onGestureEnd();
    }

    /**
     * The series of touch events has ended in an {@link MotionEvent#ACTION_UP}
     * or {@link MotionEvent#ACTION_CANCEL}.
     */
    private void onGestureEnd() {
        mVelocityTracker.recycle();
        mVelocityTracker = null;
        mIsSwiping = false;
        mListItemView = null;
    }

    /**
     * A swipe animation has started.
     */
    private void onSwipeAnimationStart(final ConversationListItemView itemView) {
        // Disallow interactions.
        itemView.setAnimating(true);
        ViewCompat.setHasTransientState(itemView, true);
        setHardwareAnimatingLayerType(itemView, ANIMATING);
    }

    /**
     * The swipe animation has ended.
     */
    private void onSwipeAnimationEnd(final ConversationListItemView itemView) {
        // Restore interactions.
        itemView.setAnimating(false);
        ViewCompat.setHasTransientState(itemView, false);
        setHardwareAnimatingLayerType(itemView, !ANIMATING);
    }

    /**
     * Animate the dismissal of the given item. The given velocityX is taken into consideration for
     * the animation duration. Whether the item is dismissed to the left or right is dependent on
     * the given velocityX.
     */
    private void animateDismiss(final ConversationListItemView itemView, final float velocityX) {
        Assert.isTrue(velocityX != 0);
        final int direction = velocityX > 0 ? SWIPE_DIRECTION_RIGHT : SWIPE_DIRECTION_LEFT;
        animateDismiss(itemView, direction, velocityX);
    }

    /**
     * Animate the dismissal of the given item. The velocityX is assumed to be 0.
     */
    private void animateDismiss(final ConversationListItemView itemView, final int swipeDirection) {
        animateDismiss(itemView, swipeDirection, 0f);
    }

    /**
     * Animate the dismissal of the given item.
     */
    private void animateDismiss(final ConversationListItemView itemView,
            final int swipeDirection, final float velocityX) {
        Assert.isTrue(swipeDirection != SWIPE_DIRECTION_NONE);

        onSwipeAnimationStart(itemView);

        final float animateTo = (swipeDirection == SWIPE_DIRECTION_RIGHT) ?
                mRecyclerView.getWidth() : -mRecyclerView.getWidth();
        final long duration;
        if (velocityX != 0) {
            final float deltaX = animateTo - itemView.getSwipeTranslationX();
            duration = calculateTranslationDuration(deltaX, velocityX);
        } else {
            duration = mDefaultDismissAnimationDuration;
        }

        final ObjectAnimator animator = getSwipeTranslationXAnimator(
                itemView, animateTo, duration, UiUtils.DEFAULT_INTERPOLATOR);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                onSwipeAnimationEnd(itemView);
            }
        });
        animator.start();
    }

    /**
     * Animate the bounce back of the given item.
     */
    private void animateRestore(final ConversationListItemView itemView,
            final float velocityX) {
        onSwipeAnimationStart(itemView);

        final float translationX = itemView.getSwipeTranslationX();
        final long duration;
        if (velocityX != 0 // Has velocity.
                && velocityX > 0 != translationX > 0) { // Right direction.
            duration = calculateTranslationDuration(translationX, velocityX);
        } else {
            duration = mDefaultRestoreAnimationDuration;
        }
        final ObjectAnimator animator = getSwipeTranslationXAnimator(
                itemView, 0f, duration, UiUtils.DEFAULT_INTERPOLATOR);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                       onSwipeAnimationEnd(itemView);
            }
        });
        animator.start();
    }

    /**
     * Create and start an animator that animates the given view's translationX
     * from its current value to the value given by animateTo.
     */
    private ObjectAnimator getSwipeTranslationXAnimator(final ConversationListItemView itemView,
            final float animateTo, final long duration, final TimeInterpolator interpolator) {
        final ObjectAnimator animator =
                ObjectAnimator.ofFloat(itemView, "swipeTranslationX", animateTo);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        return animator;
    }

    /**
     * Determine if the swipe has enough velocity to be dismissed.
     */
    private boolean isTargetSwipedFastEnough() {
        final float velocityX = getLastComputedXVelocity();
        final float velocityY = mVelocityTracker.getYVelocity();
        final float minVelocity = mMinimumFlingVelocity;
        final float translationX = mListItemView.getSwipeTranslationX();
        final float width = mListItemView.getWidth();
        return (Math.abs(velocityX) > minVelocity) // Fast enough.
                && (Math.abs(velocityX) > Math.abs(velocityY)) // Not unintentional.
                && (velocityX > 0) == (translationX > 0) // Right direction.
                && Math.abs(translationX) >
                    FLING_PERCENTAGE_OF_WIDTH_TO_DISMISS * width; // Enough movement.
  }

    /**
     * Only used during a swipe gesture. Determine if the swipe has enough distance to be
     * dismissed.
     */
    private boolean isTargetSwipedFarEnough() {
        final float velocityX = getLastComputedXVelocity();

        final float translationX = mListItemView.getSwipeTranslationX();
        final float width = mListItemView.getWidth();

        return (velocityX >= 0) == (translationX > 0) // Right direction.
                && Math.abs(translationX) >
                    PERCENTAGE_OF_WIDTH_TO_DISMISS * width; // Enough movement.
  }

    private long calculateTranslationDuration(final float deltaPosition, final float velocity) {
        Assert.isTrue(velocity != 0);
        final float durationInSeconds = Math.abs(deltaPosition / velocity);
        return Math.min((int) (durationInSeconds * UNIT_SECONDS), mMaxTranslationAnimationDuration);
    }

    private boolean hasGestureSwipeTarget() {
        return mListItemView != null;
    }

    private boolean hasValidGestureSwipeTarget() {
        return hasGestureSwipeTarget() && mListItemView.getParent() == mRecyclerView;
    }

    /**
     * Enable a hardware layer for the it view and build that layer.
     */
    private void setHardwareAnimatingLayerType(final ConversationListItemView itemView,
            final boolean animating) {
        if (animating) {
            itemView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (itemView.getWindowToken() != null) {
                itemView.buildLayer();
            }
        } else {
            itemView.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private float getLastComputedXVelocity() {
        return mVelocityTracker.getXVelocity();
    }
}