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

package com.android.messaging.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Telephony.Sms;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;

import com.android.messaging.R;
import com.android.messaging.datamodel.action.ReceiveSmsMessageAction;
import com.android.messaging.util.Assert;

import java.util.ArrayList;

/**
 * Display a class-zero SMS message to the user. Wait for the user to dismiss
 * it.
 */
public class ClassZeroActivity extends Activity {
    private static final boolean VERBOSE = false;
    private static final String TAG = "display_00";
    private static final int ON_AUTO_SAVE = 1;

    /** Default timer to dismiss the dialog. */
    private static final long DEFAULT_TIMER = 5 * 60 * 1000;

    /** To remember the exact time when the timer should fire. */
    private static final String TIMER_FIRE = "timer_fire";

    private ContentValues mMessageValues = null;

    /** Is the message read. */
    private boolean mRead = false;

    /** The timer to dismiss the dialog automatically. */
    private long mTimerSet = 0;
    private AlertDialog mDialog = null;

    private ArrayList<ContentValues> mMessageQueue = null;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            // Do not handle an invalid message.
            if (msg.what == ON_AUTO_SAVE) {
                mRead = false;
                mDialog.dismiss();
                saveMessage();
                processNextMessage();
            }
        }
    };

    private boolean queueMsgFromIntent(final Intent msgIntent) {
        final ContentValues messageValues =
                msgIntent.getParcelableExtra(UIIntents.UI_INTENT_EXTRA_MESSAGE_VALUES);
        // that takes the format argument is a hidden API right now.
        final String message = messageValues.getAsString(Sms.BODY);
        if (TextUtils.isEmpty(message)) {
            if (mMessageQueue.size() == 0) {
                finish();
            }
            return false;
        }
        mMessageQueue.add(messageValues);
        return true;
    }

    private void processNextMessage() {
        if (mMessageQueue.size() > 0) {
            mMessageQueue.remove(0);
        }
        if (mMessageQueue.size() == 0) {
            finish();
        } else {
            displayZeroMessage(mMessageQueue.get(0));
        }
    }

    private void saveMessage() {
        mMessageValues.put(Sms.Inbox.READ, mRead ? Integer.valueOf(1) : Integer.valueOf(0));
        final ReceiveSmsMessageAction action = new ReceiveSmsMessageAction(mMessageValues);
        action.start();
    }

    @Override
    protected void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (mMessageQueue == null) {
            mMessageQueue = new ArrayList<ContentValues>();
        }
        if (!queueMsgFromIntent(getIntent())) {
            return;
        }
        Assert.isTrue(mMessageQueue.size() == 1);
        if (mMessageQueue.size() == 1) {
            displayZeroMessage(mMessageQueue.get(0));
        }

        if (icicle != null) {
            mTimerSet = icicle.getLong(TIMER_FIRE, mTimerSet);
        }
    }

    private void displayZeroMessage(final ContentValues messageValues) {
        /* This'll be used by the save action */
        mMessageValues = messageValues;
        final String message = messageValues.getAsString(Sms.BODY);;

        mDialog = new AlertDialog.Builder(this).setMessage(message)
            .setPositiveButton(R.string.save, mSaveListener)
            .setNegativeButton(android.R.string.cancel, mCancelListener)
            .setTitle(R.string.class_0_message_activity)
            .setCancelable(false).show();
        final long now = SystemClock.uptimeMillis();
        mTimerSet = now + DEFAULT_TIMER;
    }

    @Override
    protected void onNewIntent(final Intent msgIntent) {
        // Running with another visible message, queue this one
        queueMsgFromIntent(msgIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final long now = SystemClock.uptimeMillis();
        if (mTimerSet <= now) {
            // Save the message if the timer already expired.
            mHandler.sendEmptyMessage(ON_AUTO_SAVE);
        } else {
            mHandler.sendEmptyMessageAtTime(ON_AUTO_SAVE, mTimerSet);
            if (VERBOSE) {
                Log.d(TAG, "onRestart time = " + Long.toString(mTimerSet) + " "
                        + this.toString());
            }
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(TIMER_FIRE, mTimerSet);
        if (VERBOSE) {
            Log.d(TAG, "onSaveInstanceState time = " + Long.toString(mTimerSet)
                    + " " + this.toString());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.removeMessages(ON_AUTO_SAVE);
        if (VERBOSE) {
            Log.d(TAG, "onStop time = " + Long.toString(mTimerSet)
                    + " " + this.toString());
        }
    }

    private final OnClickListener mCancelListener = new OnClickListener() {
        @Override
        public void onClick(final DialogInterface dialog, final int whichButton) {
            dialog.dismiss();
            processNextMessage();
        }
    };

    private final OnClickListener mSaveListener = new OnClickListener() {
        @Override
        public void onClick(final DialogInterface dialog, final int whichButton) {
            mRead = true;
            saveMessage();
            dialog.dismiss();
            processNextMessage();
        }
    };
}
