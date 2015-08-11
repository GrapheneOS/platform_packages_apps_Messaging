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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.util.SparseArray;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.UiUtils;

import java.util.Locale;
import java.util.Stack;
import java.util.regex.Pattern;

public class DatabaseWrapper {
    private static final String TAG = LogUtil.BUGLE_DATABASE_TAG;

    private final SQLiteDatabase mDatabase;
    private final Context mContext;
    private final boolean mLog;
    /**
     * Set mExplainQueryPlanRegexp (via {@link BugleGservicesKeys#EXPLAIN_QUERY_PLAN_REGEXP}
     * to regex matching queries to see query plans. For example, ".*" to show all query plans.
     */
    // See
    private final String mExplainQueryPlanRegexp;
    private static final int sTimingThreshold = 50;        // in milliseconds

    public static final int INDEX_INSERT_MESSAGE_PART = 0;
    public static final int INDEX_INSERT_MESSAGE = 1;
    public static final int INDEX_QUERY_CONVERSATIONS_LATEST_MESSAGE = 2;
    public static final int INDEX_QUERY_MESSAGES_LATEST_MESSAGE = 3;

    private final SparseArray<SQLiteStatement> mCompiledStatements;

    static class TransactionData {
        long time;
        boolean transactionSuccessful;
    }

    // track transaction on a per thread basis
    private static ThreadLocal<Stack<TransactionData>> sTransactionDepth =
            new ThreadLocal<Stack<TransactionData>>() {
        @Override
        public Stack<TransactionData> initialValue() {
            return new Stack<TransactionData>();
        }
    };

    private static String[] sFormatStrings = new String[] {
        "took %d ms to %s",
        "   took %d ms to %s",
        "      took %d ms to %s",
    };

    DatabaseWrapper(final Context context, final SQLiteDatabase db) {
        mLog = LogUtil.isLoggable(LogUtil.BUGLE_DATABASE_PERF_TAG, LogUtil.VERBOSE);
        mExplainQueryPlanRegexp = Factory.get().getBugleGservices().getString(
                BugleGservicesKeys.EXPLAIN_QUERY_PLAN_REGEXP, null);
        mDatabase = db;
        mContext = context;
        mCompiledStatements = new SparseArray<SQLiteStatement>();
    }

    public SQLiteStatement getStatementInTransaction(final int index, final String statement) {
        // Use transaction to serialize access to statements
        Assert.isTrue(mDatabase.inTransaction());
        SQLiteStatement compiled = mCompiledStatements.get(index);
        if (compiled == null) {
            compiled = mDatabase.compileStatement(statement);
            Assert.isTrue(compiled.toString().contains(statement.trim()));
            mCompiledStatements.put(index, compiled);
        }
        return compiled;
    }

    private void maybePlayDebugNoise() {
        DebugUtils.maybePlayDebugNoise(mContext, DebugUtils.DEBUG_SOUND_DB_OP);
    }

    private static void printTiming(final long t1, final String msg) {
        final int transactionDepth = sTransactionDepth.get().size();
        final long t2 = System.currentTimeMillis();
        final long delta = t2 - t1;
        if (delta > sTimingThreshold) {
            LogUtil.v(LogUtil.BUGLE_DATABASE_PERF_TAG, String.format(Locale.US,
                    sFormatStrings[Math.min(sFormatStrings.length - 1, transactionDepth)],
                    delta,
                    msg));
        }
    }

    public Context getContext() {
        return mContext;
    }

    public void beginTransaction() {
        final long t1 = System.currentTimeMillis();

        // push the current time onto the transaction stack
        final TransactionData f = new TransactionData();
        f.time = t1;
        sTransactionDepth.get().push(f);

        mDatabase.beginTransaction();
    }

    public void setTransactionSuccessful() {
        final TransactionData f = sTransactionDepth.get().peek();
        f.transactionSuccessful = true;
        mDatabase.setTransactionSuccessful();
    }

