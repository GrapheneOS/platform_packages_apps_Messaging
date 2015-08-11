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

import android.os.Handler;
import android.support.v4.util.SimpleArrayMap;
import android.text.TextUtils;

import com.android.messaging.util.Assert.RunsOnAnyThread;
import com.android.messaging.util.Assert.RunsOnMainThread;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.ThreadUtil;
import com.google.common.annotations.VisibleForTesting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Base class for action monitors
 * Actions come in various flavors but
 *  o) Fire and forget - no monitor
 *  o) Immediate local processing only - will trigger ActionCompletedListener when done
 *  o) Background worker processing only - will trigger ActionCompletedListener when done
 *  o) Immediate local processing followed by background work followed by more local processing
 *      - will trigger ActionExecutedListener once local processing complete and
 *        ActionCompletedListener when second set of local process (dealing with background
 *         worker response) is complete
 */
public class ActionMonitor {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    /**
     * Interface used to notify on completion of local execution for an action
     */
    public interface ActionExecutedListener {
        /**
         * @param result value returned by {@link Action#executeAction}
         */
        @RunsOnMainThread
        abstract void onActionExecuted(ActionMonitor monitor, final Action action,
                final Object data, final Object result);
    }

    /**
     * Interface used to notify action completion
     */
    public interface ActionCompletedListener {
        /**
         * @param result object returned from processing the action. This is the value returned by
         *               {@link Action#executeAction} if there is no background work, or
         *               else the value returned by
         *               {@link Action#processBackgroundResponse}
         */
        @RunsOnMainThread
        abstract void onActionSucceeded(ActionMonitor monitor,
                final Action action, final Object data, final Object result);
        /**
         * @param result value returned by {@link Action#processBackgroundFailure}
         */
        @RunsOnMainThread
        abstract void onActionFailed(ActionMonitor monitor, final Action action,
                final Object data, final Object result);
    }

    /**
     * Interface for being notified of action state changes - used for profiling, testing only
     */
    protected interface ActionStateChangedListener {
        /**
         * @param action the action that is changing state
         * @param state the new state of the action
         */
        @RunsOnAnyThread
        void onActionStateChanged(Action action, int state);
    }

    /**
     * Operations always start out as STATE_CREATED and finish as STATE_COMPLETE.
     * Some common state transition sequences in between include:
     * <ul>
     *   <li>Local data change only : STATE_QUEUED - STATE_EXECUTING
     *   <li>Background worker request only : STATE_BACKGROUND_ACTIONS_QUEUED
     *      - STATE_EXECUTING_BACKGROUND_ACTION
     *      - STATE_BACKGROUND_COMPLETION_QUEUED
     *      - STATE_PROCESSING_BACKGROUND_RESPONSE
     *   <li>Local plus background worker request : STATE_QUEUED - STATE_EXECUTING
     *      - STATE_BACKGROUND_ACTIONS_QUEUED
     *      - STATE_EXECUTING_BACKGROUND_ACTION
     *      - STATE_BACKGROUND_COMPLETION_QUEUED
     *      - STATE_PROCESSING_BACKGROUND_RESPONSE
     * </ul>
     */
    protected static final int STATE_UNDEFINED = 0;
    protected static final int STATE_CREATED = 1; // Just created
    protected static final int STATE_QUEUED = 2; // Action queued for processing
    protected static final int STATE_EXECUTING = 3; // Action processing on datamodel thread
    protected static final int STATE_BACKGROUND_ACTIONS_QUEUED = 4;
    protected static final int STATE_EXECUTING_BACKGROUND_ACTION = 5;
    // The background work has completed, either returning a success response or resulting in a
    // failure
    protected static final int STATE_BACKGROUND_COMPLETION_QUEUED = 6;
    protected static final int STATE_PROCESSING_BACKGROUND_RESPONSE = 7;
    protected static final int STATE_COMPLETE = 8; // Action complete

    /**
     * Lock used to protect access to state and listeners
     */
    private final Object mLock = new Object();

    /**
     * Current state of action
     */
    @VisibleForTesting
    protected int mState;

    /**
     * Listener which is notified on action completion
     */
    private ActionCompletedListener mCompletedListener;

    /**
     * Listener which is notified on action executed
     */
    private ActionExecutedListener mExecutedListener;

    /**
     * Listener which is notified of state changes
     */
    private ActionStateChangedListener mStateChangedListener;

    /**
     * Handler used to post results back to caller
     */
    private final Handler mHandler;

    /**
     * Data passed back to listeners (associated with the action when it is created)
     */
    private final Object mData;

    /**
     * The action key is used to determine equivalence of operations and their requests
     */
    private final String mActionKey;

