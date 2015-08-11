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
package com.android.messaging.datamodel.media;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.DoesNotRunOnMainThread;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.UriUtil;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryCounter;
import com.android.vcard.VCardInterpreter;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.VCardSourceDetector;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardNotSupportedException;
import com.android.vcard.exception.VCardVersionException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Requests and parses VCard data. In Bugle, we need to display VCard details in the conversation
 * view such as avatar icon and name, which can be expensive if we parse VCard every time.
 * Therefore, we'd like to load the vcard once and cache it in our media cache using the
 * MediaResourceManager component. To load the VCard, we use framework's VCard support to
 * interpret the VCard content, which gives us information such as phone and email list, which
 * we'll put in VCardResource object to be cached.
 *
 * Some particular attention is needed for the avatar icon. If the VCard contains avatar icon,
 * it's in byte array form that can't easily be cached/persisted. Therefore, we persist the
 * image bytes to the scratch directory and generate a content Uri for it, so that ContactIconView
 * may use this Uri to display and cache the image if needed.
 */
public class VCardRequest implements MediaRequest<VCardResource> {
    private final Context mContext;
    private final VCardRequestDescriptor mDescriptor;
    private final List<VCardResourceEntry> mLoadedVCards;
    private VCardResource mLoadedResource;
    private static final int VCARD_LOADING_TIMEOUT_MILLIS = 10000;  // 10s
    private static final String DEFAULT_VCARD_TYPE = "default";

    VCardRequest(final Context context, final VCardRequestDescriptor descriptor) {
        mDescriptor = descriptor;
        mContext = context;
        mLoadedVCards = new ArrayList<VCardResourceEntry>();
    }

    @Override
    public String getKey() {
        return mDescriptor.vCardUri.toString();
    }

