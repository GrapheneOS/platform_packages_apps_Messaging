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

package com.android.messaging.datamodel.data;

import com.android.messaging.datamodel.action.ActionMonitor;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction.GetOrCreateConversationActionListener;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction.GetOrCreateConversationActionMonitor;
import com.android.messaging.datamodel.binding.BindableData;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.RunsOnMainThread;
import com.android.messaging.util.LogUtil;

public class LaunchConversationData extends BindableData implements
        GetOrCreateConversationActionListener {
    public interface LaunchConversationDataListener {
        void onGetOrCreateNewConversation(String conversationId);
        void onGetOrCreateNewConversationFailed();
    }

    private LaunchConversationDataListener mListener;
    private GetOrCreateConversationActionMonitor mMonitor;

    public LaunchConversationData(final LaunchConversationDataListener listener) {
        mListener = listener;
    }

    @Override
    protected void unregisterListeners() {
        mListener = null;
        if (mMonitor != null) {
            mMonitor.unregister();
        }
        mMonitor = null;
    }

    public void getOrCreateConversation(final BindingBase<LaunchConversationData> binding,
            final String[] recipients) {
        final String bindingId = binding.getBindingId();

        // Start a new conversation from the list of contacts.
        if (isBound(bindingId) && mMonitor == null) {
            mMonitor = GetOrCreateConversationAction.getOrCreateConversation(recipients,
                    bindingId, this);
        }
    }

    @Override
    @RunsOnMainThread
    public void onGetOrCreateConversationSucceeded(final ActionMonitor monitor,
            final Object data, final String conversationId) {
        Assert.isTrue(monitor == mMonitor);
        Assert.isTrue(conversationId != null);

        final String bindingId = (String) data;
        if (isBound(bindingId) && mListener != null) {
            mListener.onGetOrCreateNewConversation(conversationId);
        }

        mMonitor = null;
    }

    @Override
    @RunsOnMainThread
    public void onGetOrCreateConversationFailed(final ActionMonitor monitor,
            final Object data) {
        Assert.isTrue(monitor == mMonitor);
        final String bindingId = (String) data;
        if (isBound(bindingId) && mListener != null) {
            mListener.onGetOrCreateNewConversationFailed();
        }
        LogUtil.e(LogUtil.BUGLE_TAG, "onGetOrCreateConversationFailed");
        mMonitor = null;
    }
}
