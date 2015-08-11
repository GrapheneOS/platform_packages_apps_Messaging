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
package com.android.messaging.sms;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Hacky way to call the hidden SystemProperties class API
 */
class SystemProperties {
    private static Method sSystemPropertiesGetMethod = null;

    public static String get(final String name) {
        if (sSystemPropertiesGetMethod == null) {
            try {
                final Class systemPropertiesClass = Class.forName("android.os.SystemProperties");
                if (systemPropertiesClass != null) {
                    sSystemPropertiesGetMethod =
                            systemPropertiesClass.getMethod("get", String.class);
                }
            } catch (final ClassNotFoundException e) {
                // Nothing to do
            } catch (final NoSuchMethodException e) {
                // Nothing to do
            }
        }
        if (sSystemPropertiesGetMethod != null) {
            try {
                return (String) sSystemPropertiesGetMethod.invoke(null, name);
            } catch (final IllegalArgumentException e) {
                // Nothing to do
            } catch (final IllegalAccessException e) {
                // Nothing to do
            } catch (final InvocationTargetException e) {
                // Nothing to do
            }
        }
        return null;
    }
}
