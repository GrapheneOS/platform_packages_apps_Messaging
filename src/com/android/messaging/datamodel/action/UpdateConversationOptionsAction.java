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

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper.ConversationColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.util.Assert;

/**
 * Action used to update conversation options such as notification settings.
 */
public class UpdateConversationOptionsAction extends Action
        implements Parcelable {
    /**
     * Enable/disable conversation notifications.
     */
    public static void enableConversationNotifications(final String conversationId,
            final boolean enableNotification) {
        Assert.notNull(conversationId);

        final UpdateConversationOptionsAction action = new UpdateConversationOptionsAction(
                conversationId, enableNotification, null, null);
        action.start();
    }

    /**
     * Sets conversation notification sound.
     */
    public static void setConversationNotificationSound(final String conversationId,
            final String ringtoneUri) {
        Assert.notNull(conversationId);

        final UpdateConversationOptionsAction action = new UpdateConversationOptionsAction(
                conversationId, null, ringtoneUri, null);
        action.start();
    }

    /**
     * Enable/disable vibrations for conversation notification.
     */
    public static void enableVibrationForConversationNotification(final String conversationId,
            final boolean enableVibration) {
        Assert.notNull(conversationId);

        final UpdateConversationOptionsAction action = new UpdateConversationOptionsAction(
                conversationId, null, null, enableVibration);
        action.start();
    }

    private static final String KEY_CONVERSATION_ID = "conversation_id";

    // Keys for all settable settings.
    private static final String KEY_ENABLE_NOTIFICATION = "enable_notification";
    private static final String KEY_RINGTONE_URI = "ringtone_uri";
    private static final String KEY_ENABLE_VIBRATION = "enable_vibration";

    protected UpdateConversationOptionsAction(final String conversationId,
            final Boolean enableNotification, final String ringtoneUri,
            final Boolean enableVibration) {
        Assert.notNull(conversationId);
        actionParameters.putString(KEY_CONVERSATION_ID, conversationId);
        if (enableNotification != null) {
            actionParameters.putBoolean(KEY_ENABLE_NOTIFICATION, enableNotification);
        }

        if (ringtoneUri != null) {
            actionParameters.putString(KEY_RINGTONE_URI, ringtoneUri);
        }

        if (enableVibration != null) {
            actionParameters.putBoolean(KEY_ENABLE_VIBRATION, enableVibration);
        }
    }

    protected void putOptionValuesInTransaction(final ContentValues values,
            final DatabaseWrapper dbWrapper) {
        Assert.isTrue(dbWrapper.getDatabase().inTransaction());
        if (actionParameters.containsKey(KEY_ENABLE_NOTIFICATION)) {
            values.put(ConversationColumns.NOTIFICATION_ENABLED,
                    actionParameters.getBoolean(KEY_ENABLE_NOTIFICATION));
        }

        if (actionParameters.containsKey(KEY_RINGTONE_URI)) {
            values.put(ConversationColumns.NOTIFICATION_SOUND_URI,
                    actionParameters.getString(KEY_RINGTONE_URI));
        }

        if (actionParameters.containsKey(KEY_ENABLE_VIBRATION)) {
            values.put(ConversationColumns.NOTIFICATION_VIBRATION,
                    actionParameters.getBoolean(KEY_ENABLE_VIBRATION));
        }
    }

    @Override
    protected Object executeAction() {
        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);

        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();
        try {
            final ContentValues values = new ContentValues();
            putOptionValuesInTransaction(values, db);

            BugleDatabaseOperations.updateConversationRowIfExists(db, conversationId, values);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        MessagingContentProvider.notifyConversationMetadataChanged(conversationId);
        return null;
    }

    protected UpdateConversationOptionsAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<UpdateConversationOptionsAction> CREATOR
            = new Parcelable.Creator<UpdateConversationOptionsAction>() {
        @Override
        public UpdateConversationOptionsAction createFromParcel(final Parcel in) {
            return new UpdateConversationOptionsAction(in);
        }

        @Override
        public UpdateConversationOptionsAction[] newArray(final int size) {
            return new UpdateConversationOptionsAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
