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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.util.Assert;

import java.util.ArrayList;
import java.util.List;

public class SnackBar {
    public static final int LONG_DURATION_IN_MS = 5000;
    public static final int SHORT_DURATION_IN_MS = 1000;
    public static final int MAX_DURATION_IN_MS = 10000;

    public interface SnackBarListener {
        void onActionClick();
    }

    /**
     * Defines an action to be performed when the user clicks on the action button on the snack bar
     */
    public static class Action {
        private final Runnable mActionRunnable;
        private final String mActionLabel;

        public final static int SNACK_BAR_UNDO = 0;
        public final static int SNACK_BAR_RETRY = 1;

        private Action(@Nullable Runnable actionRunnable, @Nullable String actionLabel) {
            mActionRunnable = actionRunnable;
            mActionLabel = actionLabel;
        }

        Runnable getActionRunnable() {
            return mActionRunnable;
        }

        String getActionLabel() {
            return mActionLabel;
        }

        public static Action createUndoAction(final Runnable undoRunnable) {
            return createCustomAction(undoRunnable, Factory.get().getApplicationContext()
                    .getString(R.string.snack_bar_undo));
        }

        public static Action createRetryAction(final Runnable retryRunnable) {
            return createCustomAction(retryRunnable, Factory.get().getApplicationContext()
                    .getString(R.string.snack_bar_retry));
        }


        public static Action createCustomAction(final Runnable runnable, final String actionLabel) {
            return new Action(runnable, actionLabel);
        }
    }

    /**
     * Defines the placement of the snack bar (e.g. anchored view, anchor gravity).
     */
    public static class Placement {
        private final View mAnchorView;
        private final boolean mAnchorAbove;

        private Placement(@NonNull final View anchorView, final boolean anchorAbove) {
            Assert.notNull(anchorView);
            mAnchorView = anchorView;
            mAnchorAbove = anchorAbove;
        }

        public View getAnchorView() {
            return mAnchorView;
        }

        public boolean getAnchorAbove() {
            return mAnchorAbove;
        }

        /**
         * Anchor the snack bar above the given {@code anchorView}.
         */
        public static Placement above(final View anchorView) {
            return new Placement(anchorView, true);
        }

        /**
         * Anchor the snack bar below the given {@code anchorView}.
         */
        public static Placement below(final View anchorView) {
            return new Placement(anchorView, false);
        }
    }

    public static class Builder {
        private static final List<SnackBarInteraction> NO_INTERACTIONS = 
            new ArrayList<SnackBarInteraction>();

        private final Context mContext;
        private final SnackBarManager mSnackBarManager;

        private String mSnackBarMessage;
        private int mDuration = LONG_DURATION_IN_MS;
        private List<SnackBarInteraction> mInteractions = NO_INTERACTIONS;
        private Action mAction;
        private Placement mPlacement;
        // The parent view is only used to get a window token and doesn't affect the layout
        private View mParentView;

        public Builder(final SnackBarManager snackBarManager, final View parentView) {
            Assert.notNull(snackBarManager);
            Assert.notNull(parentView);
            mSnackBarManager = snackBarManager;
            mContext = parentView.getContext();
            mParentView = parentView;
        }

        public Builder setText(final String snackBarMessage) {
            Assert.isTrue(!TextUtils.isEmpty(snackBarMessage));
            mSnackBarMessage = snackBarMessage;
            return this;
        }

        public Builder setAction(final Action action) {
            mAction = action;
            return this;
        }

        /**
         * Sets the duration to show this toast for in milliseconds.
         */
        public Builder setDuration(final int duration) {
            Assert.isTrue(0 < duration && duration < MAX_DURATION_IN_MS);
            mDuration = duration;
            return this;
        }

        /**
         * Sets the components that this toast's animation will interact with. These components may
         * be animated to make room for the toast.
         */
        public Builder withInteractions(final List<SnackBarInteraction> interactions) {
            mInteractions = interactions;
            return this;
        }

