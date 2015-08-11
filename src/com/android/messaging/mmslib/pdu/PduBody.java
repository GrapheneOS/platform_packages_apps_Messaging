/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.messaging.mmslib.pdu;

import java.util.Vector;

public class PduBody {
    private Vector<PduPart> mParts = null;

    /**
     * Constructor.
     */
    public PduBody() {
        mParts = new Vector<PduPart>();
    }

    /**
     * Appends the specified part to the end of this body.
     *
     * @param part part to be appended
     * @return true when success, false when fail
     * @throws NullPointerException when part is null
     */
    public boolean addPart(PduPart part) {
        if (null == part) {
            throw new NullPointerException();
        }

        return mParts.add(part);
    }

    /**
     * Inserts the specified part at the specified position.
     *
     * @param index index at which the specified part is to be inserted
     * @param part  part to be inserted
     * @throws NullPointerException when part is null
     */
    public void addPart(int index, PduPart part) {
        if (null == part) {
            throw new NullPointerException();
        }

        mParts.add(index, part);
    }

    /**
     * Remove all of the parts.
     */
    public void removeAll() {
        mParts.clear();
    }

    /**
     * Get the part at the specified position.
     *
     * @param index index of the part to return
     * @return part at the specified index
     */
    public PduPart getPart(int index) {
        return mParts.get(index);
    }

    /**
     * Get the number of parts.
     *
     * @return the number of parts
     */
    public int getPartsNum() {
        return mParts.size();
    }
}