    public void endTransaction() {
        long t1 = 0;
        long transactionStartTime = 0;
        final TransactionData f = sTransactionDepth.get().pop();
        if (f.transactionSuccessful == false) {
            LogUtil.w(TAG, "endTransaction without setting successful");
            for (final StackTraceElement st : (new Exception()).getStackTrace()) {
                LogUtil.w(TAG, "    " + st.toString());
            }
        }
        if (mLog) {
            transactionStartTime = f.time;
            t1 = System.currentTimeMillis();
        }
        try {
            mDatabase.endTransaction();
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to endTransaction", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }
        if (mLog) {
            printTiming(t1, String.format(Locale.US,
                    ">>> endTransaction (total for this transaction: %d)",
                    (System.currentTimeMillis() - transactionStartTime)));
        }
    }

    public void yieldTransaction() {
        long yieldStartTime = 0;
        if (mLog) {
            yieldStartTime = System.currentTimeMillis();
        }
        final boolean wasYielded = mDatabase.yieldIfContendedSafely();
        if (wasYielded && mLog) {
            printTiming(yieldStartTime, "yieldTransaction");
        }
    }

    public void insertWithOnConflict(final String searchTable, final String nullColumnHack,
            final ContentValues initialValues, final int conflictAlgorithm) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        try {
            mDatabase.insertWithOnConflict(searchTable, nullColumnHack, initialValues,
                    conflictAlgorithm);
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to insertWithOnConflict", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }
        if (mLog) {
            printTiming(t1, String.format(Locale.US,
                    "insertWithOnConflict with ", searchTable));
        }
    }

    private void explainQueryPlan(final SQLiteQueryBuilder qb, final SQLiteDatabase db,
            final String[] projection, final String selection,
            @SuppressWarnings("unused")
                    final String[] queryArgs,
            final String groupBy,
            @SuppressWarnings("unused")
                    final String having,
            final String sortOrder, final String limit) {
        final String queryString = qb.buildQuery(
                projection,
                selection,
                groupBy,
                null/*having*/,
                sortOrder,
                limit);
        explainQueryPlan(db, queryString, queryArgs);
    }

    private void explainQueryPlan(final SQLiteDatabase db, final String sql,
            final String[] queryArgs) {
        if (!Pattern.matches(mExplainQueryPlanRegexp, sql)) {
            return;
        }
        final Cursor planCursor = db.rawQuery("explain query plan " + sql, queryArgs);
        try {
            if (planCursor != null && planCursor.moveToFirst()) {
                final int detailColumn = planCursor.getColumnIndex("detail");
                final StringBuilder sb = new StringBuilder();
                do {
                    sb.append(planCursor.getString(detailColumn));
                    sb.append("\n");
                } while (planCursor.moveToNext());
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
                LogUtil.v(TAG, "for query " + sql + "\nplan is: "
                        + sb.toString());
            }
        } catch (final Exception e) {
            LogUtil.w(TAG, "Query plan failed ", e);
        } finally {
            if (planCursor != null) {
                planCursor.close();
            }
        }
    }

    public Cursor query(final String searchTable, final String[] projection,
            final String selection, final String[] selectionArgs, final String groupBy,
            final String having, final String orderBy, final String limit) {
        if (mExplainQueryPlanRegexp != null) {
            final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables(searchTable);
            explainQueryPlan(qb, mDatabase, projection, selection, selectionArgs,
                    groupBy, having, orderBy, limit);
        }

        maybePlayDebugNoise();
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        final Cursor cursor = mDatabase.query(searchTable, projection, selection, selectionArgs,
                groupBy, having, orderBy, limit);
        if (mLog) {
            printTiming(
                    t1,
                    String.format(Locale.US, "query %s with %s ==> %d",
                            searchTable, selection, cursor.getCount()));
        }
        return cursor;
    }

    public Cursor query(final String searchTable, final String[] columns,
            final String selection, final String[] selectionArgs, final String groupBy,
            final String having, final String orderBy) {
        return query(
                searchTable, columns, selection, selectionArgs,
                groupBy, having, orderBy, null);
    }

    public Cursor query(final SQLiteQueryBuilder qb,
            final String[] projection, final String selection, final String[] queryArgs,
            final String groupBy, final String having, final String sortOrder, final String limit) {
        if (mExplainQueryPlanRegexp != null) {
            explainQueryPlan(qb, mDatabase, projection, selection, queryArgs,
                    groupBy, having, sortOrder, limit);
        }
        maybePlayDebugNoise();
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        final Cursor cursor = qb.query(mDatabase, projection, selection, queryArgs, groupBy,
                having, sortOrder, limit);
        if (mLog) {
            printTiming(
                    t1,
                    String.format(Locale.US, "query %s with %s ==> %d",
                            qb.getTables(), selection, cursor.getCount()));
        }
        return cursor;
    }

    public long queryNumEntries(final String table, final String selection,
            final String[] selectionArgs) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        final long retval =
                DatabaseUtils.queryNumEntries(mDatabase, table, selection, selectionArgs);
        if (mLog){
            printTiming(
                    t1,
                    String.format(Locale.US, "queryNumEntries %s with %s ==> %d", table,
                            selection, retval));
        }
        return retval;
    }

