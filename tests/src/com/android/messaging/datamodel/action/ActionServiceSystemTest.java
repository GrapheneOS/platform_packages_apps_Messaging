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

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.messaging.BugleTestCase;
import com.android.messaging.Factory;
import com.android.messaging.FakeContext;
import com.android.messaging.FakeContext.FakeContextHost;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.BugleServiceTestCase;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.action.ActionMonitor.ActionCompletedListener;
import com.android.messaging.datamodel.action.ActionMonitor.ActionExecutedListener;
import com.android.messaging.datamodel.action.ActionTestHelpers.ResultTracker;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubBackgroundWorker;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubConnectivityUtil;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubLoader;

import java.util.ArrayList;

@MediumTest
public class ActionServiceSystemTest extends BugleServiceTestCase<ActionServiceImpl>
        implements ActionCompletedListener, ActionExecutedListener, FakeContextHost {
    private static final String TAG = "ActionServiceSystemTest";

    static {
        // Set flag during loading of test cases to prevent application initialization starting
        BugleTestCase.setTestsRunning();
    }

    @Override
    public void onActionSucceeded(final ActionMonitor monitor,
            final Action action, final Object data, final Object result) {
        final TestChatAction test = (TestChatAction) action;
        assertEquals("Expect correct action parameter", parameter, test.parameter);
        final ResultTracker tracker = (ResultTracker) data;
        tracker.completionResult = result;
        synchronized(tracker) {
            tracker.notifyAll();
        }
    }

    @Override
    public void onActionFailed(final ActionMonitor monitor, final Action action,
            final Object data, final Object result) {
        final TestChatAction test = (TestChatAction) action;
        assertEquals("Expect correct action parameter", parameter, test.parameter);
        final ResultTracker tracker = (ResultTracker) data;
        tracker.completionResult = result;
        synchronized(tracker) {
            tracker.notifyAll();
        }
    }

    @Override
    public void onActionExecuted(final ActionMonitor monitor, final Action action,
            final Object data, final Object result) {
        final TestChatAction test = (TestChatAction) action;
        assertEquals("Expect correct action parameter", parameter, test.parameter);
        final ResultTracker tracker = (ResultTracker) data;
        tracker.executionResult = result;
    }

    public ActionServiceSystemTest() {
        super(ActionServiceImpl.class);
    }

    public void testChatActionSucceeds() {
        final ResultTracker tracker = new ResultTracker();

        final ActionService service = DataModel.get().getActionService();
        final TestChatActionMonitor monitor = new TestChatActionMonitor(null, tracker, this, this);
        final TestChatAction initial = new TestChatAction(monitor.getActionKey(), parameter);

        assertNull("Expect completion result to start null", tracker.completionResult);
        assertNull("Expect execution result to start null", tracker.executionResult);

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(initial, 0);
        parcel.setDataPosition(0);
        final TestChatAction action = parcel.readParcelable(mContext.getClassLoader());

        synchronized(mWorker) {
            try {
                action.start(monitor);
                // Wait for callback across threads
                mWorker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for execution", false);
            }
        }

        assertEquals("Expect to see 1 server request queued", 1,
                mWorker.getRequestsMade().size());
        final Action request = mWorker.getRequestsMade().get(0);
        assertTrue("Expect Test type", request instanceof TestChatAction);

        final Bundle response = new Bundle();
        response.putString(TestChatAction.RESPONSE_TEST, processResponseResult);
        synchronized(tracker) {
            try {
                request.markBackgroundWorkStarting();
                request.markBackgroundWorkQueued();

                request.markBackgroundWorkStarting();
                request.markBackgroundCompletionQueued();
                service.handleResponseFromBackgroundWorker(request, response);
                // Wait for callback across threads
                tracker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for response processing", false);
            }
        }

        // TODO
        //assertEquals("Expect execution result set", executeActionResult, tracker.executionResult);
        assertEquals("Expect completion result set", processResponseResult,
                tracker.completionResult);
    }

    public void testChatActionFails() {
        final ResultTracker tracker = new ResultTracker();

        final ActionService service = DataModel.get().getActionService();
        final TestChatActionMonitor monitor = new TestChatActionMonitor(null, tracker, this, this);
        final TestChatAction action = new TestChatAction(monitor.getActionKey(), parameter);

        assertNull("Expect completion result to start null", tracker.completionResult);
        assertNull("Expect execution result to start null", tracker.executionResult);

        synchronized(mWorker) {
            try {
                action.start(monitor);
                // Wait for callback across threads
                mWorker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for requests", false);
            }
        }

        final ArrayList<Intent> intents = mContext.extractIntents();
        assertNotNull(intents);
        assertEquals("Expect to see one intent", intents.size(), 1);

        assertEquals("Expect to see 1 server request queued", 1,
                mWorker.getRequestsMade().size());
        final Action request = mWorker.getRequestsMade().get(0);
        assertTrue("Expect Test type", request instanceof TestChatAction);

        synchronized(tracker) {
            try {
                request.markBackgroundWorkStarting();
                request.markBackgroundWorkQueued();

                request.markBackgroundWorkStarting();
                request.markBackgroundCompletionQueued();
                service.handleFailureFromBackgroundWorker(request, new Exception("It went wrong"));
                // Wait for callback across threads
                tracker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for response processing", false);
            }
        }

        // TODO
        //assertEquals("Expect execution result set", executeActionResult, tracker.executionResult);
        assertEquals("Expect completion result set", processFailureResult,
                tracker.completionResult);
    }

    public void testChatActionNoMonitor() {
        final ActionService service = DataModel.get().getActionService();
        final TestChatAction action =
                new TestChatAction(Action.generateUniqueActionKey(null), parameter);

        synchronized(mWorker) {
            try {
                action.start();
                // Wait for callback across threads
                mWorker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for execution", false);
            }
        }

        assertEquals("Expect to see 1 server request queued", 1,
                mWorker.getRequestsMade().size());
        Action request = mWorker.getRequestsMade().get(0);
        assertTrue("Expect Test type", request instanceof TestChatAction);

        final Bundle response = new Bundle();
        response.putString(TestChatAction.RESPONSE_TEST, processResponseResult);
        synchronized(mWorker) {
            try {
                service.handleResponseFromBackgroundWorker(request, response);
                // Wait for callback across threads
                mWorker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for response processing", false);
            }
        }

        assertEquals("Expect to see second server request queued",
                2, mWorker.getRequestsMade().size());
        request = mWorker.getRequestsMade().get(1);
        assertTrue("Expect other type",
                request instanceof TestChatActionOther);
    }

    public void testChatActionUnregisterListener() {
        final ResultTracker tracker = new ResultTracker();

        final ActionService service = DataModel.get().getActionService();
        final TestChatActionMonitor monitor = new TestChatActionMonitor(null, tracker, this, this);
        final TestChatAction action = new TestChatAction(monitor.getActionKey(), parameter);

        assertNull("Expect completion result to start null", tracker.completionResult);
        assertNull("Expect execution result to start null", tracker.executionResult);

        synchronized(mWorker) {
            try {
                action.start(monitor);
                // Wait for callback across threads
                mWorker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for execution", false);
            }
        }

        assertEquals("Expect to see 1 server request queued", 1,
                mWorker.getRequestsMade().size());
        final Action request = mWorker.getRequestsMade().get(0);
        assertTrue("Expect Test type", request instanceof TestChatAction);

        monitor.unregister();

        final Bundle response = new Bundle();
        synchronized(mWorker) {
            try {
                request.markBackgroundWorkStarting();
                request.markBackgroundWorkQueued();

                request.markBackgroundWorkStarting();
                request.markBackgroundCompletionQueued();
                service.handleResponseFromBackgroundWorker(request, response);
                // Wait for callback across threads
                mWorker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for response processing", false);
            }
        }

        //assertEquals("Expect execution result set", executeActionResult, tracker.executionResult);
        assertEquals("Expect completion never called", null, tracker.completionResult);
    }

    StubBackgroundWorker mWorker;
    FakeContext mContext;
    StubLoader mLoader;

    private static final String parameter = "parameter";
    private static final Object executeActionResult = "executeActionResult";
    private static final String processResponseResult = "processResponseResult";
    private static final Object processFailureResult = "processFailureResult";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        Log.d(TAG, "ChatActionTest setUp");

        mContext = new FakeContext(getContext(), this);
        mWorker = new StubBackgroundWorker();
        FakeFactory.registerWithFakeContext(getContext(), mContext)
                .withDataModel(new FakeDataModel(mContext)
                .withBackgroundWorkerForActionService(mWorker)
                .withActionService(new ActionService())
                .withConnectivityUtil(new StubConnectivityUtil(mContext)));

        mLoader = new StubLoader();
        setContext(Factory.get().getApplicationContext());
    }

    @Override
    public String getServiceClassName() {
        return ActionServiceImpl.class.getName();
    }

    @Override
    public void startServiceForStub(final Intent intent) {
        this.startService(intent);
    }

    @Override
    public void onStartCommandForStub(final Intent intent, final int flags, final int startId) {
        this.getService().onStartCommand(intent, flags, startId);
    }

    public static class TestChatAction extends Action implements Parcelable {
        public static String RESPONSE_TEST = "response_test";
        public static String KEY_PARAMETER = "parameter";

        protected TestChatAction(final String key, final String parameter) {
            super(key);
            this.actionParameters.putString(KEY_PARAMETER, parameter);
            // Cache parameter as a member variable
            this.parameter = parameter;
        }

        // An example parameter
        public final String parameter;

        /**
         * Process the action locally - runs on datamodel service thread
         */
        @Override
        protected Object executeAction() {
            requestBackgroundWork();
            return executeActionResult;
        }

        /**
         * Process the response from the server - runs on datamodel service thread
         */
        @Override
        protected Object processBackgroundResponse(final Bundle response) {
            requestBackgroundWork(new TestChatActionOther(null, parameter));
            return response.get(RESPONSE_TEST);
        }

        /**
         * Called in case of failures when sending requests - runs on datamodel service thread
         */
        @Override
        protected Object processBackgroundFailure() {
            return processFailureResult;
        }

        private TestChatAction(final Parcel in) {
            super(in);
            // Cache parameter as a member variable
            parameter = actionParameters.getString(KEY_PARAMETER);
        }

        public static final Parcelable.Creator<TestChatAction> CREATOR
                = new Parcelable.Creator<TestChatAction>() {
            @Override
            public TestChatAction createFromParcel(final Parcel in) {
                return new TestChatAction(in);
            }

            @Override
            public TestChatAction[] newArray(final int size) {
                return new TestChatAction[size];
            }
        };

        @Override
        public void writeToParcel(final Parcel parcel, final int flags) {
            writeActionToParcel(parcel, flags);
        }
    }

    public static class TestChatActionOther extends Action implements Parcelable {
        protected TestChatActionOther(final String key, final String parameter) {
            super(generateUniqueActionKey(key));
            this.parameter = parameter;
        }

        public final String parameter;

        private TestChatActionOther(final Parcel in) {
            super(in);
            parameter = in.readString();
        }

        public static final Parcelable.Creator<TestChatActionOther> CREATOR
                = new Parcelable.Creator<TestChatActionOther>() {
            @Override
            public TestChatActionOther createFromParcel(final Parcel in) {
                return new TestChatActionOther(in);
            }

            @Override
            public TestChatActionOther[] newArray(final int size) {
                return new TestChatActionOther[size];
            }
        };

        @Override
        public void writeToParcel(final Parcel parcel, final int flags) {
            writeActionToParcel(parcel, flags);
            parcel.writeString(parameter);
        }
    }

    /**
     * An operation that notifies a listener upon completion
     */
    public static class TestChatActionMonitor extends ActionMonitor {
        /**
         * Create action state wrapping an BlockUserAction instance
         * @param account - account in which to block the user
         * @param baseKey - suggested action key from BlockUserAction
         * @param data - optional action specific data that is handed back to listener
         * @param listener - action completed listener
         */
        public TestChatActionMonitor(final String baseKey, final Object data,
                final ActionCompletedListener completed, final ActionExecutedListener executed) {
            super(STATE_CREATED, Action.generateUniqueActionKey(baseKey), data);
            setCompletedListener(completed);
            setExecutedListener(executed);
        }
    }
}
