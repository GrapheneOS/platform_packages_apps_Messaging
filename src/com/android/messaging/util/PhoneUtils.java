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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.support.v4.util.ArrayMap;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsSmsUtils;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * This class abstracts away platform dependency of calling telephony related
 * platform APIs, mostly involving TelephonyManager, SubscriptionManager and
 * a bit of SmsManager.
 *
 * The class instance can only be obtained via the get(int subId) method parameterized
 * by a SIM subscription ID. On pre-L_MR1, the subId is not used and it has to be
 * the default subId (-1).
 *
 * A convenient getDefault() method is provided for default subId (-1) on any platform
 */
public abstract class PhoneUtils {
    private static final String TAG = LogUtil.BUGLE_TAG;

    private static final int MINIMUM_PHONE_NUMBER_LENGTH_TO_FORMAT = 6;

    private static final List<SubscriptionInfo> EMPTY_SUBSCRIPTION_LIST = new ArrayList<>();

    // The canonical phone number cache
    // Each country gets its own cache. The following maps from ISO country code to
    // the country's cache. Each cache maps from original phone number to canonicalized phone
    private static final ArrayMap<String, ArrayMap<String, String>> sCanonicalPhoneNumberCache =
            new ArrayMap<>();

    protected final Context mContext;
    protected final TelephonyManager mTelephonyManager;
    protected final int mSubId;

