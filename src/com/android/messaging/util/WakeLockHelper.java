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

import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.PowerManager;
import android.os.Process;

import com.google.common.annotations.VisibleForTesting;

/**
 * Helper class used to manage wakelock state
 */
public class WakeLockHelper {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;
    private static final boolean VERBOSE = false;

    @VisibleForTesting
    public static final String EXTRA_CALLING_PID = "pid";

    private final Object mLock = new Object();
    private final String mWakeLockId;
    private final int mMyPid;

    private PowerManager.WakeLock mWakeLock;

    public WakeLockHelper(final String wakeLockId) {
        mWakeLockId = wakeLockId;
        mMyPid = Process.myPid();
    }

    /**
     * Acquire the wakelock
     */
    public void acquire(final Context context, final Intent intent, final int opcode) {
        synchronized (mLock) {
            if (mWakeLock == null) {
                if (VERBOSE) {
                    LogUtil.v(TAG, "initializing wakelock");
                }
                final PowerManager pm = (PowerManager)
                        context.getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, mWakeLockId);
            }
        }
        if (VERBOSE) {
            LogUtil.v(TAG, "acquiring " + mWakeLockId + " for opcode " + opcode);
        }
        mWakeLock.acquire();
        intent.putExtra(EXTRA_CALLING_PID, mMyPid);
    }

    /**
     * Check if wakelock held by this process
     */
    public boolean isHeld(final Intent intent) {
        final boolean respectWakeLock = (mMyPid == intent.getIntExtra(EXTRA_CALLING_PID, -1));
        return (respectWakeLock && mWakeLock.isHeld());
    }

    /**
     * Ensure that wakelock is held by this process
     */
    public boolean ensure(final Intent intent, final int opcode) {
        final boolean respectWakeLock = (mMyPid == intent.getIntExtra(EXTRA_CALLING_PID, -1));
        if (VERBOSE) {
            LogUtil.v(TAG, "WakeLockHelper.ensure Intent " + intent + " "
                    + intent.getAction() + " opcode: " + opcode
                    + " respectWakeLock " + respectWakeLock);
        }

        if (respectWakeLock) {
            final boolean isHeld = (respectWakeLock && isHeld(intent));
            if (!isHeld) {
                LogUtil.e(TAG, "WakeLockHelper.ensure called " + intent + " " + intent.getAction()
                        + " opcode: " + opcode + " sWakeLock: " + mWakeLock + " isHeld: "
                        + ((mWakeLock == null) ? "(null)" : mWakeLock.isHeld()));
                if (!Debug.isDebuggerConnected()) {
                    Assert.fail("WakeLock dropped prior to service starting");
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Release wakelock (if it is held by this process)
     */
    public void release(final Intent intent, final int opcode) {
        final boolean respectWakeLock = (mMyPid == intent.getIntExtra(EXTRA_CALLING_PID, -1));
        if (respectWakeLock) {
            try {
                mWakeLock.release();
            } catch (final RuntimeException ex) {
                LogUtil.e(TAG, "KeepAliveService.onHandleIntent exit crash " + intent + " "
                        + intent.getAction() + " opcode: " + opcode + " sWakeLock: " + mWakeLock
                        + " isHeld: " + ((mWakeLock == null) ? "(null)" : mWakeLock.isHeld()));
                if (!Debug.isDebuggerConnected()) {
                    Assert.fail("WakeLock no longer held at end of handler");
                }
            }
        }
    }
}
