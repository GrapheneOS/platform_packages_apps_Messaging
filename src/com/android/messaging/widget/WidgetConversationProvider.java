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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import com.android.messaging.R;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.WidgetPickConversationActivity;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;

public class WidgetConversationProvider extends BaseWidgetProvider {
    public static final String ACTION_NOTIFY_MESSAGES_CHANGED =
            "com.android.Bugle.intent.action.ACTION_NOTIFY_MESSAGES_CHANGED";

    public static final int WIDGET_CONVERSATION_TEMPLATE_REQUEST_CODE = 1985;
    public static final int WIDGET_CONVERSATION_REPLY_CODE = 1987;

    // Intent extras
    public static final String UI_INTENT_EXTRA_RECIPIENT = "recipient";
    public static final String UI_INTENT_EXTRA_ICON = "icon";

    /**
     * Update the widget appWidgetId
     */
    @Override
    protected void updateWidget(final Context context, final int appWidgetId) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "updateWidget appWidgetId: " + appWidgetId);
        }
        if (OsUtil.hasRequiredPermissions()) {
            rebuildWidget(context, appWidgetId);
        } else {
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId,
                    UiUtils.getWidgetMissingPermissionView(context));
        }
    }

    @Override
    protected String getAction() {
        return ACTION_NOTIFY_MESSAGES_CHANGED;
    }

    @Override
    protected int getListId() {
        return R.id.message_list;
    }

    public static void rebuildWidget(final Context context, final int appWidgetId) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "WidgetConversationProvider.rebuildWidget appWidgetId: " + appWidgetId);
        }
        final RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                R.layout.widget_conversation);
        PendingIntent clickIntent;
        final UIIntents uiIntents = UIIntents.get();
        if (!isWidgetConfigured(appWidgetId)) {
            // Widget has not been configured yet. Hide the normal UI elements and show the
            // configuration view instead.
            remoteViews.setViewVisibility(R.id.widget_label, View.GONE);
            remoteViews.setViewVisibility(R.id.message_list, View.GONE);
            remoteViews.setViewVisibility(R.id.launcher_icon, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_configuration, View.VISIBLE);

            remoteViews.setOnClickPendingIntent(R.id.widget_configuration,
                    uiIntents.getWidgetPendingIntentForConfigurationActivity(context, appWidgetId));

            // On click intent for Goto Conversation List
            clickIntent = uiIntents.getWidgetPendingIntentForConversationListActivity(context);
            remoteViews.setOnClickPendingIntent(R.id.widget_header, clickIntent);

            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "WidgetConversationProvider.rebuildWidget appWidgetId: " +
                        appWidgetId + " going into configure state");
            }
        } else {
            remoteViews.setViewVisibility(R.id.widget_label, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.message_list, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.launcher_icon, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_configuration, View.GONE);

            final String conversationId =
                    WidgetPickConversationActivity.getConversationIdPref(appWidgetId);
            final boolean isMainThread =  Looper.myLooper() == Looper.getMainLooper();
            // If we're running on the UI thread, we can't do the DB access needed to get the
            // conversation data. We'll do excute this again off of the UI thread.
            final ConversationListItemData convData = isMainThread ?
                    null : getConversationData(context, conversationId);

            // Launch an intent to avoid ANRs
            final Intent intent = new Intent(context, WidgetConversationService.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.putExtra(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID, conversationId);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            remoteViews.setRemoteAdapter(appWidgetId, R.id.message_list, intent);

            remoteViews.setTextViewText(R.id.widget_label, convData != null ?
                    convData.getName() : context.getString(R.string.app_name));

            // On click intent for Goto Conversation List
            clickIntent = uiIntents.getWidgetPendingIntentForConversationListActivity(context);
            remoteViews.setOnClickPendingIntent(R.id.widget_goto_conversation_list, clickIntent);

            // Open the conversation when click on header
            clickIntent = uiIntents.getWidgetPendingIntentForConversationActivity(context,
                    conversationId, WIDGET_CONVERSATION_REQUEST_CODE);
            remoteViews.setOnClickPendingIntent(R.id.widget_header, clickIntent);

            // On click intent for Conversation
            // Note: the template intent has to be a "naked" intent without any extras. It turns out
            // that if the template intent does have extras, those particular extras won't get
            // replaced by the fill-in intent on each list item.
            clickIntent = uiIntents.getWidgetPendingIntentForConversationActivity(context,
                    conversationId, WIDGET_CONVERSATION_TEMPLATE_REQUEST_CODE);
            remoteViews.setPendingIntentTemplate(R.id.message_list, clickIntent);

            if (isMainThread) {
                // We're running on the UI thread and we couldn't update all the parts of the
                // widget dependent on ConversationListItemData. However, we have to update
                // the widget regardless, even with those missing pieces. Here we update the
                // widget again in the background.
                SafeAsyncTask.executeOnThreadPool(new Runnable() {
                    @Override
                    public void run() {
                        rebuildWidget(context, appWidgetId);
                    }
                });
            }
        }

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews);

    }

    /*
     * notifyMessagesChanged called when the conversation changes so the widget will
     * update and reflect the changes
     */
    public static void notifyMessagesChanged(final Context context, final String conversationId) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "notifyMessagesChanged");
        }
        final Intent intent = new Intent(ACTION_NOTIFY_MESSAGES_CHANGED);
        intent.putExtra(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID, conversationId);
        context.sendBroadcast(intent);
    }

    /*
     * notifyConversationDeleted is called when a conversation is deleted. Look through all the
     * widgets and if they're displaying that conversation, force the widget into its
     * configuration state.
     */
    public static void notifyConversationDeleted(final Context context,
            final String conversationId) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "notifyConversationDeleted convId: " + conversationId);
        }

        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        for (final int appWidgetId : appWidgetManager.getAppWidgetIds(new ComponentName(context,
                WidgetConversationProvider.class))) {
            // Retrieve the persisted information for this widget from preferences.
            final String widgetConvId =
                    WidgetPickConversationActivity.getConversationIdPref(appWidgetId);

            if (widgetConvId == null || widgetConvId.equals(conversationId)) {
                if (widgetConvId != null) {
                    WidgetPickConversationActivity.deleteConversationIdPref(appWidgetId);
                }
                rebuildWidget(context, appWidgetId);
            }
        }
    }

    /*
     * notifyConversationRenamed is called when a conversation is renamed. Look through all the
     * widgets and if they're displaying that conversation, force the widget to rebuild itself
     */
    public static void notifyConversationRenamed(final Context context,
            final String conversationId) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "notifyConversationRenamed convId: " + conversationId);
        }

        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        for (final int appWidgetId : appWidgetManager.getAppWidgetIds(new ComponentName(context,
                WidgetConversationProvider.class))) {
            // Retrieve the persisted information for this widget from preferences.
            final String widgetConvId =
                    WidgetPickConversationActivity.getConversationIdPref(appWidgetId);

            if (widgetConvId != null && widgetConvId.equals(conversationId)) {
                rebuildWidget(context, appWidgetId);
            }
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "WidgetConversationProvider onReceive intent: " + intent);
        }
        final String action = intent.getAction();

        // The base class AppWidgetProvider's onReceive handles the normal widget intents. Here
        // we're looking for an intent sent by our app when it knows a message has
        // been sent or received (or a conversation has been read) and is telling the widget it
        // needs to update.
        if (getAction().equals(action)) {
            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            final int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context,
                    this.getClass()));

            if (appWidgetIds.length == 0) {
                if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                    LogUtil.v(TAG, "WidgetConversationProvider onReceive no widget ids");
                }
                return;
            }
            // Normally the conversation id points to a specific conversation and we only update
            // widgets looking at that conversation. When the conversation id is null, that means
            // there's been a massive change (such as the initial import) and we need to update
            // every conversation widget.
            final String conversationId = intent.getExtras()
                    .getString(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID);

            // Only update the widgets that match the conversation id that changed.
            for (final int widgetId : appWidgetIds) {
                // Retrieve the persisted information for this widget from preferences.
                final String widgetConvId =
                        WidgetPickConversationActivity.getConversationIdPref(widgetId);
                if (conversationId == null || TextUtils.equals(conversationId, widgetConvId)) {
                    // Update the list portion (i.e. the message list) of the widget
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, getListId());
                }
            }
        } else {
            super.onReceive(context, intent);
        }
    }

    private static ConversationListItemData getConversationData(final Context context,
            final String conversationId) {
        if (TextUtils.isEmpty(conversationId)) {
            return null;
        }
        final Uri uri = MessagingContentProvider.buildConversationMetadataUri(conversationId);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    ConversationListItemData.PROJECTION,
                    null,       // selection
                    null,       // selection args
                    null);      // sort order
            if (cursor != null && cursor.getCount() > 0) {
                final ConversationListItemData conv = new ConversationListItemData();
                cursor.moveToFirst();
                conv.bind(cursor);
                return conv;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Override
    protected void deletePreferences(final int widgetId) {
        WidgetPickConversationActivity.deleteConversationIdPref(widgetId);
    }

    /**
     * When this widget is created, it's created for a particular conversation and that
     * ConversationId is stored in shared prefs. If the associated conversation is deleted,
     * the widget doesn't get deleted. Instead, it goes into a "tap to configure" state. This
     * function determines whether the widget has been configured and has an associated
     * ConversationId.
     */
    public static boolean isWidgetConfigured(final int appWidgetId) {
        final String conversationId =
                WidgetPickConversationActivity.getConversationIdPref(appWidgetId);
        return !TextUtils.isEmpty(conversationId);
    }

}
