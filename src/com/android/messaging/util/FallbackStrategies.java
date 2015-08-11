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
package com.android.messaging.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a generic and loose-coupled framework to execute one primary and multiple fallback
 * strategies for solving a given task.<p>
 * Basically, what would have been a nasty try-catch that's hard to separate and maintain:
 * <pre><code>
 * try {
 *     // doSomething() that may fail.
 * } catch (Exception ex) {
 *     try {
 *         // fallback1() that may fail.
 *     } catch (Exception ex2) {
 *         try {
 *             // fallback2() that may fail.
 *         } catch (Exception ex3) {
 *             // ...
 *         }
 *     }
 * }
 * </code></pre>
 * Now becomes:<br>
 * <pre><code>
 * FallbackStrategies
 *      .startWith(something)
 *      .thenTry(fallback1)
 *      .thenTry(fallback2)
 *      .execute();
 * </code></pre>
 */
public class FallbackStrategies<Input, Output> {
    public interface Strategy<Input, Output> {
        Output execute(Input params) throws Exception;
    }

    private final List<Strategy<Input, Output>> mChainedStrategies;

    private FallbackStrategies(final Strategy<Input, Output> primaryStrategy) {
        mChainedStrategies = new ArrayList<Strategy<Input, Output>>();
        mChainedStrategies.add(primaryStrategy);
    }

    public static <Input, Output> FallbackStrategies<Input, Output> startWith(
            final Strategy<Input, Output> primaryStrategy) {
        return new FallbackStrategies<Input, Output>(primaryStrategy);
    }

    public FallbackStrategies<Input, Output> thenTry(final Strategy<Input, Output> strategy) {
        Assert.isFalse(mChainedStrategies.isEmpty());
        mChainedStrategies.add(strategy);
        return this;
    }

    public Output execute(final Input params) {
        final int count = mChainedStrategies.size();
        for (int i = 0; i < count; i++) {
            final Strategy<Input, Output> strategy = mChainedStrategies.get(i);
            try {
                // If succeeds, this will directly return.
                return strategy.execute(params);
            } catch (Exception ex) {
                LogUtil.e(LogUtil.BUGLE_TAG, "Exceptions occured when executing strategy " +
                        strategy + (i < count - 1 ?
                                ", attempting fallback " + mChainedStrategies.get(i + 1) :
                                ", and running out of fallbacks."), ex);
                // This will fall through and continue with the next strategy (if any).
            }
        }
        // Running out of strategies, return null.
        // TODO: Should this accept user-defined fallback value other than null?
        return null;
    }
}
