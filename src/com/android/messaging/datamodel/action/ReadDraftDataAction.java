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

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.action.ActionMonitor.ActionCompletedListener;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.RunsOnMainThread;
import com.android.messaging.util.LogUtil;
import com.google.common.annotations.VisibleForTesting;

public class ReadDraftDataAction extends Action implements Parcelable {

    /**
     * Interface for ReadDraftDataAction listeners
     */
    public interface ReadDraftDataActionListener {
        @RunsOnMainThread
        abstract void onReadDraftDataSucceeded(final ReadDraftDataAction action,
                final Object data, final MessageData message,
                final ConversationListItemData conversation);
        @RunsOnMainThread
        abstract void onReadDraftDataFailed(final ReadDraftDataAction action, final Object data);
    }

    /**
     * Read draft message and associated data (with listener)
     */
    public static ReadDraftDataActionMonitor readDraftData(final String conversationId,
            final MessageData incomingDraft, final Object data,
            final ReadDraftDataActionListener listener) {
        final ReadDraftDataActionMonitor monitor = new ReadDraftDataActionMonitor(data,
                listener);
        final ReadDraftDataAction action = new ReadDraftDataAction(conversationId,
                incomingDraft, monitor.getActionKey());
        action.start(monitor);
        return monitor;
    }

    private static final String KEY_CONVERSATION_ID = "conversationId";
    private static final String KEY_INCOMING_DRAFT = "draftMessage";

    private ReadDraftDataAction(final String conversationId, final MessageData incomingDraft,
            final String actionKey) {
        super(actionKey);
        actionParameters.putString(KEY_CONVERSATION_ID, conversationId);
        actionParameters.putParcelable(KEY_INCOMING_DRAFT, incomingDraft);
    }

    @VisibleForTesting
    class DraftData {
        public final MessageData message;
        public final ConversationListItemData conversation;

        DraftData(final MessageData message, final ConversationListItemData conversation) {
            this.message = message;
            this.conversation = conversation;
        }
    }

    @Override
    protected Object executeAction() {
        final DatabaseWrapper db = DataModel.get().getDatabase();
        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        final MessageData incomingDraft = actionParameters.getParcelable(KEY_INCOMING_DRAFT);
        final ConversationListItemData conversation =
                ConversationListItemData.getExistingConversation(db, conversationId);
        MessageData message = null;
        if (conversation != null) {
            if (incomingDraft == null) {
                message = BugleDatabaseOperations.readDraftMessageData(db, conversationId,
                        conversation.getSelfId());
            }
            if (message == null) {
                message = MessageData.createDraftMessage(conversationId, conversation.getSelfId(),
                        incomingDraft);
                LogUtil.d(LogUtil.BUGLE_TAG, "ReadDraftMessage: created draft. "
                        + "conversationId=" + conversationId
                        + " selfId=" + conversation.getSelfId());
            } else {
                LogUtil.d(LogUtil.BUGLE_TAG, "ReadDraftMessage: read draft. "
                        + "conversationId=" + conversationId
                        + " selfId=" + conversation.getSelfId());
            }
            return new DraftData(message, conversation);
        }
        return null;
    }

    /**
     * An operation that notifies a listener upon completion
     */
    public static class ReadDraftDataActionMonitor extends ActionMonitor
            implements ActionCompletedListener {

        private final ReadDraftDataActionListener mListener;

        ReadDraftDataActionMonitor(final Object data,
                final ReadDraftDataActionListener completed) {
            super(STATE_CREATED, generateUniqueActionKey("ReadDraftDataAction"), data);
            setCompletedListener(this);
            mListener = completed;
        }

        @Override
        public void onActionSucceeded(final ActionMonitor monitor,
                final Action action, final Object data, final Object result) {
            final DraftData draft = (DraftData) result;
            if (draft == null) {
                mListener.onReadDraftDataFailed((ReadDraftDataAction) action, data);
            } else {
                mListener.onReadDraftDataSucceeded((ReadDraftDataAction) action, data,
                        draft.message, draft.conversation);
            }
        }

        @Override
        public void onActionFailed(final ActionMonitor monitor,
                final Action action, final Object data, final Object result) {
            Assert.fail("Reading draft should not fail");
        }
    }

    private ReadDraftDataAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<ReadDraftDataAction> CREATOR
            = new Parcelable.Creator<ReadDraftDataAction>() {
        @Override
        public ReadDraftDataAction createFromParcel(final Parcel in) {
            return new ReadDraftDataAction(in);
        }

        @Override
        public ReadDraftDataAction[] newArray(final int size) {
            return new ReadDraftDataAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
