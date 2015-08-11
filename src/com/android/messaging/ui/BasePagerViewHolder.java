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

import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

/**
 * The base pager view holder implementation that handles basic view creation/destruction logic,
 * as well as logic to save/restore instance state that's persisted not only for activity
 * reconstruction (e.g. during a configuration change), but also cases where the individual
 * page view gets destroyed and recreated.
 *
 * To opt into saving/restoring instance state behavior for a particular page view, let the
 * PageView implement PersistentInstanceState to save and restore instance states to/from a
 * Parcelable.
 */
public abstract class BasePagerViewHolder implements PagerViewHolder {
    protected View mView;
    protected Parcelable mSavedState;

    /**
     * This is called when the entire view pager is being torn down (due to configuration change
     * for example) that will be restored later.
     */
    @Override
    public Parcelable saveState() {
        savePendingState();
        return mSavedState;
    }

    /**
     * This is called when the view pager is being restored.
     */
    @Override
    public void restoreState(final Parcelable restoredState) {
        if (restoredState != null) {
            mSavedState = restoredState;
            // If the view is already there, let it restore the state. Otherwise, it will be
            // restored the next time the view gets created.
            restorePendingState();
        }
    }

    @Override
    public void resetState() {
        mSavedState = null;
        if (mView != null && (mView instanceof PersistentInstanceState)) {
            ((PersistentInstanceState) mView).resetState();
        }
    }

    /**
     * This is called when the view itself is being torn down. This may happen when the user
     * has flipped to another page in the view pager, so we want to save the current state if
     * possible.
     */
    @Override
    public View destroyView() {
        savePendingState();
        final View retView = mView;
        mView = null;
        return retView;
    }

    @Override
    public View getView(ViewGroup container) {
        if (mView == null) {
            mView = createView(container);
            // When initially created, check if the view has any saved state. If so restore it.
            restorePendingState();
        }
        return mView;
    }

    private void savePendingState() {
        if (mView != null && (mView instanceof PersistentInstanceState)) {
            mSavedState = ((PersistentInstanceState) mView).saveState();
        }
    }

    private void restorePendingState() {
        if (mView != null && (mView instanceof PersistentInstanceState) && (mSavedState != null)) {
            ((PersistentInstanceState) mView).restoreState(mSavedState);
        }
    }

    /**
     * Create and initialize a new page view.
     */
    protected abstract View createView(ViewGroup container);
}
