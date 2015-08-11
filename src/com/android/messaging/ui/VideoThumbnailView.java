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

import android.content.Context;
import android.content.res.TypedArray;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.VideoView;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.media.ImageRequest;
import com.android.messaging.datamodel.media.MessagePartVideoThumbnailRequestDescriptor;
import com.android.messaging.datamodel.media.VideoThumbnailRequest;
import com.android.messaging.util.Assert;

/**
 * View that encapsulates a video preview (either as a thumbnail image, or video player), and the
 * a play button to overlay it.  Ensures that the video preview maintains the aspect ratio of the
 * original video while trying to respect minimum width/height and constraining to the available
 * bounds
 */
public class VideoThumbnailView extends FrameLayout {
    /**
     * When in this mode the VideoThumbnailView is a lightweight AsyncImageView with an ImageButton
     * to play the video.  Clicking play will launch a full screen player
     */
    private static final int MODE_IMAGE_THUMBNAIL = 0;

    /**
     * When in this mode the VideoThumbnailVideo will include a VideoView, and the play button will
     * play the video inline.  When in this mode, the loop and playOnLoad attributes can be applied
     * to auto-play or loop the video.
     */
    private static final int MODE_PLAYABLE_VIDEO = 1;

    private final int mMode;
    private final boolean mPlayOnLoad;
    private final boolean mAllowCrop;
    private final VideoView mVideoView;
    private final ImageButton mPlayButton;
    private final AsyncImageView mThumbnailImage;
    private int mVideoWidth;
    private int mVideoHeight;
    private Uri mVideoSource;
    private boolean mAnimating;
    private boolean mVideoLoaded;

