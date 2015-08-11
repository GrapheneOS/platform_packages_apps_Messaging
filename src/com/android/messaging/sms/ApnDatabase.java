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

package com.android.messaging.sms;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.PhoneUtils;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
 * Database helper class for looking up APNs.  This database has a single table
 * which stores the APNs that are initially created from an xml file.
 */
public class ApnDatabase extends SQLiteOpenHelper {
    private static final int DB_VERSION = 3; // added sub_id columns

    private static final String TAG = LogUtil.BUGLE_TAG;

    private static final boolean DEBUG = false;

    private static Context sContext;
    private static ApnDatabase sApnDatabase;

    private static final String APN_DATABASE_NAME = "apn.db";

    /** table for carrier APN's */
    public static final String APN_TABLE = "apn";

    // APN table
    private static final String APN_TABLE_SQL =
            "CREATE TABLE " + APN_TABLE +
                    "(_id INTEGER PRIMARY KEY," +
                    Telephony.Carriers.NAME + " TEXT," +
                    Telephony.Carriers.NUMERIC + " TEXT," +
                    Telephony.Carriers.MCC + " TEXT," +
                    Telephony.Carriers.MNC + " TEXT," +
                    Telephony.Carriers.APN + " TEXT," +
                    Telephony.Carriers.USER + " TEXT," +
                    Telephony.Carriers.SERVER + " TEXT," +
                    Telephony.Carriers.PASSWORD + " TEXT," +
                    Telephony.Carriers.PROXY + " TEXT," +
                    Telephony.Carriers.PORT + " TEXT," +
                    Telephony.Carriers.MMSPROXY + " TEXT," +
                    Telephony.Carriers.MMSPORT + " TEXT," +
                    Telephony.Carriers.MMSC + " TEXT," +
                    Telephony.Carriers.AUTH_TYPE + " INTEGER," +
                    Telephony.Carriers.TYPE + " TEXT," +
                    Telephony.Carriers.CURRENT + " INTEGER," +
                    Telephony.Carriers.PROTOCOL + " TEXT," +
                    Telephony.Carriers.ROAMING_PROTOCOL + " TEXT," +
                    Telephony.Carriers.CARRIER_ENABLED + " BOOLEAN," +
                    Telephony.Carriers.BEARER + " INTEGER," +
                    Telephony.Carriers.MVNO_TYPE + " TEXT," +
                    Telephony.Carriers.MVNO_MATCH_DATA + " TEXT," +
                    Telephony.Carriers.SUBSCRIPTION_ID + " INTEGER DEFAULT " +
                            ParticipantData.DEFAULT_SELF_SUB_ID + ");";

    public static final String[] APN_PROJECTION = {
            Telephony.Carriers.TYPE,            // 0
            Telephony.Carriers.MMSC,            // 1
            Telephony.Carriers.MMSPROXY,        // 2
            Telephony.Carriers.MMSPORT,         // 3
            Telephony.Carriers._ID,             // 4
            Telephony.Carriers.CURRENT,         // 5
            Telephony.Carriers.NUMERIC,         // 6
            Telephony.Carriers.NAME,            // 7
            Telephony.Carriers.MCC,             // 8
            Telephony.Carriers.MNC,             // 9
            Telephony.Carriers.APN,             // 10
            Telephony.Carriers.SUBSCRIPTION_ID  // 11
    };

    public static final int COLUMN_TYPE         = 0;
    public static final int COLUMN_MMSC         = 1;
    public static final int COLUMN_MMSPROXY     = 2;
    public static final int COLUMN_MMSPORT      = 3;
    public static final int COLUMN_ID           = 4;
    public static final int COLUMN_CURRENT      = 5;
    public static final int COLUMN_NUMERIC      = 6;
    public static final int COLUMN_NAME         = 7;
    public static final int COLUMN_MCC          = 8;
    public static final int COLUMN_MNC          = 9;
    public static final int COLUMN_APN          = 10;
    public static final int COLUMN_SUB_ID       = 11;