    public PhoneUtils(int subId) {
        mSubId = subId;
        mContext = Factory.get().getApplicationContext();
        mTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * Get the SIM's country code
     *
     * @return the country code on the SIM
     */
    public abstract String getSimCountry();

    /**
     * Get number of SIM slots
     *
     * @return the SIM slot count
     */
    public abstract int getSimSlotCount();

    /**
     * Get SIM's carrier name
     *
     * @return the carrier name of the SIM
     */
    public abstract String getCarrierName();

    /**
     * Check if there is SIM inserted on the device
     *
     * @return true if there is SIM inserted, false otherwise
     */
    public abstract boolean hasSim();

    /**
     * Check if the SIM is roaming
     *
     * @return true if the SIM is in romaing state, false otherwise
     */
    public abstract boolean isRoaming();

    /**
     * Get the MCC and MNC in integer of the SIM's provider
     *
     * @return an array of two ints, [0] is the MCC code and [1] is the MNC code
     */
    public abstract int[] getMccMnc();

    /**
     * Get the mcc/mnc string
     *
     * @return the text of mccmnc string
     */
    public abstract String getSimOperatorNumeric();

    /**
     * Get the SIM's self raw number, i.e. not canonicalized
     *
     * @param allowOverride Whether to use the app's setting to override the self number
     * @return the original self number
     * @throws IllegalStateException if no active subscription on L-MR1+
     */
    public abstract String getSelfRawNumber(final boolean allowOverride);

    /**
     * Returns the "effective" subId, or the subId used in the context of actual messages,
     * conversations and subscription-specific settings, for the given "nominal" sub id.
     *
     * For pre-L-MR1 platform, this should always be
     * {@value com.android.messaging.datamodel.data.ParticipantData#DEFAULT_SELF_SUB_ID};
     *
     * On the other hand, for L-MR1 and above, DEFAULT_SELF_SUB_ID will be mapped to the system
     * default subscription id for SMS.
     *
     * @param subId The input subId
     * @return the real subId if we can convert
     */
    public abstract int getEffectiveSubId(int subId);

    /**
     * Returns the number of active subscriptions in the device.
     */
    public abstract int getActiveSubscriptionCount();

    /**
     * Get {@link SmsManager} instance
     *
     * @return the relevant SmsManager instance based on OS version and subId
     */
    public abstract SmsManager getSmsManager();

    /**
     * Get the default SMS subscription id
     *
     * @return the default sub ID
     */
    public abstract int getDefaultSmsSubscriptionId();

    /**
     * Returns if there's currently a system default SIM selected for sending SMS.
     */
    public abstract boolean getHasPreferredSmsSim();

    /**
     * For L_MR1, system may return a negative subId. Convert this into our own
     * subId, so that we consistently use -1 for invalid or default.
     *
     * see b/18629526 and b/18670346
     *
     * @param intent The push intent from system
     * @param extraName The name of the sub id extra
     * @return the subId that is valid and meaningful for the app
     */
    public abstract int getEffectiveIncomingSubIdFromSystem(Intent intent, String extraName);

    /**
     * Get the subscription_id column value from a telephony provider cursor
     *
     * @param cursor The database query cursor
     * @param subIdIndex The index of the subId column in the cursor
     * @return the subscription_id column value from the cursor
     */
    public abstract int getSubIdFromTelephony(Cursor cursor, int subIdIndex);

    /**
     * Check if data roaming is enabled
     *
     * @return true if data roaming is enabled, false otherwise
     */
    public abstract boolean isDataRoamingEnabled();

    /**
     * Check if mobile data is enabled
     *
     * @return true if mobile data is enabled, false otherwise
     */
    public abstract boolean isMobileDataEnabled();

    /**
     * Get the set of self phone numbers, all normalized
     *
     * @return the set of normalized self phone numbers
     */
    public abstract HashSet<String> getNormalizedSelfNumbers();

    /**
     * This interface packages methods should only compile on L_MR1.
     * This is needed to make unit tests happy when mockito tries to
     * mock these methods. Calling on these methods on L_MR1 requires
     * an extra invocation of toMr1().
     */
    public interface LMr1 {
        /**
         * Get this SIM's information. Only applies to L_MR1 above
         *
         * @return the subscription info of the SIM
         */
        public abstract SubscriptionInfo getActiveSubscriptionInfo();

        /**
         * Get the list of active SIMs in system. Only applies to L_MR1 above
         *
         * @return the list of subscription info for all inserted SIMs
         */
        public abstract List<SubscriptionInfo> getActiveSubscriptionInfoList();

        /**
         * Register subscription change listener. Only applies to L_MR1 above
         *
         * @param listener The listener to register
         */
        public abstract void registerOnSubscriptionsChangedListener(
                SubscriptionManager.OnSubscriptionsChangedListener listener);
    }

    /**
     * The PhoneUtils class for pre L_MR1
     */
    public static class PhoneUtilsPreLMR1 extends PhoneUtils {
        private final ConnectivityManager mConnectivityManager;

        public PhoneUtilsPreLMR1() {
            super(ParticipantData.DEFAULT_SELF_SUB_ID);
            mConnectivityManager =
                    (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        @Override
        public String getSimCountry() {
            final String country = mTelephonyManager.getSimCountryIso();
            if (TextUtils.isEmpty(country)) {
                return null;
            }
            return country.toUpperCase();
        }

        @Override
        public int getSimSlotCount() {
            // Don't support MSIM pre-L_MR1
            return 1;
        }

        @Override
        public String getCarrierName() {
            return mTelephonyManager.getNetworkOperatorName();
        }

        @Override
        public boolean hasSim() {
            return mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
        }

        @Override
        public boolean isRoaming() {
            return mTelephonyManager.isNetworkRoaming();
        }

        @Override
        public int[] getMccMnc() {
            final String mccmnc = mTelephonyManager.getSimOperator();
            int mcc = 0;
            int mnc = 0;
            try {
                mcc = Integer.parseInt(mccmnc.substring(0, 3));
                mnc = Integer.parseInt(mccmnc.substring(3));
            } catch (Exception e) {
                LogUtil.w(TAG, "PhoneUtils.getMccMnc: invalid string " + mccmnc, e);
            }
            return new int[]{mcc, mnc};
        }

        @Override
        public String getSimOperatorNumeric() {
            return mTelephonyManager.getSimOperator();
        }

        @Override
        public String getSelfRawNumber(final boolean allowOverride) {
            if (allowOverride) {
                final String userDefinedNumber = getNumberFromPrefs(mContext,
                        ParticipantData.DEFAULT_SELF_SUB_ID);
                if (!TextUtils.isEmpty(userDefinedNumber)) {
                    return userDefinedNumber;
                }
            }
            return mTelephonyManager.getLine1Number();
        }

        @Override
        public int getEffectiveSubId(int subId) {
            Assert.equals(ParticipantData.DEFAULT_SELF_SUB_ID, subId);
            return ParticipantData.DEFAULT_SELF_SUB_ID;
        }

        @Override
        public SmsManager getSmsManager() {
            return SmsManager.getDefault();
        }

        @Override
        public int getDefaultSmsSubscriptionId() {
            Assert.fail("PhoneUtils.getDefaultSmsSubscriptionId(): not supported before L MR1");
            return ParticipantData.DEFAULT_SELF_SUB_ID;
        }

        @Override
        public boolean getHasPreferredSmsSim() {
            // SIM selection is not supported pre-L_MR1.
            return true;
        }

        @Override
        public int getActiveSubscriptionCount() {
            return hasSim() ? 1 : 0;
        }

        @Override
        public int getEffectiveIncomingSubIdFromSystem(Intent intent, String extraName) {
            // Pre-L_MR1 always returns the default id
            return ParticipantData.DEFAULT_SELF_SUB_ID;
        }

        @Override
        public int getSubIdFromTelephony(Cursor cursor, int subIdIndex) {
            // No subscription_id column before L_MR1
            return ParticipantData.DEFAULT_SELF_SUB_ID;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isDataRoamingEnabled() {
            boolean dataRoamingEnabled = false;
            final ContentResolver cr = mContext.getContentResolver();
            if (OsUtil.isAtLeastJB_MR1()) {
                dataRoamingEnabled =
                        (Settings.Global.getInt(cr, Settings.Global.DATA_ROAMING, 0) != 0);
            } else {
                dataRoamingEnabled =
                        (Settings.System.getInt(cr, Settings.System.DATA_ROAMING, 0) != 0);
            }
            return dataRoamingEnabled;
        }

        @Override
        public boolean isMobileDataEnabled() {
            boolean mobileDataEnabled = false;
            try {
                final Class cmClass = mConnectivityManager.getClass();
                final Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
                method.setAccessible(true); // Make the method callable
                // get the setting for "mobile data"
                mobileDataEnabled = (Boolean) method.invoke(mConnectivityManager);
            } catch (final Exception e) {
                LogUtil.e(TAG, "PhoneUtil.isMobileDataEnabled: system api not found", e);
            }
            return mobileDataEnabled;
        }

        @Override
        public HashSet<String> getNormalizedSelfNumbers() {
            final HashSet<String> numbers = new HashSet<>();
            numbers.add(getCanonicalForSelf(true/*allowOverride*/));
            return numbers;
        }
    }

    /**
     * The PhoneUtils class for L_MR1
     */
    public static class PhoneUtilsLMR1 extends PhoneUtils implements LMr1 {
        private final SubscriptionManager mSubscriptionManager;

        public PhoneUtilsLMR1(final int subId) {
            super(subId);
            mSubscriptionManager = SubscriptionManager.from(Factory.get().getApplicationContext());
        }

        @Override
        public String getSimCountry() {
            final SubscriptionInfo subInfo = getActiveSubscriptionInfo();
            if (subInfo != null) {
                final String country = subInfo.getCountryIso();
                if (TextUtils.isEmpty(country)) {
                    return null;
                }
                return country.toUpperCase();
            }
            return null;
        }

        @Override
        public int getSimSlotCount() {
            return mSubscriptionManager.getActiveSubscriptionInfoCountMax();
        }

        @Override
        public String getCarrierName() {
            final SubscriptionInfo subInfo = getActiveSubscriptionInfo();
            if (subInfo != null) {
                final CharSequence displayName = subInfo.getDisplayName();
                if (!TextUtils.isEmpty(displayName)) {
                    return displayName.toString();
                }
                final CharSequence carrierName = subInfo.getCarrierName();
                if (carrierName != null) {
                    return carrierName.toString();
                }
            }
            return null;
        }

        @Override
        public boolean hasSim() {
            return mSubscriptionManager.getActiveSubscriptionInfoCount() > 0;
        }

        @Override
        public boolean isRoaming() {
            return mSubscriptionManager.isNetworkRoaming(mSubId);
        }

        @Override
        public int[] getMccMnc() {
            int mcc = 0;
            int mnc = 0;
            final SubscriptionInfo subInfo = getActiveSubscriptionInfo();
            if (subInfo != null) {
                mcc = subInfo.getMcc();
                mnc = subInfo.getMnc();
            }
            return new int[]{mcc, mnc};
        }

        @Override
        public String getSimOperatorNumeric() {
            // For L_MR1 we return the canonicalized (xxxxxx) string
            return getMccMncString(getMccMnc());
        }

        @Override
        public String getSelfRawNumber(final boolean allowOverride) {
            if (allowOverride) {
                final String userDefinedNumber = getNumberFromPrefs(mContext, mSubId);
                if (!TextUtils.isEmpty(userDefinedNumber)) {
                    return userDefinedNumber;
                }
            }

            final SubscriptionInfo subInfo = getActiveSubscriptionInfo();
            if (subInfo != null) {
                String phoneNumber = subInfo.getNumber();
                if (TextUtils.isEmpty(phoneNumber) && LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "SubscriptionInfo phone number for self is empty!");
                }
                return phoneNumber;
            }
            LogUtil.w(TAG, "PhoneUtils.getSelfRawNumber: subInfo is null for " + mSubId);
            throw new IllegalStateException("No active subscription");
        }

        @Override
        public SubscriptionInfo getActiveSubscriptionInfo() {
            try {
                final SubscriptionInfo subInfo =
                        mSubscriptionManager.getActiveSubscriptionInfo(mSubId);
                if (subInfo == null) {
                    if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                        // This is possible if the sub id is no longer available.
                        LogUtil.d(TAG, "PhoneUtils.getActiveSubscriptionInfo(): empty sub info for "
                                + mSubId);
                    }
                }
                return subInfo;
            } catch (Exception e) {
                LogUtil.e(TAG, "PhoneUtils.getActiveSubscriptionInfo: system exception for "
                        + mSubId, e);
            }
            return null;
        }

        @Override
        public List<SubscriptionInfo> getActiveSubscriptionInfoList() {
            final List<SubscriptionInfo> subscriptionInfos =
                    mSubscriptionManager.getActiveSubscriptionInfoList();
            if (subscriptionInfos != null) {
                return subscriptionInfos;
            }
            return EMPTY_SUBSCRIPTION_LIST;
        }

        @Override
        public int getEffectiveSubId(int subId) {
            if (subId == ParticipantData.DEFAULT_SELF_SUB_ID) {
                return getDefaultSmsSubscriptionId();
            }
            return subId;
        }

        @Override
        public void registerOnSubscriptionsChangedListener(
                SubscriptionManager.OnSubscriptionsChangedListener listener) {
            mSubscriptionManager.addOnSubscriptionsChangedListener(listener);
        }

        @Override
        public SmsManager getSmsManager() {
            return SmsManager.getSmsManagerForSubscriptionId(mSubId);
        }

        @Override
        public int getDefaultSmsSubscriptionId() {
            final int systemDefaultSubId = SmsManager.getDefaultSmsSubscriptionId();
            if (systemDefaultSubId < 0) {
                // Always use -1 for any negative subId from system
                return ParticipantData.DEFAULT_SELF_SUB_ID;
            }
            return systemDefaultSubId;
        }

        @Override
        public boolean getHasPreferredSmsSim() {
            return getDefaultSmsSubscriptionId() != ParticipantData.DEFAULT_SELF_SUB_ID;
        }

        @Override
        public int getActiveSubscriptionCount() {
            return mSubscriptionManager.getActiveSubscriptionInfoCount();
        }

        @Override
        public int getEffectiveIncomingSubIdFromSystem(Intent intent, String extraName) {
            return getEffectiveIncomingSubIdFromSystem(intent.getIntExtra(extraName,
                    ParticipantData.DEFAULT_SELF_SUB_ID));
        }

        private int getEffectiveIncomingSubIdFromSystem(int subId) {
            if (subId < 0) {
                if (mSubscriptionManager.getActiveSubscriptionInfoCount() > 1) {
                    // For multi-SIM device, we can not decide which SIM to use if system
                    // does not know either. So just make it the invalid sub id.
                    return ParticipantData.DEFAULT_SELF_SUB_ID;
                }
                // For single-SIM device, it must come from the only SIM we have
                return getDefaultSmsSubscriptionId();
            }
            return subId;
        }

        @Override
        public int getSubIdFromTelephony(Cursor cursor, int subIdIndex) {
            return getEffectiveIncomingSubIdFromSystem(cursor.getInt(subIdIndex));
        }

        @Override
        public boolean isDataRoamingEnabled() {
            final SubscriptionInfo subInfo = getActiveSubscriptionInfo();
            if (subInfo == null) {
                // There is nothing we can do if system give us empty sub info
                LogUtil.e(TAG, "PhoneUtils.isDataRoamingEnabled: system return empty sub info for "
                        + mSubId);
                return false;
            }
            return subInfo.getDataRoaming() != SubscriptionManager.DATA_ROAMING_DISABLE;
        }

        @Override
        public boolean isMobileDataEnabled() {
            boolean mobileDataEnabled = false;
            try {
                final Class cmClass = mTelephonyManager.getClass();
                final Method method = cmClass.getDeclaredMethod("getDataEnabled", Integer.TYPE);
                method.setAccessible(true); // Make the method callable
                // get the setting for "mobile data"
                mobileDataEnabled = (Boolean) method.invoke(
                        mTelephonyManager, Integer.valueOf(mSubId));
            } catch (final Exception e) {
                LogUtil.e(TAG, "PhoneUtil.isMobileDataEnabled: system api not found", e);
            }
            return mobileDataEnabled;

        }

        @Override
        public HashSet<String> getNormalizedSelfNumbers() {
            final HashSet<String> numbers = new HashSet<>();
            for (SubscriptionInfo info : getActiveSubscriptionInfoList()) {
                numbers.add(PhoneUtils.get(info.getSubscriptionId()).getCanonicalForSelf(
                        true/*allowOverride*/));
            }
            return numbers;
        }
    }

    /**
     * A convenient get() method that uses the default SIM. Use this when SIM is
     * not relevant, e.g. isDefaultSmsApp
     *
     * @return an instance of PhoneUtils for default SIM
     */
    public static PhoneUtils getDefault() {
        return Factory.get().getPhoneUtils(ParticipantData.DEFAULT_SELF_SUB_ID);
    }

    /**
     * Get an instance of PhoneUtils associated with a specific SIM, which is also platform
     * specific.
     *
     * @param subId The SIM's subscription ID
     * @return the instance
     */
    public static PhoneUtils get(int subId) {
        return Factory.get().getPhoneUtils(subId);
    }

    public LMr1 toLMr1() {
        if (OsUtil.isAtLeastL_MR1()) {
            return (LMr1) this;
        } else {
            Assert.fail("PhoneUtils.toLMr1(): invalid OS version");
            return null;
        }
    }

    /**
     * Check if this device supports SMS
     *
     * @return true if SMS is supported, false otherwise
     */
    public boolean isSmsCapable() {
        return mTelephonyManager.isSmsCapable();
    }

    /**
     * Check if this device supports voice calling
     *
     * @return true if voice calling is supported, false otherwise
     */
    public boolean isVoiceCapable() {
        return mTelephonyManager.isVoiceCapable();
    }

    /**
     * Get the ISO country code from system locale setting
     *
     * @return the ISO country code from system locale
     */
    private static String getLocaleCountry() {
        final String country = Locale.getDefault().getCountry();
        if (TextUtils.isEmpty(country)) {
            return null;
        }
        return country.toUpperCase();
    }

    /**
     * Get ISO country code from the SIM, if not available, fall back to locale
     *
     * @return SIM or locale ISO country code
     */
    public String getSimOrDefaultLocaleCountry() {
        String country = getSimCountry();
        if (country == null) {
            country = getLocaleCountry();
        }
        return country;
    }

    // Get or set the cache of canonicalized phone numbers for a specific country
    private static ArrayMap<String, String> getOrAddCountryMapInCacheLocked(String country) {
        if (country == null) {
            country = "";
        }
        ArrayMap<String, String> countryMap = sCanonicalPhoneNumberCache.get(country);
        if (countryMap == null) {
            countryMap = new ArrayMap<>();
            sCanonicalPhoneNumberCache.put(country, countryMap);
        }
        return countryMap;
    }

    // Get canonicalized phone number from cache
    private static String getCanonicalFromCache(final String phoneText, String country) {
        synchronized (sCanonicalPhoneNumberCache) {
            final ArrayMap<String, String> countryMap = getOrAddCountryMapInCacheLocked(country);
            return countryMap.get(phoneText);
        }
    }

    // Put canonicalized phone number into cache
    private static void putCanonicalToCache(final String phoneText, String country,
            final String canonical) {
        synchronized (sCanonicalPhoneNumberCache) {
            final ArrayMap<String, String> countryMap = getOrAddCountryMapInCacheLocked(country);
            countryMap.put(phoneText, canonical);
        }
    }

    /**
     * Utility method to parse user input number into standard E164 number.
     *
     * @param phoneText Phone number text as input by user.
     * @param country ISO country code based on which to parse the number.
     * @return E164 phone number. Returns null in case parsing failed.
     */
    private static String getValidE164Number(final String phoneText, final String country) {
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        try {
            final PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneText, country);
            if (phoneNumber != null && phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumberUtil.format(phoneNumber, PhoneNumberFormat.E164);
            }
        } catch (final NumberParseException e) {
            LogUtil.e(TAG, "PhoneUtils.getValidE164Number(): Not able to parse phone number "
                        + LogUtil.sanitizePII(phoneText) + " for country " + country);
        }
        return null;
    }

    /**
     * Canonicalize phone number using system locale country
     *
     * @param phoneText The phone number to canonicalize
     * @return the canonicalized number
     */
    public String getCanonicalBySystemLocale(final String phoneText) {
        return getCanonicalByCountry(phoneText, getLocaleCountry());
    }

    /**
     * Canonicalize phone number using SIM's country, may fall back to system locale country
     * if SIM country can not be obtained
     *
     * @param phoneText The phone number to canonicalize
     * @return the canonicalized number
     */
    public String getCanonicalBySimLocale(final String phoneText) {
        return getCanonicalByCountry(phoneText, getSimOrDefaultLocaleCountry());
    }

    /**
     * Canonicalize phone number using a country code.
     * This uses an internal cache per country to speed up.
     *
     * @param phoneText The phone number to canonicalize
     * @param country The ISO country code to use
     * @return the canonicalized number, or the original number if can't be parsed
     */
    private String getCanonicalByCountry(final String phoneText, final String country) {
        Assert.notNull(phoneText);

        String canonicalNumber = getCanonicalFromCache(phoneText, country);
        if (canonicalNumber != null) {
            return canonicalNumber;
        }
        canonicalNumber = getValidE164Number(phoneText, country);
        if (canonicalNumber == null) {
            // If we can't normalize this number, we just use the display string number.
            // This is possible for short codes and other non-localizable numbers.
            canonicalNumber = phoneText;
        }
        putCanonicalToCache(phoneText, country, canonicalNumber);
        return canonicalNumber;
    }

    /**
     * Canonicalize the self (per SIM) phone number
     *
     * @param allowOverride whether to use the override number in app settings
     * @return the canonicalized self phone number
     */
    public String getCanonicalForSelf(final boolean allowOverride) {
        String selfNumber = null;
        try {
            selfNumber = getSelfRawNumber(allowOverride);
        } catch (IllegalStateException e) {
            // continue;
        }
        if (selfNumber == null) {
            return "";
        }
        return getCanonicalBySimLocale(selfNumber);
    }

    /**
     * Get the SIM's phone number in NATIONAL format with only digits, used in sending
     * as LINE1NOCOUNTRYCODE macro in mms_config
     *
     * @return all digits national format number of the SIM
     */
    public String getSimNumberNoCountryCode() {
        String selfNumber = null;
        try {
            selfNumber = getSelfRawNumber(false/*allowOverride*/);
        } catch (IllegalStateException e) {
            // continue
        }
        if (selfNumber == null) {
            selfNumber = "";
        }
        final String country = getSimCountry();
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        try {
            final PhoneNumber phoneNumber = phoneNumberUtil.parse(selfNumber, country);
            if (phoneNumber != null && phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumberUtil
                        .format(phoneNumber, PhoneNumberFormat.NATIONAL)
                        .replaceAll("\\D", "");
            }
        } catch (final NumberParseException e) {
            LogUtil.e(TAG, "PhoneUtils.getSimNumberNoCountryCode(): Not able to parse phone number "
                    + LogUtil.sanitizePII(selfNumber) + " for country " + country);
        }
        return selfNumber;

    }

    /**
     * Format a phone number for displaying, using system locale country.
     * If the country code matches between the system locale and the input phone number,
     * it will be formatted into NATIONAL format, otherwise, the INTERNATIONAL format
     *
     * @param phoneText The original phone text
     * @return formatted number
     */
    public String formatForDisplay(final String phoneText) {
        // Only format a valid number which length >=6
        if (TextUtils.isEmpty(phoneText) ||
                phoneText.replaceAll("\\D", "").length() < MINIMUM_PHONE_NUMBER_LENGTH_TO_FORMAT) {
            return phoneText;
        }
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        final String systemCountry = getLocaleCountry();
        final int systemCountryCode = phoneNumberUtil.getCountryCodeForRegion(systemCountry);
        try {
            final PhoneNumber parsedNumber = phoneNumberUtil.parse(phoneText, systemCountry);
            final PhoneNumberFormat phoneNumberFormat =
                    (systemCountryCode > 0 && parsedNumber.getCountryCode() == systemCountryCode) ?
                            PhoneNumberFormat.NATIONAL : PhoneNumberFormat.INTERNATIONAL;
            return phoneNumberUtil.format(parsedNumber, phoneNumberFormat);
        } catch (NumberParseException e) {
            LogUtil.e(TAG, "PhoneUtils.formatForDisplay: invalid phone number "
                    + LogUtil.sanitizePII(phoneText) + " with country " + systemCountry);
            return phoneText;
        }
    }

    /**
     * Is Messaging the default SMS app?
     * - On KLP+ this checks the system setting.
     * - On JB (and below) this always returns true, since the setting was added in KLP.
     */
    public boolean isDefaultSmsApp() {
        if (OsUtil.isAtLeastKLP()) {
            final String configuredApplication = Telephony.Sms.getDefaultSmsPackage(mContext);
            return  mContext.getPackageName().equals(configuredApplication);
        }
        return true;
    }

    /**
     * Get default SMS app package name
     *
     * @return the package name of default SMS app
     */
    public String getDefaultSmsApp() {
        if (OsUtil.isAtLeastKLP()) {
            return Telephony.Sms.getDefaultSmsPackage(mContext);
        }
        return null;
    }

    /**
     * Determines if SMS is currently enabled on this device.
     * - Device must support SMS
     * - On KLP+ we must be set as the default SMS app
     */
    public boolean isSmsEnabled() {
        return isSmsCapable() && isDefaultSmsApp();
    }

    /**
     * Returns the name of the default SMS app, or the empty string if there is
     * an error or there is no default app (e.g. JB and below).
     */
    public String getDefaultSmsAppLabel() {
        if (OsUtil.isAtLeastKLP()) {
            final String packageName = Telephony.Sms.getDefaultSmsPackage(mContext);
            final PackageManager pm = mContext.getPackageManager();
            try {
                final ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                return pm.getApplicationLabel(appInfo).toString();
            } catch (NameNotFoundException e) {
                // Fall through and return empty string
            }
        }
        return "";
    }

    /**
     * Gets the state of Airplane Mode.
     *
     * @return true if enabled.
     */
    @SuppressWarnings("deprecation")
    public boolean isAirplaneModeOn() {
        if (OsUtil.isAtLeastJB_MR1()) {
            return Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            return Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        }
    }

    public static String getMccMncString(int[] mccmnc) {
        if (mccmnc == null || mccmnc.length != 2) {
            return "000000";
        }
        return String.format("%03d%03d", mccmnc[0], mccmnc[1]);
    }

    public static String canonicalizeMccMnc(final String mcc, final String mnc) {
        try {
            return String.format("%03d%03d", Integer.parseInt(mcc), Integer.parseInt(mnc));
        } catch (final NumberFormatException e) {
            // Return invalid as is
            LogUtil.w(TAG, "canonicalizeMccMnc: invalid mccmnc:" + mcc + " ," + mnc);
        }
        return mcc + mnc;
    }

    /**
     * Returns whether the given destination is valid for sending SMS/MMS message.
     */
    public static boolean isValidSmsMmsDestination(final String destination) {
        return PhoneNumberUtils.isWellFormedSmsAddress(destination) ||
                MmsSmsUtils.isEmailAddress(destination);
    }

    public interface SubscriptionRunnable {
        void runForSubscription(int subId);
    }

    /**
     * A convenience method for iterating through all active subscriptions
     *
     * @param runnable a {@link SubscriptionRunnable} for performing work on each subscription.
     */
    public static void forEachActiveSubscription(final SubscriptionRunnable runnable) {
        if (OsUtil.isAtLeastL_MR1()) {
            final List<SubscriptionInfo> subscriptionList =
                    getDefault().toLMr1().getActiveSubscriptionInfoList();
            for (final SubscriptionInfo subscriptionInfo : subscriptionList) {
                runnable.runForSubscription(subscriptionInfo.getSubscriptionId());
            }
        } else {
            runnable.runForSubscription(ParticipantData.DEFAULT_SELF_SUB_ID);
        }
    }

    private static String getNumberFromPrefs(final Context context, final int subId) {
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String mmsPhoneNumberPrefKey =
                context.getString(R.string.mms_phone_number_pref_key);
        final String userDefinedNumber = prefs.getString(mmsPhoneNumberPrefKey, null);
        if (!TextUtils.isEmpty(userDefinedNumber)) {
            return userDefinedNumber;
        }
        return null;
    }
}
