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
package com.android.messaging.ui.conversation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.SubscriptionListData;
import com.android.messaging.datamodel.data.SubscriptionListData.SubscriptionListEntry;
import com.android.messaging.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a SIM selector above the compose message view and overlays the message list.
 */
public class SimSelectorView extends FrameLayout implements SimSelectorItemView.HostInterface {
    public interface SimSelectorViewListener {
        void onSimItemClicked(SubscriptionListEntry item);
        void onSimSelectorVisibilityChanged(boolean visible);
    }

    private ListView mSimListView;
    private final SimSelectorAdapter mAdapter;
    private boolean mShow;
    private SimSelectorViewListener mListener;
    private int mItemLayoutId;

    public SimSelectorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAdapter = new SimSelectorAdapter(getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSimListView = (ListView) findViewById(R.id.sim_list);
        mSimListView.setAdapter(mAdapter);

        // Clicking anywhere outside the switcher list should dismiss.
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showOrHide(false, true);
            }
        });
    }

    public void bind(final SubscriptionListData data) {
        mAdapter.bindData(data.getActiveSubscriptionEntriesExcludingDefault());
    }

    public void setItemLayoutId(final int layoutId) {
        mItemLayoutId = layoutId;
    }

    public void setListener(final SimSelectorViewListener listener) {
        mListener = listener;
    }

    public void toggleVisibility() {
        showOrHide(!mShow, true);
    }

    public void showOrHide(final boolean show, final boolean animate) {
        final boolean oldShow = mShow;
        mShow = show && mAdapter.getCount() > 1;
        if (oldShow != mShow) {
            if (mListener != null) {
                mListener.onSimSelectorVisibilityChanged(mShow);
            }

            if (animate) {
                // Fade in the background pane.
                setVisibility(VISIBLE);
                setAlpha(mShow ? 0.0f : 1.0f);
                animate().alpha(mShow ? 1.0f : 0.0f)
                    .setDuration(UiUtils.REVEAL_ANIMATION_DURATION)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            setAlpha(1.0f);
                            setVisibility(mShow ? VISIBLE : GONE);
                        }
                    });
            } else {
                setVisibility(mShow ? VISIBLE : GONE);
            }

            // Slide in the SIM selector list via a translate animation.
            mSimListView.setVisibility(mShow ? VISIBLE : GONE);
            if (animate) {
                mSimListView.clearAnimation();
                final TranslateAnimation translateAnimation = new TranslateAnimation(
                        Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 0,
                        Animation.RELATIVE_TO_SELF, mShow ? 1.0f : 0.0f,
                        Animation.RELATIVE_TO_SELF, mShow ? 0.0f : 1.0f);
                translateAnimation.setInterpolator(UiUtils.EASE_OUT_INTERPOLATOR);
                translateAnimation.setDuration(UiUtils.REVEAL_ANIMATION_DURATION);
                mSimListView.startAnimation(translateAnimation);
            }
        }
    }

    /**
     * An adapter that takes a list of SubscriptionListEntry and displays them as a list of
     * available SIMs in the SIM selector.
     */
    private class SimSelectorAdapter extends ArrayAdapter<SubscriptionListEntry> {
        public SimSelectorAdapter(final Context context) {
            super(context, R.layout.sim_selector_item_view, new ArrayList<SubscriptionListEntry>());
        }

        public void bindData(final List<SubscriptionListEntry> newList) {
            clear();
            addAll(newList);
            notifyDataSetChanged();
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            SimSelectorItemView itemView;
            if (convertView != null && convertView instanceof SimSelectorItemView) {
                itemView = (SimSelectorItemView) convertView;
            } else {
                final LayoutInflater inflater = (LayoutInflater) getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                itemView = (SimSelectorItemView) inflater.inflate(mItemLayoutId,
                        parent, false);
                itemView.setHostInterface(SimSelectorView.this);
            }
            itemView.bind(getItem(position));
            return itemView;
        }
    }

    @Override
    public void onSimItemClicked(SubscriptionListEntry item) {
        mListener.onSimItemClicked(item);
        showOrHide(false, true);
    }

    public boolean isOpen() {
        return mShow;
    }
}
