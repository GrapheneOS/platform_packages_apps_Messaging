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


/**
 * Fake implementation which just returns the default values and ignores put operations.
 */
public class FakeBuglePrefs extends BuglePrefs {
    public FakeBuglePrefs() {
    }

    @Override
    public int getInt(final String key, final int defaultValue) {
        return defaultValue;
    }

    @Override
    public long getLong(final String key, final long defaultValue) {
        return defaultValue;
    }

    @Override
    public boolean getBoolean(final String key, final boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public String getString(final String key, final String defaultValue) {
        return defaultValue;
    }

    @Override
    public byte[] getBytes(String key) {
        return null;
    }

    @Override
    public void putInt(final String key, final int value) {
    }

    @Override
    public void putLong(final String key, final long value) {
    }

    @Override
    public void putBoolean(final String key, final boolean value) {
    }

    @Override
    public void putString(final String key, final String value) {
    }

    @Override
    public void putBytes(String key, byte[] value) {
    }

    @Override
    public String getSharedPreferencesName() {
        return "FakeBuglePrefs";
    }

    @Override
    public void onUpgrade(int oldVersion, int newVersion) {
    }

    @Override
    public void remove(String key) {
    }
}
