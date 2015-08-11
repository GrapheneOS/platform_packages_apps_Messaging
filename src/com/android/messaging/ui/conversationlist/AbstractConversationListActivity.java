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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;

import com.android.messaging.R;
import com.android.messaging.datamodel.action.DeleteConversationAction;
import com.android.messaging.datamodel.action.UpdateConversationArchiveStatusAction;
import com.android.messaging.datamodel.action.UpdateConversationOptionsAction;
import com.android.messaging.datamodel.action.UpdateDestinationBlockedAction;
import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.SnackBar;
import com.android.messaging.ui.SnackBarInteraction;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.contact.AddContactsConfirmationDialog;
import com.android.messaging.ui.conversationlist.ConversationListFragment.ConversationListFragmentHost;
import com.android.messaging.ui.conversationlist.MultiSelectActionModeCallback.SelectedConversation;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.Trace;
import com.android.messaging.util.UiUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Base class for many Conversation List activities. This will handle the common actions of multi
 * select and common launching of intents.
 */
public abstract class AbstractConversationListActivity  extends BugleActionBarActivity
    implements ConversationListFragmentHost, MultiSelectActionModeCallback.Listener {

    private static final int REQUEST_SET_DEFAULT_SMS_APP = 1;

    protected ConversationListFragment mConversationListFragment;

    @Override
    public void onAttachFragment(final Fragment fragment) {
        Trace.beginSection("AbstractConversationListActivity.onAttachFragment");
        // Fragment could be debug dialog
        if (fragment instanceof ConversationListFragment) {
            mConversationListFragment = (ConversationListFragment) fragment;
            mConversationListFragment.setHost(this);
        }
        Trace.endSection();
    }

    @Override
    public void onBackPressed() {
        // If action mode is active dismiss it
        if (getActionMode() != null) {
            dismissActionMode();
            return;
        }
        super.onBackPressed();
    }

    protected void startMultiSelectActionMode() {
        startActionMode(new MultiSelectActionModeCallback(this));
    }

    protected void exitMultiSelectState() {
        mConversationListFragment.showFab();
        dismissActionMode();
        mConversationListFragment.updateUi();
    }

    protected boolean isInConversationListSelectMode() {
        return getActionModeCallback() instanceof MultiSelectActionModeCallback;
    }

    @Override
    public boolean isSelectionMode() {
        return isInConversationListSelectMode();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    }

    @Override
    public void onActionBarDelete(final Collection<SelectedConversation> conversations) {
        if (!PhoneUtils.getDefault().isDefaultSmsApp()) {
            // TODO: figure out a good way to combine this with the implementation in
            // ConversationFragment doing similar things
            final Activity activity = this;
            UiUtils.showSnackBarWithCustomAction(this,
                    getWindow().getDecorView().getRootView(),
                    getString(R.string.requires_default_sms_app),
                    SnackBar.Action.createCustomAction(new Runnable() {
                            @Override
                            public void run() {
                                final Intent intent =
                                        UIIntents.get().getChangeDefaultSmsAppIntent(activity);
                                startActivityForResult(intent, REQUEST_SET_DEFAULT_SMS_APP);
                            }
                        },
                        getString(R.string.requires_default_sms_change_button)),
                    null /* interactions */,
                    null /* placement */);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getResources().getQuantityString(
                        R.plurals.delete_conversations_confirmation_dialog_title,
                        conversations.size()))
                .setPositiveButton(R.string.delete_conversation_confirmation_button,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog,
                                    final int button) {
                                for (final SelectedConversation conversation : conversations) {
                                    DeleteConversationAction.deleteConversation(
                                            conversation.conversationId,
                                            conversation.timestamp);
                                }
                                exitMultiSelectState();
                            }
                })
                .setNegativeButton(R.string.delete_conversation_decline_button, null)
                .show();
    }

    @Override
    public void onActionBarArchive(final Iterable<SelectedConversation> conversations,
            final boolean isToArchive) {
        final ArrayList<String> conversationIds = new ArrayList<String>();
        for (final SelectedConversation conversation : conversations) {
            final String conversationId = conversation.conversationId;
            conversationIds.add(conversationId);
            if (isToArchive) {
                UpdateConversationArchiveStatusAction.archiveConversation(conversationId);
            } else {
                UpdateConversationArchiveStatusAction.unarchiveConversation(conversationId);
            }
        }

        final Runnable undoRunnable = new Runnable() {
            @Override
            public void run() {
                for (final String conversationId : conversationIds) {
                    if (isToArchive) {
                        UpdateConversationArchiveStatusAction.unarchiveConversation(conversationId);
                    } else {
                        UpdateConversationArchiveStatusAction.archiveConversation(conversationId);
                    }
                }
            }
        };

        final int textId =
                isToArchive ? R.string.archived_toast_message : R.string.unarchived_toast_message;
        final String message = getResources().getString(textId, conversationIds.size());
        UiUtils.showSnackBar(this, findViewById(android.R.id.list), message, undoRunnable,
                SnackBar.Action.SNACK_BAR_UNDO,
                mConversationListFragment.getSnackBarInteractions());
        exitMultiSelectState();
    }

    @Override
    public void onActionBarNotification(final Iterable<SelectedConversation> conversations,
            final boolean isNotificationOn) {
        for (final SelectedConversation conversation : conversations) {
            UpdateConversationOptionsAction.enableConversationNotifications(
                    conversation.conversationId, isNotificationOn);
        }

        final int textId = isNotificationOn ?
                R.string.notification_on_toast_message : R.string.notification_off_toast_message;
        final String message = getResources().getString(textId, 1);
        UiUtils.showSnackBar(this, findViewById(android.R.id.list), message,
            null /* undoRunnable */,
            SnackBar.Action.SNACK_BAR_UNDO, mConversationListFragment.getSnackBarInteractions());
        exitMultiSelectState();
    }

    @Override
    public void onActionBarAddContact(final SelectedConversation conversation) {
        final Uri avatarUri;
        if (conversation.icon != null) {
            avatarUri = Uri.parse(conversation.icon);
        } else {
            avatarUri = null;
        }
        final AddContactsConfirmationDialog dialog = new AddContactsConfirmationDialog(
                this, avatarUri, conversation.otherParticipantNormalizedDestination);
        dialog.show();
        exitMultiSelectState();
    }

    @Override
    public void onActionBarBlock(final SelectedConversation conversation) {
        final Resources res = getResources();
        new AlertDialog.Builder(this)
                .setTitle(res.getString(R.string.block_confirmation_title,
                        conversation.otherParticipantNormalizedDestination))
                .setMessage(res.getString(R.string.block_confirmation_message))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface arg0, final int arg1) {
                        final Context context = AbstractConversationListActivity.this;
                        final View listView = findViewById(android.R.id.list);
                        final List<SnackBarInteraction> interactions =
                                mConversationListFragment.getSnackBarInteractions();
                        final UpdateDestinationBlockedAction.UpdateDestinationBlockedActionListener
                                undoListener =
                                        new UpdateDestinationBlockedActionSnackBar(
                                                context, listView, null /* undoRunnable */,
                                                interactions);
                        final Runnable undoRunnable = new Runnable() {
                            @Override
                            public void run() {
                                UpdateDestinationBlockedAction.updateDestinationBlocked(
                                        conversation.otherParticipantNormalizedDestination, false,
                                        conversation.conversationId,
                                        undoListener);
                            }
                        };
                        final UpdateDestinationBlockedAction.UpdateDestinationBlockedActionListener
                              listener = new UpdateDestinationBlockedActionSnackBar(
                                      context, listView, undoRunnable, interactions);
                        UpdateDestinationBlockedAction.updateDestinationBlocked(
                                conversation.otherParticipantNormalizedDestination, true,
                                conversation.conversationId,
                                listener);
                        exitMultiSelectState();
                    }
                })
                .create()
                .show();
    }

    @Override
    public void onConversationClick(final ConversationListData listData,
                                    final ConversationListItemData conversationListItemData,
                                    final boolean isLongClick,
                                    final ConversationListItemView conversationView) {
        if (isLongClick && !isInConversationListSelectMode()) {
            startMultiSelectActionMode();
        }

        if (isInConversationListSelectMode()) {
            final MultiSelectActionModeCallback multiSelectActionMode =
                    (MultiSelectActionModeCallback) getActionModeCallback();
            multiSelectActionMode.toggleSelect(listData, conversationListItemData);
            mConversationListFragment.updateUi();
        } else {
            final String conversationId = conversationListItemData.getConversationId();
            Bundle sceneTransitionAnimationOptions = null;
            boolean hasCustomTransitions = false;

            UIIntents.get().launchConversationActivity(
                    this, conversationId, null,
                    sceneTransitionAnimationOptions,
                    hasCustomTransitions);
        }
    }

    @Override
    public void onCreateConversationClick() {
        UIIntents.get().launchCreateNewConversationActivity(this, null);
    }


    @Override
    public boolean isConversationSelected(final String conversationId) {
        return isInConversationListSelectMode() &&
                ((MultiSelectActionModeCallback) getActionModeCallback()).isSelected(
                        conversationId);
    }

    public void onActionBarDebug() {
        DebugUtils.showDebugOptions(this);
    }

    private static class UpdateDestinationBlockedActionSnackBar
            implements UpdateDestinationBlockedAction.UpdateDestinationBlockedActionListener {
        private final Context mContext;
        private final View mParentView;
        private final Runnable mUndoRunnable;
        private final List<SnackBarInteraction> mInteractions;

        UpdateDestinationBlockedActionSnackBar(final Context context,
                @NonNull final View parentView, @Nullable final Runnable undoRunnable,
                @Nullable List<SnackBarInteraction> interactions) {
            mContext = context;
            mParentView = parentView;
            mUndoRunnable = undoRunnable;
            mInteractions = interactions;
        }

        @Override
        public void onUpdateDestinationBlockedAction(
            final UpdateDestinationBlockedAction action,
            final boolean success, final boolean block,
            final String destination) {
            if (success) {
                final int messageId = block ? R.string.blocked_toast_message
                        : R.string.unblocked_toast_message;
                final String message = mContext.getResources().getString(messageId, 1);
                UiUtils.showSnackBar(mContext, mParentView, message, mUndoRunnable,
                        SnackBar.Action.SNACK_BAR_UNDO, mInteractions);
            }
        }
    }
}
