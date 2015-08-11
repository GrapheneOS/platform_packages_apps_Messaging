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

import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;

/**
 * Class providing interface for the ActionService - can be stubbed for testing
 */
public class ActionService {
    protected static PendingIntent makeStartActionPendingIntent(final Context context,
            final Action action, final int requestCode, final boolean launchesAnActivity) {
        return ActionServiceImpl.makeStartActionPendingIntent(context, action, requestCode,
                launchesAnActivity);
    }

    /**
     * Start an action by posting it over the the ActionService
     */
    public void startAction(final Action action) {
        ActionServiceImpl.startAction(action);
    }

    /**
     * Schedule a delayed action by posting it over the the ActionService
     */
    public void scheduleAction(final Action action, final int code,
            final long delayMs) {
        ActionServiceImpl.scheduleAction(action, code, delayMs);
    }

    /**
     * Process a response from the BackgroundWorker in the ActionService
     */
    protected void handleResponseFromBackgroundWorker(
            final Action action, final Bundle response) {
        ActionServiceImpl.handleResponseFromBackgroundWorker(action, response);
    }

    /**
     * Process a failure from the BackgroundWorker in the ActionService
     */
    protected void handleFailureFromBackgroundWorker(final Action action,
            final Exception exception) {
        ActionServiceImpl.handleFailureFromBackgroundWorker(action, exception);
    }
}
