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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.util.AccessibilityUtil;

public class CopyContactDetailDialog implements DialogInterface.OnClickListener {

    private final Context mContext;
    private final String mContactDetail;

    public CopyContactDetailDialog(final Context context, final String contactDetail) {
        mContext = context;
        mContactDetail = contactDetail;
    }

    public void show() {
        new AlertDialog.Builder(mContext)
                .setView(createBodyView())
                .setTitle(R.string.copy_to_clipboard_dialog_title)
                .setPositiveButton(R.string.copy_to_clipboard, this)
                .show();
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        final ClipboardManager clipboard =
                (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(null /* label */, mContactDetail));
    }

    private View createBodyView() {
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        TextView textView = (TextView) inflater.inflate(R.layout.copy_contact_dialog_view, null,
                false);
        textView.setText(mContactDetail);
        final String vocalizedDisplayName = AccessibilityUtil.getVocalizedPhoneNumber(
                mContext.getResources(), mContactDetail);
        textView.setContentDescription(vocalizedDisplayName);
        return textView;
    }
}
