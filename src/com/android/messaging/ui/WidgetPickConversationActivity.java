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

import android.app.Fragment;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.ui.conversationlist.ShareIntentFragment;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.widget.WidgetConversationProvider;

public class WidgetPickConversationActivity extends BaseBugleActivity implements
        ShareIntentFragment.HostInterface {

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if they press the back button.
        setResult(RESULT_CANCELED);

        // Find the widget id from the intent.
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If they gave us an intent without the widget id, just bail.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }

        final ShareIntentFragment convPicker = new ShareIntentFragment();
        final Bundle bundle = new Bundle();
        bundle.putBoolean(ShareIntentFragment.HIDE_NEW_CONVERSATION_BUTTON_KEY, true);
        convPicker.setArguments(bundle);
        convPicker.show(getFragmentManager(), "ShareIntentFragment");
    }

    @Override
    public void onAttachFragment(final Fragment fragment) {
        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (!AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(action)) {
            // Unsupported action.
            Assert.fail("Unsupported action type: " + action);
        }
    }

    @Override
    public void onConversationClick(final ConversationListItemData conversationListItemData) {
        saveConversationidPref(mAppWidgetId, conversationListItemData.getConversationId());

        // Push widget update to surface with newly set prefix
        WidgetConversationProvider.rebuildWidget(this, mAppWidgetId);

        // Make sure we pass back the original appWidgetId
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    @Override
    public void onCreateConversationClick() {
        // We should never get here because we're hiding the new conversation button in the
        // ShareIntentFragment by setting HIDE_NEW_CONVERSATION_BUTTON_KEY in the arguments.
        finish();
    }

    // Write the ConversationId to the SharedPreferences object for this widget
    static void saveConversationidPref(int appWidgetId, String conversationId) {
        final BuglePrefs prefs = Factory.get().getWidgetPrefs();
        prefs.putString(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID + appWidgetId, conversationId);
    }

    // Read the ConversationId from the SharedPreferences object for this widget.
    public static String getConversationIdPref(int appWidgetId) {
        final BuglePrefs prefs = Factory.get().getWidgetPrefs();
        return prefs.getString(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID + appWidgetId, null);
    }

    // Delete the ConversationId preference from the SharedPreferences object for this widget.
    public static void deleteConversationIdPref(int appWidgetId) {
        final BuglePrefs prefs = Factory.get().getWidgetPrefs();
        prefs.remove(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID + appWidgetId);
    }

}
