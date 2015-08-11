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

package com.android.messaging.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Binder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.android.messaging.R;
import com.android.messaging.datamodel.media.AvatarGroupRequestDescriptor;
import com.android.messaging.datamodel.media.AvatarRequestDescriptor;
import com.android.messaging.datamodel.media.ImageRequestDescriptor;
import com.android.messaging.datamodel.media.ImageResource;
import com.android.messaging.datamodel.media.MediaRequest;
import com.android.messaging.datamodel.media.MediaResourceManager;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.LogUtil;

/**
 * Remote Views Factory for Bugle Widget.
 */
abstract class BaseWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    protected static final String TAG = LogUtil.BUGLE_WIDGET_TAG;

    protected static final int MAX_ITEMS_TO_SHOW = 25;

    /**
     * Lock to avoid race condition between widgets.
     */
    protected static final Object sWidgetLock = new Object();

    protected final Context mContext;
    protected final int mAppWidgetId;
    protected boolean mShouldShowViewMore;
    protected Cursor mCursor;
    protected final AppWidgetManager mAppWidgetManager;
    protected int mIconSize;
    protected ImageResource mAvatarResource;

    public BaseWidgetFactory(Context context, Intent intent) {
        mContext = context;
        mAppWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        mAppWidgetManager = AppWidgetManager.getInstance(context);
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "BaseWidgetFactory intent: " + intent + "widget id: " + mAppWidgetId);
        }
        mIconSize = (int) context.getResources()
                .getDimension(R.dimen.contact_icon_view_normal_size);

    }

    @Override
    public void onCreate() {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "onCreate");
        }
    }

    @Override
    public void onDestroy() {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "onDestroy");
        }
        synchronized (sWidgetLock) {
            if (mCursor != null && !mCursor.isClosed()) {
                mCursor.close();
                mCursor = null;
            }
        }
    }

    @Override
    public void onDataSetChanged() {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "onDataSetChanged");
        }
        synchronized (sWidgetLock) {
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
            }
            final long token = Binder.clearCallingIdentity();
            try {
                mCursor = doQuery();
                onLoadComplete();
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    protected abstract Cursor doQuery();

    /**
     * Returns the number of items that should be shown in the widget list.  This method also
     * updates the boolean that indicates whether the "show more" item should be shown.
     * @return the number of items to be displayed in the list.
     */
    @Override
    public int getCount() {
        synchronized (sWidgetLock) {
            if (mCursor == null) {
                if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                    LogUtil.v(TAG, "getCount: 0");
                }
                return 0;
            }
            final int count = getItemCount();
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "getCount: " + count);
            }
            mShouldShowViewMore = count < mCursor.getCount();
            return count + (mShouldShowViewMore ? 1 : 0);
        }
    }

    /**
     * Returns the number of messages that should be shown in the widget.  This method
     * doesn't update the boolean that indicates whether the "show more" item should be included
     * in the list.
     * @return
     */
    protected int getItemCount() {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "getItemCount: " + mCursor.getCount());
        }
        return Math.min(mCursor.getCount(), MAX_ITEMS_TO_SHOW);
    }

    /*
     * Make the given text bold if the item is unread
     */
    protected CharSequence boldifyIfUnread(CharSequence text, final boolean unread) {
        if (!unread) {
            return text;
        }
        final SpannableStringBuilder builder = new SpannableStringBuilder(text);
        builder.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    protected Bitmap getAvatarBitmap(final Uri avatarUri) {
        final String avatarType = avatarUri == null ?
                null : AvatarUriUtil.getAvatarType(avatarUri);
        ImageRequestDescriptor descriptor;
        if (AvatarUriUtil.TYPE_GROUP_URI.equals(avatarType)) {
            descriptor = new AvatarGroupRequestDescriptor(avatarUri, mIconSize, mIconSize);
        } else {
            descriptor = new AvatarRequestDescriptor(avatarUri, mIconSize, mIconSize);
        }

        final MediaRequest<ImageResource> imageRequest =
                descriptor.buildSyncMediaRequest(mContext);
        final ImageResource imageResource =
                MediaResourceManager.get().requestMediaResourceSync(imageRequest);
        if (imageResource != null) {
            setAvatarResource(imageResource);
            return mAvatarResource.getBitmap();
        } else {
            releaseAvatarResource();
            return null;
        }
    }

    /**
     * @return the "View more messages" view. When the user taps this item, they're
     * taken to the conversation in Bugle.
     */
    abstract protected RemoteViews getViewMoreItemsView();

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private void onLoadComplete() {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "onLoadComplete");
        }
        final RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(),
                getMainLayoutId());
        mAppWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, remoteViews);
    }

    protected abstract int getMainLayoutId();

    private void setAvatarResource(final ImageResource resource) {
        if (mAvatarResource != resource) {
            // Clear out any information for what is currently used
            releaseAvatarResource();
            mAvatarResource = resource;
        }
    }

    private void releaseAvatarResource() {
        if (mAvatarResource != null) {
            mAvatarResource.release();
        }
        mAvatarResource = null;
    }
}
