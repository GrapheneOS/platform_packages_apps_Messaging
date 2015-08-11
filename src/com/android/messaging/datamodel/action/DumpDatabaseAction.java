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
import android.os.Parcel;
import android.os.Parcelable;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.LogUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DumpDatabaseAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;
    public static final String DUMP_NAME = "db_copy.db";
    private static final int BUFFER_SIZE = 16384;

    /**
     * Copy the database to external storage
     */
    public static void dumpDatabase() {
        final DumpDatabaseAction action = new DumpDatabaseAction();
        action.start();
    }

    private DumpDatabaseAction() {
    }

    @Override
    protected Object executeAction() {
        final Context context = Factory.get().getApplicationContext();
        final String dbName = DatabaseHelper.DATABASE_NAME;
        BufferedOutputStream bos = null;
        BufferedInputStream bis = null;

        long originalSize = 0;
        final File inFile = context.getDatabasePath(dbName);
        if (inFile.exists() && inFile.isFile()) {
            originalSize = inFile.length();
        }
        final File outFile = DebugUtils.getDebugFile(DUMP_NAME, true);
        if (outFile != null) {
            int totalBytes = 0;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(outFile));
                bis = new BufferedInputStream(new FileInputStream(inFile));

                final byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) > 0) {
                    bos.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
            } catch (final IOException e) {
                LogUtil.w(TAG, "Exception copying the database;"
                        + " destination may not be complete.", e);
            } finally {
                if (bos != null) {
                    try {
                        bos.close();
                    } catch (final IOException e) {
                        // Nothing to do
                    }
                }

                if (bis != null) {
                    try {
                        bis.close();
                    } catch (final IOException e) {
                        // Nothing to do
                    }
                }
                DebugUtils.ensureReadable(outFile);
                LogUtil.i(TAG, "Dump complete; orig size: " + originalSize +
                        ", copy size: " + totalBytes);
            }
        }
        return null;
    }

    private DumpDatabaseAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<DumpDatabaseAction> CREATOR
            = new Parcelable.Creator<DumpDatabaseAction>() {
        @Override
        public DumpDatabaseAction createFromParcel(final Parcel in) {
            return new DumpDatabaseAction(in);
        }

        @Override
        public DumpDatabaseAction[] newArray(final int size) {
            return new DumpDatabaseAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
