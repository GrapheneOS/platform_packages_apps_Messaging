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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.PartColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.action.UpdateMessagePartSizeAction;
import com.android.messaging.datamodel.media.ImageRequest;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.DoesNotRunOnMainThread;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.GifTranscoder;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UriUtil;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Represents a single message part. Messages consist of one or more parts which may contain
 * either text or media.
 */
public class MessagePartData implements Parcelable {
    public static final int UNSPECIFIED_SIZE = MessagingContentProvider.UNSPECIFIED_SIZE;
    public static final String[] ACCEPTABLE_IMAGE_TYPES =
            new String[] { ContentType.IMAGE_JPEG, ContentType.IMAGE_JPG, ContentType.IMAGE_PNG,
                ContentType.IMAGE_GIF };

    private static final String[] sProjection = {
        PartColumns._ID,
        PartColumns.MESSAGE_ID,
        PartColumns.TEXT,
        PartColumns.CONTENT_URI,
        PartColumns.CONTENT_TYPE,
        PartColumns.WIDTH,
        PartColumns.HEIGHT,
    };

    private static final int INDEX_ID = 0;
    private static final int INDEX_MESSAGE_ID = 1;
    private static final int INDEX_TEXT = 2;
    private static final int INDEX_CONTENT_URI = 3;
    private static final int INDEX_CONTENT_TYPE = 4;
    private static final int INDEX_WIDTH = 5;
    private static final int INDEX_HEIGHT = 6;
    // This isn't part of the projection
    private static final int INDEX_CONVERSATION_ID = 7;

