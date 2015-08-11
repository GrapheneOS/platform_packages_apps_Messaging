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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.PersonItemData;
import com.android.messaging.ui.CursorRecyclerAdapter;
import com.android.messaging.ui.PersonItemView;
import com.android.messaging.ui.PersonItemView.PersonItemViewListener;
import com.android.messaging.util.PhoneUtils;

/**
 * Turn conversation rows into PeopleItemViews
 */
public class ShareIntentAdapter
        extends CursorRecyclerAdapter<ShareIntentAdapter.ShareIntentViewHolder> {

    public interface HostInterface {
        void onConversationClicked(final ConversationListItemData conversationListItemData);
    }

    private final HostInterface mHostInterface;

    public ShareIntentAdapter(final Context context, final Cursor cursor,
            final HostInterface hostInterface) {
        super(context, cursor, 0);
        mHostInterface = hostInterface;
        setHasStableIds(true);
    }

    @Override
    public void bindViewHolder(final ShareIntentViewHolder holder, final Context context,
            final Cursor cursor) {
        holder.bind(cursor);
    }

    @Override
    public ShareIntentViewHolder createViewHolder(final Context context,
            final ViewGroup parent, final int viewType) {
        final PersonItemView itemView = (PersonItemView) LayoutInflater.from(context).inflate(
                R.layout.people_list_item_view, null);
        return new ShareIntentViewHolder(itemView);
    }

    /**
     * Holds a PersonItemView and keeps it synced with a ConversationListItemData.
     */
    public class ShareIntentViewHolder extends RecyclerView.ViewHolder implements
            PersonItemView.PersonItemViewListener {
        private final ConversationListItemData mData = new ConversationListItemData();
        private final PersonItemData mItemData = new PersonItemData() {
            @Override
            public Uri getAvatarUri() {
                return mData.getIcon() == null ? null : Uri.parse(mData.getIcon());
            }

            @Override
            public String getDisplayName() {
                return mData.getName();
            }

            @Override
            public String getDetails() {
                final String conversationName = mData.getName();
                final String conversationPhone = PhoneUtils.getDefault().formatForDisplay(
                        mData.getOtherParticipantNormalizedDestination());
                if (conversationPhone == null || conversationPhone.equals(conversationName)) {
                    return null;
                }
                return conversationPhone;
            }

            @Override
            public Intent getClickIntent() {
                return null;
            }

            @Override
            public long getContactId() {
                return ParticipantData.PARTICIPANT_CONTACT_ID_NOT_RESOLVED;
            }

            @Override
            public String getLookupKey() {
                return null;
            }

            @Override
            public String getNormalizedDestination() {
                return null;
            }
        };

        public ShareIntentViewHolder(final PersonItemView itemView) {
            super(itemView);
            itemView.setListener(this);
        }

        public void bind(Cursor cursor) {
            mData.bind(cursor);
            ((PersonItemView) itemView).bind(mItemData);
        }

        @Override
        public void onPersonClicked(PersonItemData data) {
            mHostInterface.onConversationClicked(mData);
        }

        @Override
        public boolean onPersonLongClicked(PersonItemData data) {
            return false;
        }
    }
}
