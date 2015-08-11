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

import android.support.v4.util.LongSparseArray;

/**
 * A space saving set for long values using v4 compat LongSparseArray
 */
public class LongSparseSet {
    private static final Object THE_ONLY_VALID_VALUE = new Object();
    private final LongSparseArray<Object> mSet = new LongSparseArray<Object>();

    public LongSparseSet() {
    }

    /**
     * @param key The element to check
     * @return True if the element is in the set, false otherwise
     */
    public boolean contains(long key) {
        if (mSet.get(key, null/*default*/) == THE_ONLY_VALID_VALUE) {
            return true;
        }
        return false;
    }

    /**
     * Add an element to the set
     *
     * @param key The element to add
     */
    public void add(long key) {
        mSet.put(key, THE_ONLY_VALID_VALUE);
    }

    /**
     * Remove an element from the set
     *
     * @param key The element to remove
     */
    public void remove(long key) {
        mSet.delete(key);
    }
}