    // SQL statement to insert a "complete" message part row (columns based on projection above).
    private static final String INSERT_MESSAGE_PART_SQL =
            "INSERT INTO " + DatabaseHelper.PARTS_TABLE + " ( "
                    + TextUtils.join(",", Arrays.copyOfRange(sProjection, 1, INDEX_CONVERSATION_ID))
                    + ", " + PartColumns.CONVERSATION_ID
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?)";

    // Used for stuff that's ignored or arbitrarily compressed.
    private static final long NO_MINIMUM_SIZE = 0;

    private String mPartId;
    private String mMessageId;
    private String mText;
    private Uri mContentUri;
    private String mContentType;
    private int mWidth;
    private int mHeight;
    // This kind of part can only be attached once and with no other attachment
    private boolean mSinglePartOnly;

    /** Transient data: true if destroy was already called */
    private boolean mDestroyed;

    /**
     * Create an "empty" message part
     */
    protected MessagePartData() {
        this(null, null, UNSPECIFIED_SIZE, UNSPECIFIED_SIZE);
    }

    /**
     * Create a populated text message part
     */
    protected MessagePartData(final String messageText) {
        this(null, messageText, ContentType.TEXT_PLAIN, null, UNSPECIFIED_SIZE, UNSPECIFIED_SIZE,
                false /*singlePartOnly*/);
    }

    /**
     * Create a populated attachment message part
     */
    protected MessagePartData(final String contentType, final Uri contentUri,
            final int width, final int height) {
        this(null, null, contentType, contentUri, width, height, false /*singlePartOnly*/);
    }

    /**
     * Create a populated attachment message part, with additional caption text
     */
    protected MessagePartData(final String messageText, final String contentType,
            final Uri contentUri, final int width, final int height) {
        this(null, messageText, contentType, contentUri, width, height, false /*singlePartOnly*/);
    }

    /**
     * Create a populated attachment message part, with additional caption text, single part only
     */
    protected MessagePartData(final String messageText, final String contentType,
            final Uri contentUri, final int width, final int height, final boolean singlePartOnly) {
        this(null, messageText, contentType, contentUri, width, height, singlePartOnly);
    }

    /**
     * Create a populated message part
     */
    private MessagePartData(final String messageId, final String messageText,
            final String contentType, final Uri contentUri, final int width, final int height,
            final boolean singlePartOnly) {
        mMessageId = messageId;
        mText = messageText;
        mContentType = contentType;
        mContentUri = contentUri;
        mWidth = width;
        mHeight = height;
        mSinglePartOnly = singlePartOnly;
    }

    /**
     * Create a "text" message part
     */
    public static MessagePartData createTextMessagePart(final String messageText) {
        return new MessagePartData(messageText);
    }

    /**
     * Create a "media" message part
     */
    public static MessagePartData createMediaMessagePart(final String contentType,
            final Uri contentUri, final int width, final int height) {
        return new MessagePartData(contentType, contentUri, width, height);
    }

    /**
     * Create a "media" message part with caption
     */
    public static MessagePartData createMediaMessagePart(final String caption,
            final String contentType, final Uri contentUri, final int width, final int height) {
        return new MessagePartData(null, caption, contentType, contentUri, width, height,
                false /*singlePartOnly*/
        );
    }

    /**
     * Create an empty "text" message part
     */
    public static MessagePartData createEmptyMessagePart() {
        return new MessagePartData("");
    }

    /**
     * Creates a new message part reading from the cursor
     */
    public static MessagePartData createFromCursor(final Cursor cursor) {
        final MessagePartData part = new MessagePartData();
        part.bind(cursor);
        return part;
    }

    public static String[] getProjection() {
        return sProjection;
    }

    /**
     * Updates the part id.
     * Can be used to reset the partId just prior to persisting (which will assign a new partId)
     *  or can be called on a part that does not yet have a valid part id to set it.
     */
    public void updatePartId(final String partId) {
        Assert.isTrue(TextUtils.isEmpty(partId) || TextUtils.isEmpty(mPartId));
        mPartId = partId;
    }

    /**
     * Updates the messageId for the part.
     * Can be used to reset the messageId prior to persisting (which will assign a new messageId)
     *  or can be called on a part that does not yet have a valid messageId to set it.
     */
    public void updateMessageId(final String messageId) {
        Assert.isTrue(TextUtils.isEmpty(messageId) || TextUtils.isEmpty(mMessageId));
        mMessageId = messageId;
    }

    protected static String getMessageId(final Cursor cursor) {
        return cursor.getString(INDEX_MESSAGE_ID);
    }

    protected void bind(final Cursor cursor) {
        mPartId = cursor.getString(INDEX_ID);
        mMessageId = cursor.getString(INDEX_MESSAGE_ID);
        mText = cursor.getString(INDEX_TEXT);
        mContentUri = UriUtil.uriFromString(cursor.getString(INDEX_CONTENT_URI));
        mContentType = cursor.getString(INDEX_CONTENT_TYPE);
        mWidth = cursor.getInt(INDEX_WIDTH);
        mHeight = cursor.getInt(INDEX_HEIGHT);
    }

    public final void populate(final ContentValues values) {
        // Must have a valid messageId on a part
        Assert.isTrue(!TextUtils.isEmpty(mMessageId));
        values.put(PartColumns.MESSAGE_ID, mMessageId);
        values.put(PartColumns.TEXT, mText);
        values.put(PartColumns.CONTENT_URI, UriUtil.stringFromUri(mContentUri));
        values.put(PartColumns.CONTENT_TYPE, mContentType);
        if (mWidth != UNSPECIFIED_SIZE) {
            values.put(PartColumns.WIDTH, mWidth);
        }
        if (mHeight != UNSPECIFIED_SIZE) {
            values.put(PartColumns.HEIGHT, mHeight);
        }
    }

    /**
     * Note this is not thread safe so callers need to make sure they own the wrapper + statements
     * while they call this and use the returned value.
     */
    public SQLiteStatement getInsertStatement(final DatabaseWrapper db,
                                              final String conversationId) {
        final SQLiteStatement insert = db.getStatementInTransaction(
                DatabaseWrapper.INDEX_INSERT_MESSAGE_PART, INSERT_MESSAGE_PART_SQL);
        insert.clearBindings();
        insert.bindString(INDEX_MESSAGE_ID, mMessageId);
        if (mText != null) {
            insert.bindString(INDEX_TEXT, mText);
        }
        if (mContentUri != null) {
            insert.bindString(INDEX_CONTENT_URI, mContentUri.toString());
        }
        if (mContentType != null) {
            insert.bindString(INDEX_CONTENT_TYPE, mContentType);
        }
        insert.bindLong(INDEX_WIDTH, mWidth);
        insert.bindLong(INDEX_HEIGHT, mHeight);
        insert.bindString(INDEX_CONVERSATION_ID, conversationId);
        return insert;
    }

    public final String getPartId() {
        return mPartId;
    }

    public final String getMessageId() {
        return mMessageId;
    }

    public final String getText() {
        return mText;
    }

    public final Uri getContentUri() {
        return mContentUri;
    }

    public boolean isAttachment() {
        return mContentUri != null;
    }

    public boolean isText() {
        return ContentType.isTextType(mContentType);
    }

    public boolean isImage() {
        return ContentType.isImageType(mContentType);
    }

    public boolean isMedia() {
        return ContentType.isMediaType(mContentType);
    }

    public boolean isVCard() {
        return ContentType.isVCardType(mContentType);
    }

    public boolean isAudio() {
        return ContentType.isAudioType(mContentType);
    }

    public boolean isVideo() {
        return ContentType.isVideoType(mContentType);
    }

    public final String getContentType() {
        return mContentType;
    }

    public final int getWidth() {
        return mWidth;
    }

    public final int getHeight() {
        return mHeight;
    }

    /**
    *
    * @return true if this part can only exist by itself, with no other attachments
    */
    public boolean getSinglePartOnly() {
        return mSinglePartOnly;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    protected MessagePartData(final Parcel in) {
        mMessageId = in.readString();
        mText = in.readString();
        mContentUri = UriUtil.uriFromString(in.readString());
        mContentType = in.readString();
        mWidth = in.readInt();
        mHeight = in.readInt();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        Assert.isTrue(!mDestroyed);
        dest.writeString(mMessageId);
        dest.writeString(mText);
        dest.writeString(UriUtil.stringFromUri(mContentUri));
        dest.writeString(mContentType);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof MessagePartData)) {
          return false;
        }

        MessagePartData lhs = (MessagePartData) o;
        return mWidth == lhs.mWidth && mHeight == lhs.mHeight &&
                TextUtils.equals(mMessageId, lhs.mMessageId) &&
                TextUtils.equals(mText, lhs.mText) &&
                TextUtils.equals(mContentType, lhs.mContentType) &&
                (mContentUri == null ? lhs.mContentUri == null
                                     : mContentUri.equals(lhs.mContentUri));
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + mWidth;
        result = 31 * result + mHeight;
        result = 31 * result + (mMessageId == null ? 0 : mMessageId.hashCode());
        result = 31 * result + (mText == null ? 0 : mText.hashCode());
        result = 31 * result + (mContentType == null ? 0 : mContentType.hashCode());
        result = 31 * result + (mContentUri == null ? 0 : mContentUri.hashCode());
        return result;
      }

    public static final Parcelable.Creator<MessagePartData> CREATOR
            = new Parcelable.Creator<MessagePartData>() {
        @Override
        public MessagePartData createFromParcel(final Parcel in) {
            return new MessagePartData(in);
        }

        @Override
        public MessagePartData[] newArray(final int size) {
            return new MessagePartData[size];
        }
    };

    protected Uri shouldDestroy() {
        // We should never double-destroy.
        Assert.isTrue(!mDestroyed);
        mDestroyed = true;
        Uri contentUri = mContentUri;
        mContentUri = null;
        mContentType = null;
        // Only destroy the image if it's staged in our scratch space.
        if (!MediaScratchFileProvider.isMediaScratchSpaceUri(contentUri)) {
            contentUri = null;
        }
        return contentUri;
    }

    /**
     * If application owns content associated with this part delete it (on background thread)
     */
    public void destroyAsync() {
        final Uri contentUri = shouldDestroy();
        if (contentUri != null) {
            SafeAsyncTask.executeOnThreadPool(new Runnable() {
                @Override
                public void run() {
                    Factory.get().getApplicationContext().getContentResolver().delete(
                            contentUri, null, null);
                }
            });
        }
    }

    /**
     * If application owns content associated with this part delete it
     */
    public void destroySync() {
        final Uri contentUri = shouldDestroy();
        if (contentUri != null) {
            Factory.get().getApplicationContext().getContentResolver().delete(
                    contentUri, null, null);
        }
    }

    /**
     * If this is an image part, decode the image header and potentially save the size to the db.
     */
    public void decodeAndSaveSizeIfImage(final boolean saveToStorage) {
        if (isImage()) {
            final Rect imageSize = ImageUtils.decodeImageBounds(
                    Factory.get().getApplicationContext(), mContentUri);
            if (imageSize.width() != ImageRequest.UNSPECIFIED_SIZE &&
                    imageSize.height() != ImageRequest.UNSPECIFIED_SIZE) {
                mWidth = imageSize.width();
                mHeight = imageSize.height();
                if (saveToStorage) {
                    UpdateMessagePartSizeAction.updateSize(mPartId, mWidth, mHeight);
                }
            }
        }
    }

    /**
     * Computes the minimum size that this MessagePartData could be compressed/downsampled/encoded
     * before sending to meet the maximum message size imposed by the carriers. This is used to
     * determine right before sending a message whether a message could possibly be sent. If not
     * then the user is given a chance to unselect some/all of the attachments.
     *
     * TODO: computing the minimum size could be expensive. Should we cache the
     * computed value in db to be retrieved later?
     *
     * @return the carrier-independent minimum size, in bytes.
     */
    @DoesNotRunOnMainThread
    public long getMinimumSizeInBytesForSending() {
        Assert.isNotMainThread();
        if (!isAttachment()) {
            // No limit is imposed on non-attachment part (i.e. plain text), so treat it as zero.
            return NO_MINIMUM_SIZE;
        } else if (isImage()) {
            // GIFs are resized by the native transcoder (exposed by GifTranscoder).
            if (ImageUtils.isGif(mContentType, mContentUri)) {
                final long originalImageSize = UriUtil.getContentSize(mContentUri);
                // Wish we could save the size here, but we don't have a part id yet
                decodeAndSaveSizeIfImage(false /* saveToStorage */);
                return GifTranscoder.canBeTranscoded(mWidth, mHeight) ?
                        GifTranscoder.estimateFileSizeAfterTranscode(originalImageSize)
                        : originalImageSize;
            }
            // Other images should be arbitrarily resized by ImageResizer before sending.
            return MmsUtils.MIN_IMAGE_BYTE_SIZE;
        } else if (isAudio()) {
            // Audios are already recorded with the lowest sampling settings (AMR_NB), so just
            // return the file size as the minimum size.
            return UriUtil.getContentSize(mContentUri);
        } else if (isVideo()) {
            final int mediaDurationMs = UriUtil.getMediaDurationMs(mContentUri);
            return MmsUtils.MIN_VIDEO_BYTES_PER_SECOND * mediaDurationMs
                    / TimeUnit.SECONDS.toMillis(1);
        } else if (isVCard()) {
            // We can't compress vCards.
            return UriUtil.getContentSize(mContentUri);
        } else {
            // This is some unknown media type that we don't know how to handle. Log an error
            // and try sending it anyway.
            LogUtil.e(LogUtil.BUGLE_DATAMODEL_TAG, "Unknown attachment type " + getContentType());
            return NO_MINIMUM_SIZE;
        }
    }

    @Override
    public String toString() {
        if (isText()) {
            return LogUtil.sanitizePII(getText());
        } else {
            return getContentType() + " (" + getContentUri() + ")";
        }
    }

    /**
     *
     * @return true if this part can only exist by itself, with no other attachments
     */
    public boolean isSinglePartOnly() {
        return mSinglePartOnly;
    }

    public void setSinglePartOnly(final boolean isSinglePartOnly) {
        mSinglePartOnly = isSinglePartOnly;
    }
}
