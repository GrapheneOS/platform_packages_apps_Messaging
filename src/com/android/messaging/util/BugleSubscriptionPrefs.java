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
import android.content.res.Resources;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.R;

/**
 * Provides interface to access per-subscription shared preferences. We have one instance of
 * this per active subscription.
 */
public class BugleSubscriptionPrefs extends BuglePrefsImpl {
    private final int mSubId;

    public BugleSubscriptionPrefs(final Context context, final int subId) {
        super(context);
        mSubId = subId;
    }

    @Override
    public String getSharedPreferencesName() {
        return SHARED_PREFERENCES_PER_SUBSCRIPTION_PREFIX + String.valueOf(mSubId);
    }

    @Override
    protected void validateKey(String key) {
        super.validateKey(key);
        // Callers should only access per-subscription preferences from this class
        Assert.isTrue(key.startsWith(SHARED_PREFERENCES_PER_SUBSCRIPTION_PREFIX));
    }

    @Override
    public void onUpgrade(final int oldVersion, final int newVersion) {
        switch (oldVersion) {
            case BuglePrefs.NO_SHARED_PREFERENCES_VERSION:
                // Upgrade to version 1. Adding per-subscription shared prefs.
                // Migrate values from the application-wide settings.
                migratePrefBooleanInternal(BuglePrefs.getApplicationPrefs(), "delivery_reports",
                        R.string.delivery_reports_pref_key, R.bool.delivery_reports_pref_default);
                migratePrefBooleanInternal(BuglePrefs.getApplicationPrefs(), "auto_retrieve_mms",
                        R.string.auto_retrieve_mms_pref_key, R.bool.auto_retrieve_mms_pref_default);
                migratePrefBooleanInternal(BuglePrefs.getApplicationPrefs(),
                        "auto_retrieve_mms_when_roaming",
                        R.string.auto_retrieve_mms_when_roaming_pref_key,
                        R.bool.auto_retrieve_mms_when_roaming_pref_default);
                migratePrefBooleanInternal(BuglePrefs.getApplicationPrefs(), "group_messaging",
                        R.string.group_mms_pref_key, R.bool.group_mms_pref_default);

                if (PhoneUtils.getDefault().getActiveSubscriptionCount() == 1) {
                    migratePrefStringInternal(BuglePrefs.getApplicationPrefs(), "mms_phone_number",
                            R.string.mms_phone_number_pref_key, null);
                }
        }
    }

    private void migratePrefBooleanInternal(final BuglePrefs oldPrefs, final String oldKey,
            final int newKeyResId, final int defaultValueResId) {
        final Resources resources = Factory.get().getApplicationContext().getResources();
        final boolean defaultValue = resources.getBoolean(defaultValueResId);
        final boolean oldValue = oldPrefs.getBoolean(oldKey, defaultValue);

        // Only migrate pref value if it's different than the default.
        if (oldValue != defaultValue) {
            putBoolean(resources.getString(newKeyResId), oldValue);
        }
    }

    private void migratePrefStringInternal(final BuglePrefs oldPrefs, final String oldKey,
            final int newKeyResId, final String defaultValue) {
        final Resources resources = Factory.get().getApplicationContext().getResources();
        final String oldValue = oldPrefs.getString(oldKey, defaultValue);

        // Only migrate pref value if it's different than the default.
        if (!TextUtils.equals(oldValue, defaultValue)) {
            putString(resources.getString(newKeyResId), oldValue);
        }
    }
}
