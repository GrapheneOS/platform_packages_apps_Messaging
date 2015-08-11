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

import android.os.Parcel;
import android.os.Parcelable;

import com.android.messaging.ui.contact.ContactPickerFragment;
import com.android.messaging.util.Assert;
import com.google.common.annotations.VisibleForTesting;

/**
 * Keeps track of the different UI states that the ConversationActivity may be in. This acts as
 * a state machine which, based on different actions (e.g. onAddMoreParticipants), notifies the
 * ConversationActivity about any state UI change so it can update the visuals. This class
 * implements Parcelable and it's persisted across activity tear down and relaunch.
 */
public class ConversationActivityUiState implements Parcelable, Cloneable {
    interface ConversationActivityUiStateHost {
        void onConversationContactPickerUiStateChanged(int oldState, int newState, boolean animate);
    }

    /*------ Overall UI states (conversation & contact picker) ------*/

    /** Only a full screen conversation is showing. */
    public static final int STATE_CONVERSATION_ONLY = 1;
    /** Only a full screen contact picker is showing asking user to pick the initial contact. */
    public static final int STATE_CONTACT_PICKER_ONLY_INITIAL_CONTACT = 2;
    /**
     * Only a full screen contact picker is showing asking user to pick more participants. This
     * happens after the user picked the initial contact, and then decide to go back and add more.
     */
    public static final int STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS = 3;
    /**
     * Only a full screen contact picker is showing asking user to pick more participants. However
     * user has reached max number of conversation participants and can add no more.
     */
    public static final int STATE_CONTACT_PICKER_ONLY_MAX_PARTICIPANTS = 4;
    /**
     * A hybrid mode where the conversation view + contact chips view are showing. This happens
     * right after the user picked the initial contact for which a 1-1 conversation is fetched or
     * created.
     */
    public static final int STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW = 5;

    // The overall UI state of the ConversationActivity.
    private int mConversationContactUiState;

    // The currently displayed conversation (if any).
    private String mConversationId;

    // Indicates whether we should put focus in the compose message view when the
    // ConversationFragment is attached. This is a transient state that's not persisted as
    // part of the parcelable.
    private boolean mPendingResumeComposeMessage = false;

    // The owner ConversationActivity. This is not parceled since the instance always change upon
    // object reuse.
    private ConversationActivityUiStateHost mHost;

    // Indicates the owning ConverastionActivity is in the process of updating its UI presentation
    // to be in sync with the UI states. Outside of the UI updates, the UI states here should
    // ALWAYS be consistent with the actual states of the activity.
    private int mUiUpdateCount;

    /**
     * Create a new instance with an initial conversation id.
     */
    ConversationActivityUiState(final String conversationId) {
        // The conversation activity may be initialized with only one of two states:
        // Conversation-only (when there's a conversation id) or picking initial contact
        // (when no conversation id is given).
        mConversationId = conversationId;
        mConversationContactUiState = conversationId == null ?
                STATE_CONTACT_PICKER_ONLY_INITIAL_CONTACT : STATE_CONVERSATION_ONLY;
    }

    public void setHost(final ConversationActivityUiStateHost host) {
        mHost = host;
    }

    public boolean shouldShowConversationFragment() {
        return mConversationContactUiState == STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW ||
                mConversationContactUiState == STATE_CONVERSATION_ONLY;
    }

    public boolean shouldShowContactPickerFragment() {
        return mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS ||
                mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_MAX_PARTICIPANTS ||
                mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_INITIAL_CONTACT ||
                mConversationContactUiState == STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW;
    }

    /**
     * Returns whether there's a pending request to resume message compose (i.e. set focus to
     * the compose message view and show the soft keyboard). If so, this request will be served
     * when the conversation fragment get created and resumed. This happens when the user commits
     * participant selection for a group conversation and goes back to the conversation fragment.
     * Since conversation fragment creation happens asynchronously, we issue and track this
     * pending request for it to be eventually fulfilled.
     */
    public boolean shouldResumeComposeMessage() {
        if (mPendingResumeComposeMessage) {
            // This is a one-shot operation that just keeps track of the pending resume compose
            // state. This is also a non-critical operation so we don't care about failure case.
            mPendingResumeComposeMessage = false;
            return true;
        }
        return false;
    }

    public int getDesiredContactPickingMode() {
        switch (mConversationContactUiState) {
            case STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS:
                return ContactPickerFragment.MODE_PICK_MORE_CONTACTS;
            case STATE_CONTACT_PICKER_ONLY_MAX_PARTICIPANTS:
                return ContactPickerFragment.MODE_PICK_MAX_PARTICIPANTS;
            case STATE_CONTACT_PICKER_ONLY_INITIAL_CONTACT:
                return ContactPickerFragment.MODE_PICK_INITIAL_CONTACT;
            case STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW:
                return ContactPickerFragment.MODE_CHIPS_ONLY;
            default:
                Assert.fail("Invalid contact picking mode for ConversationActivity!");
                return ContactPickerFragment.MODE_UNDEFINED;
        }
    }

    public String getConversationId() {
        return mConversationId;
    }

