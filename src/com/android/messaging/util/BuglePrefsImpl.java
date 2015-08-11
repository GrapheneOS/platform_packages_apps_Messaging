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
import android.content.SharedPreferences;
import android.util.Base64;

/**
 * Thin wrapper to get/set shared prefs values.
 */
public abstract class BuglePrefsImpl extends BuglePrefs {

    private final Context mContext;

    public BuglePrefsImpl(final Context context) {
        mContext = context;
    }

    /**
     * Validate the prefs key passed in. Subclasses should override this as needed to perform
     * runtime checks (such as making sure per-subscription settings don't sneak into application-
     * wide settings).
     */
    protected void validateKey(String key) {
    }

    @Override
    public int getInt(final String key, final int defaultValue) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        return prefs.getInt(key, defaultValue);
    }

    @Override
    public long getLong(final String key, final long defaultValue) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        return prefs.getLong(key, defaultValue);
    }

    @Override
    public boolean getBoolean(final String key, final boolean defaultValue) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        return prefs.getBoolean(key, defaultValue);
    }

    @Override
    public String getString(final String key, final String defaultValue) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        return prefs.getString(key, defaultValue);
    }

    @Override
    public byte[] getBytes(String key) {
        final String byteValue = getString(key, null);
        return byteValue == null ? null : Base64.decode(byteValue, Base64.DEFAULT);
    }

    @Override
    public void putInt(final String key, final int value) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    @Override
    public void putLong(final String key, final long value) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    @Override
    public void putBoolean(final String key, final boolean value) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    @Override
    public void putString(final String key, final String value) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
    }

    @Override
    public void putBytes(String key, byte[] value) {
        final String encodedBytes = Base64.encodeToString(value, Base64.DEFAULT);
        putString(key, encodedBytes);
    }

    @Override
    public void remove(final String key) {
        validateKey(key);
        final SharedPreferences prefs = mContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.remove(key);
        editor.apply();
    }
}
