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

import android.app.Fragment;
import android.os.Bundle;

import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.ui.BaseBugleActivity;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.conversationlist.ConversationListFragment.ConversationListFragmentHost;
import com.android.messaging.util.Assert;

/**
 * An activity that lets the user forward a SMS/MMS message by picking from a conversation in the
 * conversation list.
 */
public class ForwardMessageActivity extends BaseBugleActivity
    implements ConversationListFragmentHost {
    private MessageData mDraftMessage;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ConversationListFragment fragment =
                ConversationListFragment.createForwardMessageConversationListFragment();
        getFragmentManager().beginTransaction().add(android.R.id.content, fragment).commit();
        mDraftMessage = getIntent().getParcelableExtra(UIIntents.UI_INTENT_EXTRA_DRAFT_DATA);
    }

    @Override
    public void onAttachFragment(final Fragment fragment) {
        Assert.isTrue(fragment instanceof ConversationListFragment);
        final ConversationListFragment clf = (ConversationListFragment) fragment;
        clf.setHost(this);
    }

    @Override
    public void onConversationClick(final ConversationListData listData,
                                    final ConversationListItemData conversationListItemData,
            final boolean isLongClick, final ConversationListItemView converastionView) {
        UIIntents.get().launchConversationActivity(
                this, conversationListItemData.getConversationId(), mDraftMessage);
    }

    @Override
    public void onCreateConversationClick() {
        UIIntents.get().launchCreateNewConversationActivity(this, mDraftMessage);
    }

    @Override
    public boolean isConversationSelected(final String conversationId) {
        return false;
    }

    @Override
    public boolean isSwipeAnimatable() {
        return false;
    }

    @Override
    public boolean isSelectionMode() {
        return false;
    }
}
