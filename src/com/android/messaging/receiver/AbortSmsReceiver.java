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

import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;

/**
 * This receiver is used to abort SMS broadcasts pre-KLP when SMS is enabled.
 */
public final class AbortSmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // If we are enabled, it's our job to stop the broadcast from continuing. This
        // receiver is not used on KLP but we do an extra check here just to make sure.
        if (!OsUtil.isAtLeastKLP() && PhoneUtils.getDefault().isSmsEnabled()) {
            if (!SmsReceiver.shouldIgnoreMessage(intent)) {
                abortBroadcast();
            }
        }
    }
}
