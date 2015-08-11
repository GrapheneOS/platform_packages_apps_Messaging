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
import android.view.ContextThemeWrapper;

import com.android.messaging.BugleTestCase;
import com.android.messaging.R;
import com.android.messaging.TestUtil;

/**
 * Base class for activity unit test cases, provides boilerplate setup/teardown.
 */
public abstract class BugleActivityUnitTestCase<T extends Activity> extends
    android.test.ActivityUnitTestCase<T> {

    static {
        // Set flag during loading of test cases to prevent application initialization starting
        BugleTestCase.setTestsRunning();
    }

    public BugleActivityUnitTestCase(final Class<T> activityClass) {
        super(activityClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestUtil.testSetup(getInstrumentation().getTargetContext(), this);

        setActivityContext(new ContextThemeWrapper(getInstrumentation().getTargetContext(),
                R.style.Theme_AppCompat_Light_DarkActionBar));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        TestUtil.testTeardown(this);
    }
}
