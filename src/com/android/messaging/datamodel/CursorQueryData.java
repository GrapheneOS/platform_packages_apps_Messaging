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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.android.messaging.util.Assert;
import com.google.common.annotations.VisibleForTesting;

/**
 * Holds parameters and data (such as content URI) for performing queries on the content provider.
 * This class could then be used to perform a query using either a BoundCursorLoader or querying
 * on the content resolver directly.
 *
 * This class is used for cases where the way to load a cursor is not fixed. For example,
 * when using ContactUtil to query for phone numbers, the ContactPickerFragment wants to use
 * a CursorLoader to asynchronously load the data and tie in nicely with its data binding
 * paradigm, whereas ContactRecipientAdapter wants to synchronously perform the query on the
 * worker thread.
 */
public class CursorQueryData {
    protected final Uri mUri;
    protected final String[] mProjection;
    protected final String mSelection;
    protected final String[] mSelectionArgs;
    protected final String mSortOrder;
    protected final Context mContext;

    public CursorQueryData(final Context context, final Uri uri, final String[] projection,
            final String selection, final String[] selectionArgs, final String sortOrder) {
        mContext = context;
        mUri = uri;
        mProjection = projection;
        mSelection = selection;
        mSelectionArgs = selectionArgs;
        mSortOrder = sortOrder;
    }

    public BoundCursorLoader createBoundCursorLoader(final String bindingId) {
        return new BoundCursorLoader(bindingId, mContext, mUri, mProjection, mSelection,
                mSelectionArgs, mSortOrder);
    }

    public Cursor performSynchronousQuery() {
        Assert.isNotMainThread();
        if (mUri == null) {
            // See {@link #getEmptyQueryData}
            return null;
        } else {
            return mContext.getContentResolver().query(mUri, mProjection, mSelection,
                    mSelectionArgs, mSortOrder);
        }
    }

    @VisibleForTesting
    public Uri getUri() {
        return mUri;
    }

    /**
     * Representation of an invalid query. {@link #performSynchronousQuery} will return
     * a null Cursor.
     */
    public static CursorQueryData getEmptyQueryData() {
        return new CursorQueryData(null, null, null, null, null, null);
    }
}
