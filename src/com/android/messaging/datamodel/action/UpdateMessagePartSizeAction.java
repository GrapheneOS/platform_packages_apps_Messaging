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
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.DatabaseHelper.PartColumns;
import com.android.messaging.util.Assert;

/**
 * Action used to update size fields of a single part
 */
public class UpdateMessagePartSizeAction extends Action implements Parcelable {
    /**
     * Update size of part
     */
    public static void updateSize(final String partId, final int width, final int height) {
        Assert.notNull(partId);
        Assert.inRange(width, 0, Integer.MAX_VALUE);
        Assert.inRange(height, 0, Integer.MAX_VALUE);

        final UpdateMessagePartSizeAction action = new UpdateMessagePartSizeAction(
                partId, width, height);
        action.start();
    }

    private static final String KEY_PART_ID = "part_id";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";

    private UpdateMessagePartSizeAction(final String partId, final int width, final int height) {
        actionParameters.putString(KEY_PART_ID, partId);
        actionParameters.putInt(KEY_WIDTH, width);
        actionParameters.putInt(KEY_HEIGHT, height);
    }

    @Override
    protected Object executeAction() {
        final String partId = actionParameters.getString(KEY_PART_ID);
        final int width = actionParameters.getInt(KEY_WIDTH);
        final int height = actionParameters.getInt(KEY_HEIGHT);

        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();
        try {
            final ContentValues values = new ContentValues();

            values.put(PartColumns.WIDTH, width);
            values.put(PartColumns.HEIGHT, height);

            // Part may have been deleted so allow update to fail without asserting
            BugleDatabaseOperations.updateRowIfExists(db, DatabaseHelper.PARTS_TABLE,
                    PartColumns._ID, partId, values);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return null;
    }

    private UpdateMessagePartSizeAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<UpdateMessagePartSizeAction> CREATOR
            = new Parcelable.Creator<UpdateMessagePartSizeAction>() {
        @Override
        public UpdateMessagePartSizeAction createFromParcel(final Parcel in) {
            return new UpdateMessagePartSizeAction(in);
        }

        @Override
        public UpdateMessagePartSizeAction[] newArray(final int size) {
            return new UpdateMessagePartSizeAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
