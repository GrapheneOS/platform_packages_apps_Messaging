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

import android.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.messaging.R;
import com.android.messaging.util.LogUtil;
import com.google.common.annotations.VisibleForTesting;

/**
 * An empty activity that can be used to host a fragment or view for unit testing purposes. Lives in
 * app code vs test code due to requirement of ActivityInstrumentationTestCase2.
 */
public class TestActivity extends FragmentActivity {
    private FragmentEventListener mFragmentEventListener;

    public interface FragmentEventListener {
        public void onAttachFragment(Fragment fragment);
    }

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);

        if (bundle != null) {
            // The test case may have configured the fragment, and recreating the activity will
            // lose that configuration. The real activity is the only activity that would know
            // how to reapply that configuration.
            throw new IllegalStateException("TestActivity cannot get recreated");
        }

        // There is a race condition, but this often makes it possible for tests to run with the
        // key guard up
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.test_activity);
    }

    @VisibleForTesting
    public void setFragment(final Fragment fragment) {
        LogUtil.i(LogUtil.BUGLE_TAG, "TestActivity.setFragment");
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.test_content, fragment)
                .commit();
        getFragmentManager().executePendingTransactions();
    }

    @VisibleForTesting
    public void setView(final View view) {
        LogUtil.i(LogUtil.BUGLE_TAG, "TestActivity.setView");
        ((FrameLayout) findViewById(R.id.test_content)).addView(view);
    }

    @Override
    public void onAttachFragment(final Fragment fragment) {
        if (mFragmentEventListener != null) {
            mFragmentEventListener.onAttachFragment(fragment);
        }
    }

    public void setFragmentEventListener(final FragmentEventListener fragmentEventListener) {
        mFragmentEventListener = fragmentEventListener;
    }
}
