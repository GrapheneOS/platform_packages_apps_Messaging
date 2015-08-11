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
package com.android.messaging.ui.conversationsettings;

import android.content.Context;
import android.database.Cursor;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.PeopleOptionsItemData;
import com.android.messaging.util.Assert;

/**
 * The view for a single entry in the options section of people & options activity.
 */
public class PeopleOptionsItemView extends LinearLayout {
    /**
     * Implemented by the host of this view that handles options click event.
     */
    public interface HostInterface {
        void onOptionsItemViewClicked(PeopleOptionsItemData item, boolean isChecked);
    }

    private TextView mTitle;
    private TextView mSubtitle;
    private SwitchCompat mSwitch;
    private final PeopleOptionsItemData mData;
    private HostInterface mHostInterface;

    public PeopleOptionsItemView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mData = DataModel.get().createPeopleOptionsItemData(context);
    }

    @Override
    protected void onFinishInflate () {
        mTitle = (TextView) findViewById(R.id.title);
        mSubtitle = (TextView) findViewById(R.id.subtitle);
        mSwitch = (SwitchCompat) findViewById(R.id.switch_button);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                mHostInterface.onOptionsItemViewClicked(mData, !mData.getChecked());
            }
        });
    }

    public void bind(final Cursor cursor, final int columnIndex, ParticipantData otherParticipant,
            final HostInterface hostInterface) {
        Assert.isTrue(columnIndex < PeopleOptionsItemData.SETTINGS_COUNT && columnIndex >= 0);
        mData.bind(cursor, otherParticipant, columnIndex);
        mHostInterface = hostInterface;

        mTitle.setText(mData.getTitle());
        final String subtitle = mData.getSubtitle();
        if (TextUtils.isEmpty(subtitle)) {
            mSubtitle.setVisibility(GONE);
        } else {
            mSubtitle.setVisibility(VISIBLE);
            mSubtitle.setText(subtitle);
        }

        if (mData.getCheckable()) {
            mSwitch.setVisibility(VISIBLE);
            mSwitch.setChecked(mData.getChecked());
        } else {
            mSwitch.setVisibility(GONE);
        }

        final boolean enabled = mData.getEnabled();
        if (enabled != isEnabled()) {
            mTitle.setEnabled(enabled);
            mSubtitle.setEnabled(enabled);
            mSwitch.setEnabled(enabled);
            setAlpha(enabled ? 1.0f : 0.5f);
            setEnabled(enabled);
        }
    }
}
