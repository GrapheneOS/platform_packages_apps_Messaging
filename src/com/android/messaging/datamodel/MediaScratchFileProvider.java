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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.v4.util.SimpleArrayMap;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A very simple content provider that can serve media files from our cache directory.
 */
public class MediaScratchFileProvider extends FileProvider {
    private static final String TAG = LogUtil.BUGLE_TAG;

    private static final SimpleArrayMap<Uri, String> sUriToDisplayNameMap =
            new SimpleArrayMap<Uri, String>();

    @VisibleForTesting
    public static final String AUTHORITY =
            "com.android.messaging.datamodel.MediaScratchFileProvider";
    private static final String MEDIA_SCRATCH_SPACE_DIR = "mediascratchspace";

    public static boolean isMediaScratchSpaceUri(final Uri uri) {
        if (uri == null) {
            return false;
        }

        final List<String> segments = uri.getPathSegments();
        return (TextUtils.equals(uri.getScheme(), ContentResolver.SCHEME_CONTENT) &&
                TextUtils.equals(uri.getAuthority(), AUTHORITY) &&
                segments.size() == 1 && FileProvider.isValidFileId(segments.get(0)));
    }

    /**
     * Returns a uri that can be used to access a raw mms file.
     *
     * @return the URI for an raw mms file
     */
    public static Uri buildMediaScratchSpaceUri(final String extension) {
        final Uri uri = FileProvider.buildFileUri(AUTHORITY, extension);
        final File file = getFileWithExtension(uri.getPath(), extension);
        if (!ensureFileExists(file)) {
            LogUtil.e(TAG, "Failed to create temp file " + file.getAbsolutePath());
        }
        return uri;
    }

    public static File getFileFromUri(final Uri uri) {
        Assert.equals(AUTHORITY, uri.getAuthority());
        return getFileWithExtension(uri.getPath(), getExtensionFromUri(uri));
    }

    public static Uri.Builder getUriBuilder() {
        return (new Uri.Builder()).authority(AUTHORITY).scheme(ContentResolver.SCHEME_CONTENT);
    }

    @Override
    File getFile(final String path, final String extension) {
        return getFileWithExtension(path, extension);
    }

    private static File getFileWithExtension(final String path, final String extension) {
        final Context context = Factory.get().getApplicationContext();
        final File filePath = new File(getDirectory(context),
                TextUtils.isEmpty(extension) ? path : path + "." + extension);

        try {
            if (!filePath.getCanonicalPath()
                    .startsWith(getDirectory(context).getCanonicalPath())) {
                LogUtil.e(TAG, "getFileWithExtension: path "
                        + filePath.getCanonicalPath()
                        + " does not start with "
                        + getDirectory(context).getCanonicalPath());
                return null;
            }
        } catch (IOException e) {
            LogUtil.e(TAG, "getFileWithExtension: getCanonicalPath failed ", e);
            return null;
        }
        return filePath;
    }

    private static File getDirectory(final Context context) {
        return new File(context.getCacheDir(), MEDIA_SCRATCH_SPACE_DIR);
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
            final String[] selectionArgs, final String sortOrder) {
        if (projection != null && projection.length > 0 &&
                TextUtils.equals(projection[0], OpenableColumns.DISPLAY_NAME) &&
                isMediaScratchSpaceUri(uri)) {
            // Retrieve the display name associated with a temp file. This is used by the Contacts
            // ImportVCardActivity to retrieve the name of the contact(s) being imported.
            String displayName;
            synchronized (sUriToDisplayNameMap) {
                displayName = sUriToDisplayNameMap.get(uri);
            }
            if (!TextUtils.isEmpty(displayName)) {
                MatrixCursor cursor =
                        new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME });
                RowBuilder row = cursor.newRow();
                row.add(displayName);
                return cursor;
            }
        }
        return null;
    }

    public static void addUriToDisplayNameEntry(final Uri scratchFileUri,
            final String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return;
        }
        synchronized (sUriToDisplayNameMap) {
            sUriToDisplayNameMap.put(scratchFileUri, displayName);
        }
    }
}
