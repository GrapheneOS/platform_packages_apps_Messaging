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
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.action.BugleActionToasts;
import com.android.messaging.datamodel.action.UpdateConversationOptionsAction;
import com.android.messaging.datamodel.action.UpdateDestinationBlockedAction;
import com.android.messaging.datamodel.binding.BindableData;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;

import java.util.List;

/**
 * Services data needs for PeopleAndOptionsFragment.
 */
public class PeopleAndOptionsData extends BindableData implements
        LoaderManager.LoaderCallbacks<Cursor> {
    public interface PeopleAndOptionsDataListener {
        void onOptionsCursorUpdated(PeopleAndOptionsData data, Cursor cursor);
        void onParticipantsListLoaded(PeopleAndOptionsData data,
                List<ParticipantData> participants);
    }

    private static final String BINDING_ID = "bindingId";
    private final Context mContext;
    private final String mConversationId;
    private final ConversationParticipantsData mParticipantData;
    private LoaderManager mLoaderManager;
    private PeopleAndOptionsDataListener mListener;

    public PeopleAndOptionsData(final String conversationId, final Context context,
            final PeopleAndOptionsDataListener listener) {
        mListener = listener;
        mContext = context;
        mConversationId = conversationId;
        mParticipantData = new ConversationParticipantsData();
    }

    private static final int CONVERSATION_OPTIONS_LOADER = 1;
    private static final int PARTICIPANT_LOADER = 2;

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        final String bindingId = args.getString(BINDING_ID);
        // Check if data still bound to the requesting ui element
        if (isBound(bindingId)) {
            switch (id) {
                case CONVERSATION_OPTIONS_LOADER: {
                    final Uri uri =
                            MessagingContentProvider.buildConversationMetadataUri(mConversationId);
                    return new BoundCursorLoader(bindingId, mContext, uri,
                            PeopleOptionsItemData.PROJECTION, null, null, null);
                }

                case PARTICIPANT_LOADER: {
                    final Uri uri =
                            MessagingContentProvider
                                    .buildConversationParticipantsUri(mConversationId);
                    return new BoundCursorLoader(bindingId, mContext, uri,
                            ParticipantData.ParticipantsQuery.PROJECTION, null, null, null);
                }

                default:
                    Assert.fail("Unknown loader id for PeopleAndOptionsFragment!");
                    break;
            }
        } else {
            LogUtil.w(LogUtil.BUGLE_TAG, "Loader created after unbinding PeopleAndOptionsFragment");
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        final BoundCursorLoader cursorLoader = (BoundCursorLoader) loader;
        if (isBound(cursorLoader.getBindingId())) {
            switch (loader.getId()) {
                case CONVERSATION_OPTIONS_LOADER:
                    mListener.onOptionsCursorUpdated(this, data);
                    break;

                case PARTICIPANT_LOADER:
                    mParticipantData.bind(data);
                    mListener.onParticipantsListLoaded(this,
                            mParticipantData.getParticipantListExcludingSelf());
                    break;

                default:
                    Assert.fail("Unknown loader id for PeopleAndOptionsFragment!");
                    break;
            }
        } else {
            LogUtil.w(LogUtil.BUGLE_TAG,
                    "Loader finished after unbinding PeopleAndOptionsFragment");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        final BoundCursorLoader cursorLoader = (BoundCursorLoader) loader;
        if (isBound(cursorLoader.getBindingId())) {
            switch (loader.getId()) {
                case CONVERSATION_OPTIONS_LOADER:
                    mListener.onOptionsCursorUpdated(this, null);
                    break;

                case PARTICIPANT_LOADER:
                    mParticipantData.bind(null);
                    break;

                default:
                    Assert.fail("Unknown loader id for PeopleAndOptionsFragment!");
                    break;
            }
        } else {
            LogUtil.w(LogUtil.BUGLE_TAG, "Loader reset after unbinding PeopleAndOptionsFragment");
        }
    }

    public void init(final LoaderManager loaderManager,
            final BindingBase<PeopleAndOptionsData> binding) {
        final Bundle args = new Bundle();
        args.putString(BINDING_ID, binding.getBindingId());
        mLoaderManager = loaderManager;
        mLoaderManager.initLoader(CONVERSATION_OPTIONS_LOADER, args, this);
        mLoaderManager.initLoader(PARTICIPANT_LOADER, args, this);
    }

    @Override
    protected void unregisterListeners() {
        mListener = null;

        // This could be null if we bind but the caller doesn't init the BindableData
        if (mLoaderManager != null) {
            mLoaderManager.destroyLoader(CONVERSATION_OPTIONS_LOADER);
            mLoaderManager.destroyLoader(PARTICIPANT_LOADER);
            mLoaderManager = null;
        }
    }

    public void enableConversationNotifications(final BindingBase<PeopleAndOptionsData> binding,
            final boolean enable) {
        final String bindingId = binding.getBindingId();
        if (isBound(bindingId)) {
            UpdateConversationOptionsAction.enableConversationNotifications(
                    mConversationId, enable);
        }
    }

    public void setConversationNotificationSound(final BindingBase<PeopleAndOptionsData> binding,
            final String ringtoneUri) {
        final String bindingId = binding.getBindingId();
        if (isBound(bindingId)) {
            UpdateConversationOptionsAction.setConversationNotificationSound(mConversationId,
                    ringtoneUri);
        }
    }

    public void enableConversationNotificationVibration(
            final BindingBase<PeopleAndOptionsData> binding, final boolean enable) {
        final String bindingId = binding.getBindingId();
        if (isBound(bindingId)) {
            UpdateConversationOptionsAction.enableVibrationForConversationNotification(
                    mConversationId, enable);
        }
    }

    public void setDestinationBlocked(final BindingBase<PeopleAndOptionsData> binding,
            final boolean blocked) {
        final String bindingId = binding.getBindingId();
        final ParticipantData participantData = mParticipantData.getOtherParticipant();
        if (isBound(bindingId) && participantData != null) {
            UpdateDestinationBlockedAction.updateDestinationBlocked(
                    participantData.getNormalizedDestination(),
                    blocked, mConversationId,
                    BugleActionToasts.makeUpdateDestinationBlockedActionListener(mContext));
        }
    }
}
