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

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.google.common.annotations.VisibleForTesting;

public class ImeUtil {
    public interface ImeStateObserver {
        void onImeStateChanged(boolean imeOpen);
    }

    public interface ImeStateHost {
        void onDisplayHeightChanged(int heightMeasureSpec);
        void registerImeStateObserver(ImeUtil.ImeStateObserver observer);
        void unregisterImeStateObserver(ImeUtil.ImeStateObserver observer);
        boolean isImeOpen();
    }

    private static volatile ImeUtil sInstance;

    // Used to clear the static cached instance of ImeUtil during testing.  This is necessary
    // because a previous test may have installed a mocked instance (or vice versa).
    public static void clearInstance() {
        sInstance = null;
    }
    public static ImeUtil get() {
        if (sInstance == null) {
            synchronized (ImeUtil.class) {
                if (sInstance == null) {
                    sInstance = new ImeUtil();
                }
            }
        }
        return sInstance;
    }

    @VisibleForTesting
    public static void set(final ImeUtil imeUtil) {
        sInstance = imeUtil;
    }

    public void hideImeKeyboard(@NonNull final Context context, @NonNull final View v) {
        Assert.notNull(context);
        Assert.notNull(v);

        final InputMethodManager inputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0 /* flags */);
        }
    }

    public void showImeKeyboard(@NonNull final Context context, @NonNull final View v) {
        Assert.notNull(context);
        Assert.notNull(v);

        final InputMethodManager inputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            v.requestFocus();
            inputMethodManager.showSoftInput(v, 0 /* flags */);
        }
    }

    public static void hideSoftInput(@NonNull final Context context, @NonNull final View v) {
        final InputMethodManager inputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
