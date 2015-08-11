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

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;

import com.android.messaging.util.ConnectivityUtil;

import java.util.ArrayList;
import java.util.List;

public class ActionTestHelpers {
    private static final String TAG = "DataModelTestHelpers";

    static class StubLoader extends ContentObserver {
        ArrayList<Uri> mUriList = new ArrayList<Uri>();

        StubLoader() {
            super(null);
        }

        public void clear() {
            mUriList.clear();
        }

        @Override
        public void onChange(final boolean selfChange) {
            // Handle change.
            mUriList.add(null);
        }

        // Implement the onChange(boolean, Uri) method to take advantage of the new Uri argument.
        // Only supported on platform 16 and above...
        @Override
        public void onChange(final boolean selfChange, final Uri uri) {
            // Handle change.
            mUriList.add(uri);
        }
    }

    static class StubBackgroundWorker extends BackgroundWorker {
        public StubBackgroundWorker() {
            super();
            mActions = new ArrayList<Action>();
        }

        ArrayList<Action> mActions;
        public ArrayList<Action> getRequestsMade() {
            return mActions;
        }

        @Override
        public void queueBackgroundWork(final List<Action> actions) {
            mActions.addAll(actions);

            synchronized(this) {
                this.notifyAll();
            }
        }
    }

    static class ResultTracker {
        public Object executionResult;
        public Object completionResult;
    }

    static class StubChatActionMonitor extends ActionMonitor {
        static public class StateTransition {
            Action action;
            int from;
            int to;
            public StateTransition(final Action action, final int from, final int to) {
                this.action = action;
                this.from = from;
                this.to = to;
            }
        }

        private final ArrayList<StateTransition> mTransitions;
        public ArrayList<StateTransition> getTransitions() {
            return mTransitions;
        }

        protected StubChatActionMonitor(final int initialState, final String actionKey,
                final Object data) {
            super(initialState, actionKey, data);
            mTransitions =  new ArrayList<StateTransition>();
        }

        @Override
        protected void updateState(final Action action, final int expectedState,
                final int state) {
            mTransitions.add(new StateTransition(action, mState, state));
            super.updateState(action, expectedState, state);
        }

        public void setState(final int state) {
            mState = state;
        }

        public int getState() {
            return mState;
        }
    }

    public static class StubActionService extends ActionService {
        public static class StubActionServiceCallLog {
            public final Action action;
            public final Action request;
            public final Bundle response;
            public final Exception exception;
            public final Action update;

            public StubActionServiceCallLog(final Action action,
                    final Action request,
                    final Bundle response,
                    final Exception exception,
                    final Action update) {
                this.action = action;
                this.request = request;
                this.response = response;
                this.exception = exception;
                this.update = update;
            }
        }

        private final ArrayList<StubActionServiceCallLog> mServiceCalls =
                new ArrayList<StubActionServiceCallLog>();

        public ArrayList<StubActionServiceCallLog> getCalls() {
            return mServiceCalls;
        }

        @Override
        public void startAction(final Action action) {
            mServiceCalls.add(new StubActionServiceCallLog(action, null, null, null, null));
            synchronized(this) {
                this.notifyAll();
            }
        }

        @Override
        public void handleResponseFromBackgroundWorker(final Action request,
                final Bundle response) {
            mServiceCalls.add(new StubActionServiceCallLog(null, request, response, null, null));
            synchronized(this) {
                this.notifyAll();
            }
        }

        @Override
        protected void handleFailureFromBackgroundWorker(final Action request,
                final Exception exception) {
            mServiceCalls.add(new StubActionServiceCallLog(null, request, null, exception, null));
            synchronized(this) {
                this.notifyAll();
            }
        }
    }

    public static class StubConnectivityUtil extends ConnectivityUtil {
        public StubConnectivityUtil(final Context context) {
            super(context);
        }

        @Override
        public void registerForSignalStrength() {
        }

        @Override
        public void unregisterForSignalStrength() {
        }
    }
}
