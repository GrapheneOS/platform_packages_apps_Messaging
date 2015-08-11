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

package com.android.messaging.util;

/**
 * Very simple circular array implementation.
 *
 * @param <E> The element type of this list.
 * @LibraryInternal
 */
public class CircularArray<E> {
    private int mNextWriter;
    private boolean mHasWrapped;
    private int mMaxCount;
    Object mList[];

    /**
     * Constructor for CircularArray.
     *
     * @param count Max elements to hold in the list.
     */
    public CircularArray(int count) {
        mMaxCount = count;
        clear();
    }

    /**
     * Reset the list.
     */
    public void clear() {
        mNextWriter = 0;
        mHasWrapped = false;
        mList = new Object[mMaxCount];
    }

    /**
     * Add an element to the end of the list.
     *
     * @param object The object to add.
     */
    public void add(E object) {
        mList[mNextWriter] = object;
        ++mNextWriter;
        if (mNextWriter == mMaxCount) {
            mNextWriter = 0;
            mHasWrapped = true;
        }
    }

    /**
     * Get the number of elements in the list. This will be 0 <= returned count <= max count
     *
     * @return Elements in the circular list.
     */
    public int count() {
        if (mHasWrapped) {
            return mMaxCount;
        } else {
            return mNextWriter;
        }
    }

    /**
     * Return null if the list hasn't wrapped yet. Otherwise return the next object that would be
     * overwritten. Can be useful to avoid extra allocations.
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public E getFree() {
        if (!mHasWrapped) {
            return null;
        } else {
            return (E) mList[mNextWriter];
        }
    }

    /**
     * Get the object at index. Index 0 is the oldest item inserted into the list. Index (count() -
     * 1) is the newest.
     *
     * @param index Index to retrieve.
     * @return Object at index.
     */
    @SuppressWarnings("unchecked")
    public E get(int index) {
        if (mHasWrapped) {
            int wrappedIndex = index + mNextWriter;
            if (wrappedIndex >= mMaxCount) {
                wrappedIndex -= mMaxCount;
            }
            return (E) mList[wrappedIndex];
        } else {
            return (E) mList[index];
        }
    }
}
