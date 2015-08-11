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

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.util.Assert;

public class UpdateDestinationBlockedAction extends Action {
    public interface UpdateDestinationBlockedActionListener {
        @Assert.RunsOnMainThread
        abstract void onUpdateDestinationBlockedAction(final UpdateDestinationBlockedAction action,
                                                       final boolean success,
                                                       final boolean block,
                                                       final String destination);
    }

    public static class UpdateDestinationBlockedActionMonitor extends ActionMonitor
            implements ActionMonitor.ActionCompletedListener {
        private final UpdateDestinationBlockedActionListener mListener;

        public UpdateDestinationBlockedActionMonitor(
                Object data, UpdateDestinationBlockedActionListener mListener) {
            super(STATE_CREATED, generateUniqueActionKey("UpdateDestinationBlockedAction"), data);
            setCompletedListener(this);
            this.mListener = mListener;
        }

        private void onActionDone(final boolean succeeded,
                                  final ActionMonitor monitor,
                                  final Action action,
                                  final Object data,
                                  final Object result) {
            mListener.onUpdateDestinationBlockedAction(
                    (UpdateDestinationBlockedAction) action,
                    succeeded,
                    action.actionParameters.getBoolean(KEY_BLOCKED),
                    action.actionParameters.getString(KEY_DESTINATION));
        }

        @Override
        public void onActionSucceeded(final ActionMonitor monitor,
                                      final Action action,
                                      final Object data,
                                      final Object result) {
            onActionDone(true, monitor, action, data, result);
        }

        @Override
        public void onActionFailed(final ActionMonitor monitor,
                                   final Action action,
                                   final Object data,
                                   final Object result) {
            onActionDone(false, monitor, action, data, result);
        }
    }


    public static UpdateDestinationBlockedActionMonitor updateDestinationBlocked(
            final String destination, final boolean blocked, final String conversationId,
            final UpdateDestinationBlockedActionListener listener) {
        Assert.notNull(listener);
        final UpdateDestinationBlockedActionMonitor monitor =
                new UpdateDestinationBlockedActionMonitor(null, listener);
        final UpdateDestinationBlockedAction action =
                new UpdateDestinationBlockedAction(destination, blocked, conversationId,
                        monitor.getActionKey());
        action.start(monitor);
        return monitor;
    }

    private static final String KEY_CONVERSATION_ID = "conversation_id";
    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_BLOCKED = "blocked";

    protected UpdateDestinationBlockedAction(
            final String destination, final boolean blocked, final String conversationId,
            final String actionKey) {
        super(actionKey);
        Assert.isTrue(!TextUtils.isEmpty(destination));
        actionParameters.putString(KEY_DESTINATION, destination);
        actionParameters.putBoolean(KEY_BLOCKED, blocked);
        actionParameters.putString(KEY_CONVERSATION_ID, conversationId);
    }

    @Override
    protected Object executeAction() {
        final String destination = actionParameters.getString(KEY_DESTINATION);
        final boolean isBlocked = actionParameters.getBoolean(KEY_BLOCKED);
        String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        final DatabaseWrapper db = DataModel.get().getDatabase();
        BugleDatabaseOperations.updateDestination(db, destination, isBlocked);
        if (conversationId == null) {
            conversationId = BugleDatabaseOperations
                    .getConversationFromOtherParticipantDestination(db, destination);
        }
        if (conversationId != null) {
            if (isBlocked) {
                UpdateConversationArchiveStatusAction.archiveConversation(conversationId);
            } else {
                UpdateConversationArchiveStatusAction.unarchiveConversation(conversationId);
            }
            MessagingContentProvider.notifyParticipantsChanged(conversationId);
        }
        return null;
    }

    protected UpdateDestinationBlockedAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<UpdateDestinationBlockedAction> CREATOR
            = new Parcelable.Creator<UpdateDestinationBlockedAction>() {
        @Override
        public UpdateDestinationBlockedAction createFromParcel(final Parcel in) {
            return new UpdateDestinationBlockedAction(in);
        }

        @Override
        public UpdateDestinationBlockedAction[] newArray(final int size) {
            return new UpdateDestinationBlockedAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
