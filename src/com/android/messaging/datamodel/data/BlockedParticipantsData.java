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

package com.android.messaging.datamodel.data;

import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.messaging.datamodel.BoundCursorLoader;
import com.android.messaging.datamodel.DatabaseHelper.ParticipantColumns;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.binding.BindableData;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.util.Assert;

/**
 * Services data needs for BlockedParticipantsFragment
 */
public class BlockedParticipantsData extends BindableData implements
        LoaderManager.LoaderCallbacks<Cursor> {
    public interface BlockedParticipantsDataListener {
        public void onBlockedParticipantsCursorUpdated(final Cursor cursor);
    }
    private static final String BINDING_ID = "bindingId";
    private static final int BLOCKED_PARTICIPANTS_LOADER = 1;
    private final Context mContext;
    private LoaderManager mLoaderManager;
    private BlockedParticipantsDataListener mListener;

    public BlockedParticipantsData(final Context context,
            final BlockedParticipantsDataListener listener) {
        mContext = context;
        mListener = listener;
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        Assert.isTrue(id == BLOCKED_PARTICIPANTS_LOADER);
        final String bindingId = args.getString(BINDING_ID);
        // Check if data still bound to the requesting ui element
        if (isBound(bindingId)) {
            final Uri uri = MessagingContentProvider.PARTICIPANTS_URI;
            return new BoundCursorLoader(bindingId, mContext, uri,
                    ParticipantData.ParticipantsQuery.PROJECTION,
                    ParticipantColumns.BLOCKED + "=1", null, null);
        }
        return null;
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
        Assert.isTrue(loader.getId() == BLOCKED_PARTICIPANTS_LOADER);
        final BoundCursorLoader cursorLoader = (BoundCursorLoader) loader;
        Assert.isTrue(isBound(cursorLoader.getBindingId()));
        mListener.onBlockedParticipantsCursorUpdated(cursor);
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        Assert.isTrue(loader.getId() == BLOCKED_PARTICIPANTS_LOADER);
        final BoundCursorLoader cursorLoader = (BoundCursorLoader) loader;
        Assert.isTrue(isBound(cursorLoader.getBindingId()));
        mListener.onBlockedParticipantsCursorUpdated(null);
    }

    public void init(final LoaderManager loaderManager,
            final BindingBase<BlockedParticipantsData> binding) {
        final Bundle args = new Bundle();
        args.putString(BINDING_ID, binding.getBindingId());
        mLoaderManager = loaderManager;
        mLoaderManager.initLoader(BLOCKED_PARTICIPANTS_LOADER, args, this);
    }

    @Override
    protected void unregisterListeners() {
        mListener = null;
        if (mLoaderManager != null) {
            mLoaderManager.destroyLoader(BLOCKED_PARTICIPANTS_LOADER);
            mLoaderManager = null;
        }
    }

    public ParticipantListItemData createParticipantListItemData(Cursor cursor) {
        return new ParticipantListItemData(ParticipantData.getFromCursor(cursor));
    }
}
