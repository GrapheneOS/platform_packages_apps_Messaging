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

package com.android.messaging.datamodel.data;

import android.net.Uri;
import android.text.TextUtils;

import com.android.messaging.datamodel.MessageTextStats;
import com.android.messaging.datamodel.action.ReadDraftDataAction;
import com.android.messaging.datamodel.action.ReadDraftDataAction.ReadDraftDataActionListener;
import com.android.messaging.datamodel.action.ReadDraftDataAction.ReadDraftDataActionMonitor;
import com.android.messaging.datamodel.action.WriteDraftMessageAction;
import com.android.messaging.datamodel.binding.BindableData;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.sms.MmsSmsUtils;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.DoesNotRunOnMainThread;
import com.android.messaging.util.Assert.RunsOnMainThread;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.SafeAsyncTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DraftMessageData extends BindableData implements ReadDraftDataActionListener {

    /**
     * Interface for DraftMessageData listeners
     */
    public interface DraftMessageDataListener {
        @RunsOnMainThread
        void onDraftChanged(DraftMessageData data, int changeFlags);

        @RunsOnMainThread
        void onDraftAttachmentLimitReached(DraftMessageData data);

        @RunsOnMainThread
        void onDraftAttachmentLoadFailed();
    }

    /**
     * Interface for providing subscription-related data to DraftMessageData
     */
    public interface DraftMessageSubscriptionDataProvider {
        int getConversationSelfSubId();
    }

    // Flags sent to onDraftChanged to help the receiver limit the amount of work done
    public static int ATTACHMENTS_CHANGED  =     0x0001;
    public static int MESSAGE_TEXT_CHANGED =     0x0002;
    public static int MESSAGE_SUBJECT_CHANGED =  0x0004;
    // Whether the self participant data has been loaded
    public static int SELF_CHANGED =             0x0008;
    public static int ALL_CHANGED =              0x00FF;
    // ALL_CHANGED intentionally doesn't include WIDGET_CHANGED. ConversationFragment needs to
    // be notified if the draft it is looking at is changed externally (by a desktop widget) so it
    // can reload the draft.
    public static int WIDGET_CHANGED  =          0x0100;

    private final String mConversationId;
    private ReadDraftDataActionMonitor mMonitor;
    private final DraftMessageDataEventDispatcher mListeners;
    private DraftMessageSubscriptionDataProvider mSubscriptionDataProvider;

    private boolean mIncludeEmailAddress;
    private boolean mIsGroupConversation;
    private String mMessageText;
    private String mMessageSubject;
    private String mSelfId;
    private MessageTextStats mMessageTextStats;
    private boolean mSending;

    /** Keeps track of completed attachments in the message draft. This data is persisted to db */
    private final List<MessagePartData> mAttachments;

    /** A read-only wrapper on mAttachments surfaced to the UI layer for rendering */
    private final List<MessagePartData> mReadOnlyAttachments;

    /** Keeps track of pending attachments that are being loaded. The pending attachments are
     * transient, because they are not persisted to the database and are dropped once we go
     * to the background (after the UI calls saveToStorage) */
    private final List<PendingAttachmentData> mPendingAttachments;

    /** A read-only wrapper on mPendingAttachments surfaced to the UI layer for rendering */
    private final List<PendingAttachmentData> mReadOnlyPendingAttachments;

    /** Is the current draft a cached copy of what's been saved to the database. If so, we
     * may skip loading from database if we are still bound */
    private boolean mIsDraftCachedCopy;

    /** Whether we are currently asynchronously validating the draft before sending. */
    private CheckDraftForSendTask mCheckDraftForSendTask;

    public DraftMessageData(final String conversationId) {
        mConversationId = conversationId;
        mAttachments = new ArrayList<MessagePartData>();
        mReadOnlyAttachments = Collections.unmodifiableList(mAttachments);
        mPendingAttachments = new ArrayList<PendingAttachmentData>();
        mReadOnlyPendingAttachments = Collections.unmodifiableList(mPendingAttachments);
        mListeners = new DraftMessageDataEventDispatcher();
        mMessageTextStats = new MessageTextStats();
    }

    public void addListener(final DraftMessageDataListener listener) {
        mListeners.add(listener);
    }

    public void setSubscriptionDataProvider(final DraftMessageSubscriptionDataProvider provider) {
        mSubscriptionDataProvider = provider;
    }

    public void updateFromMessageData(final MessageData message, final String bindingId) {
        // New attachments have arrived - only update if the user hasn't already edited
        Assert.notNull(bindingId);
        // The draft is now synced with actual MessageData and no longer a cached copy.
        mIsDraftCachedCopy = false;
        // Do not use the loaded draft if the user began composing a message before the draft loaded
        // During config changes (orientation), the text fields preserve their data, so allow them
        // to be the same and still consider the draft unchanged by the user
        if (isDraftEmpty() || (TextUtils.equals(mMessageText, message.getMessageText()) &&
                TextUtils.equals(mMessageSubject, message.getMmsSubject()) &&
                mAttachments.isEmpty())) {
            // No need to clear as just checked it was empty or a subset
            setMessageText(message.getMessageText(), false /* notify */);
            setMessageSubject(message.getMmsSubject(), false /* notify */);
            for (final MessagePartData part : message.getParts()) {
                if (part.isAttachment() && getAttachmentCount() >= getAttachmentLimit()) {
                    dispatchAttachmentLimitReached();
                    break;
                }

                if (part instanceof PendingAttachmentData) {
                    // This is a pending attachment data from share intent (e.g. an shared image
                    // that we need to persist locally).
                    final PendingAttachmentData data = (PendingAttachmentData) part;
                    Assert.equals(PendingAttachmentData.STATE_PENDING, data.getCurrentState());
                    addOnePendingAttachmentNoNotify(data, bindingId);
                } else if (part.isAttachment()) {
                    addOneAttachmentNoNotify(part);
                }
            }
            dispatchChanged(ALL_CHANGED);
        } else {
            // The user has started a new message so we throw out the draft message data if there
            // is one but we also loaded the self metadata and need to let our listeners know.
            dispatchChanged(SELF_CHANGED);
        }
    }

    /**
     * Create a MessageData object containing a copy of all the parts in this DraftMessageData.
     *
     * @param clearLocalCopy whether we should clear out the in-memory copy of the parts. If we
     *        are simply pausing/resuming and not sending the message, then we can keep
     * @return the MessageData for the draft, null if self id is not set
     */
    public MessageData createMessageWithCurrentAttachments(final boolean clearLocalCopy) {
        MessageData message = null;
        if (getIsMms()) {
            message = MessageData.createDraftMmsMessage(mConversationId, mSelfId,
                    mMessageText, mMessageSubject);
            for (final MessagePartData attachment : mAttachments) {
                message.addPart(attachment);
            }
        } else {
            message = MessageData.createDraftSmsMessage(mConversationId, mSelfId,
                    mMessageText);
        }

        if (clearLocalCopy) {
            // The message now owns all the attachments and the text...
            clearLocalDraftCopy();
            dispatchChanged(ALL_CHANGED);
        } else {
            // The draft message becomes a cached copy for UI.
            mIsDraftCachedCopy = true;
        }
        return message;
    }

    private void clearLocalDraftCopy() {
        mIsDraftCachedCopy = false;
        mAttachments.clear();
        setMessageText("");
        setMessageSubject("");
    }

    public String getConversationId() {
        return mConversationId;
    }

    public String getMessageText() {
        return mMessageText;
    }

    public String getMessageSubject() {
        return mMessageSubject;
    }

    public boolean getIsMms() {
        final int selfSubId = getSelfSubId();
        return MmsSmsUtils.getRequireMmsForEmailAddress(mIncludeEmailAddress, selfSubId) ||
                (mIsGroupConversation && MmsUtils.groupMmsEnabled(selfSubId)) ||
                mMessageTextStats.getMessageLengthRequiresMms() || !mAttachments.isEmpty() ||
                !TextUtils.isEmpty(mMessageSubject);
    }

    public boolean getIsGroupMmsConversation() {
        return getIsMms() && mIsGroupConversation;
    }

    public String getSelfId() {
        return mSelfId;
    }

    public int getNumMessagesToBeSent() {
        return mMessageTextStats.getNumMessagesToBeSent();
    }

    public int getCodePointsRemainingInCurrentMessage() {
        return mMessageTextStats.getCodePointsRemainingInCurrentMessage();
    }

    public int getSelfSubId() {
        return mSubscriptionDataProvider == null ? ParticipantData.DEFAULT_SELF_SUB_ID :
                mSubscriptionDataProvider.getConversationSelfSubId();
    }

    private void setMessageText(final String messageText, final boolean notify) {
        mMessageText = messageText;
        mMessageTextStats.updateMessageTextStats(getSelfSubId(), mMessageText);
        if (notify) {
            dispatchChanged(MESSAGE_TEXT_CHANGED);
        }
    }

    private void setMessageSubject(final String subject, final boolean notify) {
        mMessageSubject = subject;
        if (notify) {
            dispatchChanged(MESSAGE_SUBJECT_CHANGED);
        }
    }

    public void setMessageText(final String messageText) {
        setMessageText(messageText, false);
    }

    public void setMessageSubject(final String subject) {
        setMessageSubject(subject, false);
    }

    public void addAttachments(final Collection<? extends MessagePartData> attachments) {
        // If the incoming attachments contains a single-only attachment, we need to clear
        // the existing attachments.
        for (final MessagePartData data : attachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the attachment we're adding can only
                // exist by itself.
                destroyAttachments();
                break;
            }
        }
        // If the existing attachments contain a single-only attachment, we need to clear the
        // existing attachments to make room for the incoming attachment.
        for (final MessagePartData data : mAttachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the single attachment can only exist
                // by itself
                destroyAttachments();
                break;
            }
        }
        // If any of the pending attachments contain a single-only attachment, we need to clear the
        // existing attachments to make room for the incoming attachment.
        for (final MessagePartData data : mPendingAttachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the single attachment can only exist
                // by itself
                destroyAttachments();
                break;
            }
        }

        boolean reachedLimit = false;
        for (final MessagePartData data : attachments) {
            // Don't break out of loop even if limit has been reached so we can destroy all
            // of the over-limit attachments.
            reachedLimit |= addOneAttachmentNoNotify(data);
        }
        if (reachedLimit) {
            dispatchAttachmentLimitReached();
        }
        dispatchChanged(ATTACHMENTS_CHANGED);
    }

    public boolean containsAttachment(final Uri contentUri) {
        for (final MessagePartData existingAttachment : mAttachments) {
            if (existingAttachment.getContentUri().equals(contentUri)) {
                return true;
            }
        }

        for (final PendingAttachmentData pendingAttachment : mPendingAttachments) {
            if (pendingAttachment.getContentUri().equals(contentUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try to add one attachment to the attachment list, while guarding against duplicates and
     * going over the limit.
     * @return true if the attachment limit was reached, false otherwise
     */
    private boolean addOneAttachmentNoNotify(final MessagePartData attachment) {
        Assert.isTrue(attachment.isAttachment());
        final boolean reachedLimit = getAttachmentCount() >= getAttachmentLimit();
        if (reachedLimit || containsAttachment(attachment.getContentUri())) {
            // Never go over the limit. Never add duplicated attachments.
            attachment.destroyAsync();
            return reachedLimit;
        } else {
            addAttachment(attachment, null /*pendingAttachment*/);
            return false;
        }
    }

    private void addAttachment(final MessagePartData attachment,
            final PendingAttachmentData pendingAttachment) {
        if (attachment != null && attachment.isSinglePartOnly()) {
            // clear any existing attachments because the attachment we're adding can only
            // exist by itself.
            destroyAttachments();
        }
        if (pendingAttachment != null && pendingAttachment.isSinglePartOnly()) {
            // clear any existing attachments because the attachment we're adding can only
            // exist by itself.
            destroyAttachments();
        }
        // If the existing attachments contain a single-only attachment, we need to clear the
        // existing attachments to make room for the incoming attachment.
        for (final MessagePartData data : mAttachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the single attachment can only exist
                // by itself
                destroyAttachments();
                break;
            }
        }
        // If any of the pending attachments contain a single-only attachment, we need to clear the
        // existing attachments to make room for the incoming attachment.
        for (final MessagePartData data : mPendingAttachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the single attachment can only exist
                // by itself
                destroyAttachments();
                break;
            }
        }
        if (attachment != null) {
            mAttachments.add(attachment);
        } else if (pendingAttachment != null) {
            mPendingAttachments.add(pendingAttachment);
        }
    }

    public void addPendingAttachment(final PendingAttachmentData pendingAttachment,
            final BindingBase<DraftMessageData> binding) {
        final boolean reachedLimit = addOnePendingAttachmentNoNotify(pendingAttachment,
                binding.getBindingId());
        if (reachedLimit) {
            dispatchAttachmentLimitReached();
        }
        dispatchChanged(ATTACHMENTS_CHANGED);
    }

    /**
     * Try to add one pending attachment, while guarding against duplicates and
     * going over the limit.
     * @return true if the attachment limit was reached, false otherwise
     */
    private boolean addOnePendingAttachmentNoNotify(final PendingAttachmentData pendingAttachment,
            final String bindingId) {
        final boolean reachedLimit = getAttachmentCount() >= getAttachmentLimit();
        if (reachedLimit || containsAttachment(pendingAttachment.getContentUri())) {
            // Never go over the limit. Never add duplicated attachments.
            pendingAttachment.destroyAsync();
            return reachedLimit;
        } else {
            Assert.isTrue(!mPendingAttachments.contains(pendingAttachment));
            Assert.equals(PendingAttachmentData.STATE_PENDING, pendingAttachment.getCurrentState());
            addAttachment(null /*attachment*/, pendingAttachment);

            pendingAttachment.loadAttachmentForDraft(this, bindingId);
            return false;
        }
    }

    public void setSelfId(final String selfId, final boolean notify) {
        LogUtil.d(LogUtil.BUGLE_TAG, "DraftMessageData: set selfId=" + selfId
                + " for conversationId=" + mConversationId);
        mSelfId = selfId;
        if (notify) {
            dispatchChanged(SELF_CHANGED);
        }
    }

    public boolean hasAttachments() {
        return !mAttachments.isEmpty();
    }

    public boolean hasPendingAttachments() {
        return !mPendingAttachments.isEmpty();
    }

    private int getAttachmentCount() {
        return mAttachments.size() + mPendingAttachments.size();
    }

    private int getVideoAttachmentCount() {
        int count = 0;
        for (MessagePartData part : mAttachments) {
            if (part.isVideo()) {
                count++;
            }
        }
        for (MessagePartData part : mPendingAttachments) {
            if (part.isVideo()) {
                count++;
            }
        }
        return count;
    }

    private int getAttachmentLimit() {
        return BugleGservices.get().getInt(
                BugleGservicesKeys.MMS_ATTACHMENT_LIMIT,
                BugleGservicesKeys.MMS_ATTACHMENT_LIMIT_DEFAULT);
    }

    public void removeAttachment(final MessagePartData attachment) {
        for (final MessagePartData existingAttachment : mAttachments) {
            if (existingAttachment.getContentUri().equals(attachment.getContentUri())) {
                mAttachments.remove(existingAttachment);
                existingAttachment.destroyAsync();
                dispatchChanged(ATTACHMENTS_CHANGED);
                break;
            }
        }
    }

    public void removeExistingAttachments(final Set<MessagePartData> attachmentsToRemove) {
        boolean removed = false;
        final Iterator<MessagePartData> iterator = mAttachments.iterator();
        while (iterator.hasNext()) {
            final MessagePartData existingAttachment = iterator.next();
            if (attachmentsToRemove.contains(existingAttachment)) {
                iterator.remove();
                existingAttachment.destroyAsync();
                removed = true;
            }
        }

        if (removed) {
            dispatchChanged(ATTACHMENTS_CHANGED);
        }
    }

    public void removePendingAttachment(final PendingAttachmentData pendingAttachment) {
        for (final PendingAttachmentData existingAttachment : mPendingAttachments) {
            if (existingAttachment.getContentUri().equals(pendingAttachment.getContentUri())) {
                mPendingAttachments.remove(pendingAttachment);
                pendingAttachment.destroyAsync();
                dispatchChanged(ATTACHMENTS_CHANGED);
                break;
            }
        }
    }

    public void updatePendingAttachment(final MessagePartData updatedAttachment,
            final PendingAttachmentData pendingAttachment) {
        for (final PendingAttachmentData existingAttachment : mPendingAttachments) {
            if (existingAttachment.getContentUri().equals(pendingAttachment.getContentUri())) {
                mPendingAttachments.remove(pendingAttachment);
                if (pendingAttachment.isSinglePartOnly()) {
                    updatedAttachment.setSinglePartOnly(true);
                }
                mAttachments.add(updatedAttachment);
                dispatchChanged(ATTACHMENTS_CHANGED);
                return;
            }
        }

        // If we are here, this means the pending attachment has been dropped before the task
        // to load it was completed. In this case destroy the temporarily staged file since it
        // is no longer needed.
        updatedAttachment.destroyAsync();
    }

    /**
     * Remove the attachments from the draft and notify any listeners.
     * @param flags typically this will be ATTACHMENTS_CHANGED. When attachments are cleared in a
     * widget, flags will also contain WIDGET_CHANGED.
     */
    public void clearAttachments(final int flags) {
        destroyAttachments();
        dispatchChanged(flags);
    }

    public List<MessagePartData> getReadOnlyAttachments() {
        return mReadOnlyAttachments;
    }

    public List<PendingAttachmentData> getReadOnlyPendingAttachments() {
        return mReadOnlyPendingAttachments;
    }

    public boolean loadFromStorage(final BindingBase<DraftMessageData> binding,
            final MessageData optionalIncomingDraft, boolean clearLocalDraft) {
        LogUtil.d(LogUtil.BUGLE_TAG, "DraftMessageData: "
                + (optionalIncomingDraft == null ? "loading" : "setting")
                + " for conversationId=" + mConversationId);
        if (clearLocalDraft) {
            clearLocalDraftCopy();
        }
        final boolean isDraftCachedCopy = mIsDraftCachedCopy;
        mIsDraftCachedCopy = false;
        // Before reading message from db ensure the caller is bound to us (and knows the id)
        if (mMonitor == null && !isDraftCachedCopy && isBound(binding.getBindingId())) {
            mMonitor = ReadDraftDataAction.readDraftData(mConversationId,
                    optionalIncomingDraft, binding.getBindingId(), this);
            return true;
        }
        return false;
    }

    /**
     * Saves the current draft to db. This will save the draft and drop any pending attachments
     * we have. The UI typically goes into the background when this is called, and instead of
     * trying to persist the state of the pending attachments (the app may be killed, the activity
     * may be destroyed), we simply drop the pending attachments for consistency.
     */
    public void saveToStorage(final BindingBase<DraftMessageData> binding) {
        saveToStorageInternal(binding);
        dropPendingAttachments();
    }

    private void saveToStorageInternal(final BindingBase<DraftMessageData> binding) {
        // Create MessageData to store to db, but don't clear the in-memory copy so UI will
        // continue to display it.
        // If self id is null then we'll not attempt to change the conversation's self id.
        final MessageData message = createMessageWithCurrentAttachments(false /* clearLocalCopy */);
        // Before writing message to db ensure the caller is bound to us (and knows the id)
        if (isBound(binding.getBindingId())){
            WriteDraftMessageAction.writeDraftMessage(mConversationId, message);
        }
    }

    /**
     * Called when we are ready to send the message. This will assemble/return the MessageData for
     * sending and clear the local draft data, both from memory and from DB. This will also bind
     * the message data with a self Id through which the message will be sent.
     *
     * @param binding the binding object from our consumer. We need to make sure we are still bound
     *        to that binding before saving to storage.
     */
    public MessageData prepareMessageForSending(final BindingBase<DraftMessageData> binding) {
        // We can't send the message while there's still stuff pending.
        Assert.isTrue(!hasPendingAttachments());
        mSending = true;
        // Assembles the message to send and empty working draft data.
        // If self id is null then message is sent with conversation's self id.
        final MessageData messageToSend =
                createMessageWithCurrentAttachments(true /* clearLocalCopy */);
        // Note sending message will empty the draft data in DB.
        mSending = false;
        return messageToSend;
    }

    public boolean isSending() {
        return mSending;
    }

    @Override // ReadDraftMessageActionListener.onReadDraftMessageSucceeded
    public void onReadDraftDataSucceeded(final ReadDraftDataAction action, final Object data,
            final MessageData message, final ConversationListItemData conversation) {
        final String bindingId = (String) data;

        // Before passing draft message on to ui ensure the data is bound to the same bindingid
        if (isBound(bindingId)) {
            mSelfId = message.getSelfId();
            mIsGroupConversation = conversation.getIsGroup();
            mIncludeEmailAddress = conversation.getIncludeEmailAddress();
            updateFromMessageData(message, bindingId);
            LogUtil.d(LogUtil.BUGLE_TAG, "DraftMessageData: draft loaded. "
                    + "conversationId=" + mConversationId + " selfId=" + mSelfId);
        } else {
            LogUtil.w(LogUtil.BUGLE_TAG, "DraftMessageData: draft loaded but not bound. "
                    + "conversationId=" + mConversationId);
        }
        mMonitor = null;
    }

    @Override // ReadDraftMessageActionListener.onReadDraftDataFailed
    public void onReadDraftDataFailed(final ReadDraftDataAction action, final Object data) {
        LogUtil.w(LogUtil.BUGLE_TAG, "DraftMessageData: draft not loaded. "
                + "conversationId=" + mConversationId);
        // The draft is now synced with actual MessageData and no longer a cached copy.
        mIsDraftCachedCopy = false;
        // Just clear the monitor - no update to draft data
        mMonitor = null;
    }

    /**
     * Check if Bugle is default sms app
     * @return
     */
    public boolean getIsDefaultSmsApp() {
        return PhoneUtils.getDefault().isDefaultSmsApp();
    }

    @Override //BindableData.unregisterListeners
    protected void unregisterListeners() {
        if (mMonitor != null) {
            mMonitor.unregister();
        }
        mMonitor = null;
        mListeners.clear();
    }

    private void destroyAttachments() {
        for (final MessagePartData attachment : mAttachments) {
            attachment.destroyAsync();
        }
        mAttachments.clear();
        mPendingAttachments.clear();
    }

    private void dispatchChanged(final int changeFlags) {
        // No change is expected to be made to the draft if it is in cached copy state.
        if (mIsDraftCachedCopy) {
            return;
        }
        // Any change in the draft will cancel any pending draft checking task, since the
        // size/status of the draft may have changed.
        if (mCheckDraftForSendTask != null) {
            mCheckDraftForSendTask.cancel(true /* mayInterruptIfRunning */);
            mCheckDraftForSendTask = null;
        }
        mListeners.onDraftChanged(this, changeFlags);
    }

    private void dispatchAttachmentLimitReached() {
        mListeners.onDraftAttachmentLimitReached(this);
    }

    /**
     * Drop any pending attachments that haven't finished. This is called after the UI goes to
     * the background and we persist the draft data to the database.
     */
    private void dropPendingAttachments() {
        mPendingAttachments.clear();
    }

    private boolean isDraftEmpty() {
        return TextUtils.isEmpty(mMessageText) && mAttachments.isEmpty() &&
                TextUtils.isEmpty(mMessageSubject);
    }

    public boolean isCheckingDraft() {
        return mCheckDraftForSendTask != null && !mCheckDraftForSendTask.isCancelled();
    }

    public void checkDraftForAction(final boolean checkMessageSize, final int selfSubId,
            final CheckDraftTaskCallback callback, final Binding<DraftMessageData> binding) {
        new CheckDraftForSendTask(checkMessageSize, selfSubId, callback, binding)
            .executeOnThreadPool((Void) null);
    }

    /**
     * Allows us to have multiple data listeners for DraftMessageData
     */
    private class DraftMessageDataEventDispatcher
        extends ArrayList<DraftMessageDataListener>
        implements DraftMessageDataListener {

        @Override
        @RunsOnMainThread
        public void onDraftChanged(DraftMessageData data, int changeFlags) {
            Assert.isMainThread();
            for (final DraftMessageDataListener listener : this) {
                listener.onDraftChanged(data, changeFlags);
            }
        }

        @Override
        @RunsOnMainThread
        public void onDraftAttachmentLimitReached(DraftMessageData data) {
            Assert.isMainThread();
            for (final DraftMessageDataListener listener : this) {
                listener.onDraftAttachmentLimitReached(data);
            }
        }

        @Override
        @RunsOnMainThread
        public void onDraftAttachmentLoadFailed() {
            Assert.isMainThread();
            for (final DraftMessageDataListener listener : this) {
                listener.onDraftAttachmentLoadFailed();
            }
        }
    }

    public interface CheckDraftTaskCallback {
        void onDraftChecked(DraftMessageData data, int result);
    }

    public class CheckDraftForSendTask extends SafeAsyncTask<Void, Void, Integer> {
        public static final int RESULT_PASSED = 0;
        public static final int RESULT_HAS_PENDING_ATTACHMENTS = 1;
        public static final int RESULT_NO_SELF_PHONE_NUMBER_IN_GROUP_MMS = 2;
        public static final int RESULT_MESSAGE_OVER_LIMIT = 3;
        public static final int RESULT_VIDEO_ATTACHMENT_LIMIT_EXCEEDED = 4;
        public static final int RESULT_SIM_NOT_READY = 5;
        private final boolean mCheckMessageSize;
        private final int mSelfSubId;
        private final CheckDraftTaskCallback mCallback;
        private final String mBindingId;
        private final List<MessagePartData> mAttachmentsCopy;
        private int mPreExecuteResult = RESULT_PASSED;

        public CheckDraftForSendTask(final boolean checkMessageSize, final int selfSubId,
                final CheckDraftTaskCallback callback, final Binding<DraftMessageData> binding) {
            mCheckMessageSize = checkMessageSize;
            mSelfSubId = selfSubId;
            mCallback = callback;
            mBindingId = binding.getBindingId();
            // Obtain an immutable copy of the attachment list so we can operate on it in the
            // background thread.
            mAttachmentsCopy = new ArrayList<MessagePartData>(mAttachments);

            mCheckDraftForSendTask = this;
        }

        @Override
        protected void onPreExecute() {
            // Perform checking work that can happen on the main thread.
            if (hasPendingAttachments()) {
                mPreExecuteResult = RESULT_HAS_PENDING_ATTACHMENTS;
                return;
            }
            if (getIsGroupMmsConversation()) {
                try {
                    if (TextUtils.isEmpty(PhoneUtils.get(mSelfSubId).getSelfRawNumber(true))) {
                        mPreExecuteResult = RESULT_NO_SELF_PHONE_NUMBER_IN_GROUP_MMS;
                        return;
                    }
                } catch (IllegalStateException e) {
                    // This happens when there is no active subscription, e.g. on Nova
                    // when the phone switches carrier.
                    mPreExecuteResult = RESULT_SIM_NOT_READY;
                    return;
                }
            }
            if (getVideoAttachmentCount() > MmsUtils.MAX_VIDEO_ATTACHMENT_COUNT) {
                mPreExecuteResult = RESULT_VIDEO_ATTACHMENT_LIMIT_EXCEEDED;
                return;
            }
        }

        @Override
        protected Integer doInBackgroundTimed(Void... params) {
            if (mPreExecuteResult != RESULT_PASSED) {
                return mPreExecuteResult;
            }

            if (mCheckMessageSize && getIsMessageOverLimit()) {
                return RESULT_MESSAGE_OVER_LIMIT;
            }
            return RESULT_PASSED;
        }

        @Override
        protected void onPostExecute(Integer result) {
            mCheckDraftForSendTask = null;
            // Only call back if we are bound to the original binding.
            if (isBound(mBindingId) && !isCancelled()) {
                mCallback.onDraftChecked(DraftMessageData.this, result);
            } else {
                if (!isBound(mBindingId)) {
                    LogUtil.w(LogUtil.BUGLE_TAG, "Message can't be sent: draft not bound");
                }
                if (isCancelled()) {
                    LogUtil.w(LogUtil.BUGLE_TAG, "Message can't be sent: draft is cancelled");
                }
            }
        }

        @Override
        protected void onCancelled() {
            mCheckDraftForSendTask = null;
        }

        /**
         * 1. Check if the draft message contains too many attachments to send
         * 2. Computes the minimum size that this message could be compressed/downsampled/encoded
         * before sending and check if it meets the carrier max size for sending.
         * @see MessagePartData#getMinimumSizeInBytesForSending()
         */
        @DoesNotRunOnMainThread
        private boolean getIsMessageOverLimit() {
            Assert.isNotMainThread();
            if (mAttachmentsCopy.size() > getAttachmentLimit()) {
                return true;
            }

            // Aggregate the size from all the attachments.
            long totalSize = 0;
            for (final MessagePartData attachment : mAttachmentsCopy) {
                totalSize += attachment.getMinimumSizeInBytesForSending();
            }
            return totalSize > MmsConfig.get(mSelfSubId).getMaxMessageSize();
        }
    }

    public void onPendingAttachmentLoadFailed(PendingAttachmentData data) {
        mListeners.onDraftAttachmentLoadFailed();
    }
}
