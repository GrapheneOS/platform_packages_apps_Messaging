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

package com.android.messaging.datamodel.action;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Telephony.Mms;
import android.support.v4.util.LongSparseArray;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.SyncManager;
import com.android.messaging.datamodel.SyncManager.ThreadInfoCache;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.mmslib.SqliteWrapper;
import com.android.messaging.sms.DatabaseMessages;
import com.android.messaging.sms.DatabaseMessages.LocalDatabaseMessage;
import com.android.messaging.sms.DatabaseMessages.MmsMessage;
import com.android.messaging.sms.DatabaseMessages.SmsMessage;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.BuglePrefsKeys;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Action used to sync messages from smsmms db to local database
 */
public class SyncMessagesAction extends Action implements Parcelable {
    static final long SYNC_FAILED = Long.MIN_VALUE;

    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    private static final String KEY_START_TIMESTAMP = "start_timestamp";
    private static final String KEY_MAX_UPDATE = "max_update";
    private static final String KEY_LOWER_BOUND = "lower_bound";
    private static final String KEY_UPPER_BOUND = "upper_bound";
    private static final String BUNDLE_KEY_LAST_TIMESTAMP = "last_timestamp";
    private static final String BUNDLE_KEY_SMS_MESSAGES = "sms_to_add";
    private static final String BUNDLE_KEY_MMS_MESSAGES = "mms_to_add";
    private static final String BUNDLE_KEY_MESSAGES_TO_DELETE = "messages_to_delete";

    /**
     * Start a full sync (backed off a few seconds to avoid pulling sending/receiving messages).
     */
    public static void fullSync() {
        final BugleGservices bugleGservices = BugleGservices.get();
        final long smsSyncBackoffTimeMillis = bugleGservices.getLong(
                BugleGservicesKeys.SMS_SYNC_BACKOFF_TIME_MILLIS,
                BugleGservicesKeys.SMS_SYNC_BACKOFF_TIME_MILLIS_DEFAULT);

        final long now = System.currentTimeMillis();
        // TODO: Could base this off most recent message in db but now should be okay...
        final long startTimestamp = now - smsSyncBackoffTimeMillis;

        final SyncMessagesAction action = new SyncMessagesAction(-1L, startTimestamp,
                0, startTimestamp);
        action.start();
    }

    /**
     * Start an incremental sync to pull messages since last sync (backed off a few seconds)..
     */
    public static void sync() {
        final BugleGservices bugleGservices = BugleGservices.get();
        final long smsSyncBackoffTimeMillis = bugleGservices.getLong(
                BugleGservicesKeys.SMS_SYNC_BACKOFF_TIME_MILLIS,
                BugleGservicesKeys.SMS_SYNC_BACKOFF_TIME_MILLIS_DEFAULT);

        final long now = System.currentTimeMillis();
        // TODO: Could base this off most recent message in db but now should be okay...
        final long startTimestamp = now - smsSyncBackoffTimeMillis;

        sync(startTimestamp);
    }

    /**
     * Start an incremental sync when the application starts up (no back off as not yet
     *  sending/receiving).
     */
    public static void immediateSync() {
        final long now = System.currentTimeMillis();
        // TODO: Could base this off most recent message in db but now should be okay...
        final long startTimestamp = now;

        sync(startTimestamp);
    }

    private static void sync(final long startTimestamp) {
        if (!OsUtil.hasSmsPermission()) {
            // Sync requires READ_SMS permission
            return;
        }

        final BuglePrefs prefs = BuglePrefs.getApplicationPrefs();
        // Lower bound is end of previous sync
        final long syncLowerBoundTimeMillis = prefs.getLong(BuglePrefsKeys.LAST_SYNC_TIME,
                    BuglePrefsKeys.LAST_SYNC_TIME_DEFAULT);

        final SyncMessagesAction action = new SyncMessagesAction(syncLowerBoundTimeMillis,
                startTimestamp, 0, startTimestamp);
        action.start();
    }

    private SyncMessagesAction(final long lowerBound, final long upperBound,
            final int maxMessagesToUpdate, final long startTimestamp) {
        actionParameters.putLong(KEY_LOWER_BOUND, lowerBound);
        actionParameters.putLong(KEY_UPPER_BOUND, upperBound);
        actionParameters.putInt(KEY_MAX_UPDATE, maxMessagesToUpdate);
        actionParameters.putLong(KEY_START_TIMESTAMP, startTimestamp);
    }

