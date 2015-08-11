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

import android.content.Intent;
import android.net.Uri;

import com.android.messaging.datamodel.binding.BindableData;

/**
 * Bridges between any particpant/contact related data and data displayed in the PersonItemView.
 */
public abstract class PersonItemData extends BindableData {
    /**
     * The UI component that listens for data change and update accordingly.
     */
    public interface PersonItemDataListener {
        void onPersonDataUpdated(PersonItemData data);
        void onPersonDataFailed(PersonItemData data, Exception exception);
    }

    private PersonItemDataListener mListener;

    public abstract Uri getAvatarUri();
    public abstract String getDisplayName();
    public abstract String getDetails();
    public abstract Intent getClickIntent();
    public abstract long getContactId();
    public abstract String getLookupKey();
    public abstract String getNormalizedDestination();

    public void setListener(final PersonItemDataListener listener) {
        if (isBound()) {
            mListener = listener;
        }
    }

    protected void notifyDataUpdated() {
        if (isBound() && mListener != null) {
            mListener.onPersonDataUpdated(this);
        }
    }

    protected void notifyDataFailed(final Exception exception) {
        if (isBound() && mListener != null) {
            mListener.onPersonDataFailed(this, exception);
        }
    }

    @Override
    protected void unregisterListeners() {
        mListener = null;
    }
}
