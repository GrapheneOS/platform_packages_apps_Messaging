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

package com.android.messaging.ui.appsettings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.Telephony;
import android.support.v4.app.NavUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.ApnDatabase;
import com.android.messaging.sms.BugleApnSettingsLoader;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.PhoneUtils;

public class ApnEditorActivity extends BugleActionBarActivity {
    private static final int ERROR_DIALOG_ID = 0;
    private static final String ERROR_MESSAGE_KEY = "error_msg";
    private ApnEditorFragment mApnEditorFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Display the fragment as the main content.
        mApnEditorFragment = new ApnEditorFragment();
        mApnEditorFragment.setSubId(getIntent().getIntExtra(UIIntents.UI_INTENT_EXTRA_SUB_ID,
                ParticipantData.DEFAULT_SELF_SUB_ID));
        getFragmentManager().beginTransaction()
            .replace(android.R.id.content, mApnEditorFragment)
            .commit();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {

        if (id == ERROR_DIALOG_ID) {
            String msg = args.getString(ERROR_MESSAGE_KEY);

            return new AlertDialog.Builder(this)
                .setPositiveButton(android.R.string.ok, null)
                .setMessage(msg)
                .create();
        }

        return super.onCreateDialog(id);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (mApnEditorFragment.validateAndSave(false)) {
                    finish();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        super.onPrepareDialog(id, dialog);

        if (id == ERROR_DIALOG_ID) {
            final String msg = args.getString(ERROR_MESSAGE_KEY);

            if (msg != null) {
                ((AlertDialog) dialog).setMessage(msg);
            }
        }
    }

    public static class ApnEditorFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

        private static final String SAVED_POS = "pos";

        private static final int MENU_DELETE = Menu.FIRST;
        private static final int MENU_SAVE = Menu.FIRST + 1;
        private static final int MENU_CANCEL = Menu.FIRST + 2;

        private EditTextPreference mMmsProxy;
        private EditTextPreference mMmsPort;
        private EditTextPreference mName;
        private EditTextPreference mMmsc;
        private EditTextPreference mMcc;
        private EditTextPreference mMnc;
        private static String sNotSet;

        private String mCurMnc;
        private String mCurMcc;

        private Cursor mCursor;
        private boolean mNewApn;
        private boolean mFirstTime;
        private String mCurrentId;

        private int mSubId;

        /**
         * Standard projection for the interesting columns of a normal note.
         */
        private static final String[] sProjection = new String[] {
            Telephony.Carriers._ID,         // 0
            Telephony.Carriers.NAME,        // 1
            Telephony.Carriers.MMSC,        // 2
            Telephony.Carriers.MCC,         // 3
            Telephony.Carriers.MNC,         // 4
            Telephony.Carriers.NUMERIC,     // 5
            Telephony.Carriers.MMSPROXY,    // 6
            Telephony.Carriers.MMSPORT,     // 7
            Telephony.Carriers.TYPE,        // 8
        };

        private static final int ID_INDEX = 0;
        private static final int NAME_INDEX = 1;
        private static final int MMSC_INDEX = 2;
        private static final int MCC_INDEX = 3;
        private static final int MNC_INDEX = 4;
        private static final int NUMERIC_INDEX = 5;
        private static final int MMSPROXY_INDEX = 6;
        private static final int MMSPORT_INDEX = 7;
        private static final int TYPE_INDEX = 8;

        private SQLiteDatabase mDatabase;

        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);

        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            addPreferencesFromResource(R.xml.apn_editor);

            setHasOptionsMenu(true);

            sNotSet = getResources().getString(R.string.apn_not_set);
            mName = (EditTextPreference) findPreference("apn_name");
            mMmsProxy = (EditTextPreference) findPreference("apn_mms_proxy");
            mMmsPort = (EditTextPreference) findPreference("apn_mms_port");
            mMmsc = (EditTextPreference) findPreference("apn_mmsc");
            mMcc = (EditTextPreference) findPreference("apn_mcc");
            mMnc = (EditTextPreference) findPreference("apn_mnc");

