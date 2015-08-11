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

package com.android.messaging.ui.mediapicker;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import com.android.messaging.R;
import com.android.messaging.ui.mediapicker.GalleryGridItemView.HostInterface;
import com.android.messaging.util.Assert;

/**
 * Bridges between the image cursor loaded by GalleryBoundCursorLoader and the GalleryGridView.
 */
public class GalleryGridAdapter extends CursorAdapter {
    private GalleryGridItemView.HostInterface mGgivHostInterface;

    public GalleryGridAdapter(final Context context, final Cursor cursor) {
        super(context, cursor, 0);
    }

    public void setHostInterface(final HostInterface ggivHostInterface) {
        mGgivHostInterface = ggivHostInterface;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindView(final View view, final Context context, final Cursor cursor) {
        Assert.isTrue(view instanceof GalleryGridItemView);
        final GalleryGridItemView galleryImageView = (GalleryGridItemView) view;
        galleryImageView.bind(cursor, mGgivHostInterface);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
        final LayoutInflater layoutInflater = LayoutInflater.from(context);
        return layoutInflater.inflate(R.layout.gallery_grid_item_view, parent, false);
    }
}
