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

package com.android.messaging.ui.appsettings;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.ui.UIIntents;

/**
 * ApnPreference implements a pref, typically used as a list item, that has a title/summary on
 * the left and a radio button on the right.
 *
 */
public class ApnPreference extends Preference implements
        CompoundButton.OnCheckedChangeListener, OnClickListener {
    static final String TAG = "ApnPreference";

    public ApnPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ApnPreference(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.apnPreferenceStyle);
    }

    public ApnPreference(Context context) {
        this(context, null);
    }

    private static String mSelectedKey = null;
    private static CompoundButton mCurrentChecked = null;
    private boolean mProtectFromCheckedChange = false;
    private boolean mSelectable = true;
    private int mSubId = ParticipantData.DEFAULT_SELF_SUB_ID;

    @Override
    public View getView(View convertView, ViewGroup parent) {
        View view = super.getView(convertView, parent);

        View widget = view.findViewById(R.id.apn_radiobutton);
        if ((widget != null) && widget instanceof RadioButton) {
            RadioButton rb = (RadioButton) widget;
            if (mSelectable) {
                rb.setOnCheckedChangeListener(this);

                boolean isChecked = getKey().equals(mSelectedKey);
                if (isChecked) {
                    mCurrentChecked = rb;
                    mSelectedKey = getKey();
                }

                mProtectFromCheckedChange = true;
                rb.setChecked(isChecked);
                mProtectFromCheckedChange = false;
            } else {
                rb.setVisibility(View.GONE);
            }
            setApnRadioButtonContentDescription(rb);
        }

        View textLayout = view.findViewById(R.id.text_layout);
        if ((textLayout != null) && textLayout instanceof RelativeLayout) {
            textLayout.setOnClickListener(this);
        }

        return view;
    }

    public void setApnRadioButtonContentDescription(final CompoundButton buttonView) {
        final View widget = (View) buttonView.getParent();
        final TextView tv = (TextView) widget.findViewById(R.id.title);
        final String apnTitle = tv.getText().toString();
        buttonView.setContentDescription(apnTitle);
    }

    public boolean isChecked() {
        return getKey().equals(mSelectedKey);
    }

    public void setChecked() {
        mSelectedKey = getKey();
    }

    public void setSubId(final int subId) {
        mSubId = subId;
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.i(TAG, "ID: " + getKey() + " :" + isChecked);
        if (mProtectFromCheckedChange) {
            return;
        }

        if (isChecked) {
            if (mCurrentChecked != null) {
                mCurrentChecked.setChecked(false);
            }
            mCurrentChecked = buttonView;
            mSelectedKey = getKey();
            callChangeListener(mSelectedKey);
        } else {
            mCurrentChecked = null;
            mSelectedKey = null;
        }
        setApnRadioButtonContentDescription(buttonView);
    }

    public void onClick(android.view.View v) {
        if ((v != null) && (R.id.text_layout == v.getId())) {
            Context context = getContext();
            if (context != null) {
                context.startActivity(
                        UIIntents.get().getApnEditorIntent(context, getKey(), mSubId));
            }
        }
    }

    public void setSelectable(boolean selectable) {
        mSelectable = selectable;
    }

    public boolean getSelectable() {
        return mSelectable;
    }
}
