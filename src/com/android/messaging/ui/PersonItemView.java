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
package com.android.messaging.ui;

import android.content.Context;
import android.content.Intent;
import android.support.v4.text.BidiFormatter;
import android.support.v4.text.TextDirectionHeuristicsCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.binding.DetachableBinding;
import com.android.messaging.datamodel.data.PersonItemData;
import com.android.messaging.datamodel.data.PersonItemData.PersonItemDataListener;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.UiUtils;

/**
 * Shows a view for a "person" - could be a contact or a participant. This always shows a
 * contact icon on the left, and the person's display name on the right.
 *
 * This view is always bound to an abstract PersonItemData class, so to use it for a specific
 * scenario, all you need to do is to create a concrete PersonItemData subclass that bridges
 * between the underlying data (e.g. ParticipantData) and what the UI wants (e.g. display name).
 */
public class PersonItemView extends LinearLayout implements PersonItemDataListener,
        OnLayoutChangeListener {
    public interface PersonItemViewListener {
        void onPersonClicked(PersonItemData data);
        boolean onPersonLongClicked(PersonItemData data);
    }

    protected final DetachableBinding<PersonItemData> mBinding;
    private TextView mNameTextView;
    private TextView mDetailsTextView;
    private ContactIconView mContactIconView;
    private View mDetailsContainer;
    private PersonItemViewListener mListener;
    private boolean mAvatarOnly;

    public PersonItemView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mBinding = BindingBase.createDetachableBinding(this);
        LayoutInflater.from(getContext()).inflate(R.layout.person_item_view, this, true);
    }

    @Override
    protected void onFinishInflate() {
        mNameTextView = (TextView) findViewById(R.id.name);
        mDetailsTextView = (TextView) findViewById(R.id.details);
        mContactIconView = (ContactIconView) findViewById(R.id.contact_icon);
        mDetailsContainer = findViewById(R.id.details_container);
        mNameTextView.addOnLayoutChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBinding.isBound()) {
            mBinding.detach();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mBinding.reAttachIfPossible();
    }

    /**
     * Binds to a person item data which will provide us info to be displayed.
     * @param personData the PersonItemData to be bound to.
     */
    public void bind(final PersonItemData personData) {
        if (mBinding.isBound()) {
            if (mBinding.getData().equals(personData)) {
                // Don't rebind if we are requesting the same data.
                return;
            }
            mBinding.unbind();
        }

        if (personData != null) {
            mBinding.bind(personData);
            mBinding.getData().setListener(this);

            // Accessibility reason : in case phone numbers are mixed in the display name,
            // we need to vocalize it for talkback.
            final String vocalizedDisplayName = AccessibilityUtil.getVocalizedPhoneNumber(
                    getResources(), getDisplayName());
            mNameTextView.setContentDescription(vocalizedDisplayName);
        }
        updateViewAppearance();
    }

    /**
     * @return Display name, possibly comma-ellipsized.
     */
    private String getDisplayName() {
        final int width = mNameTextView.getMeasuredWidth();
        final String displayName = mBinding.getData().getDisplayName();
        if (width == 0 || TextUtils.isEmpty(displayName) || !displayName.contains(",")) {
            return displayName;
        }
        final String plusOneString = getContext().getString(R.string.plus_one);
        final String plusNString = getContext().getString(R.string.plus_n);
        return BidiFormatter.getInstance().unicodeWrap(
                UiUtils.commaEllipsize(
                        displayName,
                        mNameTextView.getPaint(),
                        width,
                        plusOneString,
                        plusNString).toString(),
                TextDirectionHeuristicsCompat.LTR);
    }

    @Override
    public void onLayoutChange(final View v, final int left, final int top, final int right,
            final int bottom, final int oldLeft, final int oldTop, final int oldRight,
            final int oldBottom) {
        if (mBinding.isBound() && v == mNameTextView) {
            setNameTextView();
        }
    }

    /**
     * When set to true, we display only the avatar of the person and hide everything else.
     */
    public void setAvatarOnly(final boolean avatarOnly) {
        mAvatarOnly = avatarOnly;
        mDetailsContainer.setVisibility(avatarOnly ? GONE : VISIBLE);
    }

    public boolean isAvatarOnly() {
        return mAvatarOnly;
    }

    public void setNameTextColor(final int color) {
        mNameTextView.setTextColor(color);
    }

    public void setDetailsTextColor(final int color) {
        mDetailsTextView.setTextColor(color);
    }

    public void setListener(final PersonItemViewListener listener) {
        mListener = listener;
        if (mListener == null) {
            return;
        }
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mListener != null && mBinding.isBound()) {
                    mListener.onPersonClicked(mBinding.getData());
                }
            }
        });
        final OnLongClickListener onLongClickListener = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mListener != null && mBinding.isBound()) {
                    return mListener.onPersonLongClicked(mBinding.getData());
                }
                return false;
            }
        };
        setOnLongClickListener(onLongClickListener);
        mContactIconView.setOnLongClickListener(onLongClickListener);
    }

    public void performClickOnAvatar() {
        mContactIconView.performClick();
    }

    protected void updateViewAppearance() {
        if (mBinding.isBound()) {
            setNameTextView();

            final String details = mBinding.getData().getDetails();
            if (TextUtils.isEmpty(details)) {
                mDetailsTextView.setVisibility(GONE);
            } else {
                mDetailsTextView.setVisibility(VISIBLE);
                mDetailsTextView.setText(details);
            }

            mContactIconView.setImageResourceUri(mBinding.getData().getAvatarUri(),
                    mBinding.getData().getContactId(), mBinding.getData().getLookupKey(),
                    mBinding.getData().getNormalizedDestination());
        } else {
            mNameTextView.setText("");
            mContactIconView.setImageResourceUri(null);
        }
    }

    private void setNameTextView() {
        final String displayName = getDisplayName();
        if (TextUtils.isEmpty(displayName)) {
            mNameTextView.setVisibility(GONE);
        } else {
            mNameTextView.setVisibility(VISIBLE);
            mNameTextView.setText(displayName);
        }
    }

    @Override
    public void onPersonDataUpdated(final PersonItemData data) {
        mBinding.ensureBound(data);
        updateViewAppearance();
    }

    @Override
    public void onPersonDataFailed(final PersonItemData data, final Exception exception) {
        mBinding.ensureBound(data);
        updateViewAppearance();
    }

    public Intent getClickIntent() {
        return mBinding.getData().getClickIntent();
    }
}
