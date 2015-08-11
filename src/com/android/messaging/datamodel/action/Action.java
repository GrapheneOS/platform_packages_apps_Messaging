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
import android.text.TextUtils;

import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DataModelException;
import com.android.messaging.datamodel.action.ActionMonitor.ActionCompletedListener;
import com.android.messaging.datamodel.action.ActionMonitor.ActionExecutedListener;
import com.android.messaging.util.LogUtil;

import java.util.LinkedList;
import java.util.List;

/**
 * Base class for operations that perform application business logic off the main UI thread while
 * holding a wake lock.
 * .
 * Note all derived classes need to provide real implementation of Parcelable (this is abstract)
 */
public abstract class Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    // Members holding the parameters common to all actions - no action state
    public final String actionKey;

    // If derived classes keep their data in actionParameters then parcelable is trivial
    protected Bundle actionParameters;

    // This does not get written to the parcel
    private final List<Action> mBackgroundActions = new LinkedList<Action>();

    /**
     * Process the action locally - runs on action service thread.
     * TODO: Currently, there is no way for this method to indicate failure
     * @return result to be passed in to {@link ActionExecutedListener#onActionExecuted}. It is
     *         also the result passed in to {@link ActionCompletedListener#onActionSucceeded} if
     *         there is no background work.
     */
    protected Object executeAction() {
        return null;
    }

    /**
     * Queues up background work ie. {@link #doBackgroundWork} will be called on the
     * background worker thread.
     */
    protected void requestBackgroundWork() {
        mBackgroundActions.add(this);
    }

    /**
     * Queues up background actions for background processing after the current action has
     * completed its processing ({@link #executeAction}, {@link processBackgroundCompletion}
     * or {@link #processBackgroundFailure}) on the Action thread.
     * @param backgroundAction
     */
    protected void requestBackgroundWork(final Action backgroundAction) {
        mBackgroundActions.add(backgroundAction);
    }

    /**
     * Return flag indicating if any actions have been queued
     */
    public boolean hasBackgroundActions() {
        return !mBackgroundActions.isEmpty();
    }

    /**
     * Send queued actions to the background worker provided
     */
    public void sendBackgroundActions(final BackgroundWorker worker) {
        worker.queueBackgroundWork(mBackgroundActions);
        mBackgroundActions.clear();
    }

    /**
     * Do work in a long running background worker thread.
     * {@link #requestBackgroundWork} needs to be called for this method to
     * be called. {@link #processBackgroundFailure} will be called on the Action service thread
     * if this method throws {@link DataModelException}.
     * @return response that is to be passed to {@link #processBackgroundResponse}
     */
    protected Bundle doBackgroundWork() throws DataModelException {
        return null;
    }

    /**
     * Process the success response from the background worker. Runs on action service thread.
     * @param response the response returned by {@link #doBackgroundWork}
     * @return result to be passed in to {@link ActionCompletedListener#onActionSucceeded}
     */
    protected Object processBackgroundResponse(final Bundle response) {
        return null;
    }

    /**
     * Called in case of failures when sending background actions. Runs on action service thread
     * @return result to be passed in to {@link ActionCompletedListener#onActionFailed}
     */
    protected Object processBackgroundFailure() {
        return null;
    }

    /**
     * Constructor
     */
    protected Action(final String key) {
        this.actionKey = key;
        this.actionParameters = new Bundle();
    }

    /**
     * Constructor
     */
    protected Action() {
        this.actionKey = generateUniqueActionKey(getClass().getSimpleName());
        this.actionParameters = new Bundle();
    }

    /**
     * Queue an action and monitor for processing by the ActionService via the factory helper
     */
    protected void start(final ActionMonitor monitor) {
        ActionMonitor.registerActionMonitor(this.actionKey, monitor);
        DataModel.startActionService(this);
    }

    /**
     * Queue an action for processing by the ActionService via the factory helper
     */
    public void start() {
        DataModel.startActionService(this);
    }

    /**
     * Queue an action for delayed processing by the ActionService via the factory helper
     */
    public void schedule(final int requestCode, final long delayMs) {
        DataModel.scheduleAction(this, requestCode, delayMs);
    }

    /**
     * Called when action queues ActionService intent
     */
    protected final void markStart() {
        ActionMonitor.setState(this, ActionMonitor.STATE_CREATED,
                ActionMonitor.STATE_QUEUED);
    }

    /**
     * Mark the beginning of local action execution
     */
    protected final void markBeginExecute() {
        ActionMonitor.setState(this, ActionMonitor.STATE_QUEUED,
                ActionMonitor.STATE_EXECUTING);
    }

    /**
     * Mark the end of local action execution - either completes the action or queues
     * background actions
     */
    protected final void markEndExecute(final Object result) {
        final boolean hasBackgroundActions = hasBackgroundActions();
        ActionMonitor.setExecutedState(this, ActionMonitor.STATE_EXECUTING,
                hasBackgroundActions, result);
        if (!hasBackgroundActions) {
            ActionMonitor.setCompleteState(this, ActionMonitor.STATE_EXECUTING,
                    result, true);
        }
    }

    /**
     * Update action state to indicate that the background worker is starting
     */
    protected final void markBackgroundWorkStarting() {
        ActionMonitor.setState(this,
                ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED,
                ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION);
    }

    /**
     * Update action state to indicate that the background worker has posted its response
     * (or failure) to the Action service
     */
    protected final void markBackgroundCompletionQueued() {
        ActionMonitor.setState(this,
                ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION,
                ActionMonitor.STATE_BACKGROUND_COMPLETION_QUEUED);
    }

    /**
     * Update action state to indicate the background action failed but is being re-queued for retry
     */
    protected final void markBackgroundWorkQueued() {
        ActionMonitor.setState(this,
                ActionMonitor.STATE_EXECUTING_BACKGROUND_ACTION,
                ActionMonitor.STATE_BACKGROUND_ACTIONS_QUEUED);
    }

    /**
     * Called by ActionService to process a response from the background worker
     * @param response the response returned by {@link #doBackgroundWork}
     */
    protected final void processBackgroundWorkResponse(final Bundle response) {
        ActionMonitor.setState(this,
                ActionMonitor.STATE_BACKGROUND_COMPLETION_QUEUED,
                ActionMonitor.STATE_PROCESSING_BACKGROUND_RESPONSE);
        final Object result = processBackgroundResponse(response);
        ActionMonitor.setCompleteState(this,
                ActionMonitor.STATE_PROCESSING_BACKGROUND_RESPONSE, result, true);
    }

    /**
     * Called by ActionService when a background action fails
     */
    protected final void processBackgroundWorkFailure() {
        final Object result = processBackgroundFailure();
        ActionMonitor.setCompleteState(this, ActionMonitor.STATE_UNDEFINED,
                result, false);
    }

    private static final Object sLock = new Object();
    private static long sActionIdx = System.currentTimeMillis() * 1000;

    /**
     * Helper method to generate a unique operation index
     */
    protected static long getActionIdx() {
        long idx = 0;
        synchronized (sLock) {
            idx = ++sActionIdx;
        }
        return idx;
    }

    /**
     * This helper can be used to generate a unique key used to identify an action.
     * @param baseKey - key generated to identify the action parameters
     * @return - composite key generated by appending unique index
     */
    protected static String generateUniqueActionKey(final String baseKey) {
        final StringBuilder key = new StringBuilder();
        if (!TextUtils.isEmpty(baseKey)) {
            key.append(baseKey);
        }
        key.append(":");
        key.append(getActionIdx());
        return key.toString();
    }

    /**
     * Most derived classes use this base implementation (unless they include files handles)
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Derived classes need to implement writeToParcel (but typically should call this method
     * to parcel Action member variables before they parcel their member variables).
     */
    public void writeActionToParcel(final Parcel parcel, final int flags) {
        parcel.writeString(this.actionKey);
        parcel.writeBundle(this.actionParameters);
    }

    /**
     * Helper for derived classes to implement parcelable
     */
    public Action(final Parcel in) {
        this.actionKey = in.readString();
        // Note: Need to set classloader to ensure we can un-parcel classes from this package
        this.actionParameters = in.readBundle(Action.class.getClassLoader());
    }
}
