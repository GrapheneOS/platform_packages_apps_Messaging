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
package com.android.messaging.datamodel.media;

import android.os.SystemClock;

import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;
import com.google.common.base.Throwables;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A ref-counted class that holds loaded media resource, be it bitmaps or media bytes.
 * Subclasses must implement the close() method to release any resources (such as bitmaps)
 * when it's no longer used.
 *
 * Instances of the subclasses are:
 * 1. Loaded by their corresponding MediaRequest classes.
 * 2. Maintained by MediaResourceManager in its MediaCache pool.
 * 3. Used by the UI (such as ContactIconViews) to present the content.
 *
 * Note: all synchronized methods in this class (e.g. addRef()) should not attempt to make outgoing
 * calls that could potentially acquire media cache locks due to the potential deadlock this can
 * cause. To synchronize read/write access to shared resource, {@link #acquireLock()} and
 * {@link #releaseLock()} must be used, instead of using synchronized keyword.
 */
public abstract class RefCountedMediaResource {
    private final String mKey;
    private int mRef = 0;
    private long mLastRefAddTimestamp;

    // Set DEBUG to true to enable detailed stack trace for each addRef() and release() operation
    // to find out where each ref change happens.
    private static final boolean DEBUG = false;
    private static final String TAG = "bugle_media_ref_history";
    private final ArrayList<String> mRefHistory = new ArrayList<String>();

    // A lock that guards access to shared members in this class (and all its subclasses).
    private final ReentrantLock mLock = new ReentrantLock();

    public RefCountedMediaResource(final String key) {
        mKey = key;
    }

    public String getKey() {
        return mKey;
    }

    public void addRef() {
        acquireLock();
        try {
            if (DEBUG) {
                mRefHistory.add("Added ref current ref = " + mRef);
                mRefHistory.add(Throwables.getStackTraceAsString(new Exception()));
            }

            mRef++;
            mLastRefAddTimestamp = SystemClock.elapsedRealtime();
        } finally {
            releaseLock();
        }
    }

    public void release() {
        acquireLock();
        try {
            if (DEBUG) {
                mRefHistory.add("Released ref current ref = " + mRef);
                mRefHistory.add(Throwables.getStackTraceAsString(new Exception()));
            }

            mRef--;
            if (mRef == 0) {
                close();
            } else if (mRef < 0) {
                if (DEBUG) {
                    LogUtil.i(TAG, "Unwinding ref count history for RefCountedMediaResource "
                            + this);
                    for (final String ref : mRefHistory) {
                        LogUtil.i(TAG, ref);
                    }
                }
                Assert.fail("RefCountedMediaResource has unbalanced ref. Refcount=" + mRef);
            }
        } finally {
            releaseLock();
        }
    }

    public int getRefCount() {
        acquireLock();
        try {
            return mRef;
        } finally {
            releaseLock();
        }
    }

    public long getLastRefAddTimestamp() {
        acquireLock();
        try {
            return mLastRefAddTimestamp;
        } finally {
            releaseLock();
        }
    }

    public void assertSingularRefCount() {
        acquireLock();
        try {
            Assert.equals(1, mRef);
        } finally {
            releaseLock();
        }
    }

    void acquireLock() {
        mLock.lock();
    }

    void releaseLock() {
        mLock.unlock();
    }

    void assertLockHeldByCurrentThread() {
        Assert.isTrue(mLock.isHeldByCurrentThread());
    }

    boolean isEncoded() {
        return false;
    }

    boolean isCacheable() {
        return true;
    }

    MediaRequest<? extends RefCountedMediaResource> getMediaDecodingRequest(
            final MediaRequest<? extends RefCountedMediaResource> originalRequest) {
        return null;
    }

    MediaRequest<? extends RefCountedMediaResource> getMediaEncodingRequest(
            final MediaRequest<? extends RefCountedMediaResource> originalRequest) {
        return null;
    }

    public abstract int getMediaSize();
    protected abstract void close();
}