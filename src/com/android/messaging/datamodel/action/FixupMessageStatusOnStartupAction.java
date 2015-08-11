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

import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.util.LogUtil;

/**
 * Action used to fixup actively downloading or sending status at startup - just in case we
 * crash - never run this when a message might actually be sending or downloading.
 */
public class FixupMessageStatusOnStartupAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    public static void fixupMessageStatus() {
        final FixupMessageStatusOnStartupAction action = new FixupMessageStatusOnStartupAction();
        action.start();
    }

    private FixupMessageStatusOnStartupAction() {
    }

    @Override
    protected Object executeAction() {
        // Now mark any messages in active sending or downloading state as inactive
        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();
        int downloadFailedCnt = 0;
        int sendFailedCnt = 0;
        try {
            // For both sending and downloading messages, let's assume they failed.
            // For MMS sent/downloaded via platform, the sent/downloaded pending intent
            // may come back. That will update the message. User may see the message
            // in wrong status within a short window if that happens. But this should
            // rarely happen. This is a simple solution to situations like app gets killed
            // while the pending intent is still in the fly. Alternatively, we could
            // keep the status for platform sent/downloaded MMS and timeout these messages.
            // But that is much more complex.
            final ContentValues values = new ContentValues();
            values.put(DatabaseHelper.MessageColumns.STATUS,
                    MessageData.BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED);
            downloadFailedCnt += db.update(DatabaseHelper.MESSAGES_TABLE, values,
                    DatabaseHelper.MessageColumns.STATUS + " IN (?, ?)",
                    new String[]{
                            Integer.toString(MessageData.BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING),
                            Integer.toString(MessageData.BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING)
                    });
            values.clear();

            values.clear();
            values.put(DatabaseHelper.MessageColumns.STATUS,
                    MessageData.BUGLE_STATUS_OUTGOING_FAILED);
            sendFailedCnt = db.update(DatabaseHelper.MESSAGES_TABLE, values,
                    DatabaseHelper.MessageColumns.STATUS + " IN (?, ?)",
                    new String[]{
                            Integer.toString(MessageData.BUGLE_STATUS_OUTGOING_SENDING),
                            Integer.toString(MessageData.BUGLE_STATUS_OUTGOING_RESENDING)
                    });

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        LogUtil.i(TAG, "Fixup: Send failed - " + sendFailedCnt
                + " Download failed - " + downloadFailedCnt);

        // Don't send contentObserver notifications as displayed text should not change
        return null;
    }

    private FixupMessageStatusOnStartupAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<FixupMessageStatusOnStartupAction> CREATOR
            = new Parcelable.Creator<FixupMessageStatusOnStartupAction>() {
        @Override
        public FixupMessageStatusOnStartupAction createFromParcel(final Parcel in) {
            return new FixupMessageStatusOnStartupAction(in);
        }

        @Override
        public FixupMessageStatusOnStartupAction[] newArray(final int size) {
            return new FixupMessageStatusOnStartupAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
