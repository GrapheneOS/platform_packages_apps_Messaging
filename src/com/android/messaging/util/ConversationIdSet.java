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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Utility class to make it easy to store multiple conversation id strings in a single string
 * with delimeters.
 */
public class ConversationIdSet extends HashSet<String> {
    private static final String JOIN_DELIMITER = "|";
    private static final String SPLIT_DELIMITER = "\\|";

    public ConversationIdSet() {
        super();
    }

    public ConversationIdSet(final Collection<String> asList) {
        super(asList);
    }

    public String first() {
        if (size() > 0) {
            return iterator().next();
        } else {
            return null;
        }
    }

    public static ConversationIdSet createSet(final String conversationIdSetString) {
        ConversationIdSet set = null;
        if (conversationIdSetString != null) {
            set = new ConversationIdSet(Arrays.asList(conversationIdSetString.split(
                    SPLIT_DELIMITER)));
        }
        return set;
    }

    public String getDelimitedString() {
        return OsUtil.joinFromSetWithDelimiter(this, JOIN_DELIMITER);
    }

    public static String join(final String conversationIdSet1, final String conversationIdSet2) {
        String joined = null;
        if (conversationIdSet1 == null) {
            joined = conversationIdSet2;
        } else if (conversationIdSet2 != null) {
            joined = conversationIdSet1 + JOIN_DELIMITER + conversationIdSet2;
        }
        return joined;
    }

}