            final Intent intent = getActivity().getIntent();

            mFirstTime = savedInstanceState == null;
            mCurrentId = intent.getStringExtra(UIIntents.UI_INTENT_EXTRA_APN_ROW_ID);
            mNewApn = mCurrentId == null;

            mDatabase = ApnDatabase.getApnDatabase().getWritableDatabase();

            if (mNewApn) {
                fillUi();
            } else {
                // Do initial query not on the UI thread
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        if (mCurrentId != null) {
                            String selection = Telephony.Carriers._ID + " =?";
                            String[] selectionArgs = new String[]{ mCurrentId };
                            mCursor = mDatabase.query(ApnDatabase.APN_TABLE, sProjection, selection,
                                    selectionArgs, null, null, null, null);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        if (mCursor == null) {
                            getActivity().finish();
                            return;
                        }
                        mCursor.moveToFirst();

                        fillUi();
                    }
                }.execute((Void) null);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            getPreferenceScreen().getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        public void setSubId(final int subId) {
            mSubId = subId;
        }

        private void fillUi() {
            if (mNewApn) {
                mMcc.setText(null);
                mMnc.setText(null);
                String numeric = PhoneUtils.get(mSubId).getSimOperatorNumeric();
                // MCC is first 3 chars and then in 2 - 3 chars of MNC
                if (numeric != null && numeric.length() > 4) {
                    // Country code
                    String mcc = numeric.substring(0, 3);
                    // Network code
                    String mnc = numeric.substring(3);
                    // Auto populate MNC and MCC for new entries, based on what SIM reports
                    mMcc.setText(mcc);
                    mMnc.setText(mnc);
                    mCurMnc = mnc;
                    mCurMcc = mcc;
                }
                mName.setText(null);
                mMmsProxy.setText(null);
                mMmsPort.setText(null);
                mMmsc.setText(null);
            } else if (mFirstTime) {
                mFirstTime = false;
                // Fill in all the values from the db in both text editor and summary
                mName.setText(mCursor.getString(NAME_INDEX));
                mMmsProxy.setText(mCursor.getString(MMSPROXY_INDEX));
                mMmsPort.setText(mCursor.getString(MMSPORT_INDEX));
                mMmsc.setText(mCursor.getString(MMSC_INDEX));
                mMcc.setText(mCursor.getString(MCC_INDEX));
                mMnc.setText(mCursor.getString(MNC_INDEX));
            }

            mName.setSummary(checkNull(mName.getText()));
            mMmsProxy.setSummary(checkNull(mMmsProxy.getText()));
            mMmsPort.setSummary(checkNull(mMmsPort.getText()));
            mMmsc.setSummary(checkNull(mMmsc.getText()));
            mMcc.setSummary(checkNull(mMcc.getText()));
            mMnc.setSummary(checkNull(mMnc.getText()));
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            super.onCreateOptionsMenu(menu, inflater);
            // If it's a new APN, then cancel will delete the new entry in onPause
            if (!mNewApn) {
                menu.add(0, MENU_DELETE, 0, R.string.menu_delete_apn)
                    .setIcon(R.drawable.ic_delete_small_dark);
            }
            menu.add(0, MENU_SAVE, 0, R.string.menu_save_apn)
                .setIcon(android.R.drawable.ic_menu_save);
            menu.add(0, MENU_CANCEL, 0, R.string.menu_discard_apn_change)
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case MENU_DELETE:
                    deleteApn();
                    return true;

                case MENU_SAVE:
                    if (validateAndSave(false)) {
                        getActivity().finish();
                    }
                    return true;

                case MENU_CANCEL:
                    getActivity().finish();
                    return true;

                case android.R.id.home:
                    getActivity().onBackPressed();
                    return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onSaveInstanceState(Bundle icicle) {
            super.onSaveInstanceState(icicle);
            if (validateAndSave(true) && mCursor != null) {
                icicle.putInt(SAVED_POS, mCursor.getInt(ID_INDEX));
            }
        }

        /**
         * Check the key fields' validity and save if valid.
         * @param force save even if the fields are not valid, if the app is
         *        being suspended
         * @return true if the data was saved
         */
        private boolean validateAndSave(boolean force) {
            final String name = checkNotSet(mName.getText());
            final String mcc = checkNotSet(mMcc.getText());
            final String mnc = checkNotSet(mMnc.getText());

            if (getErrorMsg() != null && !force) {
                final Bundle bundle = new Bundle();
                bundle.putString(ERROR_MESSAGE_KEY, getErrorMsg());
                getActivity().showDialog(ERROR_DIALOG_ID, bundle);
                return false;
            }

            // Make database changes not on the UI thread
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    ContentValues values = new ContentValues();

                    // Add a dummy name "Untitled", if the user exits the screen without adding a
                    // name but entered other information worth keeping.
                    values.put(Telephony.Carriers.NAME, name.length() < 1 ?
                            getResources().getString(R.string.untitled_apn) : name);
                    values.put(Telephony.Carriers.MMSPROXY, checkNotSet(mMmsProxy.getText()));
                    values.put(Telephony.Carriers.MMSPORT, checkNotSet(mMmsPort.getText()));
                    values.put(Telephony.Carriers.MMSC, checkNotSet(mMmsc.getText()));

                    values.put(Telephony.Carriers.TYPE, BugleApnSettingsLoader.APN_TYPE_MMS);

                    values.put(Telephony.Carriers.MCC, mcc);
                    values.put(Telephony.Carriers.MNC, mnc);

                    values.put(Telephony.Carriers.NUMERIC, mcc + mnc);

                    if (mCurMnc != null && mCurMcc != null) {
                        if (mCurMnc.equals(mnc) && mCurMcc.equals(mcc)) {
                            values.put(Telephony.Carriers.CURRENT, 1);
                        }
                    }

                    if (mNewApn) {
                        mDatabase.insert(ApnDatabase.APN_TABLE, null, values);
                    } else {
                        // update the APN
                        String selection = Telephony.Carriers._ID + " =?";
                        String[] selectionArgs = new String[]{ mCurrentId };
                        int updated = mDatabase.update(ApnDatabase.APN_TABLE, values,
                                selection, selectionArgs);
                    }
                    return null;
                }
            }.execute((Void) null);

