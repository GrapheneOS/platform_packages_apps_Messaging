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

package com.android.messaging.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.messaging.BugleApplication;
import com.android.messaging.Factory;
import com.android.messaging.datamodel.action.UpdateMessageNotificationAction;
import com.android.messaging.util.BuglePrefsKeys;
import com.android.messaging.util.LogUtil;

/**
 * Receives notification of boot completion and package replacement
 */
public class BootAndPackageReplacedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            // Repost unseen notifications
            Factory.get().getApplicationPrefs().putLong(
                    BuglePrefsKeys.LATEST_NOTIFICATION_MESSAGE_TIMESTAMP, Long.MIN_VALUE);
            UpdateMessageNotificationAction.updateMessageNotification();

            BugleApplication.updateAppConfig(context);
        } else {
            LogUtil.i(LogUtil.BUGLE_TAG, "BootAndPackageReplacedReceiver got unexpected action: "
                    + intent.getAction());
        }
    }
}

