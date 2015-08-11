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

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.test.RenamingDelegatingContext;
import android.test.mock.MockContentResolver;
import android.util.Log;

import java.util.ArrayList;

public class FakeContext extends RenamingDelegatingContext {
    private static final String TAG = "FakeContext";

    public interface FakeContextHost {
        public String getServiceClassName();
        public void startServiceForStub(Intent intent);
        public void onStartCommandForStub(Intent intent, int flags, int startid);
    }

    ArrayList<Intent> mStartedIntents;
    boolean mServiceStarted = false;
    private final FakeContextHost mService;
    private final MockContentResolver mContentResolver;


    public FakeContext(final Context context, final FakeContextHost service) {
        super(context, "test_");
        mService = service;
        mStartedIntents = new ArrayList<Intent>();
        mContentResolver = new MockContentResolver();
    }

    public FakeContext(final Context context) {
        this(context, null);
    }

    public ArrayList<Intent> extractIntents() {
        final ArrayList<Intent> intents = mStartedIntents;
        mStartedIntents = new ArrayList<Intent>();
        return intents;
    }

    @Override
    public ComponentName startService(final Intent intent) {
        // Record that a startService occurred with the intent that was passed.
        Log.d(TAG, "MockContext receiving startService. intent=" + intent.toString());
        mStartedIntents.add(intent);
        if (mService == null) {
            return super.startService(intent);
        } else if (intent.getComponent() != null &&
                intent.getComponent().getClassName().equals(mService.getServiceClassName())) {
            if (!mServiceStarted) {
                Log.d(TAG, "MockContext first start service.");
                mService.startServiceForStub(intent);
            } else {
                Log.d(TAG, "MockContext not first start service. Calling onStartCommand.");
                mService.onStartCommandForStub(intent, 0, 0);
            }
            mServiceStarted = true;
            return new ComponentName(this, intent.getComponent().getClassName());
        }
        return null;
    }

    @Override
    public ContentResolver getContentResolver() {
        // If you want to use a content provider in your test, then you need to add it
        // explicitly.
        return mContentResolver;
    }

    public void addContentProvider(final String name, final ContentProvider provider) {
        mContentResolver.addProvider(name, provider);
    }

    public void addDefaultProvider(final Context context, final Uri uri) {
        final FakeContentProvider provider = new FakeContentProvider(context, uri, true);
        mContentResolver.addProvider(uri.getAuthority(), provider);
    }

}
