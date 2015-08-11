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

package com.android.messaging.datamodel.data;

import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.android.messaging.datamodel.BoundCursorLoader;
import com.android.messaging.datamodel.GalleryBoundCursorLoader;
import com.android.messaging.datamodel.binding.BindableData;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.BuglePrefsKeys;
import com.android.messaging.util.LogUtil;

/**
 * Services data needs for MediaPicker.
 */
public class MediaPickerData extends BindableData {
    public interface MediaPickerDataListener {
        void onMediaPickerDataUpdated(MediaPickerData mediaPickerData, Object data, int loaderId);
    }

    private static final String BINDING_ID = "bindingId";
    private final Context mContext;
    private LoaderManager mLoaderManager;
    private final GalleryLoaderCallbacks mGalleryLoaderCallbacks;
    private MediaPickerDataListener mListener;

    public MediaPickerData(final Context context) {
        mContext = context;
        mGalleryLoaderCallbacks = new GalleryLoaderCallbacks();
    }

    public static final int GALLERY_IMAGE_LOADER = 1;

    /**
     * A trampoline class so that we can inherit from LoaderManager.LoaderCallbacks multiple times.
     */
    private class GalleryLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            final String bindingId = args.getString(BINDING_ID);
            // Check if data still bound to the requesting ui element
            if (isBound(bindingId)) {
                switch (id) {
                    case GALLERY_IMAGE_LOADER:
                        return new GalleryBoundCursorLoader(bindingId, mContext);

                    default:
                        Assert.fail("Unknown loader id for gallery picker!");
                        break;
                }
            } else {
                LogUtil.w(LogUtil.BUGLE_TAG, "Loader created after unbinding the media picker");
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            final BoundCursorLoader cursorLoader = (BoundCursorLoader) loader;
            if (isBound(cursorLoader.getBindingId())) {
                switch (loader.getId()) {
                    case GALLERY_IMAGE_LOADER:
                        mListener.onMediaPickerDataUpdated(MediaPickerData.this, data,
                                GALLERY_IMAGE_LOADER);
                        break;

                    default:
                        Assert.fail("Unknown loader id for gallery picker!");
                        break;
                }
            } else {
                LogUtil.w(LogUtil.BUGLE_TAG, "Loader finished after unbinding the media picker");
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
            final BoundCursorLoader cursorLoader = (BoundCursorLoader) loader;
            if (isBound(cursorLoader.getBindingId())) {
                switch (loader.getId()) {
                    case GALLERY_IMAGE_LOADER:
                        mListener.onMediaPickerDataUpdated(MediaPickerData.this, null,
                                GALLERY_IMAGE_LOADER);
                        break;

                    default:
                        Assert.fail("Unknown loader id for media picker!");
                        break;
                }
            } else {
                LogUtil.w(LogUtil.BUGLE_TAG, "Loader reset after unbinding the media picker");
            }
        }
    }



    public void startLoader(final int loaderId, final BindingBase<MediaPickerData> binding,
            @Nullable Bundle args, final MediaPickerDataListener listener) {
        if (args == null) {
            args = new Bundle();
        }
        args.putString(BINDING_ID, binding.getBindingId());
        if (loaderId == GALLERY_IMAGE_LOADER) {
            mLoaderManager.initLoader(loaderId, args, mGalleryLoaderCallbacks).forceLoad();
        } else {
            Assert.fail("Unsupported loader id for media picker!");
        }
        mListener = listener;
    }

    public void destroyLoader(final int loaderId) {
        mLoaderManager.destroyLoader(loaderId);
    }

    public void init(final LoaderManager loaderManager) {
        mLoaderManager = loaderManager;
    }

    @Override
    protected void unregisterListeners() {
        // This could be null if we bind but the caller doesn't init the BindableData
        if (mLoaderManager != null) {
            mLoaderManager.destroyLoader(GALLERY_IMAGE_LOADER);
            mLoaderManager = null;
        }
    }

    /**
     * Gets the last selected chooser index, or -1 if no selection has been saved.
     */
    public int getSelectedChooserIndex() {
        return BuglePrefs.getApplicationPrefs().getInt(
                BuglePrefsKeys.SELECTED_MEDIA_PICKER_CHOOSER_INDEX,
                BuglePrefsKeys.SELECTED_MEDIA_PICKER_CHOOSER_INDEX_DEFAULT);
    }

    /**
     * Saves the selected media chooser index.
     * @param selectedIndex the selected media chooser index.
     */
    public void saveSelectedChooserIndex(final int selectedIndex) {
        BuglePrefs.getApplicationPrefs().putInt(BuglePrefsKeys.SELECTED_MEDIA_PICKER_CHOOSER_INDEX,
                selectedIndex);
    }

}