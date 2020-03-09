/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.messaging.ui.mediapicker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.ContactUtil;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.SafeAsyncTask;

/**
 * Chooser which allows the user to select an existing contact from contacts apps on this device.
 * Note that this chooser requires the Manifest.permission.READ_CONTACTS which is one of the miminum
 * set of permissions for this app. Thus no case to request READ_CONTACTS permission on it actually.
 */
class ContactMediaChooser extends MediaChooser {
    private View mEnabledView;
    private View mMissingPermissionView;

    ContactMediaChooser(final MediaPicker mediaPicker) {
        super(mediaPicker);
    }

    @Override
    public int getSupportedMediaTypes() {
        return MediaPicker.MEDIA_TYPE_VCARD;
    }

    @Override
    public int getIconResource() {
        return R.drawable.ic_person_light;
    }

    @Override
    public int getIconDescriptionResource() {
        return R.string.mediapicker_contactChooserDescription;
    }

    @Override
    int getActionBarTitleResId() {
        return R.string.mediapicker_contact_title;
    }

    @Override
    protected View createView(final ViewGroup container) {
        final LayoutInflater inflater = getLayoutInflater();
        final View view =
                inflater.inflate(
                        R.layout.mediapicker_contact_chooser,
                        container /* root */,
                        false /* attachToRoot */);
        mEnabledView = view.findViewById(R.id.mediapicker_enabled);
        mMissingPermissionView = view.findViewById(R.id.missing_permission_view);
        mEnabledView.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        // Launch an external picker to pick a contact as attachment.
                        UIIntents.get().launchContactCardPicker(mMediaPicker);
                    }
                });
        return view;
    }

    @Override
    protected void setSelected(final boolean selected) {
        super.setSelected(selected);
        if (selected && !ContactUtil.hasReadContactsPermission()) {
            mMediaPicker.requestPermissions(
                    new String[] {Manifest.permission.READ_CONTACTS},
                    MediaPicker.READ_CONTACT_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onRequestPermissionsResult(
            final int requestCode, final String permissions[], final int[] grantResults) {
        if (requestCode == MediaPicker.READ_CONTACT_PERMISSION_REQUEST_CODE) {
            final boolean permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            mEnabledView.setVisibility(permissionGranted ? View.VISIBLE : View.GONE);
            mMissingPermissionView.setVisibility(permissionGranted ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UIIntents.REQUEST_PICK_CONTACT_CARD
                && resultCode == Activity.RESULT_OK) {
            Uri contactUri = data.getData();
            if (contactUri != null) {
                String lookupKey = null;
                try (final Cursor c = getContext().getContentResolver().query(
                                        contactUri,
                                        new String[] {Contacts.LOOKUP_KEY},
                                        null,
                                        null,
                                        null)) {
                    if (c != null) {
                        c.moveToFirst();
                        lookupKey = c.getString(0);
                    }
                }
                final Uri vCardUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
                if (vCardUri != null) {
                    SafeAsyncTask.executeOnThreadPool(new Runnable() {
                        @Override
                        public void run() {
                            final PendingAttachmentData pendingItem =
                                    PendingAttachmentData.createPendingAttachmentData(
                                            ContentType.TEXT_VCARD.toLowerCase(), vCardUri);
                            mMediaPicker.dispatchPendingItemAdded(pendingItem);
                        }
                    });
                }
            }
        }
    }
}
