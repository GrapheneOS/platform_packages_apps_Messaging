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

package com.android.messaging.datamodel.action;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.messaging.sms.SmsReleaseStorage;
import com.android.messaging.util.Assert;

/**
 * Action used to handle low storage related issues on the device.
 */
public class HandleLowStorageAction extends Action implements Parcelable {
    private static final int SUB_OP_CODE_CLEAR_MEDIA_MESSAGES = 100;
    private static final int SUB_OP_CODE_CLEAR_OLD_MESSAGES = 101;

    public static void handleDeleteMediaMessages(final long durationInMillis) {
        final HandleLowStorageAction action = new HandleLowStorageAction(
                SUB_OP_CODE_CLEAR_MEDIA_MESSAGES, durationInMillis);
        action.start();
    }

    public static void handleDeleteOldMessages(final long durationInMillis) {
        final HandleLowStorageAction action = new HandleLowStorageAction(
                SUB_OP_CODE_CLEAR_OLD_MESSAGES, durationInMillis);
        action.start();
    }

    private static final String KEY_SUB_OP_CODE = "sub_op_code";
    private static final String KEY_CUTOFF_DURATION_MILLIS = "cutoff_duration_millis";

    private HandleLowStorageAction(final int subOpcode, final long durationInMillis) {
        super();
        actionParameters.putInt(KEY_SUB_OP_CODE, subOpcode);
        actionParameters.putLong(KEY_CUTOFF_DURATION_MILLIS, durationInMillis);
    }

    @Override
    protected Object executeAction() {
        final int subOpCode = actionParameters.getInt(KEY_SUB_OP_CODE);
        final long durationInMillis = actionParameters.getLong(KEY_CUTOFF_DURATION_MILLIS);
        switch (subOpCode) {
            case SUB_OP_CODE_CLEAR_MEDIA_MESSAGES:
                SmsReleaseStorage.deleteMessages(0, durationInMillis);
                break;

            case SUB_OP_CODE_CLEAR_OLD_MESSAGES:
                SmsReleaseStorage.deleteMessages(1, durationInMillis);
                break;

            default:
                Assert.fail("Unsupported action type!");
                break;
        }
        return true;
    }

    private HandleLowStorageAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<HandleLowStorageAction> CREATOR
            = new Parcelable.Creator<HandleLowStorageAction>() {
        @Override
        public HandleLowStorageAction createFromParcel(final Parcel in) {
            return new HandleLowStorageAction(in);
        }

        @Override
        public HandleLowStorageAction[] newArray(final int size) {
            return new HandleLowStorageAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
