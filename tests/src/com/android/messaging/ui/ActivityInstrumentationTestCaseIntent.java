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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;


/**
 * Purpose of this class is providing a workaround for https://b/14561718
 */
public class ActivityInstrumentationTestCaseIntent extends Intent {
    public ActivityInstrumentationTestCaseIntent(Context packageContext, Class<?> cls) {
        super(packageContext, cls);
    }
    @Override
    public Intent setComponent(ComponentName component) {
        // Ignore the ComponentName set, as the one ActivityUnitTest does is wrong (and actually
        // unnecessary).
        return this;
    }
}