    @Override
    protected Object executeAction() {
        final DatabaseWrapper db = DataModel.get().getDatabase();

        long lowerBoundTimeMillis = actionParameters.getLong(KEY_LOWER_BOUND);
        final long upperBoundTimeMillis = actionParameters.getLong(KEY_UPPER_BOUND);
        final int initialMaxMessagesToUpdate = actionParameters.getInt(KEY_MAX_UPDATE);
        final long startTimestamp = actionParameters.getLong(KEY_START_TIMESTAMP);

        if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
            LogUtil.d(TAG, "SyncMessagesAction: Request to sync messages from "
                    + lowerBoundTimeMillis + " to " + upperBoundTimeMillis + " (start timestamp = "
                    + startTimestamp + ", message update limit = " + initialMaxMessagesToUpdate
                    + ")");
        }

        final SyncManager syncManager = DataModel.get().getSyncManager();
        if (lowerBoundTimeMillis >= 0) {
            // Cursors
            final SyncCursorPair cursors = new SyncCursorPair(-1L, lowerBoundTimeMillis);
            final boolean inSync = cursors.isSynchronized(db);
            if (!inSync) {
                if (syncManager.delayUntilFullSync(startTimestamp) == 0) {
                    lowerBoundTimeMillis = -1;
                    actionParameters.putLong(KEY_LOWER_BOUND, lowerBoundTimeMillis);

                    if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                        LogUtil.d(TAG, "SyncMessagesAction: Messages before "
                                + lowerBoundTimeMillis + " not in sync; promoting to full sync");
                    }
                } else if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "SyncMessagesAction: Messages before "
                            + lowerBoundTimeMillis + " not in sync; will do incremental sync");
                }
            } else {
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "SyncMessagesAction: Messages before " + lowerBoundTimeMillis
                            + " are in sync");
                }
            }
        }

        // Check if sync allowed (can be too soon after last or one is already running)
        if (syncManager.shouldSync(lowerBoundTimeMillis < 0, startTimestamp)) {
            syncManager.startSyncBatch(upperBoundTimeMillis);
            requestBackgroundWork();
        }

        return null;
    }

    @Override
    protected Bundle doBackgroundWork() {
        final BugleGservices bugleGservices = BugleGservices.get();
        final DatabaseWrapper db = DataModel.get().getDatabase();

        final int maxMessagesToScan = bugleGservices.getInt(
                BugleGservicesKeys.SMS_SYNC_BATCH_MAX_MESSAGES_TO_SCAN,
                BugleGservicesKeys.SMS_SYNC_BATCH_MAX_MESSAGES_TO_SCAN_DEFAULT);

        final int initialMaxMessagesToUpdate = actionParameters.getInt(KEY_MAX_UPDATE);
        final int smsSyncSubsequentBatchSizeMin = bugleGservices.getInt(
                BugleGservicesKeys.SMS_SYNC_BATCH_SIZE_MIN,
                BugleGservicesKeys.SMS_SYNC_BATCH_SIZE_MIN_DEFAULT);
        final int smsSyncSubsequentBatchSizeMax = bugleGservices.getInt(
                BugleGservicesKeys.SMS_SYNC_BATCH_SIZE_MAX,
                BugleGservicesKeys.SMS_SYNC_BATCH_SIZE_MAX_DEFAULT);

        // Cap sync size to GServices limits
        final int maxMessagesToUpdate = Math.max(smsSyncSubsequentBatchSizeMin,
                Math.min(initialMaxMessagesToUpdate, smsSyncSubsequentBatchSizeMax));

        final long lowerBoundTimeMillis = actionParameters.getLong(KEY_LOWER_BOUND);
        final long upperBoundTimeMillis = actionParameters.getLong(KEY_UPPER_BOUND);

        LogUtil.i(TAG, "SyncMessagesAction: Starting batch for messages from "
                + lowerBoundTimeMillis + " to " + upperBoundTimeMillis
                + " (message update limit = " + maxMessagesToUpdate + ", message scan limit = "
                + maxMessagesToScan + ")");

        // Clear last change time so that we can work out if this batch is dirty when it completes
        final SyncManager syncManager = DataModel.get().getSyncManager();

        // Clear the singleton cache that maps threads to recipients and to conversations.
        final SyncManager.ThreadInfoCache cache = syncManager.getThreadInfoCache();
        cache.clear();

        // Sms messages to store
        final ArrayList<SmsMessage> smsToAdd = new ArrayList<SmsMessage>();
        // Mms messages to store
        final LongSparseArray<MmsMessage> mmsToAdd = new LongSparseArray<MmsMessage>();
        // List of local SMS/MMS to remove
        final ArrayList<LocalDatabaseMessage> messagesToDelete =
                new ArrayList<LocalDatabaseMessage>();

        long lastTimestampMillis = SYNC_FAILED;
        if (syncManager.isSyncing(upperBoundTimeMillis)) {
            // Cursors
            final SyncCursorPair cursors = new SyncCursorPair(lowerBoundTimeMillis,
                    upperBoundTimeMillis);

            // Actually compare the messages using cursor pair
            lastTimestampMillis = syncCursorPair(db, cursors, smsToAdd, mmsToAdd,
                    messagesToDelete, maxMessagesToScan, maxMessagesToUpdate, cache);
        }
        final Bundle response = new Bundle();

        // If comparison succeeds bundle up the changes for processing in ActionService
        if (lastTimestampMillis > SYNC_FAILED) {
            final ArrayList<MmsMessage> mmsToAddList = new ArrayList<MmsMessage>();
            for (int i = 0; i < mmsToAdd.size(); i++) {
                final MmsMessage mms = mmsToAdd.valueAt(i);
                mmsToAddList.add(mms);
            }

            response.putParcelableArrayList(BUNDLE_KEY_SMS_MESSAGES, smsToAdd);
            response.putParcelableArrayList(BUNDLE_KEY_MMS_MESSAGES, mmsToAddList);
            response.putParcelableArrayList(BUNDLE_KEY_MESSAGES_TO_DELETE, messagesToDelete);
        }
        response.putLong(BUNDLE_KEY_LAST_TIMESTAMP, lastTimestampMillis);

        return response;
    }

    /**
     * Compare messages based on timestamp and uri
     * @param db local database wrapper
     * @param cursors cursor pair holding references to local and remote messages
     * @param smsToAdd newly found sms messages to add
     * @param mmsToAdd newly found mms messages to add
     * @param messagesToDelete messages not found needing deletion
     * @param maxMessagesToScan max messages to scan for changes
     * @param maxMessagesToUpdate max messages to return for updates
     * @param cache cache for conversation id / thread id / recipient set mapping
     * @return timestamp of the oldest message seen during the sync scan
     */
    private long syncCursorPair(final DatabaseWrapper db, final SyncCursorPair cursors,
            final ArrayList<SmsMessage> smsToAdd, final LongSparseArray<MmsMessage> mmsToAdd,
            final ArrayList<LocalDatabaseMessage> messagesToDelete, final int maxMessagesToScan,
            final int maxMessagesToUpdate, final ThreadInfoCache cache) {
        long lastTimestampMillis;
        final long startTimeMillis = SystemClock.elapsedRealtime();

        // Number of messages scanned local and remote
        int localPos = 0;
        int remotePos = 0;
        int localTotal = 0;
        int remoteTotal = 0;
        // Scan through the messages on both sides and prepare messages for local message table
        // changes (including adding and deleting)
        try {
            cursors.query(db);

            localTotal = cursors.getLocalCount();
            remoteTotal = cursors.getRemoteCount();

            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "SyncMessagesAction: Scanning cursors (local count = " + localTotal
                        + ", remote count = " + remoteTotal + ", message update limit = "
                        + maxMessagesToUpdate + ", message scan limit = " + maxMessagesToScan
                        + ")");
            }

            lastTimestampMillis = cursors.scan(maxMessagesToScan, maxMessagesToUpdate,
                    smsToAdd, mmsToAdd, messagesToDelete, cache);

            localPos = cursors.getLocalPosition();
            remotePos = cursors.getRemotePosition();

            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "SyncMessagesAction: Scanned cursors (local position = " + localPos
                        + " of " + localTotal + ", remote position = " + remotePos + " of "
                        + remoteTotal + ")");
            }

            // Batch loading the parts of the MMS messages in this batch
            loadMmsParts(mmsToAdd);
            // Lookup senders for incoming mms messages
            setMmsSenders(mmsToAdd, cache);
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "SyncMessagesAction: Database exception", e);
            // Let's abort
            lastTimestampMillis = SYNC_FAILED;
        } catch (final Exception e) {
            // We want to catch anything unexpected since this is running in a separate thread
            // and any unexpected exception will just fail this thread silently.
            // Let's crash for dogfooders!
            LogUtil.wtf(TAG, "SyncMessagesAction: unexpected failure in scan", e);
            lastTimestampMillis = SYNC_FAILED;
        } finally {
            if (cursors != null) {
                cursors.close();
            }
        }

        final long endTimeMillis = SystemClock.elapsedRealtime();

        if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
            LogUtil.d(TAG, "SyncMessagesAction: Scan complete (took "
                    + (endTimeMillis - startTimeMillis) + " ms). " + smsToAdd.size()
                    + " remote SMS to add, " + mmsToAdd.size() + " MMS to add, "
                    + messagesToDelete.size() + " local messages to delete. "
                    + "Oldest timestamp seen = " + lastTimestampMillis);
        }

        return lastTimestampMillis;
    }

    /**
     * Perform local database updates and schedule follow on sync actions
     */
    @Override
    protected Object processBackgroundResponse(final Bundle response) {
        final long lastTimestampMillis = response.getLong(BUNDLE_KEY_LAST_TIMESTAMP);
        final long lowerBoundTimeMillis = actionParameters.getLong(KEY_LOWER_BOUND);
        final long upperBoundTimeMillis = actionParameters.getLong(KEY_UPPER_BOUND);
        final int maxMessagesToUpdate = actionParameters.getInt(KEY_MAX_UPDATE);
        final long startTimestamp = actionParameters.getLong(KEY_START_TIMESTAMP);

        // Check with the sync manager if any conflicting updates have been made to databases
        final SyncManager syncManager = DataModel.get().getSyncManager();
        final boolean orphan = !syncManager.isSyncing(upperBoundTimeMillis);

        // lastTimestampMillis used to indicate failure
        if (orphan) {
            // This batch does not match current in progress timestamp.
            LogUtil.w(TAG, "SyncMessagesAction: Ignoring orphan sync batch for messages from "
                    + lowerBoundTimeMillis + " to " + upperBoundTimeMillis);
        } else {
            final boolean dirty = syncManager.isBatchDirty(lastTimestampMillis);
            if (lastTimestampMillis == SYNC_FAILED) {
                LogUtil.e(TAG, "SyncMessagesAction: Sync failed - terminating");

                // Failed - update last sync times to throttle our failure rate
                final BuglePrefs prefs = BuglePrefs.getApplicationPrefs();
                // Save sync completion time so next sync will start from here
                prefs.putLong(BuglePrefsKeys.LAST_SYNC_TIME, startTimestamp);
                // Remember last full sync so that don't start background full sync right away
                prefs.putLong(BuglePrefsKeys.LAST_FULL_SYNC_TIME, startTimestamp);

                syncManager.complete();
            } else if (dirty) {
                LogUtil.w(TAG, "SyncMessagesAction: Redoing dirty sync batch of messages from "
                        + lowerBoundTimeMillis + " to " + upperBoundTimeMillis);

                // Redo this batch
                final SyncMessagesAction nextBatch =
                        new SyncMessagesAction(lowerBoundTimeMillis, upperBoundTimeMillis,
                                maxMessagesToUpdate, startTimestamp);

                syncManager.startSyncBatch(upperBoundTimeMillis);
                requestBackgroundWork(nextBatch);
            } else {
                // Succeeded
                final ArrayList<SmsMessage> smsToAdd =
                        response.getParcelableArrayList(BUNDLE_KEY_SMS_MESSAGES);
                final ArrayList<MmsMessage> mmsToAdd =
                        response.getParcelableArrayList(BUNDLE_KEY_MMS_MESSAGES);
                final ArrayList<LocalDatabaseMessage> messagesToDelete =
                        response.getParcelableArrayList(BUNDLE_KEY_MESSAGES_TO_DELETE);

                final int messagesUpdated = smsToAdd.size() + mmsToAdd.size()
                        + messagesToDelete.size();

                // Perform local database changes in one transaction
                long txnTimeMillis = 0;
                if (messagesUpdated > 0) {
                    final long startTimeMillis = SystemClock.elapsedRealtime();
                    final SyncMessageBatch batch = new SyncMessageBatch(smsToAdd, mmsToAdd,
                            messagesToDelete, syncManager.getThreadInfoCache());
                    batch.updateLocalDatabase();
                    final long endTimeMillis = SystemClock.elapsedRealtime();
                    txnTimeMillis = endTimeMillis - startTimeMillis;

                    LogUtil.i(TAG, "SyncMessagesAction: Updated local database "
                            + "(took " + txnTimeMillis + " ms). Added "
                            + smsToAdd.size() + " SMS, added " + mmsToAdd.size() + " MMS, deleted "
                            + messagesToDelete.size() + " messages.");

                    // TODO: Investigate whether we can make this more fine-grained.
                    MessagingContentProvider.notifyEverythingChanged();
                } else {
                    if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                        LogUtil.d(TAG, "SyncMessagesAction: No local database updates to make");
                    }

                    if (!syncManager.getHasFirstSyncCompleted()) {
                        // If we have never completed a sync before (fresh install) and there are
                        // no messages, still inform the UI of a change so it can update syncing
                        // messages shown to the user
                        MessagingContentProvider.notifyConversationListChanged();
                        MessagingContentProvider.notifyPartsChanged();
                    }
                }
                // Determine if there are more messages that need to be scanned
                if (lastTimestampMillis >= 0 && lastTimestampMillis >= lowerBoundTimeMillis) {
                    if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                        LogUtil.d(TAG, "SyncMessagesAction: More messages to sync; scheduling next "
                                + "sync batch now.");
                    }

                    // Include final millisecond of last sync in next sync
                    final long newUpperBoundTimeMillis = lastTimestampMillis + 1;
                    final int newMaxMessagesToUpdate = nextBatchSize(messagesUpdated,
                            txnTimeMillis);

                    final SyncMessagesAction nextBatch =
                            new SyncMessagesAction(lowerBoundTimeMillis, newUpperBoundTimeMillis,
                                    newMaxMessagesToUpdate, startTimestamp);

                    // Proceed with next batch
                    syncManager.startSyncBatch(newUpperBoundTimeMillis);
                    requestBackgroundWork(nextBatch);
                } else {
                    final BuglePrefs prefs = BuglePrefs.getApplicationPrefs();
                    // Save sync completion time so next sync will start from here
                    prefs.putLong(BuglePrefsKeys.LAST_SYNC_TIME, startTimestamp);
                    if (lowerBoundTimeMillis < 0) {
                        // Remember last full sync so that don't start another full sync right away
                        prefs.putLong(BuglePrefsKeys.LAST_FULL_SYNC_TIME, startTimestamp);
                    }

                    final long now = System.currentTimeMillis();

                    // After any sync check if new messages have arrived
                    final SyncCursorPair recents = new SyncCursorPair(startTimestamp, now);
                    final SyncCursorPair olders = new SyncCursorPair(-1L, startTimestamp);
                    final DatabaseWrapper db = DataModel.get().getDatabase();
                    if (!recents.isSynchronized(db)) {
                        LogUtil.i(TAG, "SyncMessagesAction: Changed messages after sync; "
                                + "scheduling an incremental sync now.");

                        // Just add a new batch for recent messages
                        final SyncMessagesAction nextBatch =
                                new SyncMessagesAction(startTimestamp, now, 0, startTimestamp);
                        syncManager.startSyncBatch(now);
                        requestBackgroundWork(nextBatch);
                        // After partial sync verify sync state
                    } else if (lowerBoundTimeMillis >= 0 && !olders.isSynchronized(db)) {
                        // Add a batch going back to start of time
                        LogUtil.w(TAG, "SyncMessagesAction: Changed messages before sync batch; "
                                + "scheduling a full sync now.");

                        final SyncMessagesAction nextBatch =
                                new SyncMessagesAction(-1L, startTimestamp, 0, startTimestamp);

                        syncManager.startSyncBatch(startTimestamp);
                        requestBackgroundWork(nextBatch);
                    } else {
                        LogUtil.i(TAG, "SyncMessagesAction: All messages now in sync");

                        // All done, in sync
                        syncManager.complete();
                    }
                }
                // Either sync should be complete or we should have a follow up request
                Assert.isTrue(hasBackgroundActions() || !syncManager.isSyncing());
            }
        }

        return null;
    }

    /**
     * Decide the next batch size based on the stats we collected with past batch
     * @param messagesUpdated number of messages updated in this batch
     * @param txnTimeMillis time the transaction took in ms
     * @return Target number of messages to sync for next batch
     */
    private static int nextBatchSize(final int messagesUpdated, final long txnTimeMillis) {
        final BugleGservices bugleGservices = BugleGservices.get();
        final long smsSyncSubsequentBatchTimeLimitMillis = bugleGservices.getLong(
                BugleGservicesKeys.SMS_SYNC_BATCH_TIME_LIMIT_MILLIS,
                BugleGservicesKeys.SMS_SYNC_BATCH_TIME_LIMIT_MILLIS_DEFAULT);

        if (txnTimeMillis <= 0) {
            return 0;
        }
        // Number of messages we can sync within the batch time limit using
        // the average sync time calculated based on the stats we collected
        // in previous batch
        return (int) ((double) (messagesUpdated) / (double) txnTimeMillis
                        * smsSyncSubsequentBatchTimeLimitMillis);
    }

    /**
     * Batch loading MMS parts for the messages in current batch
     */
    private void loadMmsParts(final LongSparseArray<MmsMessage> mmses) {
        final Context context = Factory.get().getApplicationContext();
        final int totalIds = mmses.size();
        for (int start = 0; start < totalIds; start += MmsUtils.MAX_IDS_PER_QUERY) {
            final int end = Math.min(start + MmsUtils.MAX_IDS_PER_QUERY, totalIds); //excluding
            final int count = end - start;
            final String batchSelection = String.format(
                    Locale.US,
                    "%s != '%s' AND %s IN %s",
                    Mms.Part.CONTENT_TYPE,
                    ContentType.APP_SMIL,
                    Mms.Part.MSG_ID,
                    MmsUtils.getSqlInOperand(count));
            final String[] batchSelectionArgs = new String[count];
            for (int i = 0; i < count; i++) {
                batchSelectionArgs[i] = Long.toString(mmses.valueAt(start + i).getId());
            }
            final Cursor cursor = SqliteWrapper.query(
                    context,
                    context.getContentResolver(),
                    MmsUtils.MMS_PART_CONTENT_URI,
                    DatabaseMessages.MmsPart.PROJECTION,
                    batchSelection,
                    batchSelectionArgs,
                    null/*sortOrder*/);
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        // Delay loading the media content for parsing for efficiency
                        // TODO: load the media and fill in the dimensions when
                        // we actually display it
                        final DatabaseMessages.MmsPart part =
                                DatabaseMessages.MmsPart.get(cursor, false/*loadMedia*/);
                        final DatabaseMessages.MmsMessage mms = mmses.get(part.mMessageId);
                        if (mms != null) {
                            mms.addPart(part);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }
    }

    /**
     * Batch loading MMS sender for the messages in current batch
     */
    private void setMmsSenders(final LongSparseArray<MmsMessage> mmses,
            final ThreadInfoCache cache) {
        // Store all the MMS messages
        for (int i = 0; i < mmses.size(); i++) {
            final MmsMessage mms = mmses.valueAt(i);

            final boolean isOutgoing = mms.mType != Mms.MESSAGE_BOX_INBOX;
            String senderId = null;
            if (!isOutgoing) {
                // We only need to find out sender phone number for received message
                senderId = getMmsSender(mms, cache);
                if (senderId == null) {
                    LogUtil.w(TAG, "SyncMessagesAction: Could not find sender of incoming MMS "
                            + "message " + mms.getUri() + "; using 'unknown sender' instead");
                    senderId = ParticipantData.getUnknownSenderDestination();
                }
            }
            mms.setSender(senderId);
        }
    }

    /**
     * Find out the sender of an MMS message
     */
    private String getMmsSender(final MmsMessage mms, final ThreadInfoCache cache) {
        final List<String> recipients = cache.getThreadRecipients(mms.mThreadId);
        Assert.notNull(recipients);
        Assert.isTrue(recipients.size() > 0);

        if (recipients.size() == 1
                && recipients.get(0).equals(ParticipantData.getUnknownSenderDestination())) {
            LogUtil.w(TAG, "SyncMessagesAction: MMS message " + mms.mUri + " has unknown sender "
                    + "(thread id = " + mms.mThreadId + ")");
        }

        return MmsUtils.getMmsSender(recipients, mms.mUri);
    }

    private SyncMessagesAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<SyncMessagesAction> CREATOR
            = new Parcelable.Creator<SyncMessagesAction>() {
        @Override
        public SyncMessagesAction createFromParcel(final Parcel in) {
            return new SyncMessagesAction(in);
        }

        @Override
        public SyncMessagesAction[] newArray(final int size) {
            return new SyncMessagesAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
