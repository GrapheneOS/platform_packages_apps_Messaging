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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.DataModelImpl;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubChatActionMonitor;

import java.util.ArrayList;

@MediumTest
public class ActionTest extends BugleTestCase {
    private static final String parameter = "parameter";
    private static final Object executeActionResult = "executeActionResult";
    private static final Object processResponseResult = "processResponseResult";
    private static final Object processFailureResult = "processFailureResult";

    private static final String mActionKey = "TheActionKey";
    private static final Object mData = "PrivateData";
    private StubChatActionMonitor mMonitor;
    private TestChatAction mAction;

    private ArrayList<StubChatActionMonitor.StateTransition> mTransitions;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        FakeFactory.register(getTestContext())
                .withDataModel(new DataModelImpl(getContext()));

        mMonitor = new StubChatActionMonitor(ActionMonitor.STATE_CREATED, mActionKey,
                mData);
        mAction = new TestChatAction(mActionKey, parameter);
        mTransitions = mMonitor.getTransitions();
    }

    private void verifyState(final int count, final int from, final int to) {
        assertEquals(to, mMonitor.getState());
        assertEquals(mTransitions.size(), count);
        verifyTransition(count-1, from , to);
    }

    private void verifyTransition(final int index, final int from, final int to) {
        assertTrue(mTransitions.size() > index);
        assertEquals(mAction, mTransitions.get(index).action);
        assertEquals(from, mTransitions.get(index).from);
        assertEquals(to, mTransitions.get(index).to);
    }

    @SmallTest
    public void testActionStartTransitionsCorrectly() {
        mMonitor.setState(ActionMonitor.STATE_CREATED);

        ActionMonitor.registerActionMonitor(mAction.actionKey, mMonitor);
        assertTrue(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertTrue(ActionMonitor.sActionMonitors.containsValue(mMonitor));
        assertEquals(ActionMonitor.sActionMonitors.get(mAction.actionKey), mMonitor);

        mAction.markStart();
        assertEquals("After start state: STATE_QUEUED", ActionMonitor.STATE_QUEUED,
                mMonitor.getState());
        verifyState(1, ActionMonitor.STATE_CREATED, ActionMonitor.STATE_QUEUED);

        ActionMonitor.unregisterActionMonitor(mAction.actionKey, mMonitor);

        assertFalse(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertFalse(ActionMonitor.sActionMonitors.containsValue(mMonitor));
    }

    @SmallTest
    public void testActionStartAssertsFromIncorrectState() {
        mMonitor.setState(ActionMonitor.STATE_UNDEFINED);

        ActionMonitor.registerActionMonitor(mAction.actionKey, mMonitor);
        assertTrue(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertTrue(ActionMonitor.sActionMonitors.containsValue(mMonitor));
        assertEquals(ActionMonitor.sActionMonitors.get(mAction.actionKey), mMonitor);

        try {
            mAction.markStart();
            fail("Expect assertion when starting from STATE_UNDEFINED");
        } catch (final IllegalStateException ex){
        }
        ActionMonitor.unregisterActionMonitor(mAction.actionKey, mMonitor);

        assertFalse(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertFalse(ActionMonitor.sActionMonitors.containsValue(mMonitor));
    }

    public void testActionTransitionsEndToEndWithRequests() {
        assertEquals("Start state: STATE_CREATED", ActionMonitor.STATE_CREATED,
                mMonitor.getState());

        ActionMonitor.registerActionMonitor(mAction.actionKey, mMonitor);
        assertTrue(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertTrue(ActionMonitor.sActionMonitors.containsValue(mMonitor));
        assertEquals(ActionMonitor.sActionMonitors.get(mAction.actionKey), mMonitor);

        mAction.markStart();

        verifyState(1, ActionMonitor.STATE_CREATED, ActionMonitor.STATE_QUEUED);

        mAction.markBeginExecute();

        verifyState(2, ActionMonitor.STATE_QUEUED, ActionMonitor.STATE_EXECUTING);

        final Object result = mAction.executeAction();
        mAction.requestBackgroundWork();

        assertEquals("Check executeAction result", result, executeActionResult);

        mAction.markEndExecute(result);

        verifyState(3, ActionMonitor.STATE_EXECUTING,
                ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED);

        mAction.markBackgroundWorkStarting();

        verifyState(4, ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED,
                ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION);

        mAction.markBackgroundWorkQueued();

        verifyState(5, ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION,
                ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED);

        mAction.markBackgroundWorkStarting();

        verifyState(6, ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED,
                ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION);

        final Bundle response = null;

        mAction.markBackgroundCompletionQueued();

        verifyState(7, ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION,
                ActionMonitor.STATE_BACKGROUND_COMPLETION_QUEUED);

        assertTrue(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertTrue(ActionMonitor.sActionMonitors.containsValue(mMonitor));
        assertEquals(ActionMonitor.sActionMonitors.get(mAction.actionKey), mMonitor);

        mAction.processBackgroundWorkResponse(response);

        verifyTransition(7, ActionMonitor.STATE_BACKGROUND_COMPLETION_QUEUED,
                ActionMonitor.STATE_PROCESSING_BACKGROUND_RESPONSE);

        verifyState(9, ActionMonitor.STATE_PROCESSING_BACKGROUND_RESPONSE,
                ActionMonitor.STATE_COMPLETE);

        assertFalse(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertFalse(ActionMonitor.sActionMonitors.containsValue(mMonitor));
    }

    public void testActionTransitionsEndToEndFailsRequests() {
        assertEquals("Start state: STATE_CREATED", ActionMonitor.STATE_CREATED,
                mMonitor.getState());

        ActionMonitor.registerActionMonitor(mAction.actionKey, mMonitor);
        assertTrue(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertTrue(ActionMonitor.sActionMonitors.containsValue(mMonitor));
        assertEquals(ActionMonitor.sActionMonitors.get(mAction.actionKey), mMonitor);

        mAction.markStart();

        verifyState(1, ActionMonitor.STATE_CREATED, ActionMonitor.STATE_QUEUED);

        mAction.markBeginExecute();

        verifyState(2, ActionMonitor.STATE_QUEUED, ActionMonitor.STATE_EXECUTING);

        final Object result = mAction.executeAction();
        mAction.requestBackgroundWork();

        assertEquals("Check executeAction result", result, executeActionResult);

        mAction.markEndExecute(result);

        verifyState(3, ActionMonitor.STATE_EXECUTING,
                ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED);

        mAction.markBackgroundWorkStarting();

        verifyState(4, ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED,
                ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION);

        mAction.markBackgroundWorkQueued();

        verifyState(5, ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION,
                ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED);

        mAction.markBackgroundWorkStarting();

        verifyState(6, ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED,
                ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION);

        assertTrue(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertTrue(ActionMonitor.sActionMonitors.containsValue(mMonitor));
        assertEquals(ActionMonitor.sActionMonitors.get(mAction.actionKey), mMonitor);

        mAction.processBackgroundWorkFailure();

        verifyState(7, ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION,
                ActionMonitor.STATE_COMPLETE);

        assertFalse(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertFalse(ActionMonitor.sActionMonitors.containsValue(mMonitor));
    }

    public void testActionTransitionsEndToEndNoRequests() {
        assertEquals("Start state: STATE_CREATED", ActionMonitor.STATE_CREATED,
                mMonitor.getState());

        ActionMonitor.registerActionMonitor(mAction.actionKey, mMonitor);
        assertTrue(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertTrue(ActionMonitor.sActionMonitors.containsValue(mMonitor));
        assertEquals(ActionMonitor.sActionMonitors.get(mAction.actionKey), mMonitor);

        mAction.markStart();

        verifyState(1, ActionMonitor.STATE_CREATED, ActionMonitor.STATE_QUEUED);

        mAction.markBeginExecute();

        verifyState(2, ActionMonitor.STATE_QUEUED, ActionMonitor.STATE_EXECUTING);

        final Object result = mAction.executeAction();

        assertEquals("Check executeAction result", result, executeActionResult);

        assertTrue(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertTrue(ActionMonitor.sActionMonitors.containsValue(mMonitor));
        assertEquals(ActionMonitor.sActionMonitors.get(mAction.actionKey), mMonitor);

        mAction.markEndExecute(result);

        verifyState(3, ActionMonitor.STATE_EXECUTING,
                ActionMonitor.STATE_COMPLETE);

        assertFalse(ActionMonitor.sActionMonitors.containsKey(mAction.actionKey));
        assertFalse(ActionMonitor.sActionMonitors.containsValue(mMonitor));
    }

    public static class TestChatAction extends Action implements Parcelable {
        protected TestChatAction(final String key, final String parameter) {
            super(key);
            this.parameter = parameter;
        }

        public final String parameter;

        /**
         * Process the action locally - runs on service thread
         */
        @Override
        protected Object executeAction() {
            assertEquals("Check parameter", parameter, ActionTest.parameter);
            return executeActionResult;
        }

        /**
         * Process the response from the server - runs on service thread
         */
        @Override
        protected Object processBackgroundResponse(final Bundle response) {
            assertEquals("Check parameter", parameter, ActionTest.parameter);
            return processResponseResult;
        }

        /**
         * Called in case of failures when sending requests - runs on service thread
         */
        @Override
        protected Object processBackgroundFailure() {
            assertEquals("Check parameter", parameter, ActionTest.parameter);
            return processFailureResult;
        }

        private TestChatAction(final Parcel in) {
            super(in);
            parameter = in.readString();
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
            parcel.writeString(parameter);
        }
    }
}
