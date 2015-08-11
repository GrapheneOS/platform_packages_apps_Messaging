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

import android.content.ContentProvider;
import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.MemoryCacheManager;
import com.android.messaging.datamodel.ParticipantRefresh.ContactContentObserver;
import com.android.messaging.datamodel.media.MediaCacheManager;
import com.android.messaging.datamodel.media.MediaResourceManager;
import com.android.messaging.sms.ApnDatabase;
import com.android.messaging.sms.BugleCarrierConfigValuesLoader;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.FakeBugleGservices;
import com.android.messaging.util.FakeBuglePrefs;
import com.android.messaging.util.MediaUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;

public class FakeFactory extends Factory {
    private Context mContext;
    private FakeContext mFakeContext;
    private BugleGservices mBugleGservices;
    private BuglePrefs mBuglePrefs;
    private DataModel mDataModel;
    private UIIntents mUIIntents;
    private MemoryCacheManager mMemoryCacheManager;
    private MediaResourceManager mMediaResourceManager;
    private MediaCacheManager mMediaCacheManager;
    @Mock protected PhoneUtils mPhoneUtils;
    private MediaUtil mMediaUtil;
    private BugleCarrierConfigValuesLoader mCarrierConfigValuesLoader;

    private FakeFactory() {
    }

    public static FakeFactory registerWithFakeContext(final Context context,
            final FakeContext fake) {
        // In tests we currently NEVER run the application/factory initialization
        Assert.isTrue(!sRegistered);
        Assert.isTrue(!sInitialized);

        final FakeFactory factory = new FakeFactory();
        Factory.setInstance(factory);

        // At this point Factory is published. Services can now get initialized and depend on
        // Factory.get().
        factory.mContext = context;
        factory.mFakeContext = fake;
        factory.mMediaResourceManager = Mockito.mock(MediaResourceManager.class);
        factory.mBugleGservices = new FakeBugleGservices();
        factory.mBuglePrefs = new FakeBuglePrefs();
        factory.mPhoneUtils = Mockito.mock(PhoneUtils.class);

        ApnDatabase.initializeAppContext(context);

        Mockito.when(factory.mPhoneUtils.getCanonicalBySystemLocale(Matchers.anyString()))
                .thenAnswer(new Answer<String>() {
                        @Override
                        public String answer(final InvocationOnMock invocation) throws Throwable {
                            final Object[] args = invocation.getArguments();
                            return (String) args[0];
                        }
                    }
                );
        Mockito.when(factory.mPhoneUtils.getCanonicalBySimLocale(Matchers.anyString())).thenAnswer(
                new Answer<String>() {
                    @Override
                    public String answer(final InvocationOnMock invocation) throws Throwable {
                        final Object[] args = invocation.getArguments();
                        return (String) args[0];
                    }
                }
        );
        Mockito.when(factory.mPhoneUtils.formatForDisplay(Matchers.anyString())).thenAnswer(
                new Answer<String>() {
                    @Override
                    public String answer(final InvocationOnMock invocation) throws Throwable {
                        return (String) invocation.getArguments()[0];
                    }
                }
        );
        if (OsUtil.isAtLeastL_MR1()) {
            Mockito.when(factory.mPhoneUtils.toLMr1()).thenReturn(
                    new PhoneUtils.LMr1() {
                        @Override
                        public SubscriptionInfo getActiveSubscriptionInfo() {
                            return null;
                        }

                        @Override
                        public List<SubscriptionInfo> getActiveSubscriptionInfoList() {
                            return null;
                        }

                        @Override
                        public void registerOnSubscriptionsChangedListener(
                                final SubscriptionManager.OnSubscriptionsChangedListener listener) {
                        }
                    }
            );
        }
        // By default only allow reading of system settings (that we provide) - can delegate
        // to real provider if required.
        final FakeContentProvider settings = new FakeContentProvider(context,
                Settings.System.CONTENT_URI, false);
        settings.addOverrideData(Settings.System.CONTENT_URI, "name=?", "time_12_24",
                new String[] { "value" }, new Object[][] { { "12" } });
        settings.addOverrideData(Settings.System.CONTENT_URI, "name=?", "sound_effects_enabled",
                new String[] { "value" }, new Object[][] { { 1 } });

        factory.withProvider(Settings.System.CONTENT_URI, settings);

        return factory;
    }

