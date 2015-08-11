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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.MediaPickerMessagePartData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.datamodel.media.ImageRequestDescriptor;
import com.android.messaging.ui.AsyncImageView.AsyncImageViewDelayLoader;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.Assert;
import com.android.messaging.util.UiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Holds and displays multiple attachments in a 4x2 grid. Each preview image "tile" can take
 * one of three sizes - small (1x1), wide (2x1) and large (2x2). We have a number of predefined
 * layout settings designed for holding 2, 3, 4+ attachments (these layout settings are
 * tweakable by design request to allow for max flexibility). For a visual example, consider the
 * following attachment layout:
 *
 * +---------------+----------------+
 * |               |                |
 * |               |       B        |
 * |               |                |
 * |       A       |-------+--------|
 * |               |       |        |
 * |               |   C   |    D   |
 * |               |       |        |
 * +---------------+-------+--------+
 *
 * In the above example, the layout consists of four tiles, A-D. A is a large tile, B is a
 * wide tile and C & D are both small tiles. A starts at (0,0) and ends at (1,1), B starts at
 * (2,0) and ends at (3,0), and so on. In our layout class we'd have these tiles in the order
 * of A-D, so that we make sure the last tile is always the one where we can put the overflow
 * indicator (e.g. "+2").
 */
public class MultiAttachmentLayout extends FrameLayout {

    public interface OnAttachmentClickListener {
        boolean onAttachmentClick(MessagePartData attachment, Rect viewBoundsOnScreen,
                boolean longPress);
    }

    private static final int GRID_WIDTH = 4;    // in # of cells
    private static final int GRID_HEIGHT = 2;   // in # of cells

    /**
     * Represents a preview image tile in the layout
     */
    private static class Tile {
        public final int startX;
        public final int startY;
        public final int endX;
        public final int endY;

        private Tile(final int startX, final int startY, final int endX, final int endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        public int getWidthMeasureSpec(final int cellWidth, final int padding) {
            return MeasureSpec.makeMeasureSpec((endX - startX + 1) * cellWidth - padding * 2,
                    MeasureSpec.EXACTLY);
        }

        public int getHeightMeasureSpec(final int cellHeight, final int padding) {
            return MeasureSpec.makeMeasureSpec((endY - startY + 1) * cellHeight - padding * 2,
                    MeasureSpec.EXACTLY);
        }

        public static Tile large(final int startX, final int startY) {
            return new Tile(startX, startY, startX + 1, startY + 1);
        }

        public static Tile wide(final int startX, final int startY) {
            return new Tile(startX, startY, startX + 1, startY);
        }

        public static Tile small(final int startX, final int startY) {
            return new Tile(startX, startY, startX, startY);
        }
    }

    /**
     * A layout simply contains a list of tiles, in the order of top-left -> bottom-right.
     */
    private static class Layout {
        public final List<Tile> tiles;
        public Layout(final Tile[] tilesArray) {
            tiles = Arrays.asList(tilesArray);
        }
    }

    /**
     * List of predefined layout configurations w.r.t no. of attachments.
     */
    private static final Layout[] ATTACHMENT_LAYOUTS_BY_COUNT = {
        null,   // Doesn't support zero attachments.
        null,   // Doesn't support one attachment. Single attachment preview is used instead.
        new Layout(new Tile[] { Tile.large(0, 0), Tile.large(2, 0) }),                  // 2 items
        new Layout(new Tile[] { Tile.large(0, 0), Tile.wide(2, 0), Tile.wide(2, 1) }),  // 3 items
        new Layout(new Tile[] { Tile.large(0, 0), Tile.wide(2, 0), Tile.small(2, 1),    // 4+ items
                Tile.small(3, 1) }),
    };

    /**
     * List of predefined RTL layout configurations w.r.t no. of attachments.
     */
    private static final Layout[] ATTACHMENT_RTL_LAYOUTS_BY_COUNT = {
        null,   // Doesn't support zero attachments.
        null,   // Doesn't support one attachment. Single attachment preview is used instead.
        new Layout(new Tile[] { Tile.large(2, 0), Tile.large(0, 0)}),                   // 2 items
        new Layout(new Tile[] { Tile.large(2, 0), Tile.wide(0, 0), Tile.wide(0, 1) }),  // 3 items
        new Layout(new Tile[] { Tile.large(2, 0), Tile.wide(0, 0), Tile.small(1, 1),    // 4+ items
                Tile.small(0, 1) }),
    };

    private Layout mCurrentLayout;
    private ArrayList<ViewWrapper> mPreviewViews;
    private int mPlusNumber;
    private TextView mPlusTextView;
    private OnAttachmentClickListener mAttachmentClickListener;
    private AsyncImageViewDelayLoader mImageViewDelayLoader;

    public MultiAttachmentLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mPreviewViews = new ArrayList<ViewWrapper>();
    }

