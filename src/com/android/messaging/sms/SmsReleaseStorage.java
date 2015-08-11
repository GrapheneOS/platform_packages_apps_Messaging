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

package com.android.messaging.sms;

import android.content.res.Resources;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.SyncManager;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.LogUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class handling message cleanup when storage is low
 */
public class SmsReleaseStorage {
    /**
     * Class representing a time duration specified by Gservices
     */
    public static class Duration {
        // Time duration unit types
        public static final int UNIT_WEEK = 'w';
        public static final int UNIT_MONTH = 'm';
        public static final int UNIT_YEAR = 'y';

        // Number of units
        public final int mCount;
        // Unit type: week, month or year
        public final int mUnit;

        public Duration(final int count, final int unit) {
            mCount = count;
            mUnit = unit;
        }
    }

    private static final String TAG = LogUtil.BUGLE_TAG;

    private static final Duration DEFAULT_DURATION = new Duration(1, Duration.UNIT_MONTH);

    private static final Pattern DURATION_PATTERN = Pattern.compile("([1-9]+\\d*)(w|m|y)");
    /**
     * Parse message retaining time duration specified by Gservices
     *
     * @return The parsed time duration from Gservices
     */
    public static Duration parseMessageRetainingDuration() {
        final String smsAutoDeleteMessageRetainingDuration =
                BugleGservices.get().getString(
                        BugleGservicesKeys.SMS_STORAGE_PURGING_MESSAGE_RETAINING_DURATION,
                        BugleGservicesKeys.SMS_STORAGE_PURGING_MESSAGE_RETAINING_DURATION_DEFAULT);
        final Matcher matcher = DURATION_PATTERN.matcher(smsAutoDeleteMessageRetainingDuration);
        try {
            if (matcher.matches()) {
                return new Duration(
                        Integer.parseInt(matcher.group(1)),
                        matcher.group(2).charAt(0));
            }
        } catch (final NumberFormatException e) {
            // Nothing to do
        }
        LogUtil.e(TAG, "SmsAutoDelete: invalid duration " +
                smsAutoDeleteMessageRetainingDuration);
        return DEFAULT_DURATION;
    }

    /**
     * Get string representation of the time duration
     *
     * @param duration
     * @return
     */
    public static String getMessageRetainingDurationString(final Duration duration) {
        final Resources resources = Factory.get().getApplicationContext().getResources();
        switch (duration.mUnit) {
            case Duration.UNIT_WEEK:
                return resources.getQuantityString(
                        R.plurals.week_count, duration.mCount, duration.mCount);
            case Duration.UNIT_MONTH:
                return resources.getQuantityString(
                        R.plurals.month_count, duration.mCount, duration.mCount);
            case Duration.UNIT_YEAR:
                return resources.getQuantityString(
                        R.plurals.year_count, duration.mCount, duration.mCount);
        }
        throw new IllegalArgumentException(
                "SmsAutoDelete: invalid duration unit " + duration.mUnit);
    }

    // Time conversations
    private static final long WEEK_IN_MILLIS = 7 * 24 * 3600 * 1000L;
    private static final long MONTH_IN_MILLIS = 30 * 24 * 3600 * 1000L;
    private static final long YEAR_IN_MILLIS = 365 * 24 * 3600 * 1000L;

    /**
     * Convert time duration to time in milliseconds
     *
     * @param duration
     * @return
     */
    public static long durationToTimeInMillis(final Duration duration) {
        switch (duration.mUnit) {
            case Duration.UNIT_WEEK:
                return duration.mCount * WEEK_IN_MILLIS;
            case Duration.UNIT_MONTH:
                return duration.mCount * MONTH_IN_MILLIS;
            case Duration.UNIT_YEAR:
                return duration.mCount * YEAR_IN_MILLIS;
        }
        return -1L;
    }

    /**
     * Delete message actions:
     * 0: delete media messages
     * 1: delete old messages
     *
     * @param actionIndex The index of the delete action to perform
     * @param durationInMillis The time duration for retaining messages
     */
    public static void deleteMessages(final int actionIndex, final long durationInMillis) {
        int deleted = 0;
        switch (actionIndex) {
            case 0: {
                // Delete media
                deleted = MmsUtils.deleteMediaMessages();
                break;
            }
            case 1: {
                // Delete old messages
                final long now = System.currentTimeMillis();
                final long cutOffTimestampInMillis = now - durationInMillis;
                // Delete messages from telephony provider
                deleted = MmsUtils.deleteMessagesOlderThan(cutOffTimestampInMillis);
                break;
            }
            default: {
                LogUtil.e(TAG, "SmsStorageStatusManager: invalid action " + actionIndex);
                break;
            }
        }

        if (deleted > 0) {
            // Kick off a sync to update local db.
            SyncManager.sync();
        }
    }
}
