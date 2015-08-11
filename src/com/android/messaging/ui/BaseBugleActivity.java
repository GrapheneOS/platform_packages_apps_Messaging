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
import android.os.Bundle;

import com.android.messaging.util.BugleActivityUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.UiUtils;

/**
 * Base class for app activities that would normally derive from Activity. Responsible for
 * ensuring app requirements are met during onResume()
 */
public class BaseBugleActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (UiUtils.redirectToPermissionCheckIfNeeded(this)) {
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtil.v(LogUtil.BUGLE_TAG, this.getLocalClassName() + ".onResume");
        BugleActivityUtil.onActivityResume(this, BaseBugleActivity.this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtil.v(LogUtil.BUGLE_TAG, this.getLocalClassName() + ".onPause");
    }
}
