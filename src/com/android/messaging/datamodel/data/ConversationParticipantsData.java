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

package com.android.messaging.datamodel.data;

import android.database.Cursor;
import android.support.v4.util.SimpleArrayMap;

import com.android.messaging.util.Assert;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A class that contains the list of all participants potentially involved in a conversation.
 * Includes both the participant records for each participant referenced in conversation
 * participants table (i.e. "other" phone numbers) plus all participants representing self
 * (i.e. one per sim recorded in the subscription manager db).
 */
public class ConversationParticipantsData implements Iterable<ParticipantData> {
    // A map from a participant id to a participant
    private final SimpleArrayMap<String, ParticipantData> mConversationParticipantsMap;
    private int mParticipantCountExcludingSelf = 0;

    public ConversationParticipantsData() {
        mConversationParticipantsMap = new SimpleArrayMap<String, ParticipantData>();
    }

    public void bind(final Cursor cursor) {
        mConversationParticipantsMap.clear();
        mParticipantCountExcludingSelf = 0;
        if (cursor != null) {
            while (cursor.moveToNext()) {
                final ParticipantData newParticipant = ParticipantData.getFromCursor(cursor);
                if (!newParticipant.isSelf()) {
                    mParticipantCountExcludingSelf++;
                }
                mConversationParticipantsMap.put(newParticipant.getId(), newParticipant);
            }
        }
    }

    @VisibleForTesting
    ParticipantData getParticipantById(final String participantId) {
        return mConversationParticipantsMap.get(participantId);
    }

    ArrayList<ParticipantData> getParticipantListExcludingSelf() {
        final ArrayList<ParticipantData> retList =
                new ArrayList<ParticipantData>(mConversationParticipantsMap.size());
        for (int i = 0; i < mConversationParticipantsMap.size(); i++) {
            final ParticipantData participant = mConversationParticipantsMap.valueAt(i);
            if (!participant.isSelf()) {
                retList.add(participant);
            }
        }
        return retList;
    }

    /**
     * For a 1:1 conversation return the other (not self) participant
     */
    public ParticipantData getOtherParticipant() {
        if (mParticipantCountExcludingSelf == 1) {
            for (int i = 0; i < mConversationParticipantsMap.size(); i++) {
                final ParticipantData participant = mConversationParticipantsMap.valueAt(i);
                if (!participant.isSelf()) {
                    return participant;
                }
            }
            Assert.fail("Could not find other participant");
        }
        return null;
    }

    public int getNumberOfParticipantsExcludingSelf() {
        return mParticipantCountExcludingSelf;
    }

    public boolean isLoaded() {
        return !mConversationParticipantsMap.isEmpty();
    }

    @Override
    public Iterator<ParticipantData> iterator() {
        return new Iterator<ParticipantData>() {
            private int mCurrentIndex = -1;

            @Override
            public boolean hasNext() {
                return mCurrentIndex < mConversationParticipantsMap.size() - 1;
            }

            @Override
            public ParticipantData next() {
                mCurrentIndex++;
                if (mCurrentIndex >= mConversationParticipantsMap.size()) {
                    throw new NoSuchElementException();
                }
                return mConversationParticipantsMap.valueAt(mCurrentIndex);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
