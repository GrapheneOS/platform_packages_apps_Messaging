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
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.messaging.Factory;
import com.android.messaging.FakeContext;
import com.android.messaging.FakeContext.FakeContextHost;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.BugleServiceTestCase;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.action.ActionMonitor.ActionCompletedListener;
import com.android.messaging.datamodel.action.ActionMonitor.ActionStateChangedListener;
import com.android.messaging.datamodel.action.ActionTestHelpers.ResultTracker;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubBackgroundWorker;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubConnectivityUtil;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubLoader;
import com.android.messaging.util.WakeLockHelper;

import java.util.ArrayList;

@MediumTest
public class ActionServiceTest extends BugleServiceTestCase<ActionServiceImpl>
        implements FakeContextHost, ActionStateChangedListener, ActionCompletedListener {
    private static final String TAG = "ActionServiceTest";

    @Override
    public void onActionStateChanged(final Action action, final int state) {
        mStates.add(state);
    }

    @Override
    public void onActionSucceeded(final ActionMonitor monitor,
            final Action action, final Object data, final Object result) {
        final TestChatAction test = (TestChatAction) action;
        assertNotSame(test.dontRelyOnMe, dontRelyOnMe);
        // This will be true - but only briefly
        assertEquals(test.dontRelyOnMe, becauseIChange);

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
        assertNotSame(test.dontRelyOnMe, dontRelyOnMe);
        // This will be true - but only briefly
        assertEquals(test.dontRelyOnMe, becauseIChange);

        final ResultTracker tracker = (ResultTracker) data;
        tracker.completionResult = result;
        synchronized(tracker) {
            tracker.notifyAll();
        }
    }

    /**
     * For a dummy action verify that the service intent is constructed and queued correctly and
     * that when that intent is processed it actually executes the action.
     */
    public void testChatServiceCreatesIntentAndExecutesAction() {
        final ResultTracker tracker = new ResultTracker();

        final TestChatActionMonitor monitor = new TestChatActionMonitor(null, tracker, this, this);
        final TestChatAction action = new TestChatAction(monitor.getActionKey(), parameter);

        action.dontRelyOnMe = dontRelyOnMe;
        assertFalse("Expect service initially stopped", mServiceStarted);

        action.start(monitor);

        assertTrue("Expect service started", mServiceStarted);

        final ArrayList<Intent> intents = mContext.extractIntents();
        assertNotNull(intents);
        assertEquals("Expect to see 1 server request queued", 1, intents.size());
        final Intent intent = intents.get(0);
        assertEquals("Check pid", intent.getIntExtra(WakeLockHelper.EXTRA_CALLING_PID, 0),
                Process.myPid());
        assertEquals("Check opcode", intent.getIntExtra(ActionServiceImpl.EXTRA_OP_CODE, 0),
                ActionServiceImpl.OP_START_ACTION);
        assertTrue("Check wakelock held", ActionServiceImpl.sWakeLock.isHeld(intent));

        synchronized(tracker) {
            try {
                this.startService(intent);
                // Wait for callback across threads
                tracker.wait(2000);
            } catch (final InterruptedException e) {
                assertTrue("Interrupted waiting for response processing", false);
            }
        }

        assertEquals("Expect three states ", mStates.size(), 3);
        assertEquals("State-0 should be STATE_QUEUED", (int)mStates.get(0),
                ActionMonitor.STATE_QUEUED);
        assertEquals("State-1 should be STATE_EXECUTING", (int)mStates.get(1),
                ActionMonitor.STATE_EXECUTING);
        assertEquals("State-2 should be STATE_COMPLETE", (int)mStates.get(2),
                ActionMonitor.STATE_COMPLETE);
        // TODO: Should find a way to reliably wait, this is a bit of a hack
        if (ActionServiceImpl.sWakeLock.isHeld(intent)) {
            Log.d(TAG, "ActionServiceTest: waiting for wakelock release");
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
            }
        }
        assertFalse("Check wakelock released", ActionServiceImpl.sWakeLock.isHeld(intent));
    }

    StubBackgroundWorker mWorker;
    FakeContext mContext;
    StubLoader mLoader;
    ActionService mService;

    ArrayList<Integer> mStates;

    private static final String parameter = "parameter";
    private static final Object dontRelyOnMe = "dontRelyOnMe";
    private static final Object becauseIChange = "becauseIChange";
    private static final Object executeActionResult = "executeActionResult";
    private static final Object processResponseResult = "processResponseResult";
    private static final Object processFailureResult = "processFailureResult";

    public ActionServiceTest() {
        super(ActionServiceImpl.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        Log.d(TAG, "ChatActionTest setUp");

        sLooper = Looper.myLooper();

        mWorker = new StubBackgroundWorker();
        mContext = new FakeContext(getContext(), this);
        FakeFactory.registerWithFakeContext(getContext(),mContext)
                .withDataModel(new FakeDataModel(mContext)
                .withBackgroundWorkerForActionService(mWorker)
                .withActionService(new ActionService())
                .withConnectivityUtil(new StubConnectivityUtil(mContext)));

        mStates = new ArrayList<Integer>();
        setContext(Factory.get().getApplicationContext());
    }

    @Override
    public String getServiceClassName() {
        return ActionServiceImpl.class.getName();
    }

    boolean mServiceStarted = false;

    @Override
    public void startServiceForStub(final Intent intent) {
        // Do nothing until later
        assertFalse(mServiceStarted);
        mServiceStarted = true;
    }

    @Override
    public void onStartCommandForStub(final Intent intent, final int flags, final int startId) {
        assertTrue(mServiceStarted);
    }

    private static Looper sLooper;
    public static void assertRunsOnOtherThread() {
        assertTrue (Looper.myLooper() != Looper.getMainLooper());
        assertTrue (Looper.myLooper() != sLooper);
    }

    public static class TestChatAction extends Action implements Parcelable {
        public static String RESPONSE_TEST = "response_test";
        public static String KEY_PARAMETER = "parameter";

        protected TestChatAction(final String key, final String parameter) {
            super(key);
            this.actionParameters.putString(KEY_PARAMETER, parameter);
        }

        transient Object dontRelyOnMe;

        /**
         * Process the action locally - runs on service thread
         */
        @Override
        protected Object executeAction() {
            this.dontRelyOnMe = becauseIChange;
            assertRunsOnOtherThread();
            return executeActionResult;
        }

        /**
         * Process the response from the server - runs on service thread
         */
        @Override
        protected Object processBackgroundResponse(final Bundle response) {
            assertRunsOnOtherThread();
            return processResponseResult;
        }

        /**
         * Called in case of failures when sending requests - runs on service thread
         */
        @Override
        protected Object processBackgroundFailure() {
            assertRunsOnOtherThread();
            return processFailureResult;
        }

        private TestChatAction(final Parcel in) {
            super(in);
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

    /**
     * An operation that notifies a listener upon state changes, execution and completion
     */
    public static class TestChatActionMonitor extends ActionMonitor {
        public TestChatActionMonitor(final String baseKey, final Object data,
                final ActionStateChangedListener listener, final ActionCompletedListener executed) {
            super(STATE_CREATED, Action.generateUniqueActionKey(baseKey), data);
            setStateChangedListener(listener);
            setCompletedListener(executed);
            assertEquals("Initial state should be STATE_CREATED", mState, STATE_CREATED);
        }
    }
}