    public Cursor rawQuery(final String sql, final String[] args) {
        if (mExplainQueryPlanRegexp != null) {
            explainQueryPlan(mDatabase, sql, args);
        }
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        final Cursor cursor = mDatabase.rawQuery(sql, args);
        if (mLog) {
            printTiming(
                    t1,
                    String.format(Locale.US, "rawQuery %s ==> %d", sql, cursor.getCount()));
        }
        return cursor;
    }

    public int update(final String table, final ContentValues values,
            final String selection, final String[] selectionArgs) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        int count = 0;
        try {
            count = mDatabase.update(table, values, selection, selectionArgs);
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to update", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }
        if (mLog) {
            printTiming(t1, String.format(Locale.US, "update %s with %s ==> %d",
                    table, selection, count));
        }
        return count;
    }

    public int delete(final String table, final String whereClause, final String[] whereArgs) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        int count = 0;
        try {
            count = mDatabase.delete(table, whereClause, whereArgs);
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to delete", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }
        if (mLog) {
            printTiming(t1,
                    String.format(Locale.US, "delete from %s with %s ==> %d", table,
                            whereClause, count));
        }
        return count;
    }

    public long insert(final String table, final String nullColumnHack,
            final ContentValues values) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        long rowId = -1;
        try {
            rowId = mDatabase.insert(table, nullColumnHack, values);
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to insert", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }
        if (mLog) {
            printTiming(t1, String.format(Locale.US, "insert to %s", table));
        }
        return rowId;
    }

    public long replace(final String table, final String nullColumnHack,
            final ContentValues values) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        long rowId = -1;
        try {
            rowId = mDatabase.replace(table, nullColumnHack, values);
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to replace", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }
        if (mLog) {
            printTiming(t1, String.format(Locale.US, "replace to %s", table));
        }
        return rowId;
    }

    public void setLocale(final Locale locale) {
        mDatabase.setLocale(locale);
    }

    public void execSQL(final String sql, final String[] bindArgs) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        try {
            mDatabase.execSQL(sql, bindArgs);
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to execSQL", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }

        if (mLog) {
            printTiming(t1, String.format(Locale.US, "execSQL %s", sql));
        }
    }

    public void execSQL(final String sql) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        try {
            mDatabase.execSQL(sql);
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to execSQL", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }

        if (mLog) {
            printTiming(t1, String.format(Locale.US, "execSQL %s", sql));
        }
    }

    public int execSQLUpdateDelete(final String sql) {
        long t1 = 0;
        if (mLog) {
            t1 = System.currentTimeMillis();
        }
        maybePlayDebugNoise();
        final SQLiteStatement statement = mDatabase.compileStatement(sql);
        int rowsUpdated = 0;
        try {
            rowsUpdated = statement.executeUpdateDelete();
        } catch (SQLiteFullException ex) {
            LogUtil.e(TAG, "Database full, unable to execSQLUpdateDelete", ex);
            UiUtils.showToastAtBottom(R.string.db_full);
        }
        if (mLog) {
            printTiming(t1, String.format(Locale.US, "execSQLUpdateDelete %s", sql));
        }
        return rowsUpdated;
    }

    public SQLiteDatabase getDatabase() {
        return mDatabase;
    }
}
