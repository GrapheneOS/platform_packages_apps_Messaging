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
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.action.Action;
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
import com.android.messaging.datamodel.data.VCardContactItemData;
import com.android.messaging.util.Assert.DoesNotRunOnMainThread;
import com.android.messaging.util.ConnectivityUtil;

public abstract class DataModel {
    private String mFocusedConversation;
    private boolean mConversationListScrolledToNewestConversation;

    public static DataModel get() {
        return Factory.get().getDataModel();
    }

    public static final void startActionService(final Action action) {
        get().getActionService().startAction(action);
    }

    public static final void scheduleAction(final Action action,
            final int code, final long delayMs) {
        get().getActionService().scheduleAction(action, code, delayMs);
    }

    public abstract ConversationListData createConversationListData(final Context context,
            final ConversationListDataListener listener, final boolean archivedMode);

    public abstract ConversationData createConversationData(final Context context,
            final ConversationDataListener listener, final String conversationId);

    public abstract ContactListItemData createContactListItemData();

    public abstract ContactPickerData createContactPickerData(final Context context,
            final ContactPickerDataListener listener);

    public abstract MediaPickerData createMediaPickerData(final Context context);

    public abstract GalleryGridItemData createGalleryGridItemData();

    public abstract LaunchConversationData createLaunchConversationData(
            LaunchConversationDataListener listener);

    public abstract PeopleOptionsItemData createPeopleOptionsItemData(final Context context);

    public abstract PeopleAndOptionsData createPeopleAndOptionsData(final String conversationId,
            final Context context, final PeopleAndOptionsDataListener listener);

    public abstract VCardContactItemData createVCardContactItemData(final Context context,
            final MessagePartData data);

    public abstract VCardContactItemData createVCardContactItemData(final Context context,
            final Uri vCardUri);

    public abstract ParticipantListItemData createParticipantListItemData(
            final ParticipantData participant);

    public abstract BlockedParticipantsData createBlockedParticipantsData(Context context,
            BlockedParticipantsDataListener listener);

    public abstract SubscriptionListData createSubscriptonListData(Context context);

    public abstract SettingsData createSettingsData(Context context, SettingsDataListener listener);

    public abstract DraftMessageData createDraftMessageData(String conversationId);

    public abstract ActionService getActionService();

    public abstract BackgroundWorker getBackgroundWorkerForActionService();

    @DoesNotRunOnMainThread
    public abstract DatabaseWrapper getDatabase();

    // Allow DataModel to coordinate with activity lifetime events.
    public abstract void onActivityResume();

    abstract void onCreateTables(final SQLiteDatabase db);

    public void setFocusedConversation(final String conversationId) {
        mFocusedConversation = conversationId;
    }

    public boolean isFocusedConversation(final String conversationId) {
        return !TextUtils.isEmpty(mFocusedConversation)
                && TextUtils.equals(mFocusedConversation, conversationId);
    }

    public void setConversationListScrolledToNewestConversation(
            final boolean scrolledToNewestConversation) {
        mConversationListScrolledToNewestConversation = scrolledToNewestConversation;
    }

    public boolean isConversationListScrolledToNewestConversation() {
        return mConversationListScrolledToNewestConversation;
    }

    /**
     * If a new message is received in the specified conversation, will the user be able to
     * observe it in some UI within the app?
     * @param conversationId conversation with the new incoming message
     */
    public boolean isNewMessageObservable(final String conversationId) {
        return isConversationListScrolledToNewestConversation()
                || isFocusedConversation(conversationId);
    }

    public abstract void onApplicationCreated();

    public abstract ConnectivityUtil getConnectivityUtil();

    public abstract SyncManager getSyncManager();
}
