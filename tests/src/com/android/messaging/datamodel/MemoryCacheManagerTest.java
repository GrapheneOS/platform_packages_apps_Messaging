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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.messaging.datamodel.MemoryCacheManager.MemoryCache;

import org.mockito.Mockito;

@SmallTest
public class MemoryCacheManagerTest extends AndroidTestCase {

    public void testRegisterCachesGetReclaimed() {
        final MemoryCache mockMemoryCache = Mockito.mock(MemoryCache.class);
        final MemoryCache otherMockMemoryCache = Mockito.mock(MemoryCache.class);
        final MemoryCacheManager memoryCacheManager = new MemoryCacheManager();

        memoryCacheManager.registerMemoryCache(mockMemoryCache);
        memoryCacheManager.registerMemoryCache(otherMockMemoryCache);
        memoryCacheManager.reclaimMemory();
        memoryCacheManager.unregisterMemoryCache(otherMockMemoryCache);
        memoryCacheManager.reclaimMemory();

        Mockito.verify(mockMemoryCache, Mockito.times(2)).reclaim();
        Mockito.verify(otherMockMemoryCache, Mockito.times(1)).reclaim();
    }
}
