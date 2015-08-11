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
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.util.LogUtil;

public class WriteDraftMessageAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    /**
     * Set draft message (no listener)
     */
    public static void writeDraftMessage(final String conversationId, final MessageData message) {
        final WriteDraftMessageAction action = new WriteDraftMessageAction(conversationId, message);
        action.start();
    }

    private static final String KEY_CONVERSATION_ID = "conversationId";
    private static final String KEY_MESSAGE = "message";

    private WriteDraftMessageAction(final String conversationId, final MessageData message) {
        actionParameters.putString(KEY_CONVERSATION_ID, conversationId);
        actionParameters.putParcelable(KEY_MESSAGE, message);
    }

    @Override
    protected Object executeAction() {
        final DatabaseWrapper db = DataModel.get().getDatabase();
        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        final MessageData message = actionParameters.getParcelable(KEY_MESSAGE);
        if (message.getSelfId() == null || message.getParticipantId() == null) {
            // This could happen when this occurs before the draft message is loaded
            // In this case, we just use the conversation's current self id as draft's
            // self id and/or participant id
            final ConversationListItemData conversation =
                    ConversationListItemData.getExistingConversation(db, conversationId);
            if (conversation != null) {
                final String senderAndSelf = conversation.getSelfId();
                if (message.getSelfId() == null) {
                    message.bindSelfId(senderAndSelf);
                }
                if (message.getParticipantId() == null) {
                    message.bindParticipantId(senderAndSelf);
                }
            } else {
                LogUtil.w(LogUtil.BUGLE_DATAMODEL_TAG, "Conversation " + conversationId +
                        "already deleted before saving draft message " +
                        message.getMessageId() + ". Aborting WriteDraftMessageAction.");
                return null;
            }
        }
        // Drafts are only kept in the local DB...
        final String messageId = BugleDatabaseOperations.updateDraftMessageData(
                db, conversationId, message, BugleDatabaseOperations.UPDATE_MODE_ADD_DRAFT);
        MessagingContentProvider.notifyConversationListChanged();
        MessagingContentProvider.notifyConversationMetadataChanged(conversationId);
        return messageId;
    }

    private WriteDraftMessageAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<WriteDraftMessageAction> CREATOR
            = new Parcelable.Creator<WriteDraftMessageAction>() {
        @Override
        public WriteDraftMessageAction createFromParcel(final Parcel in) {
            return new WriteDraftMessageAction(in);
        }

        @Override
        public WriteDraftMessageAction[] newArray(final int size) {
            return new WriteDraftMessageAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
