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

import android.view.animation.Interpolator;

/**
 * Class that acts as an interpolator to match the cubic-bezier css timing function where p0 is
 * fixed at 0,0 and p3 is fixed at 1,1
 */
public class CubicBezierInterpolator implements Interpolator {
    private final float mX1;
    private final float mY1;
    private final float mX2;
    private final float mY2;

    public CubicBezierInterpolator(final float x1, final float y1, final float x2, final float y2) {
        mX1 = x1;
        mY1 = y1;
        mX2 = x2;
        mY2 = y2;
    }

    @Override
    public float getInterpolation(float v) {
        return getY(getTForXValue(v));
    }

    private float getX(final float t) {
        return getCoordinate(t, mX1, mX2);
    }

    private float getY(final float t) {
        return getCoordinate(t, mY1, mY2);
    }

    private float getCoordinate(float t, float p1, float p2) {
        // Special case start and end.
        if (t == 0.0f || t == 1.0f) {
            return t;
        }

        // Step one - from 4 points to 3.
        float ip0 = linearInterpolate(0, p1, t);
        float ip1 = linearInterpolate(p1, p2, t);
        float ip2 = linearInterpolate(p2, 1, t);

        // Step two - from 3 points to 2.
        ip0 = linearInterpolate(ip0, ip1, t);
        ip1 = linearInterpolate(ip1, ip2, t);

        // Final step - last point.
        return linearInterpolate(ip0, ip1, t);
    }

    private float linearInterpolate(float a, float b, float progress) {
        return a + (b - a) * progress;
    }

    private float getTForXValue(final float x) {
        final float epsilon = 1e-6f;
        final int iterations = 8;

        if (x <= 0.0f) {
            return 0.0f;
        } else if (x >= 1.0f) {
            return 1.0f;
        }

        // Try gradient descent to solve for t. If it works, it is very fast.
        float t = x;
        float minT = 0.0f;
        float maxT = 1.0f;
        float value = 0.0f;
        for (int i = 0; i < iterations; i++) {
            value = getX(t);
            double derivative = (getX(t + epsilon) - value) / epsilon;
            if (Math.abs(value - x) < epsilon) {
                return t;
            } else if (Math.abs(derivative) < epsilon) {
                break;
            } else {
                if (value < x) {
                    minT = t;
                } else {
                    maxT = t;
                }
                t -= (value - x) / derivative;
            }
        }

        // If the gradient descent got stuck in a local minimum, e.g. because the
        // derivative was close to 0, use an interval bisection instead.
        for (int i = 0; Math.abs(value - x) > epsilon && i < iterations; i++) {
            if (value < x) {
                minT = t;
                t = (t + maxT) / 2.0f;
            } else {
                maxT = t;
                t = (t + minT) / 2.0f;
            }
            value = getX(t);
        }
        return t;
    }
}
