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

package com.android.messaging.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

public class ConnectivityUtil {
    // Assume not connected until informed differently
    private volatile int mCurrentServiceState = ServiceState.STATE_POWER_OFF;

    private final TelephonyManager mTelephonyManager;
    private final Context mContext;
    private final ConnectivityBroadcastReceiver mReceiver;
    private final ConnectivityManager mConnMgr;

    private ConnectivityListener mListener;
    private final IntentFilter mIntentFilter;

    public interface ConnectivityListener {
        public void onConnectivityStateChanged(final Context context, final Intent intent);
        public void onPhoneStateChanged(final Context context, int serviceState);
    }

    public ConnectivityUtil(final Context context) {
        mContext = context;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mConnMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mReceiver = new ConnectivityBroadcastReceiver();
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    }

    public int getCurrentServiceState() {
        return mCurrentServiceState;
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(final ServiceState serviceState) {
            if (mCurrentServiceState != serviceState.getState()) {
                mCurrentServiceState = serviceState.getState();
                onPhoneStateChanged(mCurrentServiceState);
            }
        }

        @Override
        public void onDataConnectionStateChanged(final int state) {
            mCurrentServiceState = (state == TelephonyManager.DATA_DISCONNECTED) ?
                    ServiceState.STATE_OUT_OF_SERVICE : ServiceState.STATE_IN_SERVICE;
        }
    };

    private void onPhoneStateChanged(final int serviceState) {
        final ConnectivityListener listener = mListener;
        if (listener != null) {
            listener.onPhoneStateChanged(mContext, serviceState);
        }
    }

    private void onConnectivityChanged(final Context context, final Intent intent) {
        final ConnectivityListener listener = mListener;
        if (listener != null) {
            listener.onConnectivityStateChanged(context, intent);
        }
    }

    public void register(final ConnectivityListener listener) {
        Assert.isTrue(mListener == null || mListener == listener);
        if (mListener == null) {
            if (mTelephonyManager != null) {
                mCurrentServiceState = (PhoneUtils.getDefault().isAirplaneModeOn() ?
                        ServiceState.STATE_POWER_OFF : ServiceState.STATE_IN_SERVICE);
                mTelephonyManager.listen(mPhoneStateListener,
                        PhoneStateListener.LISTEN_SERVICE_STATE);
            }
            if (mConnMgr != null) {
                mContext.registerReceiver(mReceiver, mIntentFilter);
            }
        }
        mListener = listener;
    }

    public void unregister() {
        if (mListener != null) {
            if (mTelephonyManager != null) {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
                mCurrentServiceState = ServiceState.STATE_POWER_OFF;
            }
            if (mConnMgr != null) {
                mContext.unregisterReceiver(mReceiver);
            }
        }
        mListener = null;
    }

    /**
     * Connectivity change broadcast receiver. This gets the network connectivity updates.
     * In case we don't get the active connectivity when we first acquire the network,
     * this receiver will notify us when it is connected, so to unblock the waiting thread
     * which is sending the message.
     */
    public class ConnectivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (!intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                return;
            }

