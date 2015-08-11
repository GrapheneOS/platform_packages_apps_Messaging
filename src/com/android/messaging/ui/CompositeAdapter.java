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
package com.android.messaging.ui;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * A general purpose adapter that composes one or more other adapters.  It
 * appends them in the order they are added.
 */
public class CompositeAdapter extends BaseAdapter {

    private static final int INITIAL_CAPACITY = 2;

    public static class Partition {
        boolean mShowIfEmpty;
        boolean mHasHeader;
        BaseAdapter mAdapter;

        public Partition(final boolean showIfEmpty, final boolean hasHeader,
                final BaseAdapter adapter) {
            this.mShowIfEmpty = showIfEmpty;
            this.mHasHeader = hasHeader;
            this.mAdapter = adapter;
        }

        /**
         * True if the directory should be shown even if no contacts are found.
         */
        public boolean showIfEmpty() {
            return mShowIfEmpty;
        }

        public boolean hasHeader() {
            return mHasHeader;
        }

        public int getCount() {
            int count = mAdapter.getCount();
            if (mHasHeader && (count != 0 || mShowIfEmpty)) {
                count++;
            }
            return count;
        }

        public BaseAdapter getAdapter() {
            return mAdapter;
        }

        public View getHeaderView(final View convertView, final ViewGroup parentView) {
            return null;
        }

        public void close() {
            // do nothing in base class.
        }
    }

    private class Observer extends DataSetObserver {
        @Override
        public void onChanged() {
            CompositeAdapter.this.notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            CompositeAdapter.this.notifyDataSetInvalidated();
        }
    };

    protected final Context mContext;
    private Partition[] mPartitions;
    private int mSize = 0;
    private int mCount = 0;
    private boolean mCacheValid = true;
    private final Observer mObserver;

    public CompositeAdapter(final Context context) {
        mContext = context;
        mObserver = new Observer();
        mPartitions = new Partition[INITIAL_CAPACITY];
    }

    public Context getContext() {
        return mContext;
    }

    public void addPartition(final Partition partition) {
        if (mSize >= mPartitions.length) {
            final int newCapacity = mSize + 2;
            final Partition[] newAdapters = new Partition[newCapacity];
            System.arraycopy(mPartitions, 0, newAdapters, 0, mSize);
            mPartitions = newAdapters;
        }
        mPartitions[mSize++] = partition;
        partition.getAdapter().registerDataSetObserver(mObserver);
        invalidate();
        notifyDataSetChanged();
    }

    public void removePartition(final int index) {
        final Partition partition = mPartitions[index];
        partition.close();
        System.arraycopy(mPartitions, index + 1, mPartitions, index,
                mSize - index - 1);
        mSize--;
        partition.getAdapter().unregisterDataSetObserver(mObserver);
        invalidate();
        notifyDataSetChanged();
    }

    public void clearPartitions() {
        for (int i = 0; i < mSize; i++) {
            final Partition partition = mPartitions[i];
            partition.close();
            partition.getAdapter().unregisterDataSetObserver(mObserver);
        }
        invalidate();
        notifyDataSetChanged();
    }

    public Partition getPartition(final int index) {
        return mPartitions[index];
    }

    public int getPartitionAtPosition(final int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            final int end = start + mPartitions[i].getCount();
            if (position >= start && position < end) {
                int offset = position - start;
                if (mPartitions[i].hasHeader() &&
                        (mPartitions[i].getCount() > 0 || mPartitions[i].showIfEmpty())) {
                    offset--;
                }
                if (offset == -1) {
                    return -1;
                }
                return i;
            }
            start = end;
        }
        return mSize - 1;
    }

    public int getPartitionCount() {
        return mSize;
    }

    public void invalidate() {
        mCacheValid = false;
    }

    private void ensureCacheValid() {
        if (mCacheValid) {
            return;
        }
        mCount = 0;
        for (int i = 0; i < mSize; i++) {
            mCount += mPartitions[i].getCount();
        }
    }

    @Override
    public int getCount() {
        ensureCacheValid();
        return mCount;
    }

    public int getCount(final int index) {
        ensureCacheValid();
        return mPartitions[index].getCount();
    }

    @Override
    public Object getItem(final int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            final int end = start + mPartitions[i].getCount();
            if (position >= start && position < end) {
                final int offset = position - start;
                final Partition partition = mPartitions[i];
                if (partition.hasHeader() && offset == 0 &&
                        (partition.getCount() > 0 || partition.showIfEmpty())) {
                    // This is the header
                    return null;
                }
                return mPartitions[i].getAdapter().getItem(offset);
            }
            start = end;
        }

        return null;
    }

    @Override
    public long getItemId(final int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            final int end = start + mPartitions[i].getCount();
            if (position >= start && position < end) {
                final int offset = position - start;
                final Partition partition = mPartitions[i];
                if (partition.hasHeader() && offset == 0 &&
                        (partition.getCount() > 0 || partition.showIfEmpty())) {
                    // Header
                    return 0;
                }
                return mPartitions[i].getAdapter().getItemId(offset);
            }
            start = end;
        }

        return 0;
    }

    @Override
    public boolean isEnabled(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            final int end = start + mPartitions[i].getCount();
            if (position >= start && position < end) {
                final int offset = position - start;
                final Partition partition = mPartitions[i];
                if (partition.hasHeader() && offset == 0 &&
                        (partition.getCount() > 0 || partition.showIfEmpty())) {
                    // This is the header
                    return false;
                }
                return true;
            }
            start = end;
        }
        return true;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parentView) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            final Partition partition = mPartitions[i];
            final int end = start + partition.getCount();
            if (position >= start && position < end) {
                int offset = position - start;
                View view;
                if (partition.hasHeader() &&
                        (partition.getCount() > 0 || partition.showIfEmpty())) {
                    offset = offset - 1;
                }
                if (offset == -1) {
                    view = partition.getHeaderView(convertView, parentView);
                } else {
                    view = partition.getAdapter().getView(offset, convertView, parentView);
                }
                if (view == null) {
                    throw new NullPointerException("View should not be null, partition: " + i
                            + " position: " + offset);
                }
                return view;
            }
            start = end;
        }

        throw new ArrayIndexOutOfBoundsException(position);
    }
}
