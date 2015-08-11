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

package com.android.messaging.ui;


import android.content.Context;
import android.net.Uri;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.messaging.FakeFactory;
import com.android.messaging.datamodel.data.MessagePartData;

import java.util.Arrays;
import java.util.Collections;

@MediumTest
public class MultiAttachmentLayoutTest extends ViewTest<MultiAttachmentLayout> {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getInstrumentation().getTargetContext();
        FakeFactory.register(context);
    }

    @Override
    protected MultiAttachmentLayout getView() {
        if (mView == null) {
            // View creation deferred (typically until test time) so that factory/appcontext is
            // ready.
            mView = new MultiAttachmentLayout(getActivity(), null);
            mView.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        }
        return mView;
    }

    protected void verifyContent(
            final MultiAttachmentLayout view,
            final int imageCount,
            final int plusCount) {
        final int count = view.getChildCount();
        int actualImageCount = 0;
        final boolean needPlusText = plusCount > 0;
        boolean hasPlusText = false;
        for (int i = 0; i < count; i++) {
            final View child = view.getChildAt(i);
            if (child instanceof AsyncImageView) {
                actualImageCount++;
            } else if (child instanceof TextView) {
                assertTrue(plusCount > 0);
                assertTrue(((TextView) child).getText().toString().contains("" + plusCount));
                hasPlusText = true;
            } else {
                // Nothing other than image and overflow text view should appear in this layout.
                fail("unexpected view in layout. view = " + child);
            }
        }
        assertEquals(imageCount, actualImageCount);
        assertEquals(needPlusText, hasPlusText);
    }

    public void testBindTwoAttachments() {
        final MultiAttachmentLayout view = getView();
        final MessagePartData testAttachment1 = MessagePartData.createMediaMessagePart(
                "image/jpeg", Uri.parse("content://uri1"), 100, 100);
        final MessagePartData testAttachment2 = MessagePartData.createMediaMessagePart(
                "image/jpeg", Uri.parse("content://uri2"), 100, 100);

        view.bindAttachments(createAttachmentList(testAttachment1, testAttachment2),
                null /* transitionRect */, 2);
        verifyContent(view, 2, 0);
    }

    public void testBindFiveAttachments() {
        final MultiAttachmentLayout view = getView();
        final MessagePartData testAttachment1 = MessagePartData.createMediaMessagePart(
                "image/jpeg", Uri.parse("content://uri1"), 100, 100);
        final MessagePartData testAttachment2 = MessagePartData.createMediaMessagePart(
                "image/jpeg", Uri.parse("content://uri2"), 100, 100);
        final MessagePartData testAttachment3 = MessagePartData.createMediaMessagePart(
                "image/jpeg", Uri.parse("content://uri3"), 100, 100);
        final MessagePartData testAttachment4 = MessagePartData.createMediaMessagePart(
                "image/jpeg", Uri.parse("content://uri4"), 100, 100);
        final MessagePartData testAttachment5 = MessagePartData.createMediaMessagePart(
                "image/jpeg", Uri.parse("content://uri5"), 100, 100);

        view.bindAttachments(createAttachmentList(testAttachment1, testAttachment2, testAttachment3,
                testAttachment4, testAttachment5), null /* transitionRect */, 5);
        verifyContent(view, 4, 1);
    }

    public void testBindTwice() {
        // Put the above two tests together so we can simulate binding twice.
        testBindTwoAttachments();
        testBindFiveAttachments();
    }

    private Iterable<MessagePartData> createAttachmentList(final MessagePartData... attachments) {
        return Collections.unmodifiableList(Arrays.asList(attachments));
    }

    @Override
    protected int getLayoutIdForView() {
        return 0;   // We construct the view with getView().
    }
}