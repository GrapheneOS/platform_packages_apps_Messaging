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

import android.content.Context;
import android.content.res.Resources;
import android.widget.Toast;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.ThreadUtil;

import javax.annotation.Nullable;

/**
 * Shows one-time, transient notifications in response to action failures (i.e. permanent failures
 * when sending a message) by showing toasts.
 */
public class BugleActionToasts {
    /**
     * Called when SendMessageAction or DownloadMmsAction finishes
     * @param conversationId the conversation of the sent or downloaded message
     * @param success did the action succeed
     * @param status the message sending status
     * @param isSms whether the message is sent using SMS
     * @param subId the subId of the SIM related to this send
     * @param isSend whether it is a send (false for download)
     */
    static void onSendMessageOrManualDownloadActionCompleted(
            final String conversationId,
            final boolean success,
            final int status,
            final boolean isSms,
            final int subId,
            final boolean isSend) {
        // We only show notifications for two cases, i.e. when mobile data is off or when we are
        // in airplane mode, both of which fail fast with permanent failures.
        if (!success && status == MmsUtils.MMS_REQUEST_MANUAL_RETRY) {
            final PhoneUtils phoneUtils = PhoneUtils.get(subId);
            if (phoneUtils.isAirplaneModeOn()) {
                if (isSend) {
                    showToast(R.string.send_message_failure_airplane_mode);
                } else {
                    showToast(R.string.download_message_failure_airplane_mode);
                }
                return;
            } else if (!isSms && !phoneUtils.isMobileDataEnabled()) {
                if (isSend) {
                    showToast(R.string.send_message_failure_no_data);
                } else {
                    showToast(R.string.download_message_failure_no_data);
                }
                return;
            }
        }

        if (AccessibilityUtil.isTouchExplorationEnabled(Factory.get().getApplicationContext())) {
            final boolean isFocusedConversation = DataModel.get().isFocusedConversation(conversationId);
            if (isFocusedConversation && success) {
                // Using View.announceForAccessibility may be preferable, but we do not have a
                // View, and so we use a toast instead.
                showToast(isSend ? R.string.send_message_success
                        : R.string.download_message_success);
                return;
            }

            // {@link MessageNotificationState#checkFailedMessages} does not post a notification for
            // failures in observable conversations. For accessibility, we provide an indication
            // here.
            final boolean isObservableConversation = DataModel.get().isNewMessageObservable(
                    conversationId);
            if (isObservableConversation && !success) {
                showToast(isSend ? R.string.send_message_failure
                        : R.string.download_message_failure);
            }
        }
    }

    public static void onMessageReceived(final String conversationId,
            @Nullable final ParticipantData sender, @Nullable final MessageData message) {
        final Context context = Factory.get().getApplicationContext();
        if (AccessibilityUtil.isTouchExplorationEnabled(context)) {
            final boolean isFocusedConversation = DataModel.get().isFocusedConversation(
                    conversationId);
            if (isFocusedConversation) {
                final Resources res = context.getResources();
                final String senderDisplayName = (sender == null)
                        ? res.getString(R.string.unknown_sender) : sender.getDisplayName(false);
                final String announcement = res.getString(
                        R.string.incoming_message_announcement, senderDisplayName,
                        (message == null) ? "" : message.getMessageText());
                showToast(announcement);
            }
        }
    }

    public static void onConversationDeleted() {
        showToast(R.string.conversation_deleted);
    }

    private static void showToast(final int messageResId) {
        ThreadUtil.getMainThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        getApplicationContext().getString(messageResId), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void showToast(final String message) {
        ThreadUtil.getMainThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static Context getApplicationContext() {
        return Factory.get().getApplicationContext();
    }

    private static class UpdateDestinationBlockedActionToast
            implements UpdateDestinationBlockedAction.UpdateDestinationBlockedActionListener {
        private final Context mContext;

        UpdateDestinationBlockedActionToast(final Context context) {
            mContext = context;
        }

        @Override
        public void onUpdateDestinationBlockedAction(
                final UpdateDestinationBlockedAction action,
                final boolean success,
                final boolean block,
                final String destination) {
            if (success) {
                Toast.makeText(mContext,
                        block
                                ? R.string.update_destination_blocked
                                : R.string.update_destination_unblocked,
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    public static UpdateDestinationBlockedAction.UpdateDestinationBlockedActionListener
            makeUpdateDestinationBlockedActionListener(final Context context) {
        return new UpdateDestinationBlockedActionToast(context);
    }
}
