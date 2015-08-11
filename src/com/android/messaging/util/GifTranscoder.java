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
import android.text.format.Formatter;

import com.google.common.base.Stopwatch;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Compresses a GIF so it can be sent via MMS.
 * <p>
 * The entry point lives in its own class, we can defer loading the native GIF transcoding library
 * into memory until we actually need it.
 */
public class GifTranscoder {
    private static final String TAG = LogUtil.BUGLE_TAG;

    private static int MIN_HEIGHT = 100;
    private static int MIN_WIDTH = 100;

    static {
        System.loadLibrary("giftranscode");
    }

    public static boolean transcode(Context context, String filePath, String outFilePath) {
        if (!isEnabled()) {
            return false;
        }
        final long inputSize = new File(filePath).length();
        Stopwatch stopwatch = Stopwatch.createStarted();
        final boolean success = transcodeInternal(filePath, outFilePath);
        stopwatch.stop();
        final long elapsedMs = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        final long outputSize = new File(outFilePath).length();
        final float compression = (inputSize > 0) ? ((float) outputSize / inputSize) : 0;

        if (success) {
            LogUtil.i(TAG, String.format("Resized GIF (%s) in %d ms, %s => %s (%.0f%%)",
                    LogUtil.sanitizePII(filePath),
                    elapsedMs,
                    Formatter.formatShortFileSize(context, inputSize),
                    Formatter.formatShortFileSize(context, outputSize),
                    compression * 100.0f));
        }
        return success;
    }

    private static native boolean transcodeInternal(String filePath, String outFilePath);

    /**
     * Estimates the size of a GIF transcoded from a GIF with the specified size.
     */
    public static long estimateFileSizeAfterTranscode(long fileSize) {
        // I tested transcoding on ~70 GIFs and found that the transcoded files are in general
        // about 25-35% the size of the original. This compression ratio is very consistent for the
        // class of GIFs we care about most: those converted from video clips and 1-3 MB in size.
        return (long) (fileSize * 0.35f);
    }

    public static boolean canBeTranscoded(int width, int height) {
        if (!isEnabled()) {
            return false;
        }
        return width >= MIN_WIDTH && height >= MIN_HEIGHT;
    }

    private static boolean isEnabled() {
        final boolean enabled = BugleGservices.get().getBoolean(
                BugleGservicesKeys.ENABLE_GIF_TRANSCODING,
                BugleGservicesKeys.ENABLE_GIF_TRANSCODING_DEFAULT);
        if (!enabled) {
            LogUtil.w(TAG, "GIF transcoding is disabled");
        }
        return enabled;
    }
}
