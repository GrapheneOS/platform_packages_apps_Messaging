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
import android.app.FragmentManager;
import android.content.res.Configuration;
import android.view.View;

/**
 * Helper class that extends Bugle.ui.ActivityInstrumentationTestCase to provide common behavior
 * across fragment tests.
 */
public abstract class FragmentTestCase<T extends Fragment>
    extends BugleActivityInstrumentationTestCase<TestActivity> {

    protected T mFragment;
    protected Class<T> mFragmentClass;

    public FragmentTestCase(final Class<T> fragmentClass) {
        super(TestActivity.class);
        mFragmentClass = fragmentClass;
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();
    }

    protected T getFragment() {
        // Fragment creation deferred (typically until test time) so that factory/appcontext is
        // ready.
        if (mFragment == null) {
            try {
                mFragment = mFragmentClass.newInstance();
            } catch (final InstantiationException e) {
                throw new IllegalStateException("Failed to instantiate fragment");
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException("Failed to instantiate fragment");
            }
        }

        return mFragment;
    }

    protected void attachFragment() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final FragmentManager fragmentManager = getActivity().getFragmentManager();
                fragmentManager.beginTransaction().add(mFragment, null /* tag */).commit();
            }
        });

        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        // In landscape mode, sleep for a second first.
        // The reason is: our UI tests don't wait for the UI thread to finish settling down
        // before exiting (because they can't know when the UI thread is done). In portrait mode,
        // things generally work fine here -- the UI thread is done by the time the test is done.
        // In landscape mode, though, since the launcher is in portrait mode, there is a lot of
        // extra work that happens in our UI when the app launches into landscape mode, and the
        // UI is often not done by the time the test finishes running. So then our teardown
        // nulls out the Factory, and then the UI keeps running and derefs the null factory,
        // and things blow up.
        // So ... as a cheap hack, sleep for one second before finishing the teardown of UI
        // tests, but only do it in landscape mode (so that developers running it in portrait
        // mode can still run the tests faster).
        if (this.getInstrumentation().getTargetContext().getResources().
                getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        super.tearDown();
    }

    protected void clickButton(final View view) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.performClick();
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    protected void setFocus(final View view, final boolean focused) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (focused) {
                    view.requestFocus();
                } else {
                    view.clearFocus();
                }
            }
        });
        getInstrumentation().waitForIdleSync();
    }
}