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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;

/**
 * A very simple content provider that can serve files.
 */
public abstract class FileProvider extends ContentProvider {
    // Object to generate random id for temp images.
    private static final Random RANDOM_ID = new Random();

    abstract File getFile(final String path, final String extension);

    private static final String FILE_EXTENSION_PARAM_KEY = "ext";

    /**
     * Check if filename conforms to requirement for our provider
     * @param fileId filename (optionally starting with path character
     * @return true if filename consists only of digits
     */
    protected static boolean isValidFileId(final String fileId) {
        // Ignore initial "/"
        for (int index = (fileId.startsWith("/") ? 1 : 0); index < fileId.length(); index++) {
            final Character c = fileId.charAt(index);
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Create a temp file (to allow writing to that one particular file)
     * @param file the file to create
     * @return true if file successfully created
     */
    protected static boolean ensureFileExists(final File file) {
        try {
            final File parentDir = file.getParentFile();
            if (parentDir.exists() || parentDir.mkdirs()) {
                return file.createNewFile();
            }
        } catch (final IOException e) {
            // fail on exceptions creating the file
        }
        return false;
    }

    /**
     * Build uri for a new temporary file (creating file)
     * @param authority authority with which to populate uri
     * @param extension optional file extension
     * @return unique uri that can be used to write temporary files
     */
    protected static Uri buildFileUri(final String authority, final String extension) {
        final long fileId = Math.abs(RANDOM_ID.nextLong());
        final Uri.Builder builder = (new Uri.Builder()).authority(authority).scheme(
                ContentResolver.SCHEME_CONTENT);
        builder.appendPath(String.valueOf(fileId));
        if (!TextUtils.isEmpty(extension)) {
            builder.appendQueryParameter(FILE_EXTENSION_PARAM_KEY, extension);
        }
        return builder.build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        final String fileId = uri.getPath();
        if (isValidFileId(fileId)) {
            final File file = getFile(fileId, getExtensionFromUri(uri));
            return file.delete() ? 1 : 0;
        }
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(final Uri uri, final String fileMode)
            throws FileNotFoundException {
        final String fileId = uri.getPath();
        if (isValidFileId(fileId)) {
            final File file = getFile(fileId, getExtensionFromUri(uri));
            final int mode =
                    (TextUtils.equals(fileMode, "r") ? ParcelFileDescriptor.MODE_READ_ONLY :
                        ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE);
            return ParcelFileDescriptor.open(file, mode);
        }
        return null;
    }

    protected static String getExtensionFromUri(final Uri uri) {
        return uri.getQueryParameter(FILE_EXTENSION_PARAM_KEY);
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
            final String[] selectionArgs, final String sortOrder) {
        // Don't support queries.
        return null;
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        // Don't support inserts.
        return null;
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection,
            final String[] selectionArgs) {
        // Don't support updates.
        return 0;
    }

    @Override
    public String getType(final Uri uri) {
        // No need for mime types.
        return null;
    }
}
