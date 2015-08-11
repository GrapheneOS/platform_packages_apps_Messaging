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
import android.os.Bundle;

import com.android.messaging.R;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.attachmentchooser.AttachmentChooserFragment.AttachmentChooserFragmentHost;
import com.android.messaging.util.Assert;

public class AttachmentChooserActivity extends BugleActionBarActivity implements
        AttachmentChooserFragmentHost {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attachment_chooser_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }

    @Override
    public void onAttachFragment(final Fragment fragment) {
        if (fragment instanceof AttachmentChooserFragment) {
            final String conversationId =
                    getIntent().getStringExtra(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID);
            Assert.notNull(conversationId);
            final AttachmentChooserFragment chooserFragment =
                    (AttachmentChooserFragment) fragment;
            chooserFragment.setConversationId(conversationId);
            chooserFragment.setHost(this);
        }
    }

    @Override
    public void onConfirmSelection() {
        setResult(RESULT_OK);
        finish();
    }
}
