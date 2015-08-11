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

package com.android.messaging.datamodel;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.messaging.datamodel.action.InsertNewMessageAction;
import com.android.messaging.datamodel.action.UpdateMessageNotificationAction;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.conversationlist.ConversationListActivity;
import com.android.messaging.util.LogUtil;

/**
 * Respond to a special intent and send an SMS message without the user's intervention, unless
 * the intent extra "showUI" is true.
 */
public class NoConfirmationSmsSendService extends IntentService {
    private static final String TAG = LogUtil.BUGLE_TAG;

    private static final String EXTRA_SUBSCRIPTION = "subscription";
    public static final String EXTRA_SELF_ID = "self_id";

    public NoConfirmationSmsSendService() {
        // Class name will be the thread name.
        super(NoConfirmationSmsSendService.class.getName());

        // Intent should be redelivered if the process gets killed before completing the job.
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "NoConfirmationSmsSendService onHandleIntent");
        }

        final String action = intent.getAction();
        if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(action)) {
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "NoConfirmationSmsSendService onHandleIntent wrong action: " +
                    action);
            }
            return;
        }
        final Bundle extras = intent.getExtras();
        if (extras == null) {
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "Called to send SMS but no extras");
            }
            return;
        }

        // Get all possible extras from intent
        final String conversationId =
                intent.getStringExtra(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID);
        final String selfId = intent.getStringExtra(EXTRA_SELF_ID);
        final boolean requiresMms = intent.getBooleanExtra(UIIntents.UI_INTENT_EXTRA_REQUIRES_MMS,
                false);
        final String message = getText(intent, Intent.EXTRA_TEXT);
        final String subject = getText(intent, Intent.EXTRA_SUBJECT);
        final int subId = extras.getInt(EXTRA_SUBSCRIPTION, ParticipantData.DEFAULT_SELF_SUB_ID);

        final Uri intentUri = intent.getData();
        final String recipients = intentUri != null ? MmsUtils.getSmsRecipients(intentUri) : null;

        if (TextUtils.isEmpty(recipients) && TextUtils.isEmpty(conversationId)) {
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "Both conversationId and recipient(s) cannot be empty");
            }
            return;
        }

        if (extras.getBoolean("showUI", false)) {
            startActivity(new Intent(this, ConversationListActivity.class));
        } else {
            if (TextUtils.isEmpty(message)) {
                if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                    LogUtil.v(TAG, "Message cannot be empty");
                }
                return;
            }

            // TODO: it's possible that a long message would require sending it via mms,
            // but we're not testing for that here and we're sending the message as an sms.

            if (TextUtils.isEmpty(conversationId)) {
                InsertNewMessageAction.insertNewMessage(subId, recipients, message, subject);
            } else {
                MessageData messageData = null;
                if (requiresMms) {
                    if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                        LogUtil.v(TAG, "Auto-sending MMS message in conversation: " +
                                conversationId);
                    }
                    messageData = MessageData.createDraftMmsMessage(conversationId, selfId, message,
                            subject);
                } else {
                    if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                        LogUtil.v(TAG, "Auto-sending SMS message in conversation: " +
                                conversationId);
                    }
                    messageData = MessageData.createDraftSmsMessage(conversationId, selfId,
                            message);
                }
                InsertNewMessageAction.insertNewMessage(messageData);
            }
            UpdateMessageNotificationAction.updateMessageNotification();
        }
    }

    private String getText(final Intent intent, final String textType) {
        final String message = intent.getStringExtra(textType);
        if (message == null) {
            final Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput != null) {
                final CharSequence extra = remoteInput.getCharSequence(textType);
                if (extra != null) {
                    return extra.toString();
                }
            }
        }
        return message;
    }

}
