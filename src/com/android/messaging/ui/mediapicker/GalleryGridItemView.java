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
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.data.GalleryGridItemData;
import com.android.messaging.ui.AsyncImageView;
import com.android.messaging.ui.ConversationDrawables;
import com.android.messaging.util.ContentType;
import com.google.common.annotations.VisibleForTesting;

import java.util.concurrent.TimeUnit;

/**
 * Shows an item in the gallery picker grid view. Hosts an FileImageView with a checkbox.
 */
public class GalleryGridItemView extends FrameLayout {
    /**
     * Implemented by the owner of this GalleryGridItemView instance to communicate on media
     * picking and selection events.
     */
    public interface HostInterface {
        void onItemClicked(View view, GalleryGridItemData data, boolean longClick);
        boolean isItemSelected(GalleryGridItemData data);
        boolean isMultiSelectEnabled();
    }

    @VisibleForTesting
    GalleryGridItemData mData;
    private AsyncImageView mImageView;
    private CheckBox mCheckBox;
    private RelativeLayout mAdditionalInfo;
    private ImageView mIcon;
    private LinearLayout mFileInfo;
    private TextView mFileName;
    private TextView mFileType;
    private HostInterface mHostInterface;
    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(final View v) {
            mHostInterface.onItemClicked(GalleryGridItemView.this, mData, false /*longClick*/);
        }
    };

    public GalleryGridItemView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mData = DataModel.get().createGalleryGridItemData();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = (AsyncImageView) findViewById(R.id.thumbnail);
        mCheckBox = (CheckBox) findViewById(R.id.checkbox);
        mCheckBox.setOnClickListener(mOnClickListener);
        mAdditionalInfo = (RelativeLayout) findViewById(R.id.additional_info);
        mIcon = (ImageView) findViewById(R.id.icon);
        mFileInfo = (LinearLayout) findViewById(R.id.file_info);
        mFileName = (TextView) findViewById(R.id.file_name);
        mFileType = (TextView) findViewById(R.id.file_type);
        setOnClickListener(mOnClickListener);
        final OnLongClickListener longClickListener = new OnLongClickListener() {
            @Override
            public boolean onLongClick(final View v) {
                mHostInterface.onItemClicked(v, mData, true /* longClick */);
                return true;
            }
        };
        setOnLongClickListener(longClickListener);
        mCheckBox.setOnLongClickListener(longClickListener);
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // Enlarge the clickable region for the checkbox to fill the entire view.
                final Rect region = new Rect(0, 0, getWidth(), getHeight());
                setTouchDelegate(new TouchDelegate(region, mCheckBox) {
                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                setPressed(true);
                                break;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                setPressed(false);
                                break;
                        }
                        return super.onTouchEvent(event);
                    }
                });
            }
        });
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        // The grid view auto-fit the columns, so we want to let the height match the width
        // to make the image square.
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }

    public void bind(final Cursor cursor, final HostInterface hostInterface) {
        final int desiredSize = getResources()
                .getDimensionPixelSize(R.dimen.gallery_image_cell_size);
        mData.bind(cursor, desiredSize, desiredSize);
        mHostInterface = hostInterface;
        updateViewState();
    }

    private void updateViewState() {
        updateImageView();
        if (mHostInterface.isMultiSelectEnabled() && !mData.isDocumentPickerItem()) {
            mCheckBox.setVisibility(VISIBLE);
            mCheckBox.setClickable(true);
            mCheckBox.setChecked(mHostInterface.isItemSelected(mData));
        } else {
            mCheckBox.setVisibility(GONE);
            mCheckBox.setClickable(false);
        }
    }

    private void updateImageView() {
        if (mData.isDocumentPickerItem()) {
            setBackgroundColor(ConversationDrawables.get().getConversationThemeColor());
            mIcon.setImageResource(R.drawable.ic_photo_library_light);
            mIcon.clearColorFilter();
            mImageView.setVisibility(GONE);
            mIcon.setVisibility(VISIBLE);
            mFileInfo.setVisibility(GONE);
            mAdditionalInfo.setVisibility(VISIBLE);
        } else {
            final String contentType = mData.getContentType();
            if (ContentType.isAudioType(contentType)) {
                final Context context = getContext();
                setBackgroundColor(
                        getResources().getColor(R.color.gallery_image_default_background));
                mIcon.setImageDrawable(
                        context.getContentResolver()
                                .getTypeInfo(contentType)
                                .getIcon()
                                .loadDrawable(context));
                mIcon.setColorFilter(
                        ConversationDrawables.get().getConversationThemeColor(),
                        PorterDuff.Mode.SRC_IN);
                mFileName.setText(mData.getFileName());
                String[] type = contentType.split("/");
                mFileType.setText(type[1].toUpperCase() + " " + type[0]);
                mImageView.setVisibility(GONE);
                mIcon.setVisibility(VISIBLE);
                mFileInfo.setVisibility(VISIBLE);
                mAdditionalInfo.setVisibility(VISIBLE);
            } else { // For image and video types
                mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                setBackgroundColor(
                        getResources().getColor(R.color.gallery_image_default_background));
                mImageView.setImageResourceId(mData.getImageRequestDescriptor());
                mImageView.setVisibility(VISIBLE);
                if (ContentType.isVideoType(mData.getContentType())) {
                    mIcon.setImageResource(R.drawable.ic_video_play_light);
                    mIcon.clearColorFilter();
                    mIcon.setVisibility(VISIBLE);
                } else {
                    mIcon.setVisibility(GONE);
                }
                mFileInfo.setVisibility(GONE);
                mAdditionalInfo.setVisibility(VISIBLE);
                final long dateSeconds = mData.getDateSeconds();
                final boolean isValidDate = (dateSeconds > 0);
                final int templateId = isValidDate ?
                        R.string.mediapicker_gallery_image_item_description :
                        R.string.mediapicker_gallery_image_item_description_no_date;
                String contentDescription = String.format(getResources().getString(templateId),
                        dateSeconds * TimeUnit.SECONDS.toMillis(1));
                mImageView.setContentDescription(contentDescription);
            }
        }
    }
}
