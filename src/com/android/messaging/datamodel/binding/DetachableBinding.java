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

import com.android.messaging.util.Assert;

/**
 * An extension on {@link Binding} that allows for temporary data detachment from the UI component.
 * This is used when, instead of destruction or data rebinding, the owning UI undergoes a
 * "detached from window" -> "re-attached to window" transition, in which case we want to
 * temporarily unbind the data and remember it so that it can be rebound when the UI is re-attached
 * to window later.
 */
public class DetachableBinding<T extends BindableData> extends Binding<T> {
    private T mDetachedData;

    DetachableBinding(Object owner) {
        super(owner);
    }

    @Override
    public void bind(T data) {
        super.bind(data);
        // Rebinding before re-attaching. Pre-emptively throw away the detached data because
        // it's now stale.
        mDetachedData = null;
    }

    public void detach() {
        Assert.isNull(mDetachedData);
        Assert.isTrue(isBound());
        mDetachedData = getData();
        unbind();
    }

    public void reAttachIfPossible() {
        if (mDetachedData != null) {
            Assert.isFalse(isBound());
            bind(mDetachedData);
            mDetachedData = null;
        }
    }
}