    public static final String[] APN_FULL_PROJECTION = {
            Telephony.Carriers.NAME,
            Telephony.Carriers.MCC,
            Telephony.Carriers.MNC,
            Telephony.Carriers.APN,
            Telephony.Carriers.USER,
            Telephony.Carriers.SERVER,
            Telephony.Carriers.PASSWORD,
            Telephony.Carriers.PROXY,
            Telephony.Carriers.PORT,
            Telephony.Carriers.MMSC,
            Telephony.Carriers.MMSPROXY,
            Telephony.Carriers.MMSPORT,
            Telephony.Carriers.AUTH_TYPE,
            Telephony.Carriers.TYPE,
            Telephony.Carriers.PROTOCOL,
            Telephony.Carriers.ROAMING_PROTOCOL,
            Telephony.Carriers.CARRIER_ENABLED,
            Telephony.Carriers.BEARER,
            Telephony.Carriers.MVNO_TYPE,
            Telephony.Carriers.MVNO_MATCH_DATA,
            Telephony.Carriers.CURRENT,
            Telephony.Carriers.SUBSCRIPTION_ID,
    };

    private static final String CURRENT_SELECTION = Telephony.Carriers.CURRENT + " NOT NULL";

    /**
     * ApnDatabase is initialized asynchronously from the application.onCreate
     * To ensure that it works in a testing environment it needs to never access the factory context
     */
    public static void initializeAppContext(final Context context) {
        sContext = context;
    }

    private ApnDatabase() {
        super(sContext, APN_DATABASE_NAME, null, DB_VERSION);
        if (DEBUG) {
            LogUtil.d(TAG, "ApnDatabase constructor");
        }
    }

    public static ApnDatabase getApnDatabase() {
        if (sApnDatabase == null) {
            sApnDatabase = new ApnDatabase();
        }
        return sApnDatabase;
    }

    public static boolean doesDatabaseExist() {
        final File dbFile = sContext.getDatabasePath(APN_DATABASE_NAME);
        return dbFile.exists();
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        if (DEBUG) {
            LogUtil.d(TAG, "ApnDatabase onCreate");
        }
        // Build the table using defaults (apn info bundled with the app)
        rebuildTables(db);
    }

