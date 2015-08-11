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
package com.android.messaging.ui.attachmentchooser;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.ui.AttachmentPreviewFactory;
import com.android.messaging.util.Assert;
import com.google.common.annotations.VisibleForTesting;

/**
 * Shows an item in the attachment picker grid.
 */
public class AttachmentGridItemView extends FrameLayout {
    public interface HostInterface {
        boolean isItemSelected(MessagePartData attachment);
        void onItemCheckedChanged(AttachmentGridItemView view, MessagePartData attachment);
        void onItemClicked(AttachmentGridItemView view, MessagePartData attachment);
    }

    @VisibleForTesting
    MessagePartData mAttachmentData;
    private FrameLayout mAttachmentViewContainer;
    private CheckBox mCheckBox;
    private HostInterface mHostInterface;

    public AttachmentGridItemView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAttachmentViewContainer = (FrameLayout) findViewById(R.id.attachment_container);
        mCheckBox = (CheckBox) findViewById(R.id.checkbox);
        mCheckBox.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                mHostInterface.onItemCheckedChanged(AttachmentGridItemView.this, mAttachmentData);
            }
        });
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                mHostInterface.onItemClicked(AttachmentGridItemView.this, mAttachmentData);
            }
        });
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // Enlarge the clickable region for the checkbox.
                final int touchAreaIncrease = getResources().getDimensionPixelOffset(
                        R.dimen.attachment_grid_checkbox_area_increase);
                final Rect region = new Rect();
                mCheckBox.getHitRect(region);
                region.inset(-touchAreaIncrease, -touchAreaIncrease);
                setTouchDelegate(new TouchDelegate(region, mCheckBox));
            }
        });
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        // The grid view auto-fits the columns, so we want to let the height match the width
        // to make the attachment preview square.
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }

    public void bind(final MessagePartData attachment, final HostInterface hostInterface) {
        Assert.isTrue(attachment.isAttachment());
        mHostInterface = hostInterface;
        updateSelectedState();
        if (mAttachmentData == null || !mAttachmentData.equals(attachment)) {
            mAttachmentData = attachment;
            updateAttachmentView();
        }
    }

    @VisibleForTesting
    HostInterface testGetHostInterface() {
        return mHostInterface;
    }

    public void updateSelectedState() {
        mCheckBox.setChecked(mHostInterface.isItemSelected(mAttachmentData));
    }

    private void updateAttachmentView() {
        mAttachmentViewContainer.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final View attachmentView = AttachmentPreviewFactory.createAttachmentPreview(inflater,
                mAttachmentData, mAttachmentViewContainer,
                AttachmentPreviewFactory.TYPE_CHOOSER_GRID, true /* startImageRequest */, null);
        mAttachmentViewContainer.addView(attachmentView);
    }
}
