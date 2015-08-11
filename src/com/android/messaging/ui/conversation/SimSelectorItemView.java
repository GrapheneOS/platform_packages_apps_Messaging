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
package com.android.messaging.ui.conversation;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.SubscriptionListData.SubscriptionListEntry;
import com.android.messaging.util.Assert;

/**
 * Shows a view for a SIM in the SIM selector.
 */
public class SimSelectorItemView extends LinearLayout {
    public interface HostInterface {
        void onSimItemClicked(SubscriptionListEntry item);
    }

    private SubscriptionListEntry mData;
    private TextView mNameTextView;
    private TextView mDetailsTextView;
    private SimIconView mSimIconView;
    private HostInterface mHost;

    public SimSelectorItemView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mNameTextView = (TextView) findViewById(R.id.name);
        mDetailsTextView = (TextView) findViewById(R.id.details);
        mSimIconView = (SimIconView) findViewById(R.id.sim_icon);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mHost.onSimItemClicked(mData);
            }
        });
    }

    public void bind(final SubscriptionListEntry simEntry) {
        Assert.notNull(simEntry);
        mData = simEntry;
        updateViewAppearance();
    }

    public void setHostInterface(final HostInterface host) {
        mHost = host;
    }

    private void updateViewAppearance() {
        Assert.notNull(mData);
        final String displayName = mData.displayName;
        if (TextUtils.isEmpty(displayName)) {
            mNameTextView.setVisibility(GONE);
        } else {
            mNameTextView.setVisibility(VISIBLE);
            mNameTextView.setText(displayName);
        }

        final String details = mData.displayDestination;
        if (TextUtils.isEmpty(details)) {
            mDetailsTextView.setVisibility(GONE);
        } else {
            mDetailsTextView.setVisibility(VISIBLE);
            mDetailsTextView.setText(details);
        }

        mSimIconView.setImageResourceUri(mData.iconUri);
    }
}