    public void bindAttachments(final Iterable<MessagePartData> attachments,
            final Rect transitionRect, final int count) {
        final ArrayList<ViewWrapper> previousViews = mPreviewViews;
        mPreviewViews = new ArrayList<ViewWrapper>();
        removeView(mPlusTextView);
        mPlusTextView = null;

        determineLayout(attachments, count);
        buildViews(attachments, previousViews, transitionRect);

        // Remove all previous views that couldn't be recycled.
        for (final ViewWrapper viewWrapper : previousViews) {
            removeView(viewWrapper.view);
        }
        requestLayout();
    }

    public OnAttachmentClickListener getOnAttachmentClickListener() {
        return mAttachmentClickListener;
    }

    public void setOnAttachmentClickListener(final OnAttachmentClickListener listener) {
        mAttachmentClickListener = listener;
    }

    public void setImageViewDelayLoader(final AsyncImageViewDelayLoader delayLoader) {
        mImageViewDelayLoader = delayLoader;
    }

    public void setColorFilter(int color) {
        for (ViewWrapper viewWrapper : mPreviewViews) {
            if (viewWrapper.view instanceof AsyncImageView) {
                ((AsyncImageView) viewWrapper.view).setColorFilter(color);
            }
        }
    }

    public void clearColorFilter() {
        for (ViewWrapper viewWrapper : mPreviewViews) {
            if (viewWrapper.view instanceof AsyncImageView) {
                ((AsyncImageView) viewWrapper.view).clearColorFilter();
            }
        }
    }

    private void determineLayout(final Iterable<MessagePartData> attachments, final int count) {
        Assert.isTrue(attachments != null);
        final boolean isRtl = AccessibilityUtil.isLayoutRtl(getRootView());
        if (isRtl) {
            mCurrentLayout = ATTACHMENT_RTL_LAYOUTS_BY_COUNT[Math.min(count,
                    ATTACHMENT_RTL_LAYOUTS_BY_COUNT.length - 1)];
        } else {
            mCurrentLayout = ATTACHMENT_LAYOUTS_BY_COUNT[Math.min(count,
                    ATTACHMENT_LAYOUTS_BY_COUNT.length - 1)];
        }

        // We must have a valid layout for the current configuration.
        Assert.notNull(mCurrentLayout);

        mPlusNumber = count - mCurrentLayout.tiles.size();
        Assert.isTrue(mPlusNumber >= 0);
    }

