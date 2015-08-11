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

package com.android.messaging.util;

import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.View;

import com.android.messaging.R;
import com.android.messaging.ui.SnackBar;
import com.android.messaging.ui.UIIntents;

public class ChangeDefaultSmsAppHelper {
    private Runnable mRunAfterMadeDefault;
    private ChangeSmsAppSettingRunnable mChangeSmsAppSettingRunnable;

    private static final int REQUEST_SET_DEFAULT_SMS_APP = 1;

    /**
     * When there's some condition that prevents an operation, such as sending a message,
     * call warnOfMissingActionConditions to put up a toast and allow the user to repair
     * that condition.
     * @param sending - true if we're called during a sending operation
     * @param runAfterMadeDefault - a runnable to run after the user responds
     *                  positively to the condition prompt and resolves the condition. It is
     *                  preferable to specify the value in {@link #handleChangeDefaultSmsResult}
     *                  as that handles the case where the process gets restarted.
     *                  If null, the user will be shown a generic toast message.
     * @param composeView - compose view that may have the keyboard opened and focused
     * @param rootView - if non-null, use this to attach a snackBar
     * @param activity - calling activity
     * @param fragment - calling fragment, may be null if called directly from an activity
     */
    public void warnOfMissingActionConditions(final boolean sending,
            final Runnable runAfterMadeDefault,
            final View composeView, final View rootView,
            final Activity activity, final Fragment fragment) {
        final PhoneUtils phoneUtils = PhoneUtils.getDefault();
        final boolean isSmsCapable = phoneUtils.isSmsCapable();
        final boolean hasPreferredSmsSim = phoneUtils.getHasPreferredSmsSim();
        final boolean isDefaultSmsApp = phoneUtils.isDefaultSmsApp();

        // Supports SMS?
        if (!isSmsCapable) {
            UiUtils.showToast(R.string.sms_disabled);

        // Has a preferred sim?
        } else if (!hasPreferredSmsSim) {
            UiUtils.showToast(R.string.no_preferred_sim_selected);

        // Is the default sms app?
        } else if (!isDefaultSmsApp) {
            mChangeSmsAppSettingRunnable = new ChangeSmsAppSettingRunnable(activity, fragment);
            promptToChangeDefaultSmsApp(sending, runAfterMadeDefault,
                    composeView, rootView, activity);
        }

        LogUtil.w(LogUtil.BUGLE_TAG, "Unsatisfied action condition: "
                + "isSmsCapable=" + isSmsCapable + ", "
                + "hasPreferredSmsSim=" + hasPreferredSmsSim + ", "
                + "isDefaultSmsApp=" + isDefaultSmsApp);
    }

    private void promptToChangeDefaultSmsApp(final boolean sending,
            final Runnable runAfterMadeDefault,
            final View composeView, final View rootView,
            final Activity activity) {
        if (composeView != null) {
            // Avoid bug in system which puts soft keyboard over dialog after orientation change
            ImeUtil.hideSoftInput(activity, composeView);
        }
        mRunAfterMadeDefault = runAfterMadeDefault;

        if (rootView == null) {
            // Immediately open the system "Change default SMS app?" dialog setting.
            mChangeSmsAppSettingRunnable.run();
        } else {
            UiUtils.showSnackBarWithCustomAction(activity,
                    rootView,
                    activity.getString(sending ? R.string.requires_default_sms_app_to_send :
                        R.string.requires_default_sms_app),
                        SnackBar.Action.createCustomAction(mChangeSmsAppSettingRunnable,
                                activity.getString(R.string.requires_default_sms_change_button)),
                                null /* interactions */,
                                SnackBar.Placement.above(composeView));
        }
    }

    private class ChangeSmsAppSettingRunnable implements Runnable {
        private final Activity mActivity;
        private final Fragment mFragment;

        public ChangeSmsAppSettingRunnable(final Activity activity, final Fragment fragment) {
            mActivity = activity;
            mFragment = fragment;
        }

        @Override
        public void run() {
            try {
                final Intent intent = UIIntents.get().getChangeDefaultSmsAppIntent(mActivity);
                if (mFragment != null) {
                    mFragment.startActivityForResult(intent, REQUEST_SET_DEFAULT_SMS_APP);
                } else {
                    mActivity.startActivityForResult(intent, REQUEST_SET_DEFAULT_SMS_APP);
                }
            } catch (final ActivityNotFoundException ex) {
                // We shouldn't get here, but the monkey on JB MR0 can trigger it.
                LogUtil.w(LogUtil.BUGLE_TAG, "Couldn't find activity:", ex);
                UiUtils.showToastAtBottom(R.string.activity_not_found_message);
            }
        }
    }

    public void handleChangeDefaultSmsResult(
            final int requestCode,
            final int resultCode,
            Runnable runAfterMadeDefault) {
        Assert.isTrue(mRunAfterMadeDefault == null || runAfterMadeDefault == null);
        if (runAfterMadeDefault == null) {
            runAfterMadeDefault = mRunAfterMadeDefault;
        }

        if (requestCode == REQUEST_SET_DEFAULT_SMS_APP) {
            if (resultCode == Activity.RESULT_OK) {
                // mRunAfterMadeDefault can be null if it was set only in
                // promptToChangeDefaultSmsApp, and the process subsequently restarted when the
                // user momentarily switched to another app. In that case, we'll simply show a
                // generic toast since we do not know what the runnable was supposed to do.
                if  (runAfterMadeDefault != null) {
                    runAfterMadeDefault.run();
                } else {
                    UiUtils.showToast(R.string.toast_after_setting_default_sms_app);
                }
            }
            mRunAfterMadeDefault = null;    // don't want to accidentally run it again
        }
    }
}


