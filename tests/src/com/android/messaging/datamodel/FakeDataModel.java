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

package com.android.messaging.datamodel;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.test.RenamingDelegatingContext;

import com.android.messaging.datamodel.action.ActionService;
import com.android.messaging.datamodel.action.BackgroundWorker;
import com.android.messaging.datamodel.data.BlockedParticipantsData;
import com.android.messaging.datamodel.data.BlockedParticipantsData.BlockedParticipantsDataListener;
import com.android.messaging.datamodel.data.ContactListItemData;
import com.android.messaging.datamodel.data.ContactPickerData;
import com.android.messaging.datamodel.data.ContactPickerData.ContactPickerDataListener;
import com.android.messaging.datamodel.data.ConversationData;
import com.android.messaging.datamodel.data.ConversationData.ConversationDataListener;
import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.ConversationListData.ConversationListDataListener;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.GalleryGridItemData;
import com.android.messaging.datamodel.data.LaunchConversationData;
import com.android.messaging.datamodel.data.LaunchConversationData.LaunchConversationDataListener;
import com.android.messaging.datamodel.data.MediaPickerData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.ParticipantListItemData;
import com.android.messaging.datamodel.data.PeopleAndOptionsData;
import com.android.messaging.datamodel.data.PeopleAndOptionsData.PeopleAndOptionsDataListener;
import com.android.messaging.datamodel.data.PeopleOptionsItemData;
import com.android.messaging.datamodel.data.SettingsData;
import com.android.messaging.datamodel.data.SettingsData.SettingsDataListener;
import com.android.messaging.datamodel.data.SubscriptionListData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.datamodel.data.VCardContactItemData;
import com.android.messaging.util.ConnectivityUtil;

public class FakeDataModel extends DataModel {
    private BackgroundWorker mWorker;
    private ActionService mActionService;
    private final DatabaseHelper mDatabaseHelper;
    private ConversationListData mConversationListData;
    private ContactPickerData mContactPickerData;
    private MediaPickerData mMediaPickerData;
    private PeopleAndOptionsData mPeopleAndOptionsData;
    private ConnectivityUtil mConnectivityUtil;
    private SyncManager mSyncManager;
    private SettingsData mSettingsData;
    private DraftMessageData mDraftMessageData;

    public FakeDataModel(final Context context) {
        super();
        if (context instanceof RenamingDelegatingContext) {
            mDatabaseHelper = DatabaseHelper.getNewInstanceForTest(context);
        } else {
            mDatabaseHelper = null;
        }
    }

    @Override
    public BackgroundWorker getBackgroundWorkerForActionService() {
        return mWorker;
    }

    public FakeDataModel withBackgroundWorkerForActionService(final BackgroundWorker worker) {
        mWorker = worker;
        return this;
    }

    public FakeDataModel withActionService(final ActionService ActionService) {
        mActionService = ActionService;
        return this;
    }

    public FakeDataModel withConversationListData(final ConversationListData conversationListData) {
        mConversationListData = conversationListData;
        return this;
    }

    public FakeDataModel withContactPickerData(final ContactPickerData contactPickerData) {
        mContactPickerData = contactPickerData;
        return this;
    }

    public FakeDataModel withMediaPickerData(final MediaPickerData mediaPickerData) {
        mMediaPickerData = mediaPickerData;
        return this;
    }

    public FakeDataModel withConnectivityUtil(final ConnectivityUtil connectivityUtil) {
        mConnectivityUtil = connectivityUtil;
        return this;
    }

    public FakeDataModel withSyncManager(final SyncManager syncManager) {
        mSyncManager = syncManager;
        return this;
    }

    public FakeDataModel withPeopleAndOptionsData(final PeopleAndOptionsData peopleAndOptionsData) {
        mPeopleAndOptionsData = peopleAndOptionsData;
        return this;
    }

    public FakeDataModel withSettingsData(final SettingsData settingsData) {
        mSettingsData = settingsData;
        return this;
    }

    public FakeDataModel withDraftMessageData(final DraftMessageData draftMessageData) {
        mDraftMessageData = draftMessageData;
        return this;
    }

    @Override
    public ConversationListData createConversationListData(final Context context,
            final ConversationListDataListener listener, final boolean archivedMode) {
        return mConversationListData;
    }

    @Override
    public ConversationData createConversationData(final Context context,
            final ConversationDataListener listener, final String conversationId) {
        throw new IllegalStateException("Add withXXX or mock this method");
    }

    @Override
    public ContactListItemData createContactListItemData() {
        // This is a lightweight data holder object for each individual list item for which
        // we don't perform any data request, so we can directly return a new instance.
        return new ContactListItemData();
    }

    @Override
    public ContactPickerData createContactPickerData(final Context context,
            final ContactPickerDataListener listener) {
        return mContactPickerData;
    }

    @Override
    public MediaPickerData createMediaPickerData(final Context context) {
        return mMediaPickerData;
    }

    @Override
    public GalleryGridItemData createGalleryGridItemData() {
        // This is a lightweight data holder object for each individual grid item for which
        // we don't perform any data request, so we can directly return a new instance.
        return new GalleryGridItemData();
    }

    @Override
    public LaunchConversationData createLaunchConversationData(
            final LaunchConversationDataListener listener) {
       return new LaunchConversationData(listener);
    }

    @Override
    public PeopleOptionsItemData createPeopleOptionsItemData(final Context context) {
        return new PeopleOptionsItemData(context);
    }

    @Override
    public PeopleAndOptionsData createPeopleAndOptionsData(final String conversationId,
            final Context context, final PeopleAndOptionsDataListener listener) {
        return mPeopleAndOptionsData;
    }

    @Override
    public VCardContactItemData createVCardContactItemData(final Context context,
            final MessagePartData data) {
        return new VCardContactItemData(context, data);
    }

    @Override
    public VCardContactItemData createVCardContactItemData(final Context context,
            final Uri vCardUri) {
        return new VCardContactItemData(context, vCardUri);
    }

    @Override
    public ParticipantListItemData createParticipantListItemData(
            final ParticipantData participant) {
        return new ParticipantListItemData(participant);
    }

    @Override
    public SubscriptionListData createSubscriptonListData(Context context) {
        return new SubscriptionListData(context);
    }

    @Override
    public SettingsData createSettingsData(Context context, SettingsDataListener listener) {
        return mSettingsData;
    }

    @Override
    public DraftMessageData createDraftMessageData(String conversationId) {
        return mDraftMessageData;
    }

    @Override
    public ActionService getActionService() {
        return mActionService;
    }

    @Override
    public ConnectivityUtil getConnectivityUtil() {
        return mConnectivityUtil;
    }

    @Override
    public SyncManager getSyncManager() {
        return mSyncManager;
    }

    @Override
    public DatabaseWrapper getDatabase() {
        // Note this will crash unless the application context is redirected...
        // This is by design so that tests do not inadvertently use the real database
        return mDatabaseHelper.getDatabase();
    }

    @Override
    void onCreateTables(final SQLiteDatabase db) {
        TestDataFactory.createTestData(db);
    }

    @Override
    public void onActivityResume() {
    }

    @Override
    public void onApplicationCreated() {
    }

    @Override
    public BlockedParticipantsData createBlockedParticipantsData(Context context,
            BlockedParticipantsDataListener listener) {
        return new BlockedParticipantsData(context, listener);
    }
}
