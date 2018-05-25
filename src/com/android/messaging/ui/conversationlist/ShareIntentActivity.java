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
import android.content.ContentResolver;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.BaseBugleActivity;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.MediaMetadataRetrieverWrapper;
import com.android.messaging.util.FileUtil;
import com.android.messaging.util.UriUtil;

import java.io.IOException;
import java.util.ArrayList;

public class ShareIntentActivity extends BaseBugleActivity implements
        ShareIntentFragment.HostInterface {

    private MessageData mDraftMessage;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) &&
                (!TextUtils.isEmpty(intent.getStringExtra("address")) ||
                !TextUtils.isEmpty(intent.getStringExtra(Intent.EXTRA_EMAIL)))) {
            // This is really more like a SENDTO intent because a destination is supplied.
            // It's coming through the SEND intent because that's the intent that is used
            // when invoking the chooser with Intent.createChooser().
            final Intent convIntent = UIIntents.get().getLaunchConversationActivityIntent(this);
            // Copy the important items from the original intent to the new intent.
            convIntent.putExtras(intent);
            convIntent.setAction(Intent.ACTION_SENDTO);
            convIntent.setDataAndType(intent.getData(), intent.getType());
            // We have to fire off the intent and finish before trying to show the fragment,
            // otherwise we get some flashing.
            startActivity(convIntent);
            finish();
            return;
        }
        new ShareIntentFragment().show(getFragmentManager(), "ShareIntentFragment");
    }

    @Override
    public void onAttachFragment(final Fragment fragment) {
        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            final Uri contentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (UriUtil.isFileUri(contentUri)) {
                LogUtil.i(
                    LogUtil.BUGLE_TAG,
                    "Ignoring attachment from file URI which are no longer supported.");
                return;
            }
            final String contentType = extractContentType(contentUri, intent.getType());
            if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.DEBUG)) {
                LogUtil.d(LogUtil.BUGLE_TAG, String.format(
                        "onAttachFragment: contentUri=%s, intent.getType()=%s, inferredType=%s",
                        contentUri, intent.getType(), contentType));
            }
            if (ContentType.TEXT_PLAIN.equals(contentType)) {
                final String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    mDraftMessage = MessageData.createSharedMessage(sharedText);
                } else {
                    mDraftMessage = null;
                }
            } else if (ContentType.isImageType(contentType) ||
                    ContentType.isVCardType(contentType) ||
                    ContentType.isAudioType(contentType) ||
                    ContentType.isVideoType(contentType)) {
                if (contentUri != null) {
                    mDraftMessage = MessageData.createSharedMessage(null);
                    addSharedImagePartToDraft(contentType, contentUri);
                } else {
                    mDraftMessage = null;
                }
            } else {
                // Unsupported content type.
                Assert.fail("Unsupported shared content type for " + contentUri + ": " + contentType
                        + " (" + intent.getType() + ")");
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            final String contentType = intent.getType();
            if (ContentType.isImageType(contentType)) {
                // Handle sharing multiple images.
                final ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(
                        Intent.EXTRA_STREAM);
                if (imageUris != null && imageUris.size() > 0) {
                    mDraftMessage = MessageData.createSharedMessage(null);
                    for (final Uri imageUri : imageUris) {
                        if (UriUtil.isFileUri(imageUri)) {
                            LogUtil.i(
                                LogUtil.BUGLE_TAG,
                                "Ignoring attachment from file URI which are no longer supported.");
                            continue;
                        }
                        final String actualContentType = extractContentType(imageUri, contentType);
                        addSharedImagePartToDraft(actualContentType, imageUri);
                    }
                } else {
                    mDraftMessage = null;
                }
            } else {
                // Unsupported content type.
                Assert.fail("Unsupported shared content type: " + contentType);
            }
        } else {
            // Unsupported action.
            Assert.fail("Unsupported action type for sharing: " + action);
        }
    }

    private static String extractContentType(final Uri uri, final String contentType) {
        if (uri == null) {
            return contentType;
        }
        // First try looking at file extension.  This is less reliable in some ways but it's
        // recommended by
        // https://developer.android.com/training/secure-file-sharing/retrieve-info.html
        // Some implementations of MediaMetadataRetriever get things horribly
        // wrong for common formats such as jpeg (reports as video/ffmpeg)
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        final String typeFromExtension = resolver.getType(uri);
        if (typeFromExtension != null) {
            return typeFromExtension;
        }
        final MediaMetadataRetrieverWrapper retriever = new MediaMetadataRetrieverWrapper();
        try {
            retriever.setDataSource(uri);
            final String extractedType = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            if (extractedType != null) {
                return extractedType;
            }
        } catch (final IOException e) {
            LogUtil.i(LogUtil.BUGLE_TAG, "Could not determine type of " + uri, e);
        } finally {
            retriever.release();
        }
        return contentType;
    }

    private void addSharedImagePartToDraft(final String contentType, final Uri imageUri) {
        if (FileUtil.isInPrivateDir(imageUri)) {
            Assert.fail("Cannot send private file " + imageUri.toString());
        } else {
            mDraftMessage.addPart(PendingAttachmentData.createPendingAttachmentData(contentType,
                    imageUri));
        }
    }

    @Override
    public void onConversationClick(final ConversationListItemData conversationListItemData) {
        UIIntents.get().launchConversationActivity(
                this, conversationListItemData.getConversationId(), mDraftMessage);
        finish();
    }

    @Override
    public void onCreateConversationClick() {
        UIIntents.get().launchCreateNewConversationActivity(this, mDraftMessage);
        finish();
    }
}
