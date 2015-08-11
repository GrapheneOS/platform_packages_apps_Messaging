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

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Debug;
import android.os.SystemClock;

import com.android.messaging.Factory;
import com.android.messaging.util.Assert.RunsOnAnyThread;

/**
 * Wrapper class which provides explicit API for:
 * <ol>
 *   <li>Threading policy choice - Users of this class should use the explicit API instead of
 *       {@link #execute} which uses different threading policy on different OS versions.
 *   <li>Enforce creation on main thread as required by AsyncTask
 *   <li>Enforce that the background task does not take longer than expected.
 * </ol>
 */
public abstract class SafeAsyncTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, Result> {
    private static final long DEFAULT_MAX_EXECUTION_TIME_MILLIS = 10 * 1000; // 10 seconds

    /** This is strongly discouraged as it can block other AsyncTasks indefinitely. */
    public static final long UNBOUNDED_TIME = Long.MAX_VALUE;

    private static final String WAKELOCK_ID = "bugle_safe_async_task_wakelock";
    protected static final int WAKELOCK_OP = 1000;
    private static WakeLockHelper sWakeLock = new WakeLockHelper(WAKELOCK_ID);

    private final long mMaxExecutionTimeMillis;
    private final boolean mCancelExecutionOnTimeout;
    private boolean mThreadPoolRequested;

    public SafeAsyncTask() {
        this(DEFAULT_MAX_EXECUTION_TIME_MILLIS, false);
    }

    public SafeAsyncTask(final long maxTimeMillis) {
        this(maxTimeMillis, false);
    }

    /**
     * @param maxTimeMillis maximum expected time for the background operation. This is just
     *        a diagnostic tool to catch unexpectedly long operations. If an operation does take
     *        longer than expected, it is fine to increase this argument. If the value is larger
     *        than a minute, you should consider using a dedicated thread so as not to interfere
     *        with other AsyncTasks.
     *
     *        <p>Use {@link #UNBOUNDED_TIME} if you do not know the maximum expected time. This
     *        is strongly discouraged as it can block other AsyncTasks indefinitely.
     *
     * @param cancelExecutionOnTimeout whether to attempt to cancel the task execution on timeout.
     *        If this is set, at execution timeout we will call cancel(), so doInBackgroundTimed()
     *        should periodically check if the task is to be cancelled and finish promptly if
     *        possible, and handle the cancel event in onCancelled(). Also, at the end of execution
     *        we will not crash the execution if it went over limit since we explicitly canceled it.
     */
    public SafeAsyncTask(final long maxTimeMillis, final boolean cancelExecutionOnTimeout) {
        Assert.isMainThread(); // AsyncTask has to be created on the main thread
        mMaxExecutionTimeMillis = maxTimeMillis;
        mCancelExecutionOnTimeout = cancelExecutionOnTimeout;
    }

    public final SafeAsyncTask<Params, Progress, Result> executeOnThreadPool(
            final Params... params) {
        Assert.isMainThread(); // AsyncTask requires this
        mThreadPoolRequested = true;
        executeOnExecutor(THREAD_POOL_EXECUTOR, params);
        return this;
    }

    protected abstract Result doInBackgroundTimed(final Params... params);

    @Override
    protected final Result doInBackground(final Params... params) {
        // This enforces that executeOnThreadPool was called, not execute. Ideally, we would
        // make execute throw an exception, but since it is final, we cannot override it.
        Assert.isTrue(mThreadPoolRequested);

        if (mCancelExecutionOnTimeout) {
            ThreadUtil.getMainThreadHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getStatus() == Status.RUNNING) {
                        // Cancel the task if it's still running.
                        LogUtil.w(LogUtil.BUGLE_TAG, String.format("%s timed out and is canceled",
                                this));
                        cancel(true /* mayInterruptIfRunning */);
                    }
                }
            }, mMaxExecutionTimeMillis);
        }

        final long startTime = SystemClock.elapsedRealtime();
        try {
            return doInBackgroundTimed(params);
        } finally {
            final long executionTime = SystemClock.elapsedRealtime() - startTime;
            if (executionTime > mMaxExecutionTimeMillis) {
                LogUtil.w(LogUtil.BUGLE_TAG, String.format("%s took %dms", this, executionTime));
                // Don't crash if debugger is attached or if we are asked to cancel on timeout.
                if (!Debug.isDebuggerConnected() && !mCancelExecutionOnTimeout) {
                    Assert.fail(this + " took too long");
                }
            }
        }

    }

    @Override
    protected void onPostExecute(final Result result) {
        // No need to use AsyncTask at all if there is no onPostExecute
        Assert.fail("Use SafeAsyncTask.executeOnThreadPool");
    }

    /**
     * This provides a way for people to run async tasks but without onPostExecute.
     * This can be called on any thread.
     *
     * Run code in a thread using AsyncTask's thread pool.
     *
     * To enable wakelock during the execution, see {@link #executeOnThreadPool(Runnable, boolean)}
     *
     * @param runnable The Runnable to execute asynchronously
     */
    @RunsOnAnyThread
    public static void executeOnThreadPool(final Runnable runnable) {
        executeOnThreadPool(runnable, false);
    }

    /**
     * This provides a way for people to run async tasks but without onPostExecute.
     * This can be called on any thread.
     *
     * Run code in a thread using AsyncTask's thread pool.
     *
     * @param runnable The Runnable to execute asynchronously
     * @param withWakeLock when set, a wake lock will be held for the duration of the runnable
     *        execution
     */
    public static void executeOnThreadPool(final Runnable runnable, final boolean withWakeLock) {
        if (withWakeLock) {
            final Intent intent = new Intent();
            sWakeLock.acquire(Factory.get().getApplicationContext(), intent, WAKELOCK_OP);
            THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        runnable.run();
                    } finally {
                        sWakeLock.release(intent, WAKELOCK_OP);
                    }
                }
            });
        } else {
            THREAD_POOL_EXECUTOR.execute(runnable);
        }
    }
}
