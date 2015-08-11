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

package com.android.messaging;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.PowerManager;
import android.test.InstrumentationTestCase;

import com.android.messaging.util.LogUtil;
import com.android.messaging.widget.BugleWidgetProvider;
import com.android.messaging.widget.WidgetConversationProvider;

import junit.framework.TestCase;

import org.mockito.MockitoAnnotations;

/**
 * Helpers that can be called from all test base classes to 'reset' state and prevent as much as
 * possible having side effects leak from one test to another.
 */
public class TestUtil {
    public static void testSetup(final Context context, final TestCase testCase) {
        haltIfTestsAreNotAbleToRun(context);

        // Workaround to get mockito to work.
        // See https://code.google.com/p/dexmaker/issues/detail?id=2. TODO: Apparently
        // solvable by using a different runner.
        System.setProperty("dexmaker.dexcache",
                context.getCacheDir().getPath());

        // Initialize @Mock objects.
        MockitoAnnotations.initMocks(testCase);

        // Tests have to explicitly override this
        Factory.setInstance(null);
    }

    public static void testTeardown(final TestCase testCase) {
        if (testCase instanceof InstrumentationTestCase) {
            // Make sure the test case is finished running or we'll get NPEs when accessing
            // Fragment.get()
            ((InstrumentationTestCase) testCase).getInstrumentation().waitForIdleSync();
        }
        Factory.setInstance(null);
    }

    private static void haltIfTestsAreNotAbleToRun(final Context context) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (!pm.isScreenOn()) {
            // Ideally we could turn it on for you using the WindowManager, but we currently run
            // the tests independently of the activity life cycle.
            LogUtil.wtf(LogUtil.BUGLE_TAG, "You need to turn on your screen to run tests!");
        }

        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int [] conversationWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context,
                WidgetConversationProvider.class));
        int [] conversationListWidgetIds = appWidgetManager.getAppWidgetIds(new
                ComponentName(context, BugleWidgetProvider.class));

        if ((conversationWidgetIds.length > 0) || (conversationListWidgetIds.length > 0)) {
            // Currently widgets asynchronously access our content providers and singletons which
            // interacts badly with our test setup and tear down.
            LogUtil.wtf(LogUtil.BUGLE_TAG, "You currently can't reliably run unit tests" +
                    " with a Messaging widget on your desktop!");
        }
    }
}