            return true;
        }

        private void deleteApn() {
            // Make database changes not on the UI thread
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    // delete the APN
                    String where = Telephony.Carriers._ID + " =?";
                    String[] whereArgs = new String[]{ mCurrentId };

                    mDatabase.delete(ApnDatabase.APN_TABLE, where, whereArgs);
                    return null;
                }
            }.execute((Void) null);

            getActivity().finish();
        }

        private String checkNull(String value) {
            if (value == null || value.length() == 0) {
                return sNotSet;
            } else {
                return value;
            }
        }

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Preference pref = findPreference(key);
            if (pref != null) {
                pref.setSummary(checkNull(sharedPreferences.getString(key, "")));
            }
        }

        private String getErrorMsg() {
            String errorMsg = null;

            String name = checkNotSet(mName.getText());
            String mcc = checkNotSet(mMcc.getText());
            String mnc = checkNotSet(mMnc.getText());

            if (name.length() < 1) {
                errorMsg = getString(R.string.error_apn_name_empty);
            } else if (mcc.length() != 3) {
                errorMsg = getString(R.string.error_mcc_not3);
            } else if ((mnc.length() & 0xFFFE) != 2) {
                errorMsg = getString(R.string.error_mnc_not23);
            }

            return errorMsg;
        }

        private String checkNotSet(String value) {
            if (value == null || value.equals(sNotSet)) {
                return "";
            } else {
                return value;
            }
        }
    }
}
