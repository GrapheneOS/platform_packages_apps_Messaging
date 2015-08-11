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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;

import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.PersonItemData;
import com.android.messaging.datamodel.data.VCardContactItemData;
import com.android.messaging.datamodel.data.PersonItemData.PersonItemDataListener;
import com.android.messaging.util.Assert;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.UriUtil;

/**
 * A fragment that shows the content of a VCard that contains one or more contacts.
 */
public class VCardDetailFragment extends Fragment implements PersonItemDataListener {
    private final Binding<VCardContactItemData> mBinding =
            BindingBase.createBinding(this);
    private ExpandableListView mListView;
    private VCardDetailAdapter mAdapter;
    private Uri mVCardUri;

    /**
     * We need to persist the VCard in the scratch directory before letting the user view it.
     * We save this Uri locally, so that if the user cancels the action and re-perform the add
     * to contacts action we don't have to persist it again.
     */
    private Uri mScratchSpaceUri;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        Assert.notNull(mVCardUri);
        final View view = inflater.inflate(R.layout.vcard_detail_fragment, container, false);
        mListView = (ExpandableListView) view.findViewById(R.id.list);
        mListView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(final View v, final int left, final int top, final int right,
                    final int bottom, final int oldLeft, final int oldTop, final int oldRight,
                    final int oldBottom) {
                mListView.setIndicatorBounds(mListView.getWidth() - getResources()
                        .getDimensionPixelSize(R.dimen.vcard_detail_group_indicator_width),
                        mListView.getWidth());
            }
        });
        mListView.setOnChildClickListener(new OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView expandableListView, View clickedView,
                    int groupPosition, int childPosition, long childId) {
                if (!(clickedView instanceof PersonItemView)) {
                    return false;
                }
                final Intent intent = ((PersonItemView) clickedView).getClickIntent();
                if (intent != null) {
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        return false;
                    }
                    return true;
                }
                return false;
            }
        });
        mBinding.bind(DataModel.get().createVCardContactItemData(getActivity(), mVCardUri));
        mBinding.getData().setListener(this);
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBinding.isBound()) {
            mBinding.unbind();
        }
        mListView.setAdapter((ExpandableListAdapter) null);
    }

    private boolean shouldShowAddToContactsItem() {
        return mBinding.isBound() && mBinding.getData().hasValidVCard();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.vcard_detail_fragment_menu, menu);
        final MenuItem addToContactsItem = menu.findItem(R.id.action_add_contact);
        addToContactsItem.setVisible(shouldShowAddToContactsItem());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_contact:
                mBinding.ensureBound();
                final Uri vCardUri = mBinding.getData().getVCardUri();

                // We have to do things in the background in case we need to copy the vcard data.
                new SafeAsyncTask<Void, Void, Uri>() {
                    @Override
                    protected Uri doInBackgroundTimed(final Void... params) {
                        // We can't delete the persisted vCard file because we don't know when to
                        // delete it, since the app that uses it (contacts, dialer) may start or
                        // shut down at any point. Therefore, we rely on the system to clean up
                        // the cache directory for us.
                        return mScratchSpaceUri != null ? mScratchSpaceUri :
                            UriUtil.persistContentToScratchSpace(vCardUri);
                    }

                    @Override
                    protected void onPostExecute(final Uri result) {
                        if (result != null) {
                            mScratchSpaceUri = result;
                            if (getActivity() != null) {
                                MediaScratchFileProvider.addUriToDisplayNameEntry(
                                        result, mBinding.getData().getDisplayName());
                                UIIntents.get().launchSaveVCardToContactsActivity(getActivity(),
                                        result);
                            }
                        }
                    }
                }.executeOnThreadPool();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setVCardUri(final Uri vCardUri) {
        Assert.isTrue(!mBinding.isBound());
        mVCardUri = vCardUri;
    }

    @Override
    public void onPersonDataUpdated(final PersonItemData data) {
        Assert.isTrue(data instanceof VCardContactItemData);
        mBinding.ensureBound();
        final VCardContactItemData vCardData = (VCardContactItemData) data;
        Assert.isTrue(vCardData.hasValidVCard());
        mAdapter = new VCardDetailAdapter(getActivity(), vCardData.getVCardResource().getVCards());
        mListView.setAdapter(mAdapter);

        // Expand the contact card if there's only one contact.
        if (mAdapter.getGroupCount() == 1) {
            mListView.expandGroup(0);
        }
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onPersonDataFailed(final PersonItemData data, final Exception exception) {
        mBinding.ensureBound();
        UiUtils.showToastAtBottom(R.string.failed_loading_vcard);
        getActivity().finish();
    }
}
