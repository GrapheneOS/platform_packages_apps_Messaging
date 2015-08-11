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

/**
 * A BindableData that's only used to be bound once. If the client needs to rebind, it needs
 * to create a new instance of the BindableOnceData.
 */
public abstract class BindableOnceData extends BindableData {
    private boolean boundOnce = false;

    @Override
    public void bind(final String bindingId) {
        // Ensures that we can't re-bind again after the first binding.
        if (boundOnce) {
            throw new IllegalStateException();
        }
        super.bind(bindingId);
        boundOnce = true;
    }

    /**
     * Checks if the instance is bound to anything.
     */
    @Override
    public boolean isBound() {
        return super.isBound();
    }
}
