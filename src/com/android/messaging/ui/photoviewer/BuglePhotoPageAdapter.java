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
package com.android.messaging.ui.photoviewer;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.v4.app.FragmentManager;

import com.android.ex.photo.adapters.PhotoPagerAdapter;
import com.android.ex.photo.fragments.PhotoViewFragment;

public class BuglePhotoPageAdapter extends PhotoPagerAdapter {

    public BuglePhotoPageAdapter(Context context, FragmentManager fm, Cursor c, float maxScale,
            boolean thumbsFullScreen) {
        super(context, fm, c, maxScale, thumbsFullScreen);
    }

    @Override
    protected PhotoViewFragment createPhotoViewFragment(Intent intent, int position,
            boolean onlyShowSpinner) {
        return BuglePhotoViewFragment.newInstance(intent, position, onlyShowSpinner);
    }
}
