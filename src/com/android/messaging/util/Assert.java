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

import android.os.Looper;

import java.util.Arrays;

public final class Assert {
    public static @interface RunsOnMainThread {}
    public static @interface DoesNotRunOnMainThread {}
    public static @interface RunsOnAnyThread {}

    private static final String TEST_THREAD_SUBSTRING = "test";

    private static boolean sIsEngBuild;
    private static boolean sShouldCrash;

    // Private constructor so no one creates this class.
    private Assert() {
    }

    // The proguard rules will strip this method out on user/userdebug builds.
    // If you change the method signature you MUST edit proguard-release.flags.
    private static void setIfEngBuild() {
        sShouldCrash = sIsEngBuild = true;
    }

    private static void refreshGservices(final BugleGservices gservices) {
        sShouldCrash = sIsEngBuild;
        if (!sShouldCrash) {
            sShouldCrash = gservices.getBoolean(
                    BugleGservicesKeys.ASSERTS_FATAL,
                    BugleGservicesKeys.ASSERTS_FATAL_DEFAULT);
        }
    }

    // Static initializer block to find out if we're running an eng or
    // release build.
    static {
        setIfEngBuild();
    }

    // This is called from FactoryImpl once the Gservices class is initialized.
    public static void initializeGservices (final BugleGservices gservices) {
        gservices.registerForChanges(new Runnable() {
            @Override
            public void run() {
                refreshGservices(gservices);
            }
        });
        refreshGservices(gservices);
    }

    /**
     * Halt execution if this is not an eng build.
     * <p>Intended for use in code paths that should be run only for tests and never on
     * a real build.
     * <p>Note that this will crash on a user build even though asserts don't normally
     * crash on a user build.
     */
    public static void isEngBuild() {
        isTrueReleaseCheck(sIsEngBuild);
    }

    /**
     * Halt execution if this isn't the case.
     */
    public static void isTrue(final boolean condition) {
        if (!condition) {
            fail("Expected condition to be true", false);
        }
    }

    /**
     * Halt execution if this isn't the case.
     */
    public static void isFalse(final boolean condition) {
        if (condition) {
            fail("Expected condition to be false", false);
        }
    }

    /**
     * Halt execution even in release builds if this isn't the case.
     */
    public static void isTrueReleaseCheck(final boolean condition) {
        if (!condition) {
            fail("Expected condition to be true", true);
        }
    }

    public static void equals(final int expected, final int actual) {
        if (expected != actual) {
            fail("Expected " + expected + " but got " + actual, false);
        }
    }

    public static void equals(final long expected, final long actual) {
        if (expected != actual) {
            fail("Expected " + expected + " but got " + actual, false);
        }
    }

    public static void equals(final Object expected, final Object actual) {
        if (expected != actual
                && (expected == null || actual == null || !expected.equals(actual))) {
            fail("Expected " + expected + " but got " + actual, false);
        }
    }

    public static void oneOf(final int actual, final int ...expected) {
        for (int value : expected) {
            if (actual == value) {
                return;
            }
        }
        fail("Expected value to be one of " + Arrays.toString(expected) + " but was " + actual);
    }

    public static void inRange(
            final int val, final int rangeMinInclusive, final int rangeMaxInclusive) {
        if (val < rangeMinInclusive || val > rangeMaxInclusive) {
            fail("Expected value in range [" + rangeMinInclusive + ", " +
                    rangeMaxInclusive + "], but was " + val, false);
        }
    }

    public static void inRange(
            final long val, final long rangeMinInclusive, final long rangeMaxInclusive) {
        if (val < rangeMinInclusive || val > rangeMaxInclusive) {
            fail("Expected value in range [" + rangeMinInclusive + ", " +
                    rangeMaxInclusive + "], but was " + val, false);
        }
    }

    public static void isMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()
                && !Thread.currentThread().getName().contains(TEST_THREAD_SUBSTRING)) {
            fail("Expected to run on main thread", false);
        }
    }

    public static void isNotMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()
                && !Thread.currentThread().getName().contains(TEST_THREAD_SUBSTRING)) {
            fail("Not expected to run on main thread", false);
        }
    }

    /**
     * Halt execution if the value passed in is not null
     * @param obj The object to check
     */
    public static void isNull(final Object obj) {
        if (obj != null) {
            fail("Expected object to be null", false);
        }
    }

    /**
     * Halt execution if the value passed in is not null
     * @param obj The object to check
     * @param failureMessage message to print when halting execution
     */
    public static void isNull(final Object obj, final String failureMessage) {
        if (obj != null) {
            fail(failureMessage, false);
        }
    }

    /**
     * Halt execution if the value passed in is null
     * @param obj The object to check
     */
    public static void notNull(final Object obj) {
        if (obj == null) {
            fail("Expected value to be non-null", false);
        }
    }

    public static void fail(final String message) {
        fail("Assert.fail() called: " + message, false);
    }

    private static void fail(final String message, final boolean crashRelease) {
        LogUtil.e(LogUtil.BUGLE_TAG, message);
        if (crashRelease || sShouldCrash) {
            throw new AssertionError(message);
        } else {
            // Find the method whose assertion failed. We're using a depth of 2, because all public
            // Assert methods delegate to this one (see javadoc on getCaller() for details).
            StackTraceElement caller = DebugUtils.getCaller(2);
            if (caller != null) {
                // This log message can be de-obfuscated by the Proguard retrace tool, just like a
                // full stack trace from a crash.
                LogUtil.e(LogUtil.BUGLE_TAG, "\tat " + caller.toString());
            }
        }
    }
}