    public static FakeFactory register(final Context applicationContext) {
        final FakeContext context = new FakeContext(applicationContext);
        return registerWithFakeContext(applicationContext, context);
    }

    public static FakeFactory registerWithoutFakeContext(final Context applicationContext) {
        return registerWithFakeContext(applicationContext, null);
    }

    @Override
    public void onRequiredPermissionsAcquired() {
    }

    @Override
    public Context getApplicationContext() {
        return ((mFakeContext != null) ? mFakeContext : mContext );
    }

    @Override
    public DataModel getDataModel() {
        return mDataModel;
    }

    @Override
    public BugleGservices getBugleGservices() {
        return mBugleGservices;
    }

    @Override
    public BuglePrefs getApplicationPrefs() {
        return mBuglePrefs;
    }

    @Override
    public BuglePrefs getWidgetPrefs() {
        return mBuglePrefs;
    }

    @Override
    public BuglePrefs getSubscriptionPrefs(final int subId) {
        return mBuglePrefs;
    }

    @Override
    public UIIntents getUIIntents() {
        return mUIIntents;
    }

    @Override
    public MemoryCacheManager getMemoryCacheManager() {
        return mMemoryCacheManager;
    }

    @Override
    public MediaResourceManager getMediaResourceManager() {
        return mMediaResourceManager;
    }

    @Override
    public MediaCacheManager getMediaCacheManager() {
        return mMediaCacheManager;
    }

    @Override
    public PhoneUtils getPhoneUtils(final int subId) {
        return mPhoneUtils;
    }

    @Override
    public MediaUtil getMediaUtil() {
        return mMediaUtil;
    }

    @Override
    public BugleCarrierConfigValuesLoader getCarrierConfigValuesLoader() {
        return mCarrierConfigValuesLoader;
    }

    @Override
    public ContactContentObserver getContactContentObserver() {
        return null;
    }

    @Override
    public void reclaimMemory() {
    }

    @Override
    public void onActivityResume() {
    }

    public FakeFactory withDataModel(final DataModel dataModel) {
        this.mDataModel = dataModel;
        return this;
    }

    public FakeFactory withUIIntents(final UIIntents uiIntents) {
        this.mUIIntents = uiIntents;
        return this;
    }

    public FakeFactory withMemoryCacheManager(final MemoryCacheManager memoryCacheManager) {
        this.mMemoryCacheManager = memoryCacheManager;
        return this;
    }

    public FakeFactory withBugleGservices(final BugleGservices bugleGservices) {
        this.mBugleGservices = bugleGservices;
        return this;
    }

    public FakeFactory withMediaCacheManager(final MediaCacheManager mediaCacheManager) {
        this.mMediaCacheManager = mediaCacheManager;
        return this;
    }

    public FakeFactory withProvider(final Uri uri, final ContentProvider provider) {
        if (mFakeContext != null) {
            mFakeContext.addContentProvider(uri.getAuthority(), provider);
        }
        return this;
    }

    public FakeFactory withDefaultProvider(final Uri uri) {
        if (mFakeContext != null) {
            mFakeContext.addDefaultProvider(this.mContext, uri);
        }
        return this;
    }

    public FakeFactory withMediaUtil(final MediaUtil mediaUtil) {
        this.mMediaUtil = mediaUtil;
        return this;
    }

    public FakeFactory withCarrierConfigValuesLoader(
            final BugleCarrierConfigValuesLoader carrierConfigValuesLoader) {
        this.mCarrierConfigValuesLoader = carrierConfigValuesLoader;
        return this;
    }
}
