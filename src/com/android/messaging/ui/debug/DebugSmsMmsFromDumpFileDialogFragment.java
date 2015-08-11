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
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.telephony.SmsMessage;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.action.ReceiveMmsMessageAction;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.receiver.SmsReceiver;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.LogUtil;

/**
 * Class that displays UI for choosing SMS/MMS dump files for debugging
 */
public class DebugSmsMmsFromDumpFileDialogFragment extends DialogFragment {
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String KEY_DUMP_FILES = "dump_files";
    public static final String KEY_ACTION = "action";

    public static final String ACTION_LOAD = "load";
    public static final String ACTION_EMAIL = "email";

    private String[] mDumpFiles;
    private String mAction;

    public static DebugSmsMmsFromDumpFileDialogFragment newInstance(final String[] dumpFiles,
            final String action) {
        final DebugSmsMmsFromDumpFileDialogFragment frag =
                new DebugSmsMmsFromDumpFileDialogFragment();
        final Bundle args = new Bundle();
        args.putSerializable(KEY_DUMP_FILES, dumpFiles);
        args.putString(KEY_ACTION, action);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        mDumpFiles = (String[]) args.getSerializable(KEY_DUMP_FILES);
        mAction = args.getString(KEY_ACTION);

        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final View layout = inflater.inflate(
                R.layout.debug_sms_mms_from_dump_file_dialog, null/*root*/);
        final ListView list = (ListView) layout.findViewById(R.id.dump_file_list);
        list.setAdapter(new DumpFileListAdapter(getActivity(), mDumpFiles));
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final Resources resources = getResources();
        if (ACTION_LOAD.equals(mAction)) {
            builder.setTitle(resources.getString(
                    R.string.load_sms_mms_from_dump_file_dialog_title));
        } else if (ACTION_EMAIL.equals(mAction)) {
            builder.setTitle(resources.getString(
                    R.string.email_sms_mms_from_dump_file_dialog_title));
        }
        builder.setView(layout);
        return builder.create();
    }

    private class DumpFileListAdapter extends ArrayAdapter<String> {
        public DumpFileListAdapter(final Context context, final String[] dumpFiles) {
            super(context, R.layout.sms_mms_dump_file_list_item, dumpFiles);
        }

        @Override
        public View getView(final int position, final View view, final ViewGroup parent) {
            TextView actionItemView;
            if (view == null || !(view instanceof TextView)) {
                final LayoutInflater inflater = LayoutInflater.from(getContext());
                actionItemView = (TextView) inflater.inflate(
                        R.layout.sms_mms_dump_file_list_item, parent, false);
            } else {
                actionItemView = (TextView) view;
            }

            final String file = getItem(position);
            actionItemView.setText(file);
            actionItemView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View view) {
                    dismiss();
                    if (ACTION_LOAD.equals(mAction)) {
                        receiveFromDumpFile(file);
                    } else if (ACTION_EMAIL.equals(mAction)) {
                        emailDumpFile(file);
                    }
                }
            });
            return actionItemView;
        }
    }

    /**
     * Load MMS/SMS from the dump file
     */
    private void receiveFromDumpFile(final String dumpFileName) {
        if (dumpFileName.startsWith(MmsUtils.SMS_DUMP_PREFIX)) {
            final SmsMessage[] messages = DebugUtils.retreiveSmsFromDumpFile(dumpFileName);
            if (messages != null) {
                SmsReceiver.deliverSmsMessages(getActivity(), ParticipantData.DEFAULT_SELF_SUB_ID,
                        0, messages);
            } else {
                LogUtil.e(LogUtil.BUGLE_TAG,
                        "receiveFromDumpFile: invalid sms dump file " + dumpFileName);
            }
        } else if (dumpFileName.startsWith(MmsUtils.MMS_DUMP_PREFIX)) {
            final byte[] data = MmsUtils.createDebugNotificationInd(dumpFileName);
            if (data != null) {
                final ReceiveMmsMessageAction action = new ReceiveMmsMessageAction(
                        ParticipantData.DEFAULT_SELF_SUB_ID, data);
                action.start();
            } else {
                LogUtil.e(LogUtil.BUGLE_TAG,
                        "receiveFromDumpFile: invalid mms dump file " + dumpFileName);
            }
        } else {
            LogUtil.e(LogUtil.BUGLE_TAG,
                    "receiveFromDumpFile: invalid dump file name " + dumpFileName);
        }
    }

    /**
     * Launch email app to send the dump file
     */
    private void emailDumpFile(final String file) {
        final Resources resources = getResources();
        final String fileLocation = "file://"
                + Environment.getExternalStorageDirectory() + "/" + file;
        final Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType(APPLICATION_OCTET_STREAM);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(fileLocation));
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT,
                resources.getString(R.string.email_sms_mms_dump_file_subject));
        getActivity().startActivity(Intent.createChooser(sharingIntent,
                resources.getString(R.string.email_sms_mms_dump_file_chooser_title)));
    }
}
