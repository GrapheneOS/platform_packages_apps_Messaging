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

import android.view.View;
import android.view.ViewGroup;

/**
 * Holds reusable View(s) for a {@link FixedViewPagerAdapter} to display a page. By using
 * reusable Views inside ViewPagers this allows us to get rid of nested fragments and the messy
 * activity lifecycle problems they entail.
 */
public interface PagerViewHolder extends PersistentInstanceState {
    /** Instructs the pager to clean up any view related resources
     * @return the destroyed View so that the adapter may remove it from the container, or
     * null if no View has been created. */
    View destroyView();

    /** @return The view that presents the page view to the user */
    View getView(ViewGroup container);
}
