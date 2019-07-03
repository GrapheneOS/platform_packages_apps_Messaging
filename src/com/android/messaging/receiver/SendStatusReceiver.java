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
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.telephony.SmsMessage;

import com.android.messaging.datamodel.action.ProcessDeliveryReportAction;
import com.android.messaging.datamodel.action.ProcessDownloadedMmsAction;
import com.android.messaging.datamodel.action.ProcessSentMessageAction;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.sms.SmsSender;
import com.android.messaging.util.LogUtil;

/**
 * The SMS sent and delivery intent receiver.
 *
 * This class just simply forwards the intents to proper recipients for actual handling.
 */
public class SendStatusReceiver extends BroadcastReceiver {
    public static final String MESSAGE_SENT_ACTION =
            "com.android.messaging.receiver.SendStatusReceiver.MESSAGE_SENT";
    public static final String MESSAGE_DELIVERED_ACTION =
            "com.android.messaging.receiver.SendStatusReceiver.MESSAGE_DELIVERED";
    public static final String MMS_SENT_ACTION =
            "com.android.messaging.receiver.SendStatusReceiver.MMS_SENT";
    public static final String MMS_DOWNLOADED_ACTION =
            "com.android.messaging.receiver.SendStatusReceiver.MMS_DOWNLOADED";

    // Defined by platform, but no constant provided. See docs for SmsManager.sendTextMessage.
    public static final String EXTRA_ERROR_CODE = "errorCode";

    public static final String EXTRA_PART_ID = "partId";
    public static final String EXTRA_SUB_ID = "subId";

    public static final int NO_ERROR_CODE = -1;
    public static final int NO_PART_ID = -1;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // This will be called on the main thread (so it should exit quickly)
        final String action = intent.getAction();
        final int resultCode = getResultCode();
        if (MESSAGE_SENT_ACTION.equals(action)) {
            final Uri requestId = intent.getData();
            SmsSender.setResult(
                    requestId,
                    resultCode,
                    intent.getIntExtra(EXTRA_ERROR_CODE, NO_ERROR_CODE),
                    intent.getIntExtra(EXTRA_PART_ID, NO_PART_ID),
                    intent.getIntExtra(EXTRA_SUB_ID, ParticipantData.DEFAULT_SELF_SUB_ID));
        } else if (MMS_SENT_ACTION.equals(action)) {
            final Uri messageUri = intent.getData();
            ProcessSentMessageAction.processMmsSent(resultCode, messageUri,
                    intent.getExtras());
        } else if (MMS_DOWNLOADED_ACTION.equals(action)) {
            ProcessDownloadedMmsAction.processMessageDownloaded(resultCode,
                    intent.getExtras());
        } else if (MESSAGE_DELIVERED_ACTION.equals(action)) {
            final SmsMessage smsMessage = MmsUtils.getSmsMessageFromDeliveryReport(intent);
            final Uri smsMessageUri = intent.getData();
            if (smsMessage == null) {
                LogUtil.e(LogUtil.BUGLE_TAG, "SendStatusReceiver: empty report message");
                return;
            }
            int status = Sms.STATUS_COMPLETE;
            try {
                final String format = intent.getStringExtra("format");
                status = smsMessage.getStatus();
                // Simple matching up CDMA status with GSM status.
                if (SmsMessage.FORMAT_3GPP2.equals(format)) {
                    final int errorClass = (status >> 24) & 0x03;
                    final int statusCode = (status >> 16) & 0x3f;
                    switch (errorClass) {
                        case 0: /*ERROR_NONE*/
                            if (statusCode == 0x02 /*STATUS_DELIVERED*/) {
                                status = Sms.STATUS_COMPLETE;
                            } else status = Sms.STATUS_PENDING;
                            break;
                        case 2: /*ERROR_TEMPORARY*/
                            // TODO: Need to check whether SC still trying to deliver the SMS to
                            // destination and will send the report again?
                            status = Sms.STATUS_PENDING;
                            break;
                        case 3: /*ERROR_PERMANENT*/
                            status = Sms.STATUS_FAILED;
                            break;
                        default:
                            status = Sms.STATUS_PENDING;
                    }
                }
            } catch (final NullPointerException e) {
                // Sometimes, SmsMessage.mWrappedSmsMessage is null causing NPE when we access
                // the methods on it although the SmsMessage itself is not null.
                LogUtil.e(LogUtil.BUGLE_TAG, "SendStatusReceiver: NPE inside SmsMessage");
                return;
            }
            ProcessDeliveryReportAction.deliveryReportReceived(smsMessageUri, status);
        }
    }
}
