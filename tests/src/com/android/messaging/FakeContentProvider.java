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
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.util.SimpleArrayMap;
import android.text.TextUtils;

import com.android.messaging.datamodel.FakeCursor;
import com.android.messaging.util.LogUtil;

import java.util.ArrayList;

public class FakeContentProvider extends ContentProvider {

    private static class ContentOverride {
        private final String uri;
        private final String where;
        private final String args;
        private final String[] columns;
        private final Object[][] data;

        ContentOverride(final String uri, final String where, final String args,
                final String[] columns, final Object[][] data) {
            this.uri = uri;
            this.where = where;
            this.args = args;
            this.columns = columns;
            this.data = data;
        }

        boolean match(final String uri, final String where, final String[] args) {
            if (!this.uri.equals(uri) || !TextUtils.equals(this.where, where)) {
                return false;
            }

            if (this.args == null || args == null) {
                return this.args == null && args == null;
            }

            return this.args.equals(TextUtils.join(";", args));
        }
    }

    private final Context mGlobalContext;
    private final ArrayList<ContentOverride> mOverrides = new ArrayList<ContentOverride>();
    private final SimpleArrayMap<String, String> mTypes = new SimpleArrayMap<String, String>();
    private final ContentProviderClient mProvider;
    private final Uri mUri;

    public FakeContentProvider(final Context context, final Uri uri, final boolean canDelegate) {
        mGlobalContext = context;
        mUri = uri;
        if (canDelegate) {
            mProvider = mGlobalContext.getContentResolver().acquireContentProviderClient(mUri);
        } else {
            mProvider = null;
        }

        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = uri.getAuthority();

        this.attachInfo(mGlobalContext, providerInfo);
    }

    public void addOverrideData(final Uri uri, final String where, final String args,
            final String[] columns, final Object[][] data) {
        mOverrides.add(new ContentOverride(uri.toString(), where, args, columns, data));
    }

    public void addOverrideType(final Uri uri, final String type) {
        mTypes.put(uri.toString(), type);
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public void shutdown() {
        if (mProvider != null) {
            mProvider.release();
        }
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
            final String[] selectionArgs, final String sortOrder) {
        LogUtil.w(LogUtil.BUGLE_TAG, "FakeContentProvider: query " + uri.toString()
                + " for " + (projection == null ? null : TextUtils.join(",", projection))
                + " where " + selection
                + " with " + (selectionArgs == null ? null : TextUtils.join(";", selectionArgs)));

        for(final ContentOverride content : mOverrides) {
            if (content.match(uri.toString(), selection, selectionArgs)) {
                return new FakeCursor(projection, content.columns, content.data);
            }
        }
        if (mProvider != null) {
            try {
                LogUtil.w(LogUtil.BUGLE_TAG, "FakeContentProvider: delgating");

                final Cursor cursor = mProvider.query(uri, projection, selection, selectionArgs,
                        sortOrder);

                LogUtil.w(LogUtil.BUGLE_TAG, "FakeContentProvider: response size "
                        + cursor.getCount() + " contains " + TextUtils.join(",",
                                cursor.getColumnNames()) + " type(0) " + cursor.getType(0));

                return cursor;
            } catch (final RemoteException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public String getType(final Uri uri) {
        String type = mTypes.get(uri.toString());
        if (type == null) {
            try {
                type = mProvider.getType(uri);
            } catch (final RemoteException e) {
                e.printStackTrace();
            }
        }
        return type;
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        // TODO: Add code to track insert operations and return correct status
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        // TODO: Add code to track delete operations and return correct status
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection,
            final String[] selectionArgs) {
        // TODO: Add code to track update operations and return correct status
        throw new UnsupportedOperationException();
    }

    public Bundle call(final String callingPkg, final String method, final String arg,
            final Bundle extras) {
        return null;
    }
}
