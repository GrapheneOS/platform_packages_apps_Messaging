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

package com.android.messaging.datamodel;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.BugleTestCase;
import com.android.messaging.datamodel.binding.BindableData;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.binding.ImmutableBindingRef;

/**
 * Test binding
 */
@SmallTest
public class BindingTest extends BugleTestCase {
    private static final Object TEST_DATA_ID = "myDataId";
    private static final Object YOUR_DATA_ID = "yourDataId";

    public void testBindingStartsUnbound() {
        final Binding<TestBindableData> binding = BindingBase.createBinding(this);
        assertNull(binding.getBindingId());
    }

    public void testDataStartsUnbound() {
        final TestBindableData data = new TestBindableData(TEST_DATA_ID);
        assertFalse(data.isBound());
    }

    public void testBindingUpdatesDataAndBindee() {
        final Binding<TestBindableData> binding = BindingBase.createBinding(this);
        final TestBindableData data = new TestBindableData(TEST_DATA_ID);
        binding.bind(data);
        assertTrue(binding.isBound());
        assertEquals(binding.getData(), data);
        assertTrue(data.isBound(binding.getBindingId()));
        assertFalse(data.isBound("SomeRandomString"));
        assertNotNull(binding.getBindingId());
        assertFalse(data.mListenersUnregistered);
    }

    public void testRebindingFails() {
        final Binding<TestBindableData> binding = BindingBase.createBinding(this);
        final TestBindableData yours = new TestBindableData(YOUR_DATA_ID);
        binding.bind(yours);
        assertEquals(binding.getData(), yours);
        assertTrue(yours.isBound(binding.getBindingId()));
        final TestBindableData data = new TestBindableData(TEST_DATA_ID);
        try {
            binding.bind(data);
            fail();
        } catch (final IllegalStateException e) {
        }
        assertTrue(binding.isBound());
        assertEquals(binding.getData(), yours);
        assertTrue(yours.isBound(binding.getBindingId()));
    }

    public void testUnbindingClearsDataAndBindee() {
        final Binding<TestBindableData> binding = BindingBase.createBinding(this);
        final TestBindableData data = new TestBindableData(TEST_DATA_ID);
        binding.bind(data);
        assertTrue(data.isBound(binding.getBindingId()));
        assertTrue(binding.isBound());
        binding.unbind();
        try {
            final TestBindableData other = binding.getData();
            fail();
        } catch (final IllegalStateException e) {
        }
        assertFalse(data.isBound());
        assertNull(binding.getBindingId());
        assertTrue(data.mListenersUnregistered);
    }

    public void testUnbindingAndRebinding() {
        final Binding<TestBindableData> binding = BindingBase.createBinding(this);
        final TestBindableData yours = new TestBindableData(YOUR_DATA_ID);
        binding.bind(yours);
        assertEquals(binding.getData(), yours);
        assertTrue(yours.isBound(binding.getBindingId()));
        binding.unbind();
        assertFalse(yours.isBound());
        assertNull(binding.getBindingId());

        final TestBindableData data = new TestBindableData(TEST_DATA_ID);
        binding.bind(data);
        assertEquals(binding.getData(), data);
        assertTrue(data.isBound(binding.getBindingId()));
        assertFalse(data.isBound("SomeRandomString"));
        assertTrue(binding.isBound());
        assertNotNull(binding.getBindingId());
    }

    public void testBindingReference() {
        final Binding<TestBindableData> binding = BindingBase.createBinding(this);
        final TestBindableData data = new TestBindableData(TEST_DATA_ID);
        binding.bind(data);
        assertEquals(binding.getData(), data);
        assertTrue(data.isBound(binding.getBindingId()));

        final ImmutableBindingRef<TestBindableData> bindingRef =
                BindingBase.createBindingReference(binding);
        assertEquals(bindingRef.getData(), data);
        assertTrue(data.isBound(bindingRef.getBindingId()));

        binding.unbind();
        assertFalse(binding.isBound());
        assertNull(binding.getBindingId());
        assertFalse(bindingRef.isBound());
        assertNull(bindingRef.getBindingId());
    }

    static class TestBindableData extends BindableData {
        private final Object mDataId;
        public boolean mListenersUnregistered;

        public TestBindableData(final Object dataId) {
            mDataId = dataId;
            mListenersUnregistered = false;
        }

        @Override
        public void unregisterListeners() {
            mListenersUnregistered = true;
        }

        @Override
        public boolean isBound() {
            return super.isBound();
        }
    }
}
