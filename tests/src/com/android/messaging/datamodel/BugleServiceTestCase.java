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

package com.android.messaging.datamodel;

import android.app.Service;
import android.test.ServiceTestCase;

import com.android.messaging.BugleTestCase;
import com.android.messaging.TestUtil;


/*
 * Base class for service tests that takes care of housekeeping that is commong amongst our service
 * test case.
 */
public abstract class BugleServiceTestCase<T extends Service> extends ServiceTestCase<T> {

    static {
        // Set flag during loading of test cases to prevent application initialization starting
        BugleTestCase.setTestsRunning();
    }

    public BugleServiceTestCase(final Class<T> serviceClass) {
        super(serviceClass);
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      TestUtil.testSetup(getContext(), this);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        TestUtil.testTeardown(this);
    }
}