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
 * Base class for data objects that will be bound to a piece of the UI
 */
public abstract class BindableData {
    /**
     * Called by Binding during unbind to allow data to proactively unregister callbacks
     * Data instance should release all listeners that may call back to the host UI
     */
    protected abstract void unregisterListeners();

    /**
     * Key used to identify the piece of UI that the data is currently bound to
     */
    private String mBindingId;

    /**
     * Bind this data to the ui host - checks data is currently unbound
     */
    public void bind(final String bindingId) {
        if (isBound() || bindingId == null) {
            throw new IllegalStateException();
        }
        mBindingId = bindingId;
    }

    /**
     * Unbind this data from the ui host - checks that the data is currently bound to specified id
     */
    public void unbind(final String bindingId) {
        if (!isBound(bindingId)) {
            throw new IllegalStateException();
        }
        unregisterListeners();
        mBindingId = null;
    }

    /**
     * Check to see if the data is bound to anything
     *
     * TODO: This should be package private because it's supposed to only be used by Binding,
     * however, several classes call this directly.  We want the classes to track what they are
     * bound to.
     */
    protected boolean isBound() {
        return (mBindingId != null);
    }

    /**
     * Check to see if data is still bound with specified bindingId before calling over to ui
     */
    public boolean isBound(final String bindingId) {
        return (bindingId.equals(mBindingId));
    }
}
