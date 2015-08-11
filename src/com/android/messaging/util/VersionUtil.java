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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import java.util.Locale;

public final class VersionUtil {
    private static final Object sLock = new Object();
    private static VersionUtil sInstance;
    private final String mSimpleVersionName;
    private final int mVersionCode;

    public static VersionUtil getInstance(final Context context) {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new VersionUtil(context);
            }
        }
        return sInstance;
    }

    private VersionUtil(final Context context) {
        int versionCode;
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            versionCode = pi.versionCode;
        } catch (final NameNotFoundException exception) {
            Assert.fail("couldn't get package info " + exception);
            versionCode = -1;
        }
        mVersionCode = versionCode;
        final int majorBuildNumber = versionCode / 1000;
        // Use US locale to format version number so that other language characters don't
        // show up in version string.
        mSimpleVersionName = String.format(Locale.US, "%d.%d.%03d",
                majorBuildNumber / 10000,
                (majorBuildNumber / 1000) % 10,
                majorBuildNumber % 1000);
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    public String getSimpleName() {
        return mSimpleVersionName;
    }
}
