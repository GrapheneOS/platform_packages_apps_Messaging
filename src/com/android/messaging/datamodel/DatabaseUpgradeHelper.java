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

import android.database.sqlite.SQLiteDatabase;

import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;

public class DatabaseUpgradeHelper {
    private static final String TAG = LogUtil.BUGLE_DATABASE_TAG;

    public void doOnUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Assert.isTrue(newVersion >= oldVersion);
        if (oldVersion == newVersion) {
            return;
        }

        LogUtil.i(TAG, "Database upgrade started from version " + oldVersion + " to " + newVersion);

        // Add future upgrade code here
    }

    public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        DatabaseHelper.rebuildTables(db);
        LogUtil.e(TAG, "Database downgrade requested for version " +
                oldVersion + " version " + newVersion + ", forcing db rebuild!");
    }
}