    /**
     * Get action key identifying associated action
     */
    public String getActionKey() {
        return mActionKey;
    }

    /**
     * Unregister listeners so that they will not be called back - override this method if needed
     */
    public void unregister() {
        clearListeners();
    }

    /**
     * Unregister listeners so that they will not be called
     */
    protected final void clearListeners() {
        synchronized (mLock) {
            mCompletedListener = null;
            mExecutedListener = null;
        }
    }

    /**
     * Create a monitor associated with a particular action instance
     */
    protected ActionMonitor(final int initialState, final String actionKey,
            final Object data) {
        mHandler = ThreadUtil.getMainThreadHandler();
        mActionKey = actionKey;
        mState = initialState;
        mData = data;
    }

    /**
     * Return flag to indicate if action is complete
     */
    public boolean isComplete() {
        boolean complete = false;
        synchronized (mLock) {
            complete = (mState == STATE_COMPLETE);
        }
        return complete;
    }

    /**
     * Set listener that will be called with action completed result
     */
    protected final void setCompletedListener(final ActionCompletedListener listener) {
        synchronized (mLock) {
            mCompletedListener = listener;
        }
    }

    /**
     * Set listener that will be called with local execution result
     */
    protected final void setExecutedListener(final ActionExecutedListener listener) {
        synchronized (mLock) {
            mExecutedListener = listener;
        }
    }

    /**
     * Set listener that will be called with local execution result
     */
    protected final void setStateChangedListener(final ActionStateChangedListener listener) {
        synchronized (mLock) {
            mStateChangedListener = listener;
        }
    }

    /**
     * Perform a state update transition
     * @param action - action whose state is updating
     * @param expectedOldState - expected existing state of action (can be UNKNOWN)
     * @param newState - new state which will be set
     */
    @VisibleForTesting
    protected void updateState(final Action action, final int expectedOldState,
            final int newState) {
        ActionStateChangedListener listener = null;
        synchronized (mLock) {
            if (expectedOldState != STATE_UNDEFINED &&
                    mState != expectedOldState) {
                throw new IllegalStateException("On updateState to " + newState + " was " + mState
                        + " expecting " + expectedOldState);
            }
            if (newState != mState) {
                mState = newState;
                listener = mStateChangedListener;
            }
        }
        if (listener != null) {
            listener.onActionStateChanged(action, newState);
        }
    }

