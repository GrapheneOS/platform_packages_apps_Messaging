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

import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.DataModel;

import org.mockito.Mock;
import org.mockito.Mockito;

public abstract class BugleActivityTest extends BugleActivityUnitTestCase<BugleActionBarActivity> {
    @Mock protected DataModel mDataModel;

    public BugleActivityTest() {
        super(BugleActionBarActivity.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Create activity
        final ActivityInstrumentationTestCaseIntent intent =
                new ActivityInstrumentationTestCaseIntent(getInstrumentation().getTargetContext(),
                TestActivity.class);
        startActivity(intent, null, null);

        FakeFactory.register(getInstrumentation().getTargetContext())
            .withDataModel(mDataModel);
    }

    public void testOnResumeDataModelCallback() {
        getInstrumentation().callActivityOnStart(getActivity());
        getInstrumentation().callActivityOnResume(getActivity());
        Mockito.verify(mDataModel).onActivityResume();
    }
}
