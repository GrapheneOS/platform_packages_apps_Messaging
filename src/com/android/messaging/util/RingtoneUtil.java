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
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.R;

public class RingtoneUtil {
    /**
     * Return a ringtone Uri for the string representation passed in. Use the app
     * and system defaults as fallbacks
     * @param ringtoneString is the ringtone to resolve
     * @return the Uri of the ringtone or the fallback ringtone
     */
    public static Uri getNotificationRingtoneUri(String ringtoneString) {
        if (ringtoneString == null) {
            // No override specified, fall back to system-wide setting.
            final BuglePrefs prefs = BuglePrefs.getApplicationPrefs();
            final Context context = Factory.get().getApplicationContext();
            final String prefKey = context.getString(R.string.notification_sound_pref_key);
            ringtoneString = prefs.getString(prefKey, null);
        }

        if (!TextUtils.isEmpty(ringtoneString)) {
            // We have set a value, even if it is the default Uri at some point
            return Uri.parse(ringtoneString);
        } else if (ringtoneString == null) {
            // We have no setting specified (== null), so we default to the system default
            return Settings.System.DEFAULT_NOTIFICATION_URI;
        } else {
            // An empty string (== "") here is the result of selecting "None" as the ringtone
            return null;
        }
    }
}