            onConnectivityChanged(context, intent);
        }
    }

    private int mSignalLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;

    // We use a separate instance than mPhoneStateListener because the lifetimes are different.
    private final PhoneStateListener mSignalStrengthListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(final SignalStrength signalStrength) {
            mSignalLevel = getLevel(signalStrength);
        }
    };

    public void registerForSignalStrength() {
        mTelephonyManager.listen(
                mSignalStrengthListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    public void unregisterForSignalStrength() {
        mTelephonyManager.listen(mSignalStrengthListener, PhoneStateListener.LISTEN_NONE);
    }

    /**
     * @param subId This is ignored because TelephonyManager does not support it.
     * @return Signal strength as level 0..4
     */
    public int getSignalLevel(final int subId) {
        return mSignalLevel;
    }

    private static final int SIGNAL_STRENGTH_NONE_OR_UNKNOWN = 0;
    private static final int SIGNAL_STRENGTH_POOR = 1;
    private static final int SIGNAL_STRENGTH_MODERATE = 2;
    private static final int SIGNAL_STRENGTH_GOOD = 3;
    private static final int SIGNAL_STRENGTH_GREAT = 4;

    private static final int GSM_SIGNAL_STRENGTH_GREAT = 12;
    private static final int GSM_SIGNAL_STRENGTH_GOOD = 8;
    private static final int GSM_SIGNAL_STRENGTH_MODERATE = 8;

    private static int getLevel(final SignalStrength signalStrength) {
        if (signalStrength.isGsm()) {
            // From frameworks/base/telephony/java/android/telephony/CellSignalStrengthGsm.java

            // ASU ranges from 0 to 31 - TS 27.007 Sec 8.5
            // asu = 0 (-113dB or less) is very weak
            // signal, its better to show 0 bars to the user in such cases.
            // asu = 99 is a special case, where the signal strength is unknown.
            final int asu = signalStrength.getGsmSignalStrength();
            if (asu <= 2 || asu == 99) return SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
            else if (asu >= GSM_SIGNAL_STRENGTH_GREAT) return SIGNAL_STRENGTH_GREAT;
            else if (asu >= GSM_SIGNAL_STRENGTH_GOOD) return SIGNAL_STRENGTH_GOOD;
            else if (asu >= GSM_SIGNAL_STRENGTH_MODERATE) return SIGNAL_STRENGTH_MODERATE;
            else return SIGNAL_STRENGTH_POOR;
        } else {
            // From frameworks/base/telephony/java/android/telephony/CellSignalStrengthCdma.java

            final int cdmaLevel = getCdmaLevel(signalStrength);
            final int evdoLevel = getEvdoLevel(signalStrength);
            if (evdoLevel == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
                /* We don't know evdo, use cdma */
                return getCdmaLevel(signalStrength);
            } else if (cdmaLevel == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
                /* We don't know cdma, use evdo */
                return getEvdoLevel(signalStrength);
            } else {
                /* We know both, use the lowest level */
                return cdmaLevel < evdoLevel ? cdmaLevel : evdoLevel;
            }
        }
    }

    /**
     * Get cdma as level 0..4
     */
    private static int getCdmaLevel(final SignalStrength signalStrength) {
        final int cdmaDbm = signalStrength.getCdmaDbm();
        final int cdmaEcio = signalStrength.getCdmaEcio();
        int levelDbm;
        int levelEcio;
        if (cdmaDbm >= -75) levelDbm = SIGNAL_STRENGTH_GREAT;
        else if (cdmaDbm >= -85) levelDbm = SIGNAL_STRENGTH_GOOD;
        else if (cdmaDbm >= -95) levelDbm = SIGNAL_STRENGTH_MODERATE;
        else if (cdmaDbm >= -100) levelDbm = SIGNAL_STRENGTH_POOR;
        else levelDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        // Ec/Io are in dB*10
        if (cdmaEcio >= -90) levelEcio = SIGNAL_STRENGTH_GREAT;
        else if (cdmaEcio >= -110) levelEcio = SIGNAL_STRENGTH_GOOD;
        else if (cdmaEcio >= -130) levelEcio = SIGNAL_STRENGTH_MODERATE;
        else if (cdmaEcio >= -150) levelEcio = SIGNAL_STRENGTH_POOR;
        else levelEcio = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        final int level = (levelDbm < levelEcio) ? levelDbm : levelEcio;
        return level;
    }
    /**
     * Get Evdo as level 0..4
     */
    private static int getEvdoLevel(final SignalStrength signalStrength) {
        final int evdoDbm = signalStrength.getEvdoDbm();
        final int evdoSnr = signalStrength.getEvdoSnr();
        int levelEvdoDbm;
        int levelEvdoSnr;
        if (evdoDbm >= -65) levelEvdoDbm = SIGNAL_STRENGTH_GREAT;
        else if (evdoDbm >= -75) levelEvdoDbm = SIGNAL_STRENGTH_GOOD;
        else if (evdoDbm >= -90) levelEvdoDbm = SIGNAL_STRENGTH_MODERATE;
        else if (evdoDbm >= -105) levelEvdoDbm = SIGNAL_STRENGTH_POOR;
        else levelEvdoDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        if (evdoSnr >= 7) levelEvdoSnr = SIGNAL_STRENGTH_GREAT;
        else if (evdoSnr >= 5) levelEvdoSnr = SIGNAL_STRENGTH_GOOD;
        else if (evdoSnr >= 3) levelEvdoSnr = SIGNAL_STRENGTH_MODERATE;
        else if (evdoSnr >= 1) levelEvdoSnr = SIGNAL_STRENGTH_POOR;
        else levelEvdoSnr = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        final int level = (levelEvdoDbm < levelEvdoSnr) ? levelEvdoDbm : levelEvdoSnr;
        return level;
    }
}
