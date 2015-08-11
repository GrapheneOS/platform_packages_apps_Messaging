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
package com.android.messaging.ui.conversationlist;

import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuItem;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.util.Assert;

import java.util.Collection;
import java.util.HashSet;

public class MultiSelectActionModeCallback implements Callback {
    private HashSet<String> mBlockedSet;

    public interface Listener {
        void onActionBarDelete(Collection<SelectedConversation> conversations);
        void onActionBarArchive(Iterable<SelectedConversation> conversations,
                boolean isToArchive);
        void onActionBarNotification(Iterable<SelectedConversation> conversations,
                boolean isNotificationOn);
        void onActionBarAddContact(final SelectedConversation conversation);
        void onActionBarBlock(final SelectedConversation conversation);
        void onActionBarHome();
    }

    static class SelectedConversation {
        public final String conversationId;
        public final long timestamp;
        public final String icon;
        public final String otherParticipantNormalizedDestination;
        public final CharSequence participantLookupKey;
        public final boolean isGroup;
        public final boolean isArchived;
        public final boolean notificationEnabled;
        public SelectedConversation(ConversationListItemData data) {
            conversationId = data.getConversationId();
            timestamp = data.getTimestamp();
            icon = data.getIcon();
            otherParticipantNormalizedDestination = data.getOtherParticipantNormalizedDestination();
            participantLookupKey = data.getParticipantLookupKey();
            isGroup = data.getIsGroup();
            isArchived = data.getIsArchived();
            notificationEnabled = data.getNotificationEnabled();
        }
    }

    private final ArrayMap<String, SelectedConversation> mSelectedConversations;

    private Listener mListener;
    private MenuItem mArchiveMenuItem;
    private MenuItem mUnarchiveMenuItem;
    private MenuItem mAddContactMenuItem;
    private MenuItem mBlockMenuItem;
    private MenuItem mNotificationOnMenuItem;
    private MenuItem mNotificationOffMenuItem;
    private boolean mHasInflated;

    public MultiSelectActionModeCallback(final Listener listener) {
        mListener = listener;
        mSelectedConversations = new ArrayMap<>();

    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        actionMode.getMenuInflater().inflate(R.menu.conversation_list_fragment_select_menu, menu);
        mArchiveMenuItem = menu.findItem(R.id.action_archive);
        mUnarchiveMenuItem = menu.findItem(R.id.action_unarchive);
        mAddContactMenuItem = menu.findItem(R.id.action_add_contact);
        mBlockMenuItem = menu.findItem(R.id.action_block);
        mNotificationOffMenuItem = menu.findItem(R.id.action_notification_off);
        mNotificationOnMenuItem = menu.findItem(R.id.action_notification_on);
        mHasInflated = true;
        updateActionIconsVisiblity();
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        switch(menuItem.getItemId()) {
            case R.id.action_delete:
                mListener.onActionBarDelete(mSelectedConversations.values());
                return true;
            case R.id.action_archive:
                mListener.onActionBarArchive(mSelectedConversations.values(), true);
                return true;
            case R.id.action_unarchive:
                mListener.onActionBarArchive(mSelectedConversations.values(), false);
                return true;
            case R.id.action_notification_off:
                mListener.onActionBarNotification(mSelectedConversations.values(), false);
                return true;
            case R.id.action_notification_on:
                mListener.onActionBarNotification(mSelectedConversations.values(), true);
                return true;
            case R.id.action_add_contact:
                Assert.isTrue(mSelectedConversations.size() == 1);
                mListener.onActionBarAddContact(mSelectedConversations.valueAt(0));
                return true;
            case R.id.action_block:
                Assert.isTrue(mSelectedConversations.size() == 1);
                mListener.onActionBarBlock(mSelectedConversations.valueAt(0));
                return true;
            case android.R.id.home:
                mListener.onActionBarHome();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mListener = null;
        mSelectedConversations.clear();
        mHasInflated = false;
    }

    public void toggleSelect(final ConversationListData listData,
                             final ConversationListItemData conversationListItemData) {
        Assert.notNull(conversationListItemData);
        mBlockedSet = listData.getBlockedParticipants();
        final String id = conversationListItemData.getConversationId();
        if (mSelectedConversations.containsKey(id)) {
            mSelectedConversations.remove(id);
        } else {
            mSelectedConversations.put(id, new SelectedConversation(conversationListItemData));
        }

        if (mSelectedConversations.isEmpty()) {
            mListener.onActionBarHome();
        } else {
            updateActionIconsVisiblity();
        }
    }

    public boolean isSelected(final String selectedId) {
        return mSelectedConversations.containsKey(selectedId);
    }

    private void updateActionIconsVisiblity() {
        if (!mHasInflated) {
            return;
        }

        if (mSelectedConversations.size() == 1) {
            final SelectedConversation conversation = mSelectedConversations.valueAt(0);
            // The look up key is a key given to us by contacts app, so if we have a look up key,
            // we know that the participant is already in contacts.
            final boolean isInContacts = !TextUtils.isEmpty(conversation.participantLookupKey);
            mAddContactMenuItem.setVisible(!conversation.isGroup && !isInContacts);
            // ParticipantNormalizedDestination is always null for group conversations.
            final String otherParticipant = conversation.otherParticipantNormalizedDestination;
            mBlockMenuItem.setVisible(otherParticipant != null
                    && !mBlockedSet.contains(otherParticipant));
        } else {
            mBlockMenuItem.setVisible(false);
            mAddContactMenuItem.setVisible(false);
        }

        boolean hasCurrentlyArchived = false;
        boolean hasCurrentlyUnarchived = false;
        boolean hasCurrentlyOnNotification = false;
        boolean hasCurrentlyOffNotification = false;
        final Iterable<SelectedConversation> conversations = mSelectedConversations.values();
        for (final SelectedConversation conversation : conversations) {
            if (conversation.notificationEnabled) {
                hasCurrentlyOnNotification = true;
            } else {
                hasCurrentlyOffNotification = true;
            }

            if (conversation.isArchived) {
                hasCurrentlyArchived = true;
            } else {
                hasCurrentlyUnarchived = true;
            }

            // If we found at least one of each example we don't need to keep looping.
            if (hasCurrentlyOffNotification && hasCurrentlyOnNotification &&
                    hasCurrentlyArchived && hasCurrentlyUnarchived) {
                break;
            }
        }
        // If we have notification off conversations we show on button, if we have notification on
        // conversation we show off button. We can show both if we have a mixture.
        mNotificationOffMenuItem.setVisible(hasCurrentlyOnNotification);
        mNotificationOnMenuItem.setVisible(hasCurrentlyOffNotification);

        mArchiveMenuItem.setVisible(hasCurrentlyUnarchived);
        mUnarchiveMenuItem.setVisible(hasCurrentlyArchived);
    }
}
