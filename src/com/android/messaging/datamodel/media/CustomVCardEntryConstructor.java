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
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardInterpreter;
import com.android.vcard.VCardProperty;

import java.util.ArrayList;
import java.util.List;

public class CustomVCardEntryConstructor implements VCardInterpreter {

    public interface EntryHandler {
        /**
         * Called when the parsing started.
         */
        public void onStart();

        /**
         * The method called when one vCard entry is created. Children come before their parent in
         * nested vCard files.
         *
         * e.g.
         * In the following vCard, the entry for "entry2" comes before one for "entry1".
         * <code>
         * BEGIN:VCARD
         * N:entry1
         * BEGIN:VCARD
         * N:entry2
         * END:VCARD
         * END:VCARD
         * </code>
         */
        public void onEntryCreated(final CustomVCardEntry entry);

        /**
         * Called when the parsing ended.
         * Able to be use this method for showing performance log, etc.
         */
        public void onEnd();
    }

    /**
     * Represents current stack of VCardEntry. Used to support nested vCard (vCard 2.1).
     */
    private final List<CustomVCardEntry> mEntryStack = new ArrayList<CustomVCardEntry>();
    private CustomVCardEntry mCurrentEntry;

    private final int mVCardType;
    private final Account mAccount;

    private final List<EntryHandler> mEntryHandlers = new ArrayList<EntryHandler>();

    public CustomVCardEntryConstructor() {
        this(VCardConfig.VCARD_TYPE_V21_GENERIC, null);
    }

    public CustomVCardEntryConstructor(final int vcardType) {
        this(vcardType, null);
    }

    public CustomVCardEntryConstructor(final int vcardType, final Account account) {
        mVCardType = vcardType;
        mAccount = account;
    }

    public void addEntryHandler(EntryHandler entryHandler) {
        mEntryHandlers.add(entryHandler);
    }

    @Override
    public void onVCardStarted() {
        for (EntryHandler entryHandler : mEntryHandlers) {
            entryHandler.onStart();
        }
    }

    @Override
    public void onVCardEnded() {
        for (EntryHandler entryHandler : mEntryHandlers) {
            entryHandler.onEnd();
        }
    }

    public void clear() {
        mCurrentEntry = null;
        mEntryStack.clear();
    }

    @Override
    public void onEntryStarted() {
        mCurrentEntry = new CustomVCardEntry(mVCardType, mAccount);
        mEntryStack.add(mCurrentEntry);
    }

    @Override
    public void onEntryEnded() {
        mCurrentEntry.consolidateFields();
        for (EntryHandler entryHandler : mEntryHandlers) {
            entryHandler.onEntryCreated(mCurrentEntry);
        }

        final int size = mEntryStack.size();
        if (size > 1) {
            CustomVCardEntry parent = mEntryStack.get(size - 2);
            parent.addChild(mCurrentEntry);
            mCurrentEntry = parent;
        } else {
            mCurrentEntry = null;
        }
        mEntryStack.remove(size - 1);
    }

    @Override
    public void onPropertyCreated(VCardProperty property) {
        mCurrentEntry.addProperty(property);
    }
}