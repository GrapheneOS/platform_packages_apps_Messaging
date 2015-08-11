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

import android.os.Process;
import android.util.Log;

import com.android.messaging.Factory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Save the app's own log to dump along with adb bugreport
 */
public abstract class LogSaver {
    /**
     * Writes the accumulated log entries, from oldest to newest, to the specified PrintWriter.
     * Log lines are emitted in much the same form as logcat -v threadtime -- specifically,
     * lines will include a timestamp, pid, tid, level, and tag.
     *
     * @param writer The PrintWriter to output
     */
    public abstract void dump(PrintWriter writer);

    /**
     * Log a line
     *
     * @param level The log level to use
     * @param tag The log tag
     * @param msg The message of the log line
     */
    public abstract void log(int level, String tag, String msg);

    /**
     * Check if the LogSaver still matches the current Gservices settings
     *
     * @return true if matches, false otherwise
     */
    public abstract boolean isCurrent();

    private LogSaver() {
    }

    public static LogSaver newInstance() {
        final boolean persistent = BugleGservices.get().getBoolean(
                BugleGservicesKeys.PERSISTENT_LOGSAVER,
                BugleGservicesKeys.PERSISTENT_LOGSAVER_DEFAULT);
        if (persistent) {
            final int setSize = BugleGservices.get().getInt(
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_ROTATION_SET_SIZE,
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_ROTATION_SET_SIZE_DEFAULT);
            final int fileLimitBytes = BugleGservices.get().getInt(
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_FILE_LIMIT_BYTES,
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_FILE_LIMIT_BYTES_DEFAULT);
            return new DiskLogSaver(setSize, fileLimitBytes);
        } else {
            final int size = BugleGservices.get().getInt(
                    BugleGservicesKeys.IN_MEMORY_LOGSAVER_RECORD_COUNT,
                    BugleGservicesKeys.IN_MEMORY_LOGSAVER_RECORD_COUNT_DEFAULT);
            return new MemoryLogSaver(size);
        }
    }

    /**
     * A circular in-memory log to be used to log potentially verbose logs. The logs will be
     * persisted in memory in the application and can be dumped by various dump() methods.
     * For example, adb shell dumpsys activity provider com.android.messaging.
     * The dump will also show up in bugreports.
     */
    private static final class MemoryLogSaver extends LogSaver {
        /**
         * Record to store a single log entry. Stores timestamp, tid, level, tag, and message.
         * It can be reused when the circular log rolls over. This avoids creating new objects.
         */
        private static class LogRecord {
            int mTid;
            String mLevelString;
            long mTimeMillis;     // from System.currentTimeMillis
            String mTag;
            String mMessage;

            LogRecord() {
            }

            void set(int tid, int level, long time, String tag, String message) {
                this.mTid = tid;
                this.mTimeMillis = time;
                this.mTag = tag;
                this.mMessage = message;
                this.mLevelString = getLevelString(level);
            }
        }

        private final int mSize;
        private final CircularArray<LogRecord> mLogList;
        private final Object mLock;

        private final SimpleDateFormat mSdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

        public MemoryLogSaver(final int size) {
            mSize = size;
            mLogList = new CircularArray<LogRecord>(size);
            mLock = new Object();
        }

        @Override
        public void dump(PrintWriter writer) {
            int pid = Process.myPid();
            synchronized (mLock) {
                for (int i = 0; i < mLogList.count(); i++) {
                    LogRecord rec = mLogList.get(i);
                    writer.println(String.format("%s %5d %5d %s %s: %s",
                            mSdf.format(rec.mTimeMillis),
                            pid, rec.mTid, rec.mLevelString, rec.mTag, rec.mMessage));
                }
            }
        }

        @Override
        public void log(int level, String tag, String msg) {
            synchronized (mLock) {
                LogRecord rec = mLogList.getFree();
                if (rec == null) {
                    rec = new LogRecord();
                }
                rec.set(Process.myTid(), level, System.currentTimeMillis(), tag, msg);
                mLogList.add(rec);
            }
        }

        @Override
        public boolean isCurrent() {
            final boolean persistent = BugleGservices.get().getBoolean(
                    BugleGservicesKeys.PERSISTENT_LOGSAVER,
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_DEFAULT);
            if (persistent) {
                return false;
            }
            final int size = BugleGservices.get().getInt(
                    BugleGservicesKeys.IN_MEMORY_LOGSAVER_RECORD_COUNT,
                    BugleGservicesKeys.IN_MEMORY_LOGSAVER_RECORD_COUNT_DEFAULT);
            return size == mSize;
        }
    }