    @Override
    @DoesNotRunOnMainThread
    public VCardResource loadMediaBlocking(List<MediaRequest<VCardResource>> chainedTask)
            throws Exception {
        Assert.isNotMainThread();
        Assert.isTrue(mLoadedResource == null);
        Assert.equals(0, mLoadedVCards.size());

        // The VCard library doesn't support synchronously loading the media resource. Therefore,
        // We have to burn the thread waiting for the result to come back.
        final CountDownLatch signal = new CountDownLatch(1);
        if (!parseVCard(mDescriptor.vCardUri, signal)) {
            // Directly fail without actually going through the interpreter, return immediately.
            throw new VCardException("Invalid vcard");
        }

        signal.await(VCARD_LOADING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (mLoadedResource == null) {
            // Maybe null if failed or timeout.
            throw new VCardException("Failure or timeout loading vcard");
        }
        return mLoadedResource;
    }

    @Override
    public int getCacheId() {
        return BugleMediaCacheManager.VCARD_CACHE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public MediaCache<VCardResource> getMediaCache() {
        return (MediaCache<VCardResource>) MediaCacheManager.get().getOrCreateMediaCacheById(
                getCacheId());
    }

    @DoesNotRunOnMainThread
    private boolean parseVCard(final Uri targetUri, final CountDownLatch signal) {
        Assert.isNotMainThread();
        final VCardEntryCounter counter = new VCardEntryCounter();
        final VCardSourceDetector detector = new VCardSourceDetector();
        boolean result;
        try {
            // We don't know which type should be used to parse the Uri.
            // It is possible to misinterpret the vCard, but we expect the parser
            // lets VCardSourceDetector detect the type before the misinterpretation.
            result = readOneVCardFile(targetUri, VCardConfig.VCARD_TYPE_UNKNOWN,
                    detector, true, null);
        } catch (final VCardNestedException e) {
            try {
                final int estimatedVCardType = detector.getEstimatedType();
                // Assume that VCardSourceDetector was able to detect the source.
                // Try again with the detector.
                result = readOneVCardFile(targetUri, estimatedVCardType,
                        counter, false, null);
            } catch (final VCardNestedException e2) {
                result = false;
                LogUtil.e(LogUtil.BUGLE_TAG, "Must not reach here. " + e2);
            }
        }

        if (!result) {
            // Load failure.
            return false;
        }

        return doActuallyReadOneVCard(targetUri, true, detector, null, signal);
    }

    @DoesNotRunOnMainThread
    private boolean doActuallyReadOneVCard(final Uri uri, final boolean showEntryParseProgress,
            final VCardSourceDetector detector, final List<String> errorFileNameList,
            final CountDownLatch signal) {
        Assert.isNotMainThread();
        int vcardType = detector.getEstimatedType();
        if (vcardType == VCardConfig.VCARD_TYPE_UNKNOWN) {
            vcardType = VCardConfig.getVCardTypeFromString(DEFAULT_VCARD_TYPE);
        }
        final CustomVCardEntryConstructor builder =
                new CustomVCardEntryConstructor(vcardType, null);
        builder.addEntryHandler(new ContactVCardEntryHandler(signal));

        try {
            if (!readOneVCardFile(uri, vcardType, builder, false, null)) {
                return false;
            }
        } catch (final VCardNestedException e) {
            LogUtil.e(LogUtil.BUGLE_TAG, "Must not reach here. " + e);
            return false;
        }
        return true;
    }

    @DoesNotRunOnMainThread
    private boolean readOneVCardFile(final Uri uri, final int vcardType,
            final VCardInterpreter interpreter,
            final boolean throwNestedException, final List<String> errorFileNameList)
                    throws VCardNestedException {
        Assert.isNotMainThread();
        final ContentResolver resolver = mContext.getContentResolver();
        VCardParser vCardParser;
        InputStream is;
        try {
            is = resolver.openInputStream(uri);
            vCardParser = new VCardParser_V21(vcardType);
            vCardParser.addInterpreter(interpreter);

            try {
                vCardParser.parse(is);
            } catch (final VCardVersionException e1) {
                try {
                    is.close();
                } catch (final IOException e) {
                    // Do nothing.
                }
                if (interpreter instanceof CustomVCardEntryConstructor) {
                    // Let the object clean up internal temporal objects,
                    ((CustomVCardEntryConstructor) interpreter).clear();
                }

                is = resolver.openInputStream(uri);

                try {
                    vCardParser = new VCardParser_V30(vcardType);
                    vCardParser.addInterpreter(interpreter);
                    vCardParser.parse(is);
                } catch (final VCardVersionException e2) {
                    throw new VCardException("vCard with unspported version.");
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (final IOException e) {
                        // Do nothing.
                    }
                }
            }
        } catch (final IOException e) {
            LogUtil.e(LogUtil.BUGLE_TAG, "IOException was emitted: " + e.getMessage());

            if (errorFileNameList != null) {
                errorFileNameList.add(uri.toString());
            }
            return false;
        } catch (final VCardNotSupportedException e) {
            if ((e instanceof VCardNestedException) && throwNestedException) {
                throw (VCardNestedException) e;
            }
            if (errorFileNameList != null) {
                errorFileNameList.add(uri.toString());
            }
            return false;
        } catch (final VCardException e) {
            if (errorFileNameList != null) {
                errorFileNameList.add(uri.toString());
            }
            return false;
        }
        return true;
    }

    class ContactVCardEntryHandler implements CustomVCardEntryConstructor.EntryHandler {
        final CountDownLatch mSignal;

        public ContactVCardEntryHandler(final CountDownLatch signal) {
            mSignal = signal;
        }

        @Override
        public void onStart() {
        }

        @Override
        @DoesNotRunOnMainThread
        public void onEntryCreated(final CustomVCardEntry entry) {
            Assert.isNotMainThread();
            final String displayName = entry.getDisplayName();
            final List<VCardEntry.PhotoData> photos = entry.getPhotoList();
            Uri avatarUri = null;
            if (photos != null && photos.size() > 0) {
                // The photo data is in bytes form, so we need to persist it in our temp directory
                // so that ContactIconView can load it and display it later
                // (and cache it, of course).
                for (final VCardEntry.PhotoData photo : photos) {
                    final byte[] photoBytes = photo.getBytes();
                    if (photoBytes != null) {
                        final InputStream inputStream = new ByteArrayInputStream(photoBytes);
                        try {
                            avatarUri = UriUtil.persistContentToScratchSpace(inputStream);
                            if (avatarUri != null) {
                                // Just load the first avatar and be done. Want more? wait for V2.
                                break;
                            }
                        } finally {
                            try {
                                inputStream.close();
                            } catch (final IOException e) {
                                // Do nothing.
                            }
                        }
                    }
                }
            }

            // Fall back to generated avatar.
            if (avatarUri == null) {
                String destination = null;
                final List<VCardEntry.PhoneData> phones = entry.getPhoneList();
                if (phones != null && phones.size() > 0) {
                    destination = PhoneUtils.getDefault().getCanonicalBySystemLocale(
                            phones.get(0).getNumber());
                }

                if (destination == null) {
                    final List<VCardEntry.EmailData> emails = entry.getEmailList();
                    if (emails != null && emails.size() > 0) {
                        destination = emails.get(0).getAddress();
                    }
                }
                avatarUri = AvatarUriUtil.createAvatarUri(null, displayName, destination, null);
            }

            // Add the loaded vcard to the list.
            mLoadedVCards.add(new VCardResourceEntry(entry, avatarUri));
        }

        @Override
        public void onEnd() {
            // Finished loading all vCard entries, signal the loading thread to proceed with the
            // result.
            if (mLoadedVCards.size() > 0) {
                mLoadedResource = new VCardResource(getKey(), mLoadedVCards);
            }
            mSignal.countDown();
        }
    }

    @Override
    public int getRequestType() {
        return MediaRequest.REQUEST_LOAD_MEDIA;
    }

    @Override
    public MediaRequestDescriptor<VCardResource> getDescriptor() {
        return mDescriptor;
    }
}
