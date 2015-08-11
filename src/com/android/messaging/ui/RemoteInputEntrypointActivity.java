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
package com.android.messaging.ui;

import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;

import com.android.messaging.datamodel.NoConfirmationSmsSendService;
import com.android.messaging.util.LogUtil;

public class RemoteInputEntrypointActivity extends BaseBugleActivity {
    private static final String TAG = LogUtil.BUGLE_TAG;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            LogUtil.w(TAG, "No intent attached");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Perform some action depending on the intent
        String action = intent.getAction();
        if (Intent.ACTION_SENDTO.equals(action)) {
            // Build and send the intent
            final Intent sendIntent = new Intent(this, NoConfirmationSmsSendService.class);
            sendIntent.setAction(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE);
            sendIntent.putExtras(intent);
            // Wear apparently passes all of its extras via the clip data. Must pass it along.
            sendIntent.setClipData(intent.getClipData());
            startService(sendIntent);
            setResult(RESULT_OK);
        } else {
            LogUtil.w(TAG, "Unrecognized intent action: " + action);
            setResult(RESULT_CANCELED);
        }
        // This activity should never stick around after processing the intent
        finish();
    }
}
