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

import android.graphics.Typeface;

/**
 * Provides access to typefaces used by code. Specially important for typefaces coming from assets,
 * which appear (from platform code inspection) to not be cached.
 * Note: Considered making this a singleton provided by factory/appcontext, but seemed too simple,
 * not worth stubbing.
 */
public class Typefaces {
    private static Typeface sRobotoBold;
    private static Typeface sRobotoNormal;

    public static Typeface getRobotoBold() {
        Assert.isMainThread();
        if (sRobotoBold == null) {
            sRobotoBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        }
        return sRobotoBold;
    }

    public static Typeface getRobotoNormal() {
        Assert.isMainThread();
        if (sRobotoNormal == null) {
            sRobotoNormal = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        }
        return sRobotoNormal;
    }
}