    /**
     * Get a copy of user changes in the old table
     *
     * @return The list of user changed apns
     */
    public static List<ContentValues> loadUserDataFromOldTable(final SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            cursor = db.query(APN_TABLE,
                    APN_FULL_PROJECTION, CURRENT_SELECTION,
                    null/*selectionArgs*/,
                    null/*groupBy*/, null/*having*/, null/*orderBy*/);
            if (cursor != null) {
                final List<ContentValues> result = Lists.newArrayList();
                while (cursor.moveToNext()) {
                    final ContentValues row = cursorToValues(cursor);
                    if (row != null) {
                        result.add(row);
                    }
                }
                return result;
            }
        } catch (final SQLiteException e) {
            LogUtil.w(TAG, "ApnDatabase.loadUserDataFromOldTable: no old user data: " + e, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static final String[] ID_PROJECTION = new String[]{Telephony.Carriers._ID};

    private static final String ID_SELECTION = Telephony.Carriers._ID + "=?";

    /**
     * Store use changes of old table into the new apn table
     *
     * @param data The user changes
     */
    public static void saveUserDataFromOldTable(
            final SQLiteDatabase db, final List<ContentValues> data) {
        if (data == null || data.size() < 1) {
            return;
        }
        for (final ContentValues row : data) {
            // Build query from the row data. It is an exact match, column by column,
            // except the CURRENT column
            final StringBuilder selectionBuilder = new StringBuilder();
            final ArrayList<String> selectionArgs = Lists.newArrayList();
            for (final String key : row.keySet()) {
                if (!Telephony.Carriers.CURRENT.equals(key)) {
                    if (selectionBuilder.length() > 0) {
                        selectionBuilder.append(" AND ");
                    }
                    final String value = row.getAsString(key);
                    if (TextUtils.isEmpty(value)) {
                        selectionBuilder.append(key).append(" IS NULL");
                    } else {
                        selectionBuilder.append(key).append("=?");
                        selectionArgs.add(value);
                    }
                }
            }
            Cursor cursor = null;
            try {
                cursor = db.query(APN_TABLE,
                        ID_PROJECTION,
                        selectionBuilder.toString(),
                        selectionArgs.toArray(new String[0]),
                        null/*groupBy*/, null/*having*/, null/*orderBy*/);
                if (cursor != null && cursor.moveToFirst()) {
                    db.update(APN_TABLE, row, ID_SELECTION, new String[]{cursor.getString(0)});
                } else {
                    // User APN does not exist, insert into the new table
                    row.put(Telephony.Carriers.NUMERIC,
                            PhoneUtils.canonicalizeMccMnc(
                                    row.getAsString(Telephony.Carriers.MCC),
                                    row.getAsString(Telephony.Carriers.MNC))
                    );
                    db.insert(APN_TABLE, null/*nullColumnHack*/, row);
                }
            } catch (final SQLiteException e) {
                LogUtil.e(TAG, "ApnDatabase.saveUserDataFromOldTable: query error " + e, e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    // Convert Cursor to ContentValues
    private static ContentValues cursorToValues(final Cursor cursor) {
        final int columnCount = cursor.getColumnCount();
        if (columnCount > 0) {
            final ContentValues result = new ContentValues();
            for (int i = 0; i < columnCount; i++) {
                final String name = cursor.getColumnName(i);
                final String value = cursor.getString(i);
                result.put(name, value);
            }
            return result;
        }
        return null;
    }

    @Override
    public void onOpen(final SQLiteDatabase db) {
        super.onOpen(db);
        if (DEBUG) {
            LogUtil.d(TAG, "ApnDatabase onOpen");
        }
    }

    @Override
    public void close() {
        super.close();
        if (DEBUG) {
            LogUtil.d(TAG, "ApnDatabase close");
        }
    }

    private void rebuildTables(final SQLiteDatabase db) {
        if (DEBUG) {
            LogUtil.d(TAG, "ApnDatabase rebuildTables");
        }
        db.execSQL("DROP TABLE IF EXISTS " + APN_TABLE + ";");
        db.execSQL(APN_TABLE_SQL);
        loadApnTable(db);
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        if (DEBUG) {
            LogUtil.d(TAG, "ApnDatabase onUpgrade");
        }
        rebuildTables(db);
    }

    @Override
    public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        if (DEBUG) {
            LogUtil.d(TAG, "ApnDatabase onDowngrade");
        }
        rebuildTables(db);
    }

    /**
     * Load APN table from app resources
     */
    private static void loadApnTable(final SQLiteDatabase db) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "ApnDatabase loadApnTable");
        }
        final Resources r = sContext.getResources();
        final XmlResourceParser parser = r.getXml(R.xml.apns);
        final ApnsXmlProcessor processor = ApnsXmlProcessor.get(parser);
        processor.setApnHandler(new ApnsXmlProcessor.ApnHandler() {
            @Override
            public void process(final ContentValues apnValues) {
                db.insert(APN_TABLE, null/*nullColumnHack*/, apnValues);
            }
        });
        try {
            processor.process();
        } catch (final Exception e) {
            Log.e(TAG, "Got exception while loading APN database.", e);
        } finally {
            parser.close();
        }
    }

    public static void forceBuildAndLoadApnTables() {
        final SQLiteDatabase db = getApnDatabase().getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + APN_TABLE);
        // Table(s) always need for JB MR1 for APN support for MMS because JB MR1 throws
        // a SecurityException when trying to access the carriers table (which holds the
        // APNs). Some JB MR2 devices also throw the security exception, so we're building
        // the table for JB MR2, too.
        db.execSQL(APN_TABLE_SQL);

        loadApnTable(db);
    }

    /**
     * Clear all tables
     */
    public static void clearTables() {
        final SQLiteDatabase db = getApnDatabase().getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + APN_TABLE);
        db.execSQL(APN_TABLE_SQL);
    }
}
