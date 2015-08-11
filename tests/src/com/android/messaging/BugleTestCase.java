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

package com.android.messaging;

import android.content.Context;
import android.test.AndroidTestCase;

/*
 * Base class for service tests that takes care of housekeeping that is common amongst basic test
 * cases.
 */
public abstract class BugleTestCase extends AndroidTestCase {

    static {
        // Set flag during loading of test cases to prevent application initialization starting
        setTestsRunning();
    }

    public static void setTestsRunning() {
        BugleApplication.setTestsRunning();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestUtil.testSetup(super.getContext(), this);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        TestUtil.testTeardown(this);
    }

    @Override
    public Context getContext() {
        // This doesn't really get the "application context" - just the fake context
        // that the factory has been initialized with for each test case.
        return Factory.get().getApplicationContext();
    }

    public Context getTestContext() {
        return super.getContext();
    }
}