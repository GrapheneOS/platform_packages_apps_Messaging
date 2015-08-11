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
import android.graphics.Rect;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.util.ArrayMap;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.android.messaging.R;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.binding.ImmutableBindingRef;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.GalleryGridItemData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.DraftMessageData.DraftMessageDataListener;
import com.android.messaging.ui.PersistentInstanceState;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.LogUtil;

import java.util.Iterator;
import java.util.Map;

/**
 * Shows a list of galley images from external storage in a GridView with multi-select
 * capabilities, and with the option to intent out to a standalone image picker.
 */
public class GalleryGridView extends MediaPickerGridView implements
        GalleryGridItemView.HostInterface,
        PersistentInstanceState,
        DraftMessageDataListener {
    /**
     * Implemented by the owner of this GalleryGridView instance to communicate on image
     * picking and multi-image selection events.
     */
    public interface GalleryGridViewListener {
        void onDocumentPickerItemClicked();
        void onItemSelected(MessagePartData item);
        void onItemUnselected(MessagePartData item);
        void onConfirmSelection();
        void onUpdate();
    }

    private GalleryGridViewListener mListener;

    // TODO: Consider putting this into the data model object if we add more states.
    private final ArrayMap<Uri, MessagePartData> mSelectedImages;
    private boolean mIsMultiSelectMode = false;
    private ImmutableBindingRef<DraftMessageData> mDraftMessageDataModel;

    public GalleryGridView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mSelectedImages = new ArrayMap<Uri, MessagePartData>();
    }

    public void setHostInterface(final GalleryGridViewListener hostInterface) {
        mListener = hostInterface;
    }

    public void setDraftMessageDataModel(final BindingBase<DraftMessageData> dataModel) {
        mDraftMessageDataModel = BindingBase.createBindingReference(dataModel);
        mDraftMessageDataModel.getData().addListener(this);
    }

    @Override
    public void onItemClicked(final View view, final GalleryGridItemData data,
            final boolean longClick) {
        if (data.isDocumentPickerItem()) {
            mListener.onDocumentPickerItemClicked();
        } else if (ContentType.isMediaType(data.getContentType())) {
            if (longClick) {
                // Turn on multi-select mode when an item is long-pressed.
                setMultiSelectEnabled(true);
            }

            final Rect startRect = new Rect();
            view.getGlobalVisibleRect(startRect);
            if (isMultiSelectEnabled()) {
                toggleItemSelection(startRect, data);
            } else {
                mListener.onItemSelected(data.constructMessagePartData(startRect));
            }
        } else {
            LogUtil.w(LogUtil.BUGLE_TAG,
                    "Selected item has invalid contentType " + data.getContentType());
        }
    }

    @Override
    public boolean isItemSelected(final GalleryGridItemData data) {
        return mSelectedImages.containsKey(data.getImageUri());
    }

    int getSelectionCount() {
        return mSelectedImages.size();
    }

    @Override
    public boolean isMultiSelectEnabled() {
        return mIsMultiSelectMode;
    }

    private void toggleItemSelection(final Rect startRect, final GalleryGridItemData data) {
        Assert.isTrue(isMultiSelectEnabled());
        if (isItemSelected(data)) {
            final MessagePartData item = mSelectedImages.remove(data.getImageUri());
            mListener.onItemUnselected(item);
            if (mSelectedImages.size() == 0) {
                // No image is selected any more, turn off multi-select mode.
                setMultiSelectEnabled(false);
            }
        } else {
            final MessagePartData item = data.constructMessagePartData(startRect);
            mSelectedImages.put(data.getImageUri(), item);
            mListener.onItemSelected(item);
        }
        invalidateViews();
    }

    private void toggleMultiSelect() {
        mIsMultiSelectMode = !mIsMultiSelectMode;
        invalidateViews();
    }

    private void setMultiSelectEnabled(final boolean enabled) {
        if (mIsMultiSelectMode != enabled) {
            toggleMultiSelect();
        }
    }

    private boolean canToggleMultiSelect() {
        // We allow the user to toggle multi-select mode only when nothing has selected. If
        // something has been selected, we show a confirm button instead.
        return mSelectedImages.size() == 0;
    }

    public void onCreateOptionsMenu(final MenuInflater inflater, final Menu menu) {
        inflater.inflate(R.menu.gallery_picker_menu, menu);
        final MenuItem toggleMultiSelect = menu.findItem(R.id.action_multiselect);
        final MenuItem confirmMultiSelect = menu.findItem(R.id.action_confirm_multiselect);
        final boolean canToggleMultiSelect = canToggleMultiSelect();
        toggleMultiSelect.setVisible(canToggleMultiSelect);
        confirmMultiSelect.setVisible(!canToggleMultiSelect);
    }

    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_multiselect:
                Assert.isTrue(canToggleMultiSelect());
                toggleMultiSelect();
                return true;

            case R.id.action_confirm_multiselect:
                Assert.isTrue(!canToggleMultiSelect());
                mListener.onConfirmSelection();
                return true;
        }
        return false;
    }


    @Override
    public void onDraftChanged(final DraftMessageData data, final int changeFlags) {
        mDraftMessageDataModel.ensureBound(data);
        // Whenever attachment changed, refresh selection state to remove those that are not
        // selected.
        if ((changeFlags & DraftMessageData.ATTACHMENTS_CHANGED) ==
                DraftMessageData.ATTACHMENTS_CHANGED) {
            refreshImageSelectionStateOnAttachmentChange();
        }
    }

    @Override
    public void onDraftAttachmentLimitReached(final DraftMessageData data) {
        mDraftMessageDataModel.ensureBound(data);
        // Whenever draft attachment limit is reach, refresh selection state to remove those
        // not actually added to draft.
        refreshImageSelectionStateOnAttachmentChange();
    }

    @Override
    public void onDraftAttachmentLoadFailed() {
        // Nothing to do since the failed attachment gets removed automatically.
    }

    private void refreshImageSelectionStateOnAttachmentChange() {
        boolean changed = false;
        final Iterator<Map.Entry<Uri, MessagePartData>> iterator =
                mSelectedImages.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Uri, MessagePartData> entry = iterator.next();
            if (!mDraftMessageDataModel.getData().containsAttachment(entry.getKey())) {
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            mListener.onUpdate();
            invalidateViews();
        }
    }

    @Override   // PersistentInstanceState
    public Parcelable saveState() {
        return onSaveInstanceState();
    }

    @Override   // PersistentInstanceState
    public void restoreState(final Parcelable restoredState) {
        onRestoreInstanceState(restoredState);
        invalidateViews();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState savedState = new SavedState(superState);
        savedState.isMultiSelectMode = mIsMultiSelectMode;
        savedState.selectedImages = mSelectedImages.values()
                .toArray(new MessagePartData[mSelectedImages.size()]);
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(final Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        final SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        mIsMultiSelectMode = savedState.isMultiSelectMode;
        mSelectedImages.clear();
        for (int i = 0; i < savedState.selectedImages.length; i++) {
            final MessagePartData selectedImage = savedState.selectedImages[i];
            mSelectedImages.put(selectedImage.getContentUri(), selectedImage);
        }
    }

    @Override   // PersistentInstanceState
    public void resetState() {
        mSelectedImages.clear();
        mIsMultiSelectMode = false;
        invalidateViews();
    }

    public static class SavedState extends BaseSavedState {
        boolean isMultiSelectMode;
        MessagePartData[] selectedImages;

        SavedState(final Parcelable superState) {
            super(superState);
        }

        private SavedState(final Parcel in) {
            super(in);
            isMultiSelectMode = in.readInt() == 1 ? true : false;

            // Read parts
            final int partCount = in.readInt();
            selectedImages = new MessagePartData[partCount];
            for (int i = 0; i < partCount; i++) {
                selectedImages[i] = ((MessagePartData) in.readParcelable(
                        MessagePartData.class.getClassLoader()));
            }
        }

        @Override
        public void writeToParcel(final Parcel out, final int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(isMultiSelectMode ? 1 : 0);

            // Write parts
            out.writeInt(selectedImages.length);
            for (final MessagePartData image : selectedImages) {
                out.writeParcelable(image, flags);
            }
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(final Parcel in) {
                return new SavedState(in);
            }
            @Override
            public SavedState[] newArray(final int size) {
                return new SavedState[size];
            }
        };
    }
}
