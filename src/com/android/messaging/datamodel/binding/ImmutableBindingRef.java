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
 * A immutable wrapper around a Binding object. Callers can only access readonly methods like
 * getData(), isBound() and ensureBound() but not bind() and unbind(). This is used for MVC pattern
 * where both the View and the Controller needs access to a centrally bound Model object. The View
 * is the one that owns the bind/unbind logic of the Binding, whereas controller only serves as a
 * consumer.
 */
public class ImmutableBindingRef<T extends BindableData> extends BindingBase<T> {
    /**
     * The referenced, read-only binding object.
     */
    private final BindingBase<T> mBinding;

    /**
     * Hidden ctor.
     */
    ImmutableBindingRef(final BindingBase<T> binding) {
        mBinding = resolveBinding(binding);
    }

    @Override
    public T getData() {
        return mBinding.getData();
    }

    @Override
    public boolean isBound() {
        return mBinding.isBound();
    }

    @Override
    public boolean isBound(final T data) {
        return mBinding.isBound(data);
    }

    @Override
    public void ensureBound() {
        mBinding.ensureBound();
    }

    @Override
    public void ensureBound(final T data) {
        mBinding.ensureBound(data);
    }

    @Override
    public String getBindingId() {
        return mBinding.getBindingId();
    }

    /**
     * Resolve the source binding to the real BindingImpl it's referencing. This avoids the
     * redundancy of multiple wrapper calls when creating a binding reference from an existing
     * binding reference.
     */
    private BindingBase<T> resolveBinding(final BindingBase<T> binding) {
        BindingBase<T> resolvedBinding = binding;
        while (resolvedBinding instanceof ImmutableBindingRef<?>) {
            resolvedBinding = ((ImmutableBindingRef<T>) resolvedBinding).mBinding;
        }
        Assert.isTrue(resolvedBinding instanceof Binding<?>);
        return resolvedBinding;
    }
}
