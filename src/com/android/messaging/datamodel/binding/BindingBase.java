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
 * The binding class keeps track of a binding between a ui component and an item of BindableData
 * It allows each side to ensure that when it communicates with the other they are still bound
 * together.
 * NOTE: Ensure that the UI component uses the same binding instance for it's whole lifetime
 *  (DO NOT CREATE A NEW BINDING EACH TIME A NEW PIECE OF DATA IS BOUND)...
 *
 * The ui component owns the binding instance.
 * It can use it [isBound(data)] to see if the binding still binds to the right piece of data
 *
 * Upon binding the data is informed of a unique binding key generated in this class and can use
 * that to ensure that it is still issuing callbacks to the right piece of ui.
 */
public abstract class BindingBase<T extends BindableData> {
    /**
     * Creates a new exclusively owned binding for the owner object.
     */
    public static <T extends BindableData> Binding<T> createBinding(final Object owner) {
        return new Binding<T>(owner);
    }

    /**
     * Creates a new read-only binding referencing the source binding object.
     * TODO: We may want to refcount the Binding references, so that when the binding owner
     * calls unbind() when there's still outstanding references we can catch it.
     */
    public static <T extends BindableData> ImmutableBindingRef<T> createBindingReference(
            final BindingBase<T> srcBinding) {
        return new ImmutableBindingRef<T>(srcBinding);
    }

    /**
     * Creates a detachable binding for the owner object. Use this if your owner object is a UI
     * component that may undergo a "detached from window" -> "re-attached to window" transition.
     */
    public static <T extends BindableData> DetachableBinding<T> createDetachableBinding(
            final Object owner) {
        return new DetachableBinding<T>(owner);
    }

    public abstract T getData();

    /**
     * Check if binding connects to the specified data instance
     */
    public abstract boolean isBound();

    /**
     * Check if binding connects to the specified data instance
     */
    public abstract boolean isBound(final T data);

    /**
     * Throw if binding connects to the specified data instance
     */
    public abstract void ensureBound();

    /**
     * Throw if binding connects to the specified data instance
     */
    public abstract void ensureBound(final T data);

    /**
     * Return the binding id for this binding (will be null if not bound)
     */
    public abstract String getBindingId();
}
