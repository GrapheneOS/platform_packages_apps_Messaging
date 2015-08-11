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
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Telephony.Threads;
import android.provider.Telephony.ThreadsColumns;

import com.android.messaging.Factory;
import com.android.messaging.mmslib.SqliteWrapper;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.LogUtil;

public class LogTelephonyDatabaseAction extends Action implements Parcelable {
    // Because we use sanitizePII, we should also use BUGLE_TAG
    private static final String TAG = LogUtil.BUGLE_TAG;

    private static final String[] ALL_THREADS_PROJECTION = {
        Threads._ID,
        Threads.DATE,
        Threads.MESSAGE_COUNT,
        Threads.RECIPIENT_IDS,
        Threads.SNIPPET,
        Threads.SNIPPET_CHARSET,
        Threads.READ,
        Threads.ERROR,
        Threads.HAS_ATTACHMENT };

    // Constants from the Telephony Database
    private static final int ID               = 0;
    private static final int DATE             = 1;
    private static final int MESSAGE_COUNT    = 2;
    private static final int RECIPIENT_IDS    = 3;
    private static final int SNIPPET          = 4;
    private static final int SNIPPET_CHAR_SET = 5;
    private static final int READ             = 6;
    private static final int ERROR            = 7;
    private static final int HAS_ATTACHMENT   = 8;

    /**
     * Log telephony data to logcat
     */
    public static void dumpDatabase() {
        final LogTelephonyDatabaseAction action = new LogTelephonyDatabaseAction();
        action.start();
    }

    private LogTelephonyDatabaseAction() {
    }

    @Override
    protected Object executeAction() {
        final Context context = Factory.get().getApplicationContext();

        if (!DebugUtils.isDebugEnabled()) {
            LogUtil.e(TAG, "Can't log telephony database unless debugging is enabled");
            return null;
        }

        if (!LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
            LogUtil.w(TAG, "Can't log telephony database unless DEBUG is turned on for TAG: " +
                    TAG);
            return null;
        }

        LogUtil.d(TAG, "\n");
        LogUtil.d(TAG, "Dump of canoncial_addresses table");
        LogUtil.d(TAG, "*********************************");

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                Uri.parse("content://mms-sms/canonical-addresses"), null, null, null, null);

        if (cursor == null) {
            LogUtil.w(TAG, "null Cursor in content://mms-sms/canonical-addresses");
        } else {
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String number = cursor.getString(1);
                    LogUtil.d(TAG, LogUtil.sanitizePII("id: " + id + " number: " + number));
                }
            } finally {
                cursor.close();
            }
        }

        LogUtil.d(TAG, "\n");
        LogUtil.d(TAG, "Dump of threads table");
        LogUtil.d(TAG, "*********************");

        cursor = SqliteWrapper.query(context, context.getContentResolver(),
                Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build(),
                ALL_THREADS_PROJECTION, null, null, "date ASC");
        try {
            while (cursor.moveToNext()) {
                LogUtil.d(TAG, LogUtil.sanitizePII("threadId: " + cursor.getLong(ID) +
                        " " + ThreadsColumns.DATE + " : " + cursor.getLong(DATE) +
                        " " + ThreadsColumns.MESSAGE_COUNT + " : " + cursor.getInt(MESSAGE_COUNT) +
                        " " + ThreadsColumns.SNIPPET + " : " + cursor.getString(SNIPPET) +
                        " " + ThreadsColumns.READ + " : " + cursor.getInt(READ) +
                        " " + ThreadsColumns.ERROR + " : " + cursor.getInt(ERROR) +
                        " " + ThreadsColumns.HAS_ATTACHMENT + " : " +
                            cursor.getInt(HAS_ATTACHMENT) +
                        " " + ThreadsColumns.RECIPIENT_IDS + " : " +
                            cursor.getString(RECIPIENT_IDS)));
            }
        } finally {
            cursor.close();
        }

        return null;
    }

    private LogTelephonyDatabaseAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<LogTelephonyDatabaseAction> CREATOR
            = new Parcelable.Creator<LogTelephonyDatabaseAction>() {
        @Override
        public LogTelephonyDatabaseAction createFromParcel(final Parcel in) {
            return new LogTelephonyDatabaseAction(in);
        }

        @Override
        public LogTelephonyDatabaseAction[] newArray(final int size) {
            return new LogTelephonyDatabaseAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