    /**
     * Perform a state update transition
     * @param action - action whose state is updating
     * @param expectedOldState - expected existing state of action (can be UNKNOWN)
     * @param newState - new state which will be set
     */
    static void setState(final Action action, final int expectedOldState,
            final int newState) {
        int oldMonitorState = expectedOldState;
        int newMonitorState = newState;
        final ActionMonitor monitor
                = ActionMonitor.lookupActionMonitor(action.actionKey);
        if (monitor != null) {
            oldMonitorState = monitor.mState;
            monitor.updateState(action, expectedOldState, newState);
            newMonitorState = monitor.mState;
        }
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            LogUtil.v(TAG, "Operation-" + action.actionKey + ": @" + df.format(new Date())
                    + "UTC State = " + oldMonitorState + " - " + newMonitorState);
        }
    }

    /**
     * Mark action complete
     * @param action - action whose state is updating
     * @param expectedOldState - expected existing state of action (can be UNKNOWN)
     * @param result - object returned from processing the action. This is the value returned by
     *                 {@link Action#executeAction} if there is no background work, or
     *                 else the value returned by {@link Action#processBackgroundResponse}
     *                 or {@link Action#processBackgroundFailure}
     */
    private final void complete(final Action action,
            final int expectedOldState, final Object result,
            final boolean succeeded) {
        ActionCompletedListener completedListener = null;
        synchronized (mLock) {
            setState(action, expectedOldState, STATE_COMPLETE);
            completedListener = mCompletedListener;
            mExecutedListener = null;
            mStateChangedListener = null;
        }
        if (completedListener != null) {
            // Marshal to UI thread
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ActionCompletedListener listener = null;
                    synchronized (mLock) {
                        if (mCompletedListener != null) {
                            listener = mCompletedListener;
                        }
                        mCompletedListener = null;
                    }
                    if (listener != null) {
                        if (succeeded) {
                            listener.onActionSucceeded(ActionMonitor.this,
                                    action, mData, result);
                        } else {
                            listener.onActionFailed(ActionMonitor.this,
                                    action, mData, result);
                        }
                    }
                }
            });
        }
    }

    /**
     * Mark action complete
     * @param action - action whose state is updating
     * @param expectedOldState - expected existing state of action (can be UNKNOWN)
     * @param result - object returned from processing the action. This is the value returned by
     *                 {@link Action#executeAction} if there is no background work, or
     *                 else the value returned by {@link Action#processBackgroundResponse}
     *                 or {@link Action#processBackgroundFailure}
     */
    static void setCompleteState(final Action action, final int expectedOldState,
            final Object result, final boolean succeeded) {
        int oldMonitorState = expectedOldState;
        final ActionMonitor monitor
                = ActionMonitor.lookupActionMonitor(action.actionKey);
        if (monitor != null) {
            oldMonitorState = monitor.mState;
            monitor.complete(action, expectedOldState, result, succeeded);
            unregisterActionMonitorIfComplete(action.actionKey, monitor);
        }
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            LogUtil.v(TAG, "Operation-" + action.actionKey + ": @" + df.format(new Date())
                    + "UTC State = " + oldMonitorState + " - " + STATE_COMPLETE);
        }
    }

    /**
     * Mark action complete
     * @param action - action whose state is updating
     * @param expectedOldState - expected existing state of action (can be UNKNOWN)
     * @param hasBackgroundActions - has the completing action requested background work
     * @param result - the return value of {@link Action#executeAction}
     */
    final void executed(final Action action,
            final int expectedOldState, final boolean hasBackgroundActions, final Object result) {
        ActionExecutedListener executedListener = null;
        synchronized (mLock) {
            if (hasBackgroundActions) {
                setState(action, expectedOldState, STATE_BACKGROUND_ACTIONS_QUEUED);
            }
            executedListener = mExecutedListener;
        }
        if (executedListener != null) {
            // Marshal to UI thread
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ActionExecutedListener listener = null;
                    synchronized (mLock) {
                        if (mExecutedListener != null) {
                            listener = mExecutedListener;
                            mExecutedListener = null;
                        }
                    }
                    if (listener != null) {
                        listener.onActionExecuted(ActionMonitor.this,
                                action, mData, result);
                    }
                }
            });
        }
    }

    /**
     * Mark action complete
     * @param action - action whose state is updating
     * @param expectedOldState - expected existing state of action (can be UNKNOWN)
     * @param hasBackgroundActions - has the completing action requested background work
     * @param result - the return value of {@link Action#executeAction}
     */
    static void setExecutedState(final Action action,
            final int expectedOldState, final boolean hasBackgroundActions, final Object result) {
        int oldMonitorState = expectedOldState;
        final ActionMonitor monitor
                = ActionMonitor.lookupActionMonitor(action.actionKey);
        if (monitor != null) {
            oldMonitorState = monitor.mState;
            monitor.executed(action, expectedOldState, hasBackgroundActions, result);
        }
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            LogUtil.v(TAG, "Operation-" + action.actionKey + ": @" + df.format(new Date())
                    + "UTC State = " + oldMonitorState + " - EXECUTED");
        }
    }

    /**
     * Map of action monitors indexed by actionKey
     */
    @VisibleForTesting
    static SimpleArrayMap<String, ActionMonitor> sActionMonitors =
            new SimpleArrayMap<String, ActionMonitor>();

    /**
     * Insert new monitor into map
     */
    static void registerActionMonitor(final String actionKey,
            final ActionMonitor monitor) {
        if (monitor != null
                && (TextUtils.isEmpty(monitor.getActionKey())
                        || TextUtils.isEmpty(actionKey)
                        || !actionKey.equals(monitor.getActionKey()))) {
            throw new IllegalArgumentException("Monitor key " + monitor.getActionKey()
                    + " not compatible with action key " + actionKey);
        }
        synchronized (sActionMonitors) {
            sActionMonitors.put(actionKey, monitor);
        }
    }

    /**
     * Find monitor associated with particular action
     */
    private static ActionMonitor lookupActionMonitor(final String actionKey) {
        ActionMonitor monitor = null;
        synchronized (sActionMonitors) {
            monitor = sActionMonitors.get(actionKey);
        }
        return monitor;
    }

    /**
     * Remove monitor from map
     */
    @VisibleForTesting
    static void unregisterActionMonitor(final String actionKey,
            final ActionMonitor monitor) {
        if (monitor != null) {
            synchronized (sActionMonitors) {
                sActionMonitors.remove(actionKey);
            }
        }
    }

    /**
     * Remove monitor from map if the action is complete
     */
    static void unregisterActionMonitorIfComplete(final String actionKey,
            final ActionMonitor monitor) {
        if (monitor != null && monitor.isComplete()) {
            synchronized (sActionMonitors) {
                sActionMonitors.remove(actionKey);
            }
        }
    }
}
