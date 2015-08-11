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

import com.android.messaging.BugleTestCase;
import com.android.messaging.TestUtil;

/**
 * Helper class that extends ActivityInstrumentationTestCase2 to provide some extra common
 * initialization (eg. Mockito boilerplate).
 */
public class BugleActivityInstrumentationTestCase<T extends Activity>
    extends android.test.ActivityInstrumentationTestCase2<T> {

    static {
        // Set flag during loading of test cases to prevent application initialization starting
        BugleTestCase.setTestsRunning();
    }

    public BugleActivityInstrumentationTestCase(final Class<T> activityClass) {
        super(activityClass);
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      setActivityInitialTouchMode(false);
      TestUtil.testSetup(getInstrumentation().getTargetContext(), this);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        TestUtil.testTeardown(this);
    }
}