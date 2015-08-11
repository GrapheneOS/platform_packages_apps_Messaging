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
package com.android.messaging.ui.mediapicker;

import android.content.Context;
import android.provider.MediaStore.Images.Media;
import android.view.View;
import android.widget.CheckBox;

import com.android.messaging.FakeFactory;
import com.android.messaging.R;
import com.android.messaging.datamodel.FakeCursor;
import com.android.messaging.datamodel.FakeDataModel;
import com.android.messaging.datamodel.data.GalleryGridItemData;
import com.android.messaging.datamodel.data.TestDataFactory;
import com.android.messaging.ui.AsyncImageView;
import com.android.messaging.ui.ViewTest;
import com.android.messaging.util.UriUtil;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

public class GalleryGridItemViewTest extends ViewTest<GalleryGridItemView> {

    @Mock GalleryGridItemView.HostInterface mockHost;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getInstrumentation().getTargetContext();
        FakeFactory.register(context)
            .withDataModel(new FakeDataModel(context));
    }

    protected void verifyClickedItem(final View view, final GalleryGridItemData data) {
        Mockito.verify(mockHost).onItemClicked(view, data, false /* longClick */);
    }

    protected void verifyContent(
            final GalleryGridItemView view,
            final String imageUrl,
            final boolean showCheckbox,
            final boolean isSelected) {
        final AsyncImageView imageView = (AsyncImageView) view.findViewById(R.id.image);
        final CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkbox);

        assertNotNull(imageView);
        assertTrue(imageView.mImageRequestBinding.isBound());
        assertTrue(imageView.mImageRequestBinding.getData().getKey().startsWith(imageUrl));
        assertNotNull(checkBox);
        if (showCheckbox) {
            assertEquals(View.VISIBLE, checkBox.getVisibility());
            assertEquals(isSelected, checkBox.isChecked());
        } else {
            assertNotSame(View.VISIBLE, checkBox.getVisibility());
        }
    }

    public void testBind() {
        Mockito.when(mockHost.isMultiSelectEnabled()).thenReturn(false);
        Mockito.when(mockHost.isItemSelected(Matchers.<GalleryGridItemData>any()))
                .thenReturn(false);
        final GalleryGridItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getGalleryGridCursor();
        cursor.moveToFirst();
        final String path = (String) cursor.getAt(Media.DATA, 0);
        view.bind(cursor, mockHost);
        verifyContent(view, UriUtil.getUriForResourceFile(path).toString(),
                false, false);
    }

    public void testBindMultiSelectUnSelected() {
        Mockito.when(mockHost.isMultiSelectEnabled()).thenReturn(true);
        Mockito.when(mockHost.isItemSelected(Matchers.<GalleryGridItemData>any()))
                .thenReturn(false);
        final GalleryGridItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getGalleryGridCursor();
        cursor.moveToFirst();
        final String path = (String) cursor.getAt(Media.DATA, 0);
        view.bind(cursor, mockHost);
        verifyContent(view, UriUtil.getUriForResourceFile(path).toString(),
                true, false);
    }

    public void testBindMultiSelectSelected() {
        Mockito.when(mockHost.isMultiSelectEnabled()).thenReturn(true);
        Mockito.when(mockHost.isItemSelected(Matchers.<GalleryGridItemData>any()))
                .thenReturn(true);
        final GalleryGridItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getGalleryGridCursor();
        cursor.moveToFirst();
        final String path = (String) cursor.getAt(Media.DATA, 0);
        view.bind(cursor, mockHost);
        verifyContent(view, UriUtil.getUriForResourceFile(path).toString(),
                true, true);
    }

    public void testClick() {
        Mockito.when(mockHost.isMultiSelectEnabled()).thenReturn(false);
        Mockito.when(mockHost.isItemSelected(Matchers.<GalleryGridItemData>any()))
                .thenReturn(false);
        final GalleryGridItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getGalleryGridCursor();
        cursor.moveToFirst();
        view.bind(cursor, mockHost);
        view.performClick();
        verifyClickedItem(view, view.mData);
    }

    public void testBindTwice() {
        Mockito.when(mockHost.isMultiSelectEnabled()).thenReturn(true);
        Mockito.when(mockHost.isItemSelected(Matchers.<GalleryGridItemData>any()))
                .thenReturn(false);
        final GalleryGridItemView view = getView();
        final FakeCursor cursor = TestDataFactory.getGalleryGridCursor();

        cursor.moveToFirst();
        view.bind(cursor, mockHost);

        cursor.moveToNext();
        final String path = (String) cursor.getAt(Media.DATA, 1);
        view.bind(cursor, mockHost);
        verifyContent(view, UriUtil.getUriForResourceFile(path).toString(),
                true, false);
    }

    @Override
    protected int getLayoutIdForView() {
        return R.layout.gallery_grid_item_view;
    }
}
