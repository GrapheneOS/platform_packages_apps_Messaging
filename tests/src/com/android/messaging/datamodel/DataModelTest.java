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

import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.data.ConversationData;
import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.ConversationData.ConversationDataListener;
import com.android.messaging.datamodel.data.ConversationListData.ConversationListDataListener;

import org.mockito.Mock;

public class DataModelTest extends BugleTestCase {

    DataModel dataModel;
    @Mock protected ConversationDataListener mockConversationDataListener;
    @Mock protected ConversationListDataListener mockConversationListDataListener;

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      dataModel = new DataModelImpl(getTestContext());
      FakeFactory.register(mContext)
              .withDataModel(dataModel);
    }

    @SmallTest
    public void testCreateConversationList() {
        final ConversationListData list = dataModel.createConversationListData(getContext(),
                        mockConversationListDataListener, true);
        assertTrue(list instanceof ConversationListData);
        final ConversationData conv = dataModel.createConversationData(getContext(),
                mockConversationDataListener, "testConversation");
        assertTrue(conv instanceof ConversationData);
    }

    private static final String FOCUSED_CONV_ID = "focused_conv_id";

    @SmallTest
    public void testFocusedConversationIsObservable() {
        dataModel.setFocusedConversation(FOCUSED_CONV_ID);
        assertTrue(dataModel.isNewMessageObservable(FOCUSED_CONV_ID));
        dataModel.setFocusedConversation(null);
        assertFalse(dataModel.isNewMessageObservable(FOCUSED_CONV_ID));
    }

    @SmallTest
    public void testConversationIsObservableInList() {
        dataModel.setConversationListScrolledToNewestConversation(true);
        assertTrue(dataModel.isNewMessageObservable(FOCUSED_CONV_ID));
        dataModel.setConversationListScrolledToNewestConversation(false);
        assertFalse(dataModel.isNewMessageObservable(FOCUSED_CONV_ID));
    }
}
