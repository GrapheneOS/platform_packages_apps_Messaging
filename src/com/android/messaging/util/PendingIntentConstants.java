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


public class PendingIntentConstants {
    // Notifications
    public static final int SMS_NOTIFICATION_ID = 0;
    public static final int SMS_SECONDARY_USER_NOTIFICATION_ID = 1;
    public static final int MSG_SEND_ERROR = 2;
    public static final int SMS_STORAGE_LOW_NOTIFICATION_ID = 3;

    // Request codes
    public static final int UPDATE_NOTIFICATIONS_ALARM_ACTION_ID = 100;

    public static final int MIN_ASSIGNED_REQUEST_CODE = 1001;

    // Logging
    private static final String TAG = LogUtil.BUGLE_TAG;
    private static final boolean VERBOSE = false;

    // Internal Constants
    private static final String NOTIFICATION_REQUEST_CODE_PREFS = "notificationRequestCodes.v1";
    private static final String REQUEST_CODE_DELIMITER = "|";
    private static final String MAX_REQUEST_CODE_KEY = "maxRequestCode";
}
