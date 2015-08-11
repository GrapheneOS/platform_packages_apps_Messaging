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

import android.os.SystemClock;

/**
 * A utility timer that logs the execution time of operations
 */
public class LoggingTimer {
    private static final int NO_WARN_LIMIT = -1;

    private final String mTag;
    private final String mName;
    private final long mWarnLimitMillis;
    private long mStartMillis;

    public LoggingTimer(final String tag, final String name) {
        this(tag, name, NO_WARN_LIMIT);
    }

    public LoggingTimer(final String tag, final String name, final long warnLimitMillis) {
        mTag = tag;
        mName = name;
        mWarnLimitMillis = warnLimitMillis;
    }

    /**
     * This method should be called at the start of the operation to be timed.
     */
    public void start() {
        mStartMillis = SystemClock.elapsedRealtime();

        if (LogUtil.isLoggable(mTag, LogUtil.VERBOSE)) {
            LogUtil.v(mTag, "Timer start for " + mName);
        }
    }

    /**
     * This method should be called at the end of the operation to be timed. It logs the time since
     * the last call to {@link #start}
     */
    public void stopAndLog() {
        final long elapsedMs = SystemClock.elapsedRealtime() - mStartMillis;

        final String logMessage = String.format("Used %dms for %s", elapsedMs, mName);

        LogUtil.save(LogUtil.DEBUG, mTag, logMessage);

        if (mWarnLimitMillis != NO_WARN_LIMIT && elapsedMs > mWarnLimitMillis) {
            LogUtil.w(mTag, logMessage);
        } else if (LogUtil.isLoggable(mTag, LogUtil.VERBOSE)) {
            LogUtil.v(mTag, logMessage);
        }
    }
}
