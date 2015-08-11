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

import android.view.View;


/**
 * Base class for view tests. Derived class just has to provide a layout id. Tests can then just
 * call getView() to get a created view and test its behavior.
 */
public abstract class ViewTest<T extends View> extends BugleActivityUnitTestCase<TestActivity> {
    public ViewTest() {
        super(TestActivity.class);
    }

    protected T mView;

    @SuppressWarnings("unchecked")
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Create activity
        final ActivityInstrumentationTestCaseIntent intent =
                new ActivityInstrumentationTestCaseIntent(getInstrumentation().getTargetContext(),
                TestActivity.class);
        startActivity(intent, null, null);
    }

    @SuppressWarnings("unchecked")
    protected T getView() {
        if (mView == null) {
            // View creation deferred (typically until test time) so that factory/appcontext is
            // ready.
            mView = (T) getActivity().getLayoutInflater().inflate(getLayoutIdForView(), null);
        }
        return mView;
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

    protected abstract int getLayoutIdForView();
}
