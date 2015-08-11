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

package com.android.messaging.datamodel;

public class DataModelException extends Exception {
    private static final long serialVersionUID = 1L;

    private static final int FIRST = 100;

    // ERRORS GENERATED INTERNALLY BY DATA MODEL.

    // ERRORS RELATED WITH SMS.
    public static final int ERROR_SMS_TEMPORARY_FAILURE = 116;
    public static final int ERROR_SMS_PERMANENT_FAILURE = 117;
    public static final int ERROR_MMS_TEMPORARY_FAILURE = 118;
    public static final int ERROR_MMS_PERMANENT_UNKNOWN_FAILURE = 119;

    // Request expired.
    public static final int ERROR_EXPIRED = 120;
    // Request canceled by user.
    public static final int ERROR_CANCELED = 121;

    public static final int ERROR_MOBILE_DATA_DISABLED  = 123;
    public static final int ERROR_MMS_SERVICE_BLOCKED   = 124;
    public static final int ERROR_MMS_INVALID_ADDRESS   = 125;
    public static final int ERROR_MMS_NETWORK_PROBLEM   = 126;
    public static final int ERROR_MMS_MESSAGE_NOT_FOUND = 127;
    public static final int ERROR_MMS_MESSAGE_FORMAT_CORRUPT = 128;
    public static final int ERROR_MMS_CONTENT_NOT_ACCEPTED = 129;
    public static final int ERROR_MMS_MESSAGE_NOT_SUPPORTED = 130;
    public static final int ERROR_MMS_REPLY_CHARGING_ERROR = 131;
    public static final int ERROR_MMS_ADDRESS_HIDING_NOT_SUPPORTED = 132;
    public static final int ERROR_MMS_LACK_OF_PREPAID = 133;
    public static final int ERROR_MMS_CAN_NOT_PERSIST = 134;
    public static final int ERROR_MMS_NO_AVAILABLE_APN = 135;
    public static final int ERROR_MMS_INVALID_MESSAGE_TO_SEND = 136;
    public static final int ERROR_MMS_INVALID_MESSAGE_RECEIVED = 137;
    public static final int ERROR_MMS_NO_CONFIGURATION = 138;

    private static final int LAST = 138;

    private final boolean mIsInjection;
    private final int mErrorCode;
    private final String mMessage;
    private final long mBackoff;

    public DataModelException(final int errorCode, final Exception innerException,
            final long backoff, final boolean injection, final String message) {
        // Since some of the exceptions passed in may not be serializable, only record message
        // instead of setting inner exception for Exception class. Otherwise, we will get
        // serialization issues when we pass ServerRequestException as intent extra later.
        if (errorCode < FIRST || errorCode > LAST) {
            throw new IllegalArgumentException("error code out of range: " + errorCode);
        }
        mIsInjection = injection;
        mErrorCode = errorCode;
        if (innerException != null) {
            mMessage = innerException.getMessage() + " -- " +
                    (mIsInjection ? "[INJECTED] -- " : "") + message;
        } else {
            mMessage = (mIsInjection ? "[INJECTED] -- " : "") + message;
        }

        mBackoff = backoff;
    }

    public DataModelException(final int errorCode) {
        this(errorCode, null, 0, false, null);
    }

    public DataModelException(final int errorCode, final Exception innerException) {
        this(errorCode, innerException, 0, false, null);
    }

    public DataModelException(final int errorCode, final String message) {
        this(errorCode, null, 0, false, message);
    }

    @Override
    public String getMessage() {
        return mMessage;
    }

    public int getErrorCode() {
        return mErrorCode;
    }
}
