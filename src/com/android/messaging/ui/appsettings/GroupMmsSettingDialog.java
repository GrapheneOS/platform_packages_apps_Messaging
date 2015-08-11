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

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioButton;

import com.android.messaging.R;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BuglePrefs;

/**
 * Displays an on/off switch for group MMS setting for a given subscription.
 */
public class GroupMmsSettingDialog {
    private final Context mContext;
    private final int mSubId;
    private AlertDialog mDialog;

    /**
     * Shows a new group MMS setting dialog.
     */
    public static void showDialog(final Context context, final int subId) {
        new GroupMmsSettingDialog(context, subId).show();
    }

    private GroupMmsSettingDialog(final Context context, final int subId) {
        mContext = context;
        mSubId = subId;
    }

    private void show() {
        Assert.isNull(mDialog);
        mDialog = new AlertDialog.Builder(mContext)
                .setView(createView())
                .setTitle(R.string.group_mms_pref_title)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void changeGroupMmsSettings(final boolean enable) {
        Assert.notNull(mDialog);
        BuglePrefs.getSubscriptionPrefs(mSubId).putBoolean(
                mContext.getString(R.string.group_mms_pref_key), enable);
        mDialog.dismiss();
    }

    private View createView() {
        final LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View rootView = inflater.inflate(R.layout.group_mms_setting_dialog, null, false);
        final RadioButton disableButton = (RadioButton)
                rootView.findViewById(R.id.disable_group_mms_button);
        final RadioButton enableButton = (RadioButton)
                rootView.findViewById(R.id.enable_group_mms_button);
        disableButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                changeGroupMmsSettings(false);
            }
        });
        enableButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                changeGroupMmsSettings(true);
            }
        });
        final boolean mmsEnabled = BuglePrefs.getSubscriptionPrefs(mSubId).getBoolean(
                mContext.getString(R.string.group_mms_pref_key),
                mContext.getResources().getBoolean(R.bool.group_mms_pref_default));
        enableButton.setChecked(mmsEnabled);
        disableButton.setChecked(!mmsEnabled);
        return rootView;
    }
}