    /**
     * A persistent, on-disk log saver. It uses the standard Java util logger along with
     * a rotation log file set to store the logs in app's local file directory "app_logs".
     */
    private static final class DiskLogSaver extends LogSaver {
        private static final String DISK_LOG_DIR_NAME = "logs";

        private final int mSetSize;
        private final int mFileLimitBytes;
        private Logger mDiskLogger;

        public DiskLogSaver(final int setSize, final int fileLimitBytes) {
            Assert.isTrue(setSize > 0);
            Assert.isTrue(fileLimitBytes > 0);
            mSetSize = setSize;
            mFileLimitBytes = fileLimitBytes;
            initDiskLog();
        }

        private static void clearDefaultHandlers(Logger logger) {
            Assert.notNull(logger);
            for (Handler handler : logger.getHandlers()) {
                logger.removeHandler(handler);
            }
        }

        private void initDiskLog() {
            mDiskLogger = Logger.getLogger(LogUtil.BUGLE_TAG);
            // We don't want the default console handler
            clearDefaultHandlers(mDiskLogger);
            // Don't want duplicate print in system log
            mDiskLogger.setUseParentHandlers(false);
            // FileHandler manages the log files in a fixed rotation set
            final File logDir = Factory.get().getApplicationContext().getDir(
                    DISK_LOG_DIR_NAME, 0/*mode*/);
            FileHandler handler = null;
            try {
                handler = new FileHandler(
                        logDir + "/%g.log", mFileLimitBytes, mSetSize, true/*append*/);
            } catch (Exception e) {
                Log.e(LogUtil.BUGLE_TAG, "LogSaver: fail to init disk logger", e);
                return;
            }
            final Formatter formatter = new Formatter() {
                @Override
                public String format(java.util.logging.LogRecord r) {
                    return r.getMessage();
                }
            };
            handler.setFormatter(formatter);
            handler.setLevel(Level.ALL);
            mDiskLogger.addHandler(handler);
        }

        @Override
        public void dump(PrintWriter writer) {
            for (int i = mSetSize - 1; i >= 0; i--) {
                final File logDir = Factory.get().getApplicationContext().getDir(
                        DISK_LOG_DIR_NAME, 0/*mode*/);
                final String logFilePath = logDir + "/" + i + ".log";
                try {
                    final File logFile = new File(logFilePath);
                    if (!logFile.exists()) {
                        continue;
                    }
                    final BufferedReader reader = new BufferedReader(new FileReader(logFile));
                    for (String line; (line = reader.readLine()) != null;) {
                        line = line.trim();
                        writer.println(line);
                    }
                } catch (FileNotFoundException e) {
                    Log.w(LogUtil.BUGLE_TAG, "LogSaver: can not find log file " + logFilePath);
                } catch (IOException e) {
                    Log.w(LogUtil.BUGLE_TAG, "LogSaver: can not read log file", e);
                }
            }
        }

        @Override
        public void log(int level, String tag, String msg) {
            final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
            mDiskLogger.info(String.format("%s %5d %5d %s %s: %s\n",
                    sdf.format(System.currentTimeMillis()),
                    Process.myPid(), Process.myTid(), getLevelString(level), tag, msg));
        }

        @Override
        public boolean isCurrent() {
            final boolean persistent = BugleGservices.get().getBoolean(
                    BugleGservicesKeys.PERSISTENT_LOGSAVER,
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_DEFAULT);
            if (!persistent) {
                return false;
            }
            final int setSize = BugleGservices.get().getInt(
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_ROTATION_SET_SIZE,
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_ROTATION_SET_SIZE_DEFAULT);
            final int fileLimitBytes = BugleGservices.get().getInt(
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_FILE_LIMIT_BYTES,
                    BugleGservicesKeys.PERSISTENT_LOGSAVER_FILE_LIMIT_BYTES_DEFAULT);
            return setSize == mSetSize && fileLimitBytes == mFileLimitBytes;
        }
    }

    private static String getLevelString(final int level) {
        switch (level) {
            case android.util.Log.DEBUG:
                return "D";
            case android.util.Log.WARN:
                return "W";
            case android.util.Log.INFO:
                return "I";
            case android.util.Log.VERBOSE:
                return "V";
            case android.util.Log.ERROR:
                return "E";
            case android.util.Log.ASSERT:
                return "A";
            default:
                return "?";
        }
    }
}
