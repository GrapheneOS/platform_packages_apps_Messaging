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
package com.android.messaging.ui.debug;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.util.LogUtil;

public class DebugMmsConfigItemView extends LinearLayout implements OnClickListener,
        OnCheckedChangeListener, DialogInterface.OnClickListener {

    public interface MmsConfigItemListener {
        void onValueChanged(String key, String keyType, String value);
    }

    private TextView mTitle;
    private TextView mTextValue;
    private Switch mSwitch;
    private String mKey;
    private String mKeyType;
    private MmsConfigItemListener mListener;
    private EditText mEditText;

    public DebugMmsConfigItemView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    protected void onFinishInflate () {
        mTitle = (TextView) findViewById(R.id.title);
        mTextValue = (TextView) findViewById(R.id.text_value);
        mSwitch = (Switch) findViewById(R.id.switch_button);
        setOnClickListener(this);
        mSwitch.setOnCheckedChangeListener(this);
    }

    public void bind(final String key, final String keyType, final String value,
            final MmsConfigItemListener listener) {
        mListener = listener;
        mKey = key;
        mKeyType = keyType;
        mTitle.setText(key);

        switch (keyType) {
            case MmsConfig.KEY_TYPE_BOOL:
                mSwitch.setVisibility(View.VISIBLE);
                mTextValue.setVisibility(View.GONE);
                mSwitch.setChecked(Boolean.valueOf(value));
                break;
            case MmsConfig.KEY_TYPE_STRING:
            case MmsConfig.KEY_TYPE_INT:
                mTextValue.setVisibility(View.VISIBLE);
                mSwitch.setVisibility(View.GONE);
                mTextValue.setText(value);
                break;
            default:
                mTextValue.setVisibility(View.GONE);
                mSwitch.setVisibility(View.GONE);
                LogUtil.e(LogUtil.BUGLE_TAG, "Unexpected keytype: " + keyType);
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mListener.onValueChanged(mKey, mKeyType, String.valueOf(isChecked));
    }

    @Override
    public void onClick(View v) {
        if (MmsConfig.KEY_TYPE_BOOL.equals(mKeyType)) {
            return;
        }
        final Context context = getContext();
        mEditText = new EditText(context);
        mEditText.setText(mTextValue.getText());
        mEditText.setFocusable(true);
        if (MmsConfig.KEY_TYPE_INT.equals(mKeyType)) {
            mEditText.setInputType(InputType.TYPE_CLASS_PHONE);
        } else {
            mEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(mKey)
                .setView(mEditText)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                mEditText.requestFocus();
                mEditText.selectAll();
                ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE))
                        .toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
            }
        });
        dialog.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        mListener.onValueChanged(mKey, mKeyType, mEditText.getText().toString());
    }
}
