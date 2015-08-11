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

import android.os.Bundle;
import android.test.mock.MockCursor;

import java.util.ArrayList;

/**
 * A simple in memory fake cursor that can be used for UI tests.
 */
public class FakeCursor extends MockCursor {
    private final ArrayList<Integer> mProjection;
    private final String[] mColumnNamesOfData;
    private final Object[][] mData;
    private int mIndex;

    public FakeCursor(final String[] projection, final String[] columnNames,
            final Object[][] data) {
        mColumnNamesOfData = columnNames;
        mData = data;
        mIndex = -1;
        mProjection = new ArrayList<Integer>(projection.length);
        for (final String column : projection) {
            mProjection.add(getColumnIndex(column));
        }
    }

    public Object getAt(final String columnName, final int row) {
        final int dataIdx = getColumnIndex(columnName);
        return (dataIdx < 0 || row < 0 || row >= mData.length) ? 0 : mData[row][dataIdx];
    }

    @Override
    public int getCount() {
        return mData.length;
    }

    @Override
    public boolean isFirst() {
        return mIndex == 0;
    }

    @Override
    public boolean isLast() {
        return mIndex == mData.length - 1;
    }

    @Override
    public boolean moveToFirst() {
        if (mData.length == 0) {
            return false;
        }
        mIndex = 0;
        return true;
    }

    @Override
    public boolean moveToPosition(final int position) {
        if (position < 0 || position >= mData.length) {
            return false;
        }
        mIndex = position;
        return true;
    }

    @Override
    public int getPosition() {
        return mIndex;
    }

    @Override
    public boolean moveToPrevious() {
        if (mIndex <= 0) {
            return false;
        }
        mIndex--;
        return true;
    }

    @Override
    public boolean moveToNext() {
        if (mIndex == mData.length - 1) {
            return false;
        }

        mIndex++;
        return true;
    }

    @Override
    public int getColumnCount() {
        return mColumnNamesOfData.length;
    }

    @Override
    public int getColumnIndex(final String columnName) {
        for (int i = 0 ; i < mColumnNamesOfData.length ; i++) {
            if (mColumnNamesOfData[i].equals(columnName)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getColumnIndexOrThrow(final String columnName) {
        final int result = getColumnIndex(columnName);
        if (result == -1) {
            throw new IllegalArgumentException();
        }

        return result;
    }

    @Override
    public String getString(final int columnIndex) {
        final int dataIdx = mProjection.get(columnIndex);
        final Object obj = (dataIdx < 0 ? null : mData[mIndex][dataIdx]);
        return (obj == null ? null : obj.toString());
    }

    @Override
    public int getInt(final int columnIndex) {
        final int dataIdx = mProjection.get(columnIndex);
        return (dataIdx < 0 ? 0 : (Integer) mData[mIndex][dataIdx]);
    }

    @Override
    public long getLong(final int columnIndex) {
        final int dataIdx = mProjection.get(columnIndex);
        return (dataIdx < 0 ? 0 : (Long) mData[mIndex][dataIdx]);
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public Bundle getExtras() { return null; }
}
