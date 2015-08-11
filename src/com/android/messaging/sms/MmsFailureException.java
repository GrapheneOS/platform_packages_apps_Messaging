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

import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.util.Assert;

/**
 * Exception for MMS failures
 */
public class MmsFailureException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Hint of how we should retry in case of failure. Take values defined in MmsUtils.
     */
    public final int retryHint;

    /**
     * If set, provides a more detailed reason for the failure.
     */
    public final int rawStatus;

    private void checkRetryHint() {
        Assert.isTrue(retryHint == MmsUtils.MMS_REQUEST_AUTO_RETRY
                || retryHint == MmsUtils.MMS_REQUEST_MANUAL_RETRY
                || retryHint == MmsUtils.MMS_REQUEST_NO_RETRY);
    }
    /**
     * Creates a new MmsFailureException.
     *
     * @param retryHint Hint for how to retry
     */
    public MmsFailureException(final int retryHint) {
        super();
        this.retryHint = retryHint;
        checkRetryHint();
        this.rawStatus = MessageData.RAW_TELEPHONY_STATUS_UNDEFINED;
    }

    public MmsFailureException(final int retryHint, final int rawStatus) {
        super();
        this.retryHint = retryHint;
        checkRetryHint();
        this.rawStatus = rawStatus;
    }

    /**
     * Creates a new MmsFailureException with the specified detail message.
     *
     * @param retryHint Hint for how to retry
     * @param message the detail message.
     */
    public MmsFailureException(final int retryHint, String message) {
        super(message);
        this.retryHint = retryHint;
        checkRetryHint();
        this.rawStatus = MessageData.RAW_TELEPHONY_STATUS_UNDEFINED;
    }

    /**
     * Creates a new MmsFailureException with the specified cause.
     *
     * @param retryHint Hint for how to retry
     * @param cause the cause.
     */
    public MmsFailureException(final int retryHint, Throwable cause) {
        super(cause);
        this.retryHint = retryHint;
        checkRetryHint();
        this.rawStatus = MessageData.RAW_TELEPHONY_STATUS_UNDEFINED;
    }

    /**
     * Creates a new MmsFailureException
     * with the specified detail message and cause.
     *
     * @param retryHint Hint for how to retry
     * @param message the detail message.
     * @param cause the cause.
     */
    public MmsFailureException(final int retryHint, String message, Throwable cause) {
        super(message, cause);
        this.retryHint = retryHint;
        checkRetryHint();
        this.rawStatus = MessageData.RAW_TELEPHONY_STATUS_UNDEFINED;
    }
}
