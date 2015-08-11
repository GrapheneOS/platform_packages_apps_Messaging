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
package com.android.messaging.ui;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.android.messaging.R;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.BlockedParticipantsData;
import com.android.messaging.datamodel.data.BlockedParticipantsData.BlockedParticipantsDataListener;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.util.Assert;

/**
 * Show a list of currently blocked participants.
 */
public class BlockedParticipantsFragment extends Fragment
        implements BlockedParticipantsDataListener {
    private ListView mListView;
    private BlockedParticipantListAdapter mAdapter;
    private final Binding<BlockedParticipantsData> mBinding =
            BindingBase.createBinding(this);

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view =
                inflater.inflate(R.layout.blocked_participants_fragment, container, false);
        mListView = (ListView) view.findViewById(android.R.id.list);
        mAdapter = new BlockedParticipantListAdapter(getActivity(), null);
        mListView.setAdapter(mAdapter);
        mBinding.bind(DataModel.get().createBlockedParticipantsData(getActivity(), this));
        mBinding.getData().init(getLoaderManager(), mBinding);
        return view;
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinding.unbind();
    }

    /**
     * An adapter to display ParticipantListItemView based on ParticipantData.
     */
    private class BlockedParticipantListAdapter extends CursorAdapter {
        public BlockedParticipantListAdapter(final Context context, final Cursor cursor) {
            super(context, cursor, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context)
                    .inflate(R.layout.blocked_participant_list_item_view, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Assert.isTrue(view instanceof BlockedParticipantListItemView);
            ((BlockedParticipantListItemView) view).bind(
                    mBinding.getData().createParticipantListItemData(cursor));
        }
    }

    @Override
    public void onBlockedParticipantsCursorUpdated(Cursor cursor) {
        mAdapter.swapCursor(cursor);
    }
}
