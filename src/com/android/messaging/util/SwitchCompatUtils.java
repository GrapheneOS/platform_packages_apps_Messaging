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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.graphics.drawable.DrawableWrapper;
import android.support.v7.widget.SwitchCompat;
import android.util.TypedValue;

/* Most methods in this file are copied from
 * v7/appcompat/src/android/support/v7/internal/widget/TintManager.java. It would be better if
 * we could have just extended the TintManager but this is a final class that we do not have
 * access to. */

/**
 * Util methods for the SwitchCompat widget
 */
public class SwitchCompatUtils {
    /**
     * Given a color and a SwitchCompat view, updates the SwitchCompat to appear with the appropiate
     * color when enabled and checked
     */
    public static void updateSwitchCompatColor(SwitchCompat switchCompat, final int color) {
        final Context context = switchCompat.getContext();
        final TypedValue typedValue = new TypedValue();

        switchCompat.setThumbDrawable(getColorTintedDrawable(switchCompat.getThumbDrawable(),
                getSwitchThumbColorStateList(context, color, typedValue),
                PorterDuff.Mode.MULTIPLY));

        switchCompat.setTrackDrawable(getColorTintedDrawable(switchCompat.getTrackDrawable(),
                getSwitchTrackColorStateList(context, color, typedValue), PorterDuff.Mode.SRC_IN));
    }

    private static Drawable getColorTintedDrawable(Drawable oldDrawable,
            final ColorStateList colorStateList, final PorterDuff.Mode mode) {
        final int[] thumbState = oldDrawable.isStateful() ? oldDrawable.getState() : null;
        if (oldDrawable instanceof DrawableWrapper) {
            oldDrawable = ((DrawableWrapper) oldDrawable).getWrappedDrawable();
        }
        final Drawable newDrawable = new TintDrawableWrapper(oldDrawable, colorStateList, mode);
        if (thumbState != null) {
            newDrawable.setState(thumbState);
        }
        return newDrawable;
    }

    private static ColorStateList getSwitchThumbColorStateList(final Context context,
            final int color, final TypedValue typedValue) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;
        // Disabled state
        states[i] = new int[] { -android.R.attr.state_enabled };
        colors[i] = getColor(Color.parseColor("#ffbdbdbd"), 1f);
        i++;
        states[i] = new int[] { android.R.attr.state_checked };
        colors[i] = color;
        i++;
        // Default enabled state
        states[i] = new int[0];
        colors[i] = getThemeAttrColor(context, typedValue,
                android.support.v7.appcompat.R.attr.colorSwitchThumbNormal);
        i++;
        return new ColorStateList(states, colors);
    }

    private static ColorStateList getSwitchTrackColorStateList(final Context context,
            final int color, final TypedValue typedValue) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;
        // Disabled state
        states[i] = new int[] { -android.R.attr.state_enabled };
        colors[i] = getThemeAttrColor(context, typedValue, android.R.attr.colorForeground, 0.1f);
        i++;
        states[i] = new int[] { android.R.attr.state_checked };
        colors[i] = getColor(color, 0.3f);
        i++;
        // Default enabled state
        states[i] = new int[0];
        colors[i] = getThemeAttrColor(context, typedValue, android.R.attr.colorForeground, 0.3f);
        i++;
        return new ColorStateList(states, colors);
    }

    private static int getThemeAttrColor(final Context context, final TypedValue typedValue,
            final int attr) {
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_INT
                    && typedValue.type <= TypedValue.TYPE_LAST_INT) {
                return typedValue.data;
            } else if (typedValue.type == TypedValue.TYPE_STRING) {
                return context.getResources().getColor(typedValue.resourceId);
            }
        }
        return 0;
    }

    private static int getThemeAttrColor(final Context context, final TypedValue typedValue,
            final int attr, final float alpha) {
        final int color = getThemeAttrColor(context, typedValue, attr);
        return getColor(color, alpha);
    }

    private static int getColor(int color, float alpha) {
        final int originalAlpha = Color.alpha(color);
        // Return the color, multiplying the original alpha by the disabled value
        return (color & 0x00ffffff) | (Math.round(originalAlpha * alpha) << 24);
    }
}
