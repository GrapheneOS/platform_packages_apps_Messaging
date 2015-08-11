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

import com.android.messaging.Factory;

/**
 * A thin wrapper for getting GServices value. During constructor time a one time background thread
 * will cache all GServices key with the prefix of "bugle_". All get calls will wait for Gservices
 * to finish caching the first time. In practice, the background thread will finish before any get
 * request.
 */
public abstract class BugleGservices {
    static final String BUGLE_GSERVICES_PREFIX = "bugle_";

    public static BugleGservices get() {
        return Factory.get().getBugleGservices();
    }

    public abstract void registerForChanges(final Runnable r);

    /**
     * @param key The key to look up in GServices
     * @param defaultValue The default value if value in GServices is null or if
     * NumberFormatException is caught.
     * @return The corresponding value, or the default value.
     */
    public abstract long getLong(final String key, final long defaultValue);

    /**
     * @param key The key to look up in GServices
     * @param defaultValue The default value if value in GServices is null or if
     * NumberFormatException is caught.
     * @return The corresponding value, or the default value.
     */
    public abstract int getInt(final String key, final int defaultValue);

    /**
     * @param key The key to look up in GServices
     * @param defaultValue The default value if value in GServices is null.
     * @return The corresponding value, or the default value.
     */
    public abstract boolean getBoolean(final String key, final boolean defaultValue);

    /**
     * @param key The key to look up in GServices
     * @param defaultValue The default value if value in GServices is null.
     * @return The corresponding value, or the default value.
     */
    public abstract String getString(final String key, final String defaultValue);

    /**
     * @param key The key to look up in GServices
     * @param defaultValue The default value if value in GServices is null.
     * @return The corresponding value, or the default value.
     */
    public abstract float getFloat(final String key, final float defaultValue);
}
