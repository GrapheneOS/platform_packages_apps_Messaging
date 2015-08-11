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
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.messaging.R;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.datamodel.data.ConversationListData.ConversationListDataListener;
import com.android.messaging.ui.ListEmptyView;
import com.android.messaging.datamodel.DataModel;

/**
 * Allow user to pick conversation to which an incoming attachment will be shared.
 */
public class ShareIntentFragment extends DialogFragment implements ConversationListDataListener,
        ShareIntentAdapter.HostInterface {
    public static final String HIDE_NEW_CONVERSATION_BUTTON_KEY = "hide_conv_button_key";

    public interface HostInterface {
        public void onConversationClick(final ConversationListItemData conversationListItemData);
        public void onCreateConversationClick();
    }

    private final Binding<ConversationListData> mListBinding = BindingBase.createBinding(this);
    private RecyclerView mRecyclerView;
    private ListEmptyView mEmptyListMessageView;
    private ShareIntentAdapter mAdapter;
    private HostInterface mHost;
    private boolean mDismissed;

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public Dialog onCreateDialog(final Bundle bundle) {
        final Activity activity = getActivity();
        final LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.share_intent_conversation_list_view, null);
        mEmptyListMessageView = (ListEmptyView) view.findViewById(R.id.no_conversations_view);
        mEmptyListMessageView.setImageHint(R.drawable.ic_oobe_conv_list);
        // The default behavior for default layout param generation by LinearLayoutManager is to
        // provide width and height of WRAP_CONTENT, but this is not desirable for
        // ShareIntentFragment; the view in each row should be a width of MATCH_PARENT so that
        // the entire row is tappable.
        final LinearLayoutManager manager = new LinearLayoutManager(activity) {
            @Override
            public RecyclerView.LayoutParams generateDefaultLayoutParams() {
                return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        };
        mListBinding.getData().init(getLoaderManager(), mListBinding);
        mAdapter = new ShareIntentAdapter(activity, null, this);
        mRecyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        mRecyclerView.setLayoutManager(manager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        final Builder dialogBuilder = new AlertDialog.Builder(activity)
                .setView(view)
                .setTitle(R.string.share_intent_activity_label);

        final Bundle arguments = getArguments();
        if (arguments == null || !arguments.getBoolean(HIDE_NEW_CONVERSATION_BUTTON_KEY)) {
            dialogBuilder.setPositiveButton(R.string.share_new_message, new OnClickListener() {
                        @Override
                    public void onClick(DialogInterface dialog, int which) {
                            mDismissed = true;
                            mHost.onCreateConversationClick();
                    }
                });
        }
        return dialogBuilder.setNegativeButton(R.string.share_cancel, null)
                .create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (!mDismissed) {
            final Activity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
        }
    }

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mListBinding.unbind();
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        if (activity instanceof HostInterface) {
            mHost = (HostInterface) activity;
        }
        mListBinding.bind(DataModel.get().createConversationListData(activity, this, false));
    }

    @Override
    public void onConversationListCursorUpdated(final ConversationListData data,
            final Cursor cursor) {
        mListBinding.ensureBound(data);
        mAdapter.swapCursor(cursor);
        updateEmptyListUi(cursor == null || cursor.getCount() == 0);
    }

    /**
     * {@inheritDoc} from SharIntentItemView.HostInterface
     */
    @Override
    public void onConversationClicked(final ConversationListItemData conversationListItemData) {
        mHost.onConversationClick(conversationListItemData);
    }

    // Show and hide empty list UI as needed with appropriate text based on view specifics
    private void updateEmptyListUi(final boolean isEmpty) {
        if (isEmpty) {
            mEmptyListMessageView.setTextHint(R.string.conversation_list_empty_text);
            mEmptyListMessageView.setVisibility(View.VISIBLE);
        } else {
            mEmptyListMessageView.setVisibility(View.GONE);
        }
    }

    @Override
    public void setBlockedParticipantsAvailable(boolean blockedAvailable) {
    }
}
