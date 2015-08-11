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
package com.android.messaging.ui.attachmentchooser;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.DraftMessageData.DraftMessageDataListener;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.attachmentchooser.AttachmentGridView.AttachmentGridHost;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

public class AttachmentChooserFragment extends Fragment implements DraftMessageDataListener,
        AttachmentGridHost {
    public interface AttachmentChooserFragmentHost {
        void onConfirmSelection();
    }

    private AttachmentGridView mAttachmentGridView;
    private AttachmentGridAdapter mAdapter;
    private AttachmentChooserFragmentHost mHost;

    @VisibleForTesting
    Binding<DraftMessageData> mBinding = BindingBase.createBinding(this);

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.attachment_chooser_fragment, container, false);
        mAttachmentGridView = (AttachmentGridView) view.findViewById(R.id.grid);
        mAdapter = new AttachmentGridAdapter(getActivity());
        mAttachmentGridView.setAdapter(mAdapter);
        mAttachmentGridView.setHost(this);
        setHasOptionsMenu(true);
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinding.unbind();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.attachment_chooser_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_confirm_selection:
                confirmSelection();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @VisibleForTesting
    void confirmSelection() {
        if (mBinding.isBound()) {
            mBinding.getData().removeExistingAttachments(
                    mAttachmentGridView.getUnselectedAttachments());
            mBinding.getData().saveToStorage(mBinding);
            mHost.onConfirmSelection();
        }
    }

    public void setConversationId(final String conversationId) {
        mBinding.bind(DataModel.get().createDraftMessageData(conversationId));
        mBinding.getData().addListener(this);
        mBinding.getData().loadFromStorage(mBinding, null, false);
    }

    public void setHost(final AttachmentChooserFragmentHost host) {
        mHost = host;
    }

    @Override
    public void onDraftChanged(final DraftMessageData data, final int changeFlags) {
        mBinding.ensureBound(data);
        if ((changeFlags & DraftMessageData.ATTACHMENTS_CHANGED) ==
                DraftMessageData.ATTACHMENTS_CHANGED) {
            mAdapter.onAttachmentsLoaded(data.getReadOnlyAttachments());
        }
    }

    @Override
    public void onDraftAttachmentLimitReached(final DraftMessageData data) {
        // Do nothing since the user is in the process of unselecting attachments.
    }

    @Override
    public void onDraftAttachmentLoadFailed() {
        // Do nothing since the user is in the process of unselecting attachments.
    }

    @Override
    public void displayPhoto(final Rect viewRect, final Uri photoUri) {
        final Uri imagesUri = MessagingContentProvider.buildDraftImagesUri(
                mBinding.getData().getConversationId());
        UIIntents.get().launchFullScreenPhotoViewer(
                getActivity(), photoUri, viewRect, imagesUri);
    }

    @Override
    public void updateSelectionCount(int count) {
        if (getActivity() instanceof BugleActionBarActivity) {
            final ActionBar actionBar = ((BugleActionBarActivity) getActivity())
                    .getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(getResources().getString(
                        R.string.attachment_chooser_selection, count));
            }
        }
    }

    class AttachmentGridAdapter extends ArrayAdapter<MessagePartData> {
        public AttachmentGridAdapter(final Context context) {
            super(context, R.layout.attachment_grid_item_view, new ArrayList<MessagePartData>());
        }

        public void onAttachmentsLoaded(final List<MessagePartData> attachments) {
            clear();
            addAll(attachments);
            notifyDataSetChanged();
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            AttachmentGridItemView itemView;
            final MessagePartData item = getItem(position);
            if (convertView != null && convertView instanceof AttachmentGridItemView) {
                itemView = (AttachmentGridItemView) convertView;
            } else {
                final LayoutInflater inflater = (LayoutInflater) getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                itemView = (AttachmentGridItemView) inflater.inflate(
                        R.layout.attachment_grid_item_view, parent, false);
            }
            itemView.bind(item, mAttachmentGridView);
            return itemView;
        }
    }
}
