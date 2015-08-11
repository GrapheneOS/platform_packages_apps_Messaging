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

import android.content.Context;

import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.MemoryCacheManager;
import com.android.messaging.datamodel.ParticipantRefresh.ContactContentObserver;
import com.android.messaging.datamodel.media.MediaCacheManager;
import com.android.messaging.datamodel.media.MediaResourceManager;
import com.android.messaging.sms.BugleCarrierConfigValuesLoader;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.MediaUtil;
import com.android.messaging.util.PhoneUtils;
import com.google.common.annotations.VisibleForTesting;

public abstract class Factory {

    // Making this volatile because on the unit tests, setInstance is called from a unit test
    // thread, and then it's read on the UI thread.
    private static volatile Factory sInstance;
    @VisibleForTesting
    protected static boolean sRegistered;
    @VisibleForTesting
    protected static boolean sInitialized;

    public static Factory get() {
        return sInstance;
    }

    protected static void setInstance(final Factory factory) {
        // Not allowed to call this after real application initialization is complete
        Assert.isTrue(!sRegistered);
        Assert.isTrue(!sInitialized);
        sInstance = factory;
    }
    public abstract void onRequiredPermissionsAcquired();

    public abstract Context getApplicationContext();
    public abstract DataModel getDataModel();
    public abstract BugleGservices getBugleGservices();
    public abstract BuglePrefs getApplicationPrefs();
    public abstract BuglePrefs getSubscriptionPrefs(int subId);
    public abstract BuglePrefs getWidgetPrefs();
    public abstract UIIntents getUIIntents();
    public abstract MemoryCacheManager getMemoryCacheManager();
    public abstract MediaResourceManager getMediaResourceManager();
    public abstract MediaCacheManager getMediaCacheManager();
    public abstract ContactContentObserver getContactContentObserver();
    public abstract PhoneUtils getPhoneUtils(int subId);
    public abstract MediaUtil getMediaUtil();
    public abstract BugleCarrierConfigValuesLoader getCarrierConfigValuesLoader();
    // Note this needs to run from any thread
    public abstract void reclaimMemory();

    public abstract void onActivityResume();
}