    private void buildViews(final Iterable<MessagePartData> attachments,
            final ArrayList<ViewWrapper> previousViews, final Rect transitionRect) {
        final LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        final int count = mCurrentLayout.tiles.size();
        int i = 0;
        final Iterator<MessagePartData> iterator = attachments.iterator();
        while (iterator.hasNext() && i < count) {
            final MessagePartData attachment = iterator.next();
            ViewWrapper attachmentWrapper = null;
            // Try to recycle a previous view first
            for (int j = 0; j < previousViews.size(); j++) {
                final ViewWrapper previousView = previousViews.get(j);
                if (previousView.attachment.equals(attachment) &&
                        !(previousView.attachment instanceof PendingAttachmentData)) {
                    attachmentWrapper = previousView;
                    previousViews.remove(j);
                    break;
                }
            }

            if (attachmentWrapper == null) {
                final View view = AttachmentPreviewFactory.createAttachmentPreview(layoutInflater,
                        attachment, this, AttachmentPreviewFactory.TYPE_MULTIPLE,
                        false /* startImageRequest */, mAttachmentClickListener);

                if (view == null) {
                    // createAttachmentPreview can return null if something goes wrong (e.g.
                    // attachment has unsupported contentType)
                    continue;
                }
                if (view instanceof AsyncImageView && mImageViewDelayLoader != null) {
                    AsyncImageView asyncImageView = (AsyncImageView) view;
                    asyncImageView.setDelayLoader(mImageViewDelayLoader);
                }
                addView(view);
                attachmentWrapper = new ViewWrapper(view, attachment);
                // Help animate from single to multi by copying over the prev location
                if (count == 2 && i == 1 && transitionRect != null) {
                    attachmentWrapper.prevLeft = transitionRect.left;
                    attachmentWrapper.prevTop = transitionRect.top;
                    attachmentWrapper.prevWidth = transitionRect.width();
                    attachmentWrapper.prevHeight = transitionRect.height();
                }
            }
            i++;
            Assert.notNull(attachmentWrapper);
            mPreviewViews.add(attachmentWrapper);

            // The first view will animate in using PopupTransitionAnimation, but the remaining
            // views will slide from their previous position to their new position within the
            // layout
            if (i == 0) {
                AttachmentPreview.tryAnimateViewIn(attachment, attachmentWrapper.view);
            }
            attachmentWrapper.needsSlideAnimation = i > 0;
        }

        // Build the plus text view (e.g. "+2") for when there are more attachments than what
        // this layout can display.
        if (mPlusNumber > 0) {
            mPlusTextView = (TextView) layoutInflater.inflate(R.layout.attachment_more_text_view,
                    null /* parent */);
            mPlusTextView.setText(getResources().getString(R.string.attachment_more_items,
                    mPlusNumber));
            addView(mPlusTextView);
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int maxWidth = getResources().getDimensionPixelSize(
                R.dimen.multiple_attachment_preview_width);
        final int maxHeight = getResources().getDimensionPixelSize(
                R.dimen.multiple_attachment_preview_height);
        final int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), maxWidth);
        final int height = maxHeight;
        final int cellWidth = width / GRID_WIDTH;
        final int cellHeight = height / GRID_HEIGHT;
        final int count = mPreviewViews.size();
        final int padding = getResources().getDimensionPixelOffset(
                R.dimen.multiple_attachment_preview_padding);
        for (int i = 0; i < count; i++) {
            final View view =  mPreviewViews.get(i).view;
            final Tile imageTile = mCurrentLayout.tiles.get(i);
            view.measure(imageTile.getWidthMeasureSpec(cellWidth, padding),
                    imageTile.getHeightMeasureSpec(cellHeight, padding));

            // Now that we know the size, we can request an appropriately-sized image.
            if (view instanceof AsyncImageView) {
                final ImageRequestDescriptor imageRequest =
                        AttachmentPreviewFactory.getImageRequestDescriptorForAttachment(
                                mPreviewViews.get(i).attachment,
                                view.getMeasuredWidth(),
                                view.getMeasuredHeight());
                ((AsyncImageView) view).setImageResourceId(imageRequest);
            }

            if (i == count - 1 && mPlusTextView != null) {
                // The plus text view always covers the last attachment.
                mPlusTextView.measure(imageTile.getWidthMeasureSpec(cellWidth, padding),
                        imageTile.getHeightMeasureSpec(cellHeight, padding));
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right,
            final int bottom) {
        final int cellWidth = getMeasuredWidth() / GRID_WIDTH;
        final int cellHeight = getMeasuredHeight() / GRID_HEIGHT;
        final int padding = getResources().getDimensionPixelOffset(
                R.dimen.multiple_attachment_preview_padding);
        final int count = mPreviewViews.size();
        for (int i = 0; i < count; i++) {
            final ViewWrapper viewWrapper =  mPreviewViews.get(i);
            final View view = viewWrapper.view;
            final Tile imageTile = mCurrentLayout.tiles.get(i);
            final int tileLeft = imageTile.startX * cellWidth;
            final int tileTop = imageTile.startY * cellHeight;
            view.layout(tileLeft + padding, tileTop + padding,
                    tileLeft + view.getMeasuredWidth(),
                    tileTop + view.getMeasuredHeight());
            if (viewWrapper.needsSlideAnimation) {
                trySlideAttachmentView(viewWrapper);
                viewWrapper.needsSlideAnimation = false;
            } else {
                viewWrapper.prevLeft = view.getLeft();
                viewWrapper.prevTop = view.getTop();
                viewWrapper.prevWidth = view.getWidth();
                viewWrapper.prevHeight = view.getHeight();
            }

            if (i == count - 1 && mPlusTextView != null) {
                // The plus text view always covers the last attachment.
                mPlusTextView.layout(tileLeft + padding, tileTop + padding,
                        tileLeft + mPlusTextView.getMeasuredWidth(),
                        tileTop + mPlusTextView.getMeasuredHeight());
            }
        }
    }

    private void trySlideAttachmentView(final ViewWrapper viewWrapper) {
        if (!(viewWrapper.attachment instanceof MediaPickerMessagePartData)) {
            return;
        }
        final View view = viewWrapper.view;


        final int xOffset = viewWrapper.prevLeft - view.getLeft();
        final int yOffset = viewWrapper.prevTop - view.getTop();
        final float scaleX = viewWrapper.prevWidth / (float) view.getWidth();
        final float scaleY = viewWrapper.prevHeight / (float) view.getHeight();

        if (xOffset == 0 && yOffset == 0 && scaleX == 1 && scaleY == 1) {
            // Layout hasn't changed
            return;
        }

        final AnimationSet animationSet = new AnimationSet(
                true /* shareInterpolator */);
        animationSet.addAnimation(new TranslateAnimation(xOffset, 0, yOffset, 0));
        animationSet.addAnimation(new ScaleAnimation(scaleX, 1, scaleY, 1));
        animationSet.setDuration(
                UiUtils.MEDIAPICKER_TRANSITION_DURATION);
        animationSet.setInterpolator(UiUtils.DEFAULT_INTERPOLATOR);
        view.startAnimation(animationSet);
        view.invalidate();
        viewWrapper.prevLeft = view.getLeft();
        viewWrapper.prevTop = view.getTop();
        viewWrapper.prevWidth = view.getWidth();
        viewWrapper.prevHeight = view.getHeight();
    }

    public View findViewForAttachment(final MessagePartData attachment) {
        for (ViewWrapper wrapper : mPreviewViews) {
            if (wrapper.attachment.equals(attachment) &&
                    !(wrapper.attachment instanceof PendingAttachmentData)) {
                return wrapper.view;
            }
        }
        return null;
    }

    private static class ViewWrapper {
        final View view;
        final MessagePartData attachment;
        boolean needsSlideAnimation;
        int prevLeft;
        int prevTop;
        int prevWidth;
        int prevHeight;

        ViewWrapper(final View view, final MessagePartData attachment) {
            this.view = view;
            this.attachment = attachment;
        }
    }
}
