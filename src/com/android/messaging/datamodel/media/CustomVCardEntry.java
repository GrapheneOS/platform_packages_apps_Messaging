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
package com.android.messaging.datamodel.media;

import android.accounts.Account;
import android.support.v4.util.ArrayMap;

import com.android.vcard.VCardEntry;
import com.android.vcard.VCardProperty;

import java.util.Map;

/**
 * Class which extends VCardEntry to add support for unknown properties.  Currently there is a TODO
 * to add this in the VCardEntry code, but we have to extend it to add the needed support
 */
public class CustomVCardEntry extends VCardEntry {
    // List of properties keyed by their name for easy lookup
    private final Map<String, VCardProperty> mAllProperties;

    public CustomVCardEntry(int vCardType, Account account) {
        super(vCardType, account);
        mAllProperties = new ArrayMap<String, VCardProperty>();
    }

    @Override
    public void addProperty(VCardProperty property) {
        super.addProperty(property);
        mAllProperties.put(property.getName(), property);
    }

    public VCardProperty getProperty(String name) {
        return mAllProperties.get(name);
    }
}
