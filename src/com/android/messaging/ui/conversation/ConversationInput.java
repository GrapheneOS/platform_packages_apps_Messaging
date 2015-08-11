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

import android.os.Bundle;
import android.support.v7.app.ActionBar;

/**
 * The base class for a method of user input, e.g. media picker.
 */
public abstract class ConversationInput {
    /**
     * The host component where all input components are contained. This is typically the
     * conversation fragment but may be mocked in test code.
     */
    public interface ConversationInputBase {
        boolean showHideInternal(final ConversationInput target, final boolean show,
                final boolean animate);
        String getInputStateKey(final ConversationInput input);
        void beginUpdate();
        void handleOnShow(final ConversationInput target);
        void endUpdate();
    }

    protected boolean mShowing;
    protected ConversationInputBase mConversationInputBase;

    public abstract boolean show(boolean animate);
    public abstract boolean hide(boolean animate);

    public ConversationInput(ConversationInputBase baseHost, final boolean isShowing) {
        mConversationInputBase = baseHost;
        mShowing = isShowing;
    }

    public boolean onBackPressed() {
        if (mShowing) {
            mConversationInputBase.showHideInternal(this, false /* show */, true /* animate */);
            return true;
        }
        return false;
    }

    public boolean onNavigationUpPressed() {
        return false;
    }

    /**
     * Toggle the visibility of this view.
     * @param animate
     * @return true if the view is now shown, false if it now hidden
     */
    public boolean toggle(final boolean animate) {
        mConversationInputBase.showHideInternal(this, !mShowing /* show */, true /* animate */);
        return mShowing;
    }

    public void saveState(final Bundle savedState) {
        savedState.putBoolean(mConversationInputBase.getInputStateKey(this), mShowing);
    }

    public void restoreState(final Bundle savedState) {
        // Things are hidden by default, so only handle show.
        if (savedState.getBoolean(mConversationInputBase.getInputStateKey(this))) {
            mConversationInputBase.showHideInternal(this, true /* show */, false /* animate */);
        }
    }

    public boolean updateActionBar(final ActionBar actionBar) {
        return false;
    }

    /**
     * Update our visibility flag in response to visibility change, both for actions
     * initiated by this class (through the show/hide methods), and for external changes
     * tracked by event listeners (e.g. ImeStateObserver, MediaPickerListener). As part of
     * handling an input showing, we will hide all other inputs to ensure they are mutually
     * exclusive.
     */
    protected void onVisibilityChanged(final boolean visible) {
        if (mShowing != visible) {
            mConversationInputBase.beginUpdate();
            mShowing = visible;
            if (visible) {
                mConversationInputBase.handleOnShow(this);
            }
            mConversationInputBase.endUpdate();
        }
    }
}
