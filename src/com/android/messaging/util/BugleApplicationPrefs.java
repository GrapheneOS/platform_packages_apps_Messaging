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
package com.android.messaging.util;

import android.content.Context;

/**
 * Provides interface to access application-wide shared preferences. This includes both the user
 * visible preferences (e.g. the general settings in the settings page), and internal preferences
 * under {@link BuglePrefsKeys}.
 */
public class BugleApplicationPrefs extends BuglePrefsImpl {
    public BugleApplicationPrefs(Context context) {
        super(context);
    }

    @Override
    public String getSharedPreferencesName() {
        return SHARED_PREFERENCES_NAME;
    }

    @Override
    protected void validateKey(String key) {
        super.validateKey(key);
        // Callers shouldn't try to access per-subscription preferences from this class
        Assert.isFalse(key.startsWith(SHARED_PREFERENCES_PER_SUBSCRIPTION_PREFIX));
    }

    @Override
    public void onUpgrade(int oldVersion, int newVersion) {
    }
}