    public VideoThumbnailView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final TypedArray typedAttributes =
                context.obtainStyledAttributes(attrs, R.styleable.VideoThumbnailView);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.video_thumbnail_view, this, true);

        mPlayOnLoad = typedAttributes.getBoolean(R.styleable.VideoThumbnailView_playOnLoad, false);
        final boolean loop =
                typedAttributes.getBoolean(R.styleable.VideoThumbnailView_loop, false);
        mMode = typedAttributes.getInt(R.styleable.VideoThumbnailView_mode, MODE_IMAGE_THUMBNAIL);
        mAllowCrop = typedAttributes.getBoolean(R.styleable.VideoThumbnailView_allowCrop, false);

        mVideoWidth = ImageRequest.UNSPECIFIED_SIZE;
        mVideoHeight = ImageRequest.UNSPECIFIED_SIZE;

        if (mMode == MODE_PLAYABLE_VIDEO) {
            mVideoView = new VideoView(context);
            // Video view tries to request focus on start which pulls focus from the user's intended
            // focus when we add this control.  Remove focusability to prevent this.  The play
            // button can still be focused
            mVideoView.setFocusable(false);
            mVideoView.setFocusableInTouchMode(false);
            mVideoView.clearFocus();
            addView(mVideoView, 0, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(final MediaPlayer mediaPlayer) {
                    mVideoLoaded = true;
                    mVideoWidth = mediaPlayer.getVideoWidth();
                    mVideoHeight = mediaPlayer.getVideoHeight();
                    mediaPlayer.setLooping(loop);
                    trySwitchToVideo();
                }
            });
            mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(final MediaPlayer mediaPlayer) {
                    mPlayButton.setVisibility(View.VISIBLE);
                }
            });
            mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(final MediaPlayer mediaPlayer, final int i, final int i2) {
                    return true;
                }
            });
        } else {
            mVideoView = null;
        }

        mPlayButton = (ImageButton) findViewById(R.id.video_thumbnail_play_button);
        if (loop) {
            mPlayButton.setVisibility(View.GONE);
        } else {
            mPlayButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View view) {
                    if (mVideoSource == null) {
                        return;
                    }

                    if (mMode == MODE_PLAYABLE_VIDEO) {
                        mVideoView.seekTo(0);
                        start();
                    } else {
                        UIIntents.get().launchFullScreenVideoViewer(getContext(), mVideoSource);
                    }
                }
            });
            mPlayButton.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(final View view) {
                    // Button prevents long click from propagating up, do it manually
                    VideoThumbnailView.this.performLongClick();
                    return true;
                }
            });
        }

        mThumbnailImage = (AsyncImageView) findViewById(R.id.video_thumbnail_image);
        if (mAllowCrop) {
            mThumbnailImage.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            mThumbnailImage.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            mThumbnailImage.setScaleType(ScaleType.CENTER_CROP);
        } else {
            // This is the default setting in the layout, so No-op.
        }
        final int maxHeight = typedAttributes.getDimensionPixelSize(
                R.styleable.VideoThumbnailView_android_maxHeight, ImageRequest.UNSPECIFIED_SIZE);
        if (maxHeight != ImageRequest.UNSPECIFIED_SIZE) {
            mThumbnailImage.setMaxHeight(maxHeight);
            mThumbnailImage.setAdjustViewBounds(true);
        }

        typedAttributes.recycle();
    }

    @Override
    protected void onAnimationStart() {
        super.onAnimationStart();
        mAnimating = true;
    }

    @Override
    protected void onAnimationEnd() {
        super.onAnimationEnd();
        mAnimating = false;
        trySwitchToVideo();
    }

    private void trySwitchToVideo() {
        if (mAnimating) {
            // Don't start video or hide image until after animation completes
            return;
        }

        if (!mVideoLoaded) {
            // Video hasn't loaded, nothing more to do
            return;
        }

        if (mPlayOnLoad) {
            start();
        } else {
            mVideoView.seekTo(0);
        }
    }

    private boolean hasVideoSize() {
        return mVideoWidth != ImageRequest.UNSPECIFIED_SIZE &&
                mVideoHeight != ImageRequest.UNSPECIFIED_SIZE;
    }

    public void start() {
        Assert.equals(MODE_PLAYABLE_VIDEO, mMode);
        mPlayButton.setVisibility(View.GONE);
        mThumbnailImage.setVisibility(View.GONE);
        mVideoView.start();
    }

    // TODO: The check could be added to MessagePartData itself so that all users of MessagePartData
    // get the right behavior, instead of requiring all the users to do similar checks.
    private static boolean shouldUseGenericVideoIcon(final boolean incomingMessage) {
        return incomingMessage && !VideoThumbnailRequest.shouldShowIncomingVideoThumbnails();
    }

    public void setSource(final MessagePartData part, final boolean incomingMessage) {
        if (part == null) {
            clearSource();
        } else {
            mVideoSource = part.getContentUri();
            if (shouldUseGenericVideoIcon(incomingMessage)) {
                mThumbnailImage.setImageResource(R.drawable.generic_video_icon);
                mVideoWidth = ImageRequest.UNSPECIFIED_SIZE;
                mVideoHeight = ImageRequest.UNSPECIFIED_SIZE;
            } else {
                mThumbnailImage.setImageResourceId(
                        new MessagePartVideoThumbnailRequestDescriptor(part));
                if (mVideoView != null) {
                    mVideoView.setVideoURI(mVideoSource);
                }
                mVideoWidth = part.getWidth();
                mVideoHeight = part.getHeight();
            }
        }
    }

    public void setSource(final Uri videoSource, final boolean incomingMessage) {
        if (videoSource == null) {
            clearSource();
        } else {
            mVideoSource = videoSource;
            if (shouldUseGenericVideoIcon(incomingMessage)) {
                mThumbnailImage.setImageResource(R.drawable.generic_video_icon);
                mVideoWidth = ImageRequest.UNSPECIFIED_SIZE;
                mVideoHeight = ImageRequest.UNSPECIFIED_SIZE;
            } else {
                mThumbnailImage.setImageResourceId(
                        new MessagePartVideoThumbnailRequestDescriptor(videoSource));
                if (mVideoView != null) {
                    mVideoView.setVideoURI(videoSource);
                }
            }
        }
    }

    private void clearSource() {
        mVideoSource = null;
        mThumbnailImage.setImageResourceId(null);
        mVideoWidth = ImageRequest.UNSPECIFIED_SIZE;
        mVideoHeight = ImageRequest.UNSPECIFIED_SIZE;
        if (mVideoView != null) {
            mVideoView.setVideoURI(null);
        }
    }

    @Override
    public void setMinimumWidth(final int minWidth) {
        super.setMinimumWidth(minWidth);
        if (mVideoView != null) {
            mVideoView.setMinimumWidth(minWidth);
        }
    }

    @Override
    public void setMinimumHeight(final int minHeight) {
        super.setMinimumHeight(minHeight);
        if (mVideoView != null) {
            mVideoView.setMinimumHeight(minHeight);
        }
    }

    public void setColorFilter(int color) {
        mThumbnailImage.setColorFilter(color);
        mPlayButton.setColorFilter(color);
    }

    public void clearColorFilter() {
        mThumbnailImage.clearColorFilter();
        mPlayButton.clearColorFilter();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mAllowCrop) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int desiredWidth = 1;
        int desiredHeight = 1;
        if (mVideoView != null) {
            mVideoView.measure(widthMeasureSpec, heightMeasureSpec);
        }
        mThumbnailImage.measure(widthMeasureSpec, heightMeasureSpec);
        if (hasVideoSize()) {
            desiredWidth = mVideoWidth;
            desiredHeight = mVideoHeight;
        } else {
            desiredWidth = mThumbnailImage.getMeasuredWidth();
            desiredHeight = mThumbnailImage.getMeasuredHeight();
        }

        final int minimumWidth = getMinimumWidth();
        final int minimumHeight = getMinimumHeight();

        // Constrain the scale to fit within the supplied size
        final float maxScale = Math.max(
                MeasureSpec.getSize(widthMeasureSpec) / (float) desiredWidth,
                MeasureSpec.getSize(heightMeasureSpec) / (float) desiredHeight);

        // Scale up to reach minimum width/height
        final float widthScale = Math.max(1, minimumWidth / (float) desiredWidth);
        final float heightScale = Math.max(1, minimumHeight / (float) desiredHeight);
        final float scale = Math.min(maxScale, Math.max(widthScale, heightScale));
        desiredWidth = (int) (desiredWidth * scale);
        desiredHeight = (int) (desiredHeight * scale);

        setMeasuredDimension(desiredWidth, desiredHeight);
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right,
            final int bottom) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.layout(0, 0, right - left, bottom - top);
        }
    }
}
