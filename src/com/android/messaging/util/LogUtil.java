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
 * Log utility class.
 */
public class LogUtil {
    public static final String BUGLE_TAG = "MessagingApp";
    public static final String PROFILE_TAG = "MessagingAppProf";
    public static final String BUGLE_DATABASE_TAG = "MessagingAppDb";
    public static final String BUGLE_DATABASE_PERF_TAG = "MessagingAppDbPerf";
    public static final String BUGLE_DATAMODEL_TAG = "MessagingAppDataModel";
    public static final String BUGLE_IMAGE_TAG = "MessagingAppImage";
    public static final String BUGLE_NOTIFICATIONS_TAG = "MessagingAppNotif";
    public static final String BUGLE_WIDGET_TAG = "MessagingAppWidget";

    public static final int DEBUG = android.util.Log.DEBUG;
    public static final int WARN = android.util.Log.WARN;
    public static final int VERBOSE = android.util.Log.VERBOSE;
    public static final int INFO = android.util.Log.INFO;
    public static final int ERROR = android.util.Log.ERROR;

    // If this is non-null, DEBUG and higher logs will be tracked in-memory. It will not include
    // VERBOSE logs.
    private static LogSaver sDebugLogSaver;
    private static volatile boolean sCaptureDebugLogs;

    /**
     * Read Gservices to see if logging should be enabled.
     */
    public static void refreshGservices(final BugleGservices gservices) {
        sCaptureDebugLogs = gservices.getBoolean(
                BugleGservicesKeys.ENABLE_LOG_SAVER,
                BugleGservicesKeys.ENABLE_LOG_SAVER_DEFAULT);
        if (sCaptureDebugLogs && (sDebugLogSaver == null || !sDebugLogSaver.isCurrent())) {
            // We were not capturing logs before. We are now.
            sDebugLogSaver = LogSaver.newInstance();
        } else if (!sCaptureDebugLogs && sDebugLogSaver != null) {
            // We were capturing logs. We aren't anymore.
            sDebugLogSaver = null;
        }
    }

 // This is called from FactoryImpl once the Gservices class is initialized.
    public static void initializeGservices (final BugleGservices gservices) {
        gservices.registerForChanges(new Runnable() {
            @Override
            public void run() {
                refreshGservices(gservices);
            }
        });
        refreshGservices(gservices);
    }

    /**
     * Send a {@link #VERBOSE} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void v(final String tag, final String msg) {
        println(android.util.Log.VERBOSE, tag, msg);
    }

    /**
     * Send a {@link #VERBOSE} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void v(final String tag, final String msg, final Throwable tr) {
        println(android.util.Log.VERBOSE, tag, msg + '\n'
                + android.util.Log.getStackTraceString(tr));
    }

    /**
     * Send a {@link #DEBUG} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void d(final String tag, final String msg) {
        println(android.util.Log.DEBUG, tag, msg);
    }

    /**
     * Send a {@link #DEBUG} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void d(final String tag, final String msg, final Throwable tr) {
        println(android.util.Log.DEBUG, tag, msg + '\n'
                + android.util.Log.getStackTraceString(tr));
    }

    /**
     * Send an {@link #INFO} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void i(final String tag, final String msg) {
        println(android.util.Log.INFO, tag, msg);
    }

    /**
     * Send a {@link #INFO} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void i(final String tag, final String msg, final Throwable tr) {
        println(android.util.Log.INFO, tag, msg + '\n'
                + android.util.Log.getStackTraceString(tr));
    }

    /**
     * Send a {@link #WARN} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void w(final String tag, final String msg) {
        println(android.util.Log.WARN, tag, msg);
    }

    /**
     * Send a {@link #WARN} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void w(final String tag, final String msg, final Throwable tr) {
        println(android.util.Log.WARN, tag, msg);
        println(android.util.Log.WARN, tag, android.util.Log.getStackTraceString(tr));
    }

    /**
     * Send an {@link #ERROR} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void e(final String tag, final String msg) {
        println(android.util.Log.ERROR, tag, msg);
    }

    /**
     * Send a {@link #ERROR} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void e(final String tag, final String msg, final Throwable tr) {
        println(android.util.Log.ERROR, tag, msg);
        println(android.util.Log.ERROR, tag, android.util.Log.getStackTraceString(tr));
    }

    /**
     * What a Terrible Failure: Report a condition that should never happen.
     * The error will always be logged at level ASSERT with the call stack.
     * Depending on system configuration, a report may be added to the
     * {@link android.os.DropBoxManager} and/or the process may be terminated
     * immediately with an error dialog.
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     */
    public static void wtf(final String tag, final String msg) {
        // Make sure this goes into our log buffer
        println(android.util.Log.ASSERT, tag, "wtf\n" + msg);
        android.util.Log.wtf(tag, msg, new Exception());
    }

    /**
     * What a Terrible Failure: Report a condition that should never happen.
     * The error will always be logged at level ASSERT with the call stack.
     * Depending on system configuration, a report may be added to the
     * {@link android.os.DropBoxManager} and/or the process may be terminated
     * immediately with an error dialog.
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static void wtf(final String tag, final String msg, final Throwable tr) {
        // Make sure this goes into our log buffer
        println(android.util.Log.ASSERT, tag, "wtf\n" + msg + '\n' +
                android.util.Log.getStackTraceString(tr));
        android.util.Log.wtf(tag, msg, tr);
    }

    /**
     * Low-level logging call.
     * @param level The priority/type of this log message
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    private static void println(final int level, final String tag, final String msg) {
        android.util.Log.println(level, tag, msg);

        LogSaver serviceLog = sDebugLogSaver;
        if (serviceLog != null && level >= android.util.Log.DEBUG) {
            serviceLog.log(level, tag, msg);
        }
    }

    /**
     * Save logging into LogSaver only, for dumping to bug report
     *
     * @param level The priority/type of this log message
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void save(final int level, final String tag, final String msg) {
        LogSaver serviceLog = sDebugLogSaver;
        if (serviceLog != null) {
            serviceLog.log(level, tag, msg);
        }
    }

    /**
     * Checks to see whether or not a log for the specified tag is loggable at the specified level.
     * See {@link android.util.Log#isLoggable(String, int)} for more discussion.
     */
    public static boolean isLoggable(final String tag, final int level) {
        return android.util.Log.isLoggable(tag, level);
    }

    /**
     * Returns text as is if {@value #BUGLE_TAG}'s log level is set to DEBUG or VERBOSE;
     * returns "--" otherwise. Useful for log statements where we don't want to log
     * various strings (e.g., usernames) with default logging to avoid leaking PII in logcat.
     */
    public static String sanitizePII(final String text) {
        if (text == null) {
            return null;
        }

        if (android.util.Log.isLoggable(BUGLE_TAG, android.util.Log.DEBUG)) {
            return text;
        } else {
            return "Redacted-" + text.length();
        }
    }

    public static void dump(java.io.PrintWriter out) {
        final LogSaver logsaver = sDebugLogSaver;
        if (logsaver != null) {
            logsaver.dump(out);
        }
    }
}
