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

package com.android.messaging.datamodel.binding;

import java.util.concurrent.atomic.AtomicLong;

public class Binding<T extends BindableData> extends BindingBase<T> {
    private static AtomicLong sBindingIdx = new AtomicLong(System.currentTimeMillis() * 1000);

    private String mBindingId;
    private T mData;
    private final Object mOwner;
    private boolean mWasBound;

    /**
     * Initialize a binding instance - the owner is typically the containing class
     */
    Binding(final Object owner) {
        mOwner = owner;
    }

    @Override
    public T getData() {
        ensureBound();
        return mData;
    }

    @Override
    public boolean isBound() {
        return (mData != null && mData.isBound(mBindingId));
    }

    @Override
    public boolean isBound(final T data) {
        return (isBound() && data == mData);
    }

    @Override
    public void ensureBound() {
        if (!isBound()) {
            throw new IllegalStateException("not bound; wasBound = " + mWasBound);
        }
    }

    @Override
    public void ensureBound(final T data) {
        if (!isBound()) {
            throw new IllegalStateException("not bound; wasBound = " + mWasBound);
        } else if (data != mData) {
            throw new IllegalStateException("not bound to correct data " + data + " vs " + mData);
        }
    }

    @Override
    public String getBindingId() {
        return mBindingId;
    }

    public void bind(final T data) {
        // Check both this binding and the data not already bound
        if (mData != null || data.isBound()) {
            throw new IllegalStateException("already bound when binding to " + data);
        }
        // Generate a unique identifier for this bind call
        mBindingId = Long.toHexString(sBindingIdx.getAndIncrement());
        data.bind(mBindingId);
        mData = data;
        mWasBound = true;
    }

    public void unbind() {
        // Check this binding is bound and that data is bound to this binding
        if (mData == null || !mData.isBound(mBindingId)) {
            throw new IllegalStateException("not bound when unbind");
        }
        mData.unbind(mBindingId);
        mData = null;
        mBindingId = null;
    }
}
