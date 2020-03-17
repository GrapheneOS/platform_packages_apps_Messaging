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

import android.app.PendingIntent;
import android.net.Uri;
import androidx.core.app.NotificationCompat;

import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.util.ConversationIdSet;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Base class for representing notifications. The main reason for this class is that in order to
 * show pictures or avatars they might need to be loaded in the background. This class and
 * subclasses can do the main work to get the notification ready and then wait until any images
 * that are needed are ready before posting.
 *
 * The creation of a notification is split into two parts. The NotificationState ctor should
 * setup the basic information including the mContentIntent. A Notification Builder is created in
 * RealTimeChatNotifications and passed to the build() method of each notification where the
 * Notification is fully specified.
 *
 * TODO: There is still some duplication and inconsistency in the utility functions and
 * placement of different building blocks across notification types (e.g. summary text for accounts)
 */
public abstract class NotificationState {
    private static final int CONTENT_INTENT_REQUEST_CODE_OFFSET = 0;
    private static final int CLEAR_INTENT_REQUEST_CODE_OFFSET = 1;
    private static final int READ_INTENT_REQUEST_CODE_OFFSET = 2;
    private static final int NUM_REQUEST_CODES_NEEDED = 3;

    public interface FailedMessageQuery {
        static final String FAILED_MESSAGES_WHERE_CLAUSE =
                "((" + MessageColumns.STATUS + " = " +
                MessageData.BUGLE_STATUS_OUTGOING_FAILED + " OR " +
                MessageColumns.STATUS + " = " +
                MessageData.BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED + ") AND " +
                DatabaseHelper.MessageColumns.SEEN + " = 0)";

        static final String FAILED_ORDER_BY = DatabaseHelper.MessageColumns.CONVERSATION_ID + ", " +
                DatabaseHelper.MessageColumns.SENT_TIMESTAMP + " asc";
    }

    public final ConversationIdSet mConversationIds;
    public final HashSet<String> mPeople;

    public NotificationCompat.Style mNotificationStyle;
    public NotificationCompat.Builder mNotificationBuilder;
    public boolean mCanceled;
    public int mType;
    public int mBaseRequestCode;
    public ArrayList<Uri> mParticipantAvatarsUris = null;
    public ArrayList<Uri> mParticipantContactUris = null;

    NotificationState(final ConversationIdSet conversationIds) {
        mConversationIds = conversationIds;
        mPeople = new HashSet<String>();
    }

    /**
     * The intent to be triggered when the notification is dismissed.
     */
    public abstract PendingIntent getClearIntent();

    /**
     * The intent to be triggered when mark as read is pressed.
     */
    public abstract PendingIntent getReadIntent();

    protected Uri getAttachmentUri() {
        return null;
    }

    // Returns the mime type of the attachment (See ContentType class for definitions)
    protected String getAttachmentType() {
        return null;
    }

    /**
     * Build the notification using the given builder.
     * @param builder
     * @return The style of the notification.
     */
    protected abstract NotificationCompat.Style build(NotificationCompat.Builder builder);

    protected void setAvatarUrlsForConversation(final String conversationId) {
    }

    protected void setPeopleForConversation(final String conversationId) {
    }

    /**
     * Reserves request codes for this notification type. By default 2 codes are reserved, one for
     * the main intent and another for the cancel intent. Override this function to reserve more.
     */
    public int getNumRequestCodesNeeded() {
        return NUM_REQUEST_CODES_NEEDED;
    }

    public int getContentIntentRequestCode() {
        return mBaseRequestCode + CONTENT_INTENT_REQUEST_CODE_OFFSET;
    }

    public int getClearIntentRequestCode() {
        return mBaseRequestCode + CLEAR_INTENT_REQUEST_CODE_OFFSET;
    }

    public int getReadIntentRequestCode() {
        return mBaseRequestCode + READ_INTENT_REQUEST_CODE_OFFSET;
    }

    /**
     * Gets the appropriate icon needed for notifications.
     */
    public abstract int getIcon();

    /**
     * @return the type of notification that should be used from {@link RealTimeChatNotifications}
     * so that the proper ringtone and vibrate settings can be used.
     */
    public int getLatestMessageNotificationType() {
        return BugleNotifications.LOCAL_SMS_NOTIFICATION;
    }

    /**
     * @return the notification priority level for this notification.
     */
    public abstract int getPriority();

    /** @return custom ringtone URI or null if not set */
    public String getRingtoneUri() {
        return null;
    }

    public boolean getNotificationVibrate() {
        return false;
    }

    public long getLatestReceivedTimestamp() {
        return Long.MIN_VALUE;
    }
}
