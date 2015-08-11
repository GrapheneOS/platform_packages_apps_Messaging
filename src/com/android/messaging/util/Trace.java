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


import android.annotation.TargetApi;
import android.os.Build;

/**
 * Helper class for systrace (see http://developer.android.com/tools/help/systrace.html).<p>
 * To enable, set log.tag.Bugle_Trace (defined by {@link #TAG} to VERBOSE before
 * the process starts.<p>
 * Note that this will run only on JBMR2 or later; on earlier platforms or if the log
 * tag isn't set, calls to {@link #beginSection(String)} or {@link #endSection()} are no-ops. <p>
 * Internally, calls dispatch to either a class that actually does work or a class that doesn't.
 * This avoids Dalvik complaining when it loads the class on earlier platforms that the
 * opcodes aren't available, and, according to the Dalvik team, using vtable dispatching for
 * something like this should be faster than if (OsUtil.isAtLeast...()) on each call.
 */
public final class Trace {
    private static final String TAG = "Bugle_Trace";
    private abstract static class AbstractTrace {
        abstract void beginSection(String sectionName);
        abstract void endSection();
    }

    private static final AbstractTrace sTrace;

    // Static initializer to pick the correct trace class to handle tracing.
    static {
        // Use android.util.Log instead of LogUtil here to avoid pulling in Gservices
        // too early in app startup.
        if (OsUtil.isAtLeastJB_MR2() &&
                android.util.Log.isLoggable(TAG, android.util.Log.VERBOSE)) {
            sTrace = new TraceJBMR2();
        } else {
            sTrace = new TraceShim();
        }
    }

    /**
     * Writes a trace message to indicate that a given section of code has begun. This call must
     * be followed by a corresponding call to {@link #endSection()} on the same thread.
     *
     * <p class="note"> At this time the vertical bar character '|', newline character '\n', and
     * null character '\0' are used internally by the tracing mechanism.  If sectionName contains
     * these characters they will be replaced with a space character in the trace.
     *
     * @param sectionName The name of the code section to appear in the trace.  This may be at
     * most 127 Unicode code units long.
     */
    public static void beginSection(String sectionName) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "beginSection() " + sectionName);
        }
        sTrace.beginSection(sectionName);
    }

    /**
     * Writes a trace message to indicate that a given section of code has ended. This call must
     * be preceeded by a corresponding call to {@link #beginSection(String)}. Calling this method
     * will mark the end of the most recently begun section of code, so care must be taken to
     * ensure that beginSection / endSection pairs are properly nested and called from the same
     * thread.
     */
    public static void endSection() {
        sTrace.endSection();
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "endSection()");
        }
    }

    /**
     * Internal class that we use if we really did enable tracing.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static final class TraceJBMR2 extends AbstractTrace {
        @Override
        void beginSection(String sectionName) {
            android.os.Trace.beginSection(sectionName);
        }

        @Override
        void endSection() {
            android.os.Trace.endSection();
        }
    }

    /**
     * Dummy class that we use if we aren't really tracing.
     */
    private static final class TraceShim extends AbstractTrace {
        @Override
        void beginSection(String sectionName) {
        }

        @Override
        void endSection() {
        }
    }
}
