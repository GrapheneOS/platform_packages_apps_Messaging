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
import android.content.CursorLoader;
import android.net.Uri;

/**
 * Extension to basic cursor loader that has an attached binding id
 */
public class BoundCursorLoader extends CursorLoader {
    private final String mBindingId;

    /**
     * Create cursor loader for associated binding id
     */
    public BoundCursorLoader(final String bindingId, final Context context, final Uri uri,
            final String[] projection, final String selection, final String[] selectionArgs,
            final String sortOrder) {
        super(context, uri, projection, selection, selectionArgs, sortOrder);
        mBindingId = bindingId;
    }

    /**
     * Binding id associated with this loader - consume can check to verify data still valid
     * @return
     */
    public String getBindingId() {
        return mBindingId;
    }
}
