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

import android.view.Gravity;
import android.view.View;
import android.view.ViewPropertyAnimator;

import com.google.common.base.Preconditions;

/**
 * An interface that defines how a component can be animated with an {@link SnackBar}.
 */
public interface SnackBarInteraction {
    /**
     * Returns the animator that will be run in reaction to the given SnackBar being shown.
     *
     * Implementations may return null here if it determines that the given SnackBar does not need
     * to animate this component.
     */
    ViewPropertyAnimator animateOnSnackBarShow(SnackBar snackBar);

    /**
     * Returns the animator that will be run in reaction to the given SnackBar being dismissed.
     *
     * Implementations may return null here if it determines that the given SnackBar does not need
     * to animate this component.
     */
    ViewPropertyAnimator animateOnSnackBarDismiss(SnackBar snackBar);

    /**
     * A basic implementation of {@link SnackBarInteraction} that assumes that the
     * {@link SnackBar} is always shown with {@link Gravity#BOTTOM} and that the provided View will
     * always need to be translated up to make room for the SnackBar.
     */
    public static class BasicSnackBarInteraction implements SnackBarInteraction {
        private final View mView;

        public BasicSnackBarInteraction(final View view) {
            mView = Preconditions.checkNotNull(view);
        }

        @Override
        public ViewPropertyAnimator animateOnSnackBarShow(final SnackBar snackBar) {
            final View rootView = snackBar.getRootView();
            return mView.animate().translationY(-rootView.getMeasuredHeight());
        }

        @Override
        public ViewPropertyAnimator animateOnSnackBarDismiss(final SnackBar snackBar) {
            return mView.animate().translationY(0);
        }
    }
}