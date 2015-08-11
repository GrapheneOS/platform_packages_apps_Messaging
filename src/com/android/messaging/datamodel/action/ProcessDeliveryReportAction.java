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

import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Telephony;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;

import java.util.concurrent.TimeUnit;

public class ProcessDeliveryReportAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    private static final String KEY_URI = "uri";
    private static final String KEY_STATUS = "status";

    private ProcessDeliveryReportAction(final Uri uri, final int status) {
        actionParameters.putParcelable(KEY_URI, uri);
        actionParameters.putInt(KEY_STATUS, status);
    }

    public static void deliveryReportReceived(final Uri uri, final int status) {
        final ProcessDeliveryReportAction action = new ProcessDeliveryReportAction(uri, status);
        action.start();
    }

    @Override
    protected Object executeAction() {
        final Uri smsMessageUri = actionParameters.getParcelable(KEY_URI);
        final int status = actionParameters.getInt(KEY_STATUS);

        final DatabaseWrapper db = DataModel.get().getDatabase();

        final long messageRowId = ContentUris.parseId(smsMessageUri);
        if (messageRowId < 0) {
            LogUtil.e(TAG, "ProcessDeliveryReportAction: can't find message");
            return null;
        }
        final long timeSentInMillis = System.currentTimeMillis();
        // Update telephony provider
        if (smsMessageUri != null) {
            MmsUtils.updateSmsStatusAndDateSent(smsMessageUri, status, timeSentInMillis);
        }

        // Update local message
        db.beginTransaction();
        try {
            final ContentValues values = new ContentValues();
            final int bugleStatus = SyncMessageBatch.bugleStatusForSms(true /*outgoing*/,
                    Telephony.Sms.MESSAGE_TYPE_SENT /* type */, status);
            values.put(DatabaseHelper.MessageColumns.STATUS, bugleStatus);
            values.put(DatabaseHelper.MessageColumns.SENT_TIMESTAMP,
                    TimeUnit.MILLISECONDS.toMicros(timeSentInMillis));

            final MessageData messageData =
                    BugleDatabaseOperations.readMessageData(db, smsMessageUri);

            // Check the message was not removed before the delivery report comes in
            if (messageData !=  null) {
                Assert.isTrue(smsMessageUri.equals(messageData.getSmsMessageUri()));

                // Row must exist as was just loaded above (on ActionService thread)
                BugleDatabaseOperations.updateMessageRow(db, messageData.getMessageId(), values);

                MessagingContentProvider.notifyMessagesChanged(messageData.getConversationId());
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return null;
    }

    private ProcessDeliveryReportAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<ProcessDeliveryReportAction> CREATOR
            = new Parcelable.Creator<ProcessDeliveryReportAction>() {
        @Override
        public ProcessDeliveryReportAction createFromParcel(final Parcel in) {
            return new ProcessDeliveryReportAction(in);
        }

        @Override
        public ProcessDeliveryReportAction[] newArray(final int size) {
            return new ProcessDeliveryReportAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