        /**
         * Place the snack bar with the given placement requirement.
         */
        public Builder withPlacement(final Placement placement) {
            Assert.isNull(mPlacement);
            mPlacement = placement;
            return this;
        }

        public SnackBar build() {
            return new SnackBar(this);
        }

        public void show() {
            mSnackBarManager.show(build());
        }
    }

    private final View mRootView;
    private final Context mContext;
    private final View mSnackBarView;
    private final String mText;
    private final int mDuration;
    private final List<SnackBarInteraction> mInteractions;
    private final Action mAction;
    private final Placement mPlacement;
    private final TextView mActionTextView;
    private final TextView mMessageView;
    private final FrameLayout mMessageWrapper;
    private final View mParentView;

    private SnackBarListener mListener;

    private SnackBar(final Builder builder) {
        mContext = builder.mContext;
        mRootView = LayoutInflater.from(mContext).inflate(R.layout.snack_bar,
            null /* WindowManager will show this in show() below */);
        mSnackBarView = mRootView.findViewById(R.id.snack_bar);
        mText = builder.mSnackBarMessage;
        mDuration = builder.mDuration;
        mAction = builder.mAction;
        mPlacement = builder.mPlacement;
        mParentView = builder.mParentView;
        if (builder.mInteractions == null) {
            mInteractions = new ArrayList<SnackBarInteraction>();
        } else {
            mInteractions = builder.mInteractions;
        }

        mActionTextView = (TextView) mRootView.findViewById(R.id.snack_bar_action);
        mMessageView = (TextView) mRootView.findViewById(R.id.snack_bar_message);
        mMessageWrapper = (FrameLayout) mRootView.findViewById(R.id.snack_bar_message_wrapper);

        setUpButton();
        setUpTextLines();
    }

    public Context getContext() {
        return mContext;
    }

    public View getRootView() {
        return mRootView;
    }

    public View getParentView() {
        return mParentView;
    }

    public View getSnackBarView() {
        return mSnackBarView;
    }

    public String getMessageText() {
        return mText;
    }

    public String getActionLabel() {
        if (mAction == null) {
            return null;
        }
        return mAction.getActionLabel();
    }

    public int getDuration() {
        return mDuration;
    }

    public Placement getPlacement() {
        return mPlacement;
    }

    public List<SnackBarInteraction> getInteractions() {
        return mInteractions;
    }

    public void setEnabled(final boolean enabled) {
        mActionTextView.setClickable(enabled);
    }

    public void setListener(final SnackBarListener snackBarListener) {
        mListener = snackBarListener;
    }

    private void setUpButton() {
        if (mAction == null || mAction.getActionRunnable() == null) {
            mActionTextView.setVisibility(View.GONE);
            // In the XML layout we add left/right padding to the button to add space between
            // the message text and the button and on the right side we add padding to put space
            // between the button and the edge of the snack bar. This is so the button can use the
            // padding area as part of it's click target. Since we have no button, we need to put
            // some margin on the right side. While the left margin is already set on the wrapper,
            // we're setting it again to not have to make a special case for RTL.
            final MarginLayoutParams lp = (MarginLayoutParams) mMessageWrapper.getLayoutParams();
            final int leftRightMargin = mContext.getResources().getDimensionPixelSize(
                    R.dimen.snack_bar_left_right_margin);
            lp.leftMargin = leftRightMargin;
            lp.rightMargin = leftRightMargin;
            mMessageWrapper.setLayoutParams(lp);
        } else {
            mActionTextView.setVisibility(View.VISIBLE);
            mActionTextView.setText(mAction.getActionLabel());
            mActionTextView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    mAction.getActionRunnable().run();
                    if (mListener != null) {
                        mListener.onActionClick();
                    }
                }
            });
        }
    }

    private void setUpTextLines() {
        if (mText == null) {
            mMessageView.setVisibility(View.GONE);
        } else {
            mMessageView.setVisibility(View.VISIBLE);
            mMessageView.setText(mText);
        }
    }
}
