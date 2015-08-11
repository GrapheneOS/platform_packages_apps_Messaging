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

package com.android.messaging.ui.contact;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.view.ViewPager;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.widget.ListView;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.action.ActionTestHelpers;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubActionService;
import com.android.messaging.datamodel.action.ActionTestHelpers.StubActionService.StubActionServiceCallLog;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction;
import com.android.messaging.datamodel.data.ContactPickerData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.ui.CustomHeaderViewPagerAdapter;
import com.android.messaging.ui.FragmentTestCase;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.contact.ContactPickerFragment.ContactPickerFragmentHost;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;


/**
 * Unit tests for {@link ContactPickerFragment}.
 */
@LargeTest
public class ContactPickerFragmentTest
    extends FragmentTestCase<ContactPickerFragment> {

    @Mock protected ContactPickerData mMockContactPickerData;
    @Mock protected UIIntents mMockUIIntents;
    @Mock protected ContactPickerFragmentHost mockHost;
    protected FakeDataModel mFakeDataModel;
    private ActionTestHelpers.StubActionService mService;

    public ContactPickerFragmentTest() {
        super(ContactPickerFragment.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final Context context = getInstrumentation().getTargetContext();
        mService = new StubActionService();
        mFakeDataModel = new FakeDataModel(context)
            .withContactPickerData(mMockContactPickerData)
            .withActionService(mService);
        FakeFactory.register(context)
                .withDataModel(mFakeDataModel)
                .withUIIntents(mMockUIIntents);
    }

    /**
     * Helper method to initialize the ContactPickerFragment and its data.
     */
    private ContactPickerFragmentTest initFragment(final int initialMode) {
        Mockito.when(mMockContactPickerData.isBound(Matchers.anyString()))
            .thenReturn(true);

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ContactPickerFragment fragment = getFragment();
                fragment.setHost(mockHost);
                fragment.setContactPickingMode(initialMode, false);

                getActivity().setFragment(fragment);
                Mockito.verify(mMockContactPickerData).init(fragment.getLoaderManager(),
                        fragment.mBinding);
            }
        });
        getInstrumentation().waitForIdleSync();
        return this;
    }

    /**
     * Bind the datamodel with all contacts cursor to populate the all contacts list in the
     * fragment.
     */
    private ContactPickerFragmentTest loadWithAllContactsCursor(final Cursor cursor) {
        Mockito.when(mMockContactPickerData.isBound(Matchers.anyString()))
            .thenReturn(true);

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getFragment().onAllContactsCursorUpdated(cursor);
            }
        });
        getInstrumentation().waitForIdleSync();
        return this;
    }

    /**
     * Bind the datamodel with frequent contacts cursor to populate the contacts list in the
     * fragment.
     */
    private ContactPickerFragmentTest loadWithFrequentContactsCursor(final Cursor cursor) {
        Mockito.when(mMockContactPickerData.isBound(Matchers.anyString()))
            .thenReturn(true);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getFragment().onFrequentContactsCursorUpdated(cursor);
            }
        });
        getInstrumentation().waitForIdleSync();
        return this;
    }

    /**
     * Test the initial state of the fragment before loading data.
     */
    public void testInitialState() {
        initFragment(ContactPickerFragment.MODE_PICK_INITIAL_CONTACT);

        // Make sure that the frequent contacts view is shown by default.
        final ViewPager pager = (ViewPager) getFragment().getView().findViewById(R.id.pager);
        final View currentPagedView = pager.getChildAt(pager.getCurrentItem());
        final View frequentContactsView = ((CustomHeaderViewPagerAdapter) pager.getAdapter())
                .getViewHolder(0).getView(null);
        assertEquals(frequentContactsView, currentPagedView);
    }

    /**
     * Verifies that list view gets correctly populated given a cursor.
     */
    public void testLoadAllContactsList() {
        final Cursor cursor = TestDataFactory.getAllContactListCursor();
        initFragment(ContactPickerFragment.MODE_PICK_INITIAL_CONTACT)
                .loadWithAllContactsCursor(cursor);
        final ListView listView = (ListView) getFragment().getView()
                .findViewById(R.id.all_contacts_list);
        assertEquals(cursor.getCount(), listView.getCount());
    }

    /**
     * Verifies that list view gets correctly populated given a cursor.
     */
    public void testLoadFrequentContactsList() {
        final Cursor cursor = TestDataFactory.getFrequentContactListCursor();
        initFragment(ContactPickerFragment.MODE_PICK_INITIAL_CONTACT)
                .loadWithFrequentContactsCursor(cursor);
        final ListView listView = (ListView) getFragment().getView()
                .findViewById(R.id.frequent_contacts_list);
        assertEquals(cursor.getCount(), listView.getCount());
    }

    public void testPickInitialContact() {
        final Cursor cursor = TestDataFactory.getFrequentContactListCursor();
        initFragment(ContactPickerFragment.MODE_PICK_INITIAL_CONTACT)
                .loadWithFrequentContactsCursor(cursor);
        final ListView listView = (ListView) getFragment().getView()
                .findViewById(R.id.frequent_contacts_list);
        // Click on the first contact to add it.
        final ContactListItemView cliv = (ContactListItemView) listView.getChildAt(0);
        clickButton(cliv);
        final ContactRecipientAutoCompleteView chipsView = (ContactRecipientAutoCompleteView)
                getFragment().getView()
                .findViewById(R.id.recipient_text_view);
        // Verify the contact is added to the chips view.
        final List<ParticipantData> participants =
                chipsView.getRecipientParticipantDataForConversationCreation();
        assertEquals(1, participants.size());
        assertEquals(cliv.mData.getDestination(), participants.get(0).getSendDestination());
        assertTrue(mService.getCalls().get(0).action instanceof GetOrCreateConversationAction);
    }

    public void testLeaveChipsMode() {
        final Cursor cursor = TestDataFactory.getFrequentContactListCursor();
        initFragment(ContactPickerFragment.MODE_CHIPS_ONLY)
                .loadWithFrequentContactsCursor(cursor);
        // Click on the add more participants button
        // TODO: Figure out a way to click on the add more participants button now that
        // it's part of the menu.
        // final ImageButton AddMoreParticipantsButton = (ImageButton) getFragment().getView()
        //         .findViewById(R.id.add_more_participants_button);
        // clickButton(AddMoreParticipantsButton);
        // Mockito.verify(mockHost).onInitiateAddMoreParticipants();
    }

    public void testPickMoreContacts() {
        final Cursor cursor = TestDataFactory.getFrequentContactListCursor();
        initFragment(ContactPickerFragment.MODE_PICK_MORE_CONTACTS)
                .loadWithFrequentContactsCursor(cursor);
        final ListView listView = (ListView) getFragment().getView()
                .findViewById(R.id.frequent_contacts_list);
        // Click on the first contact to add it.
        final ContactListItemView cliv = (ContactListItemView) listView.getChildAt(0);
        clickButton(cliv);
        // Verify that we don't attempt to create a conversation right away.
        assertEquals(0, mService.getCalls().size());
    }
}