    /**
     * Called whenever the contact picker fragment successfully fetched or created a conversation.
     */
    public void onGetOrCreateConversation(final String conversationId) {
        int newState = STATE_CONVERSATION_ONLY;
        if (mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_INITIAL_CONTACT) {
            newState = STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW;
        } else if (mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS ||
                mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_MAX_PARTICIPANTS) {
            newState = STATE_CONVERSATION_ONLY;
        } else {
            // New conversation should only be created when we are in one of the contact picking
            // modes.
            Assert.fail("Invalid conversation activity state: can't create conversation!");
        }
        mConversationId = conversationId;
        performUiStateUpdate(newState, true);
    }

    /**
     * Called when the user started composing message. If we are in the hybrid chips state, we
     * should commit to enter the conversation only state.
     */
    public void onStartMessageCompose() {
        // This cannot happen when we are in one of the full-screen contact picking states.
        Assert.isTrue(mConversationContactUiState != STATE_CONTACT_PICKER_ONLY_INITIAL_CONTACT &&
                mConversationContactUiState != STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS &&
                mConversationContactUiState != STATE_CONTACT_PICKER_ONLY_MAX_PARTICIPANTS);
        if (mConversationContactUiState == STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW) {
            performUiStateUpdate(STATE_CONVERSATION_ONLY, true);
        }
    }

    /**
     * Called when the user initiated an action to add more participants in the hybrid state,
     * namely clicking on the "add more participants" button or entered a new contact chip via
     * auto-complete.
     */
    public void onAddMoreParticipants() {
        if (mConversationContactUiState == STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW) {
            mPendingResumeComposeMessage = true;
            performUiStateUpdate(STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS, true);
        } else {
            // This is only possible in the hybrid state.
            Assert.fail("Invalid conversation activity state: can't add more participants!");
        }
    }

    /**
     * Called each time the number of participants is updated to check against the limit and
     * update the ui state accordingly.
     */
    public void onParticipantCountUpdated(final boolean canAddMoreParticipants) {
        if (mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS
                && !canAddMoreParticipants) {
            performUiStateUpdate(STATE_CONTACT_PICKER_ONLY_MAX_PARTICIPANTS, false);
        } else if (mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_MAX_PARTICIPANTS
                && canAddMoreParticipants) {
            performUiStateUpdate(STATE_CONTACT_PICKER_ONLY_ADD_MORE_CONTACTS, false);
        }
    }

    private void performUiStateUpdate(final int conversationContactState, final boolean animate) {
        // This starts one UI update cycle, during which we allow the conversation activity's
        // UI presentation to be temporarily out of sync with the states here.
        beginUiUpdate();

        if (conversationContactState != mConversationContactUiState) {
            final int oldState = mConversationContactUiState;
            mConversationContactUiState = conversationContactState;
            notifyOnOverallUiStateChanged(oldState, mConversationContactUiState, animate);
        }
        endUiUpdate();
    }

    private void notifyOnOverallUiStateChanged(
            final int oldState, final int newState, final boolean animate) {
        // Always verify state validity whenever we have a state change.
        assertValidState();
        Assert.isTrue(isUiUpdateInProgress());

        // Only do this if we are still attached to the host. mHost can be null if the host
        // activity is already destroyed, but due to timing the contained UI components may still
        // receive events such as focus change and trigger a callback to the Ui state. We'd like
        // to guard against those cases.
        if (mHost != null) {
            mHost.onConversationContactPickerUiStateChanged(oldState, newState, animate);
        }
    }

    private void assertValidState() {
        // Conversation id may be null IF AND ONLY IF the user is picking the initial contact to
        // start a conversation.
        Assert.isTrue((mConversationContactUiState == STATE_CONTACT_PICKER_ONLY_INITIAL_CONTACT) ==
                (mConversationId == null));
    }

    private void beginUiUpdate() {
        mUiUpdateCount++;
    }

    private void endUiUpdate() {
        if (--mUiUpdateCount < 0) {
            Assert.fail("Unbalanced Ui updates!");
        }
    }

    private boolean isUiUpdateInProgress() {
        return mUiUpdateCount > 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeInt(mConversationContactUiState);
        dest.writeString(mConversationId);
    }

    private ConversationActivityUiState(final Parcel in) {
        mConversationContactUiState = in.readInt();
        mConversationId = in.readString();

        // Always verify state validity whenever we initialize states.
        assertValidState();
    }

    public static final Parcelable.Creator<ConversationActivityUiState> CREATOR
        = new Parcelable.Creator<ConversationActivityUiState>() {
        @Override
        public ConversationActivityUiState createFromParcel(final Parcel in) {
            return new ConversationActivityUiState(in);
        }

        @Override
        public ConversationActivityUiState[] newArray(final int size) {
            return new ConversationActivityUiState[size];
        }
    };

    @Override
    protected ConversationActivityUiState clone() {
        try {
            return (ConversationActivityUiState) super.clone();
        } catch (CloneNotSupportedException e) {
            Assert.fail("ConversationActivityUiState: failed to clone(). Is there a mutable " +
                    "reference?");
        }
        return null;
    }

    /**
     * allows for overridding the internal UI state. Should never be called except by test code.
     */
    @VisibleForTesting
    void testSetUiState(final int uiState) {
        mConversationContactUiState = uiState;
    }
}
