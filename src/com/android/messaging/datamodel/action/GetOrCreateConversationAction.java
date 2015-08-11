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

package com.android.messaging.datamodel.action;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.action.ActionMonitor.ActionCompletedListener;
import com.android.messaging.datamodel.data.LaunchConversationData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.RunsOnMainThread;
import com.android.messaging.util.LogUtil;

import java.util.ArrayList;

/**
 * Action used to get or create a conversation for a list of conversation participants.
 */
public class GetOrCreateConversationAction extends Action implements Parcelable {
    /**
     * Interface for GetOrCreateConversationAction listeners
     */
    public interface GetOrCreateConversationActionListener {
        @RunsOnMainThread
        abstract void onGetOrCreateConversationSucceeded(final ActionMonitor monitor,
                final Object data, final String conversationId);

        @RunsOnMainThread
        abstract void onGetOrCreateConversationFailed(final ActionMonitor monitor,
                final Object data);
    }

    public static GetOrCreateConversationActionMonitor getOrCreateConversation(
            final ArrayList<ParticipantData> participants, final Object data,
            final GetOrCreateConversationActionListener listener) {
        final GetOrCreateConversationActionMonitor monitor = new
                GetOrCreateConversationActionMonitor(data, listener);
        final GetOrCreateConversationAction action = new GetOrCreateConversationAction(participants,
                monitor.getActionKey());
        action.start(monitor);
        return monitor;
    }


    public static GetOrCreateConversationActionMonitor getOrCreateConversation(
            final String[] recipients, final Object data, final LaunchConversationData listener) {
        final ArrayList<ParticipantData> participants = new ArrayList<>();
        for (String recipient : recipients) {
            recipient = recipient.trim();
            if (!TextUtils.isEmpty(recipient)) {
                participants.add(ParticipantData.getFromRawPhoneBySystemLocale(recipient));
            } else {
                LogUtil.w(LogUtil.BUGLE_TAG, "getOrCreateConversation hit empty recipient");
            }
        }
        return getOrCreateConversation(participants, data, listener);
    }

    private static final String KEY_PARTICIPANTS_LIST = "participants_list";

    private GetOrCreateConversationAction(final ArrayList<ParticipantData> participants,
            final String actionKey) {
        super(actionKey);
        actionParameters.putParcelableArrayList(KEY_PARTICIPANTS_LIST, participants);
    }

    /**
     * Lookup the conversation or create a new one.
     */
    @Override
    protected Object executeAction() {
        final DatabaseWrapper db = DataModel.get().getDatabase();

        // First find the thread id for this list of participants.
        final ArrayList<ParticipantData> participants =
                actionParameters.getParcelableArrayList(KEY_PARTICIPANTS_LIST);
        BugleDatabaseOperations.sanitizeConversationParticipants(participants);
        final ArrayList<String> recipients =
                BugleDatabaseOperations.getRecipientsFromConversationParticipants(participants);

        final long threadId = MmsUtils.getOrCreateThreadId(Factory.get().getApplicationContext(),
                recipients);

        if (threadId < 0) {
            LogUtil.w(LogUtil.BUGLE_TAG, "Couldn't create a threadId in SMS db for numbers : " +
                    LogUtil.sanitizePII(recipients.toString()));
            // TODO: Add a better way to indicate an error from executeAction.
            return null;
        }

        final String conversationId = BugleDatabaseOperations.getOrCreateConversation(db, threadId,
                false, participants, false, false, null);

        return conversationId;
    }

    /**
     * A monitor that notifies a listener upon completion
     */
    public static class GetOrCreateConversationActionMonitor extends ActionMonitor
            implements ActionCompletedListener {
        private final GetOrCreateConversationActionListener mListener;

        GetOrCreateConversationActionMonitor(final Object data,
                final GetOrCreateConversationActionListener listener) {
            super(STATE_CREATED, generateUniqueActionKey("GetOrCreateConversationAction"), data);
            setCompletedListener(this);
            mListener = listener;
        }

        @Override
        public void onActionSucceeded(final ActionMonitor monitor,
                final Action action, final Object data, final Object result) {
            if (result == null) {
                mListener.onGetOrCreateConversationFailed(monitor, data);
            } else {
                mListener.onGetOrCreateConversationSucceeded(monitor, data, (String) result);
            }
        }

        @Override
        public void onActionFailed(final ActionMonitor monitor,
                final Action action, final Object data, final Object result) {
            // TODO: Currently onActionFailed is only called if there is an error in
            // processing requests, not for errors in the local processing.
            Assert.fail("Unreachable");
            mListener.onGetOrCreateConversationFailed(monitor, data);
        }
    }

    private GetOrCreateConversationAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<GetOrCreateConversationAction> CREATOR
            = new Parcelable.Creator<GetOrCreateConversationAction>() {
        @Override
        public GetOrCreateConversationAction createFromParcel(final Parcel in) {
            return new GetOrCreateConversationAction(in);
        }

        @Override
        public GetOrCreateConversationAction[] newArray(final int size) {
            return new GetOrCreateConversationAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
