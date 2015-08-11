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

#ifndef GIF_TRANSCODER_H
#define GIF_TRANSCODER_H

#include <sys/types.h>

#include "gif_lib.h"

// 24-bit color with alpha, stored in order: A, R, G, B.
// The internal GIF render buffer stores pixels using this format.
typedef uint32_t ColorARGB;

// Compresses a GIF (probably animated) so it can be sent via MMS, which generally has a 1 MB limit
// on attachments. GIF image data is already compressed (LZW), so to achieve further reduction in
// file size, we reduce the image dimensions.
//
// Helpful GIF references:
// GIF89A spec: http://www.w3.org/Graphics/GIF/spec-gif89a.txt
// What's in a GIF: http://giflib.sourceforge.net/whatsinagif/index.html
//
class GifTranscoder {
public:
    GifTranscoder() {}
    ~GifTranscoder() {}

    // Resizes a GIF's width and height to 50% of their original dimensions. The new file is
    // written to pathOut.
    //
    // The image is resized using a box filter, which averages the colors in each 2x2 box of pixels
    // in the source to generate the color of the pixel in the destination.
    //
    // Returns GIF_OK (1) on success, or GIF_ERROR (0) on failure.
    int transcode(const char* pathIn, const char* pathOut);

private:
    // Implementation of the box filter algorithm.
    static bool resizeBoxFilter(GifFileType* gifIn, GifFileType* gifOut);

    // Reads the raster data for the current image of the GIF.
    static bool readImage(GifFileType* gifIn, GifByteType* rasterBits);

    // Renders the current image of the GIF into the supplied render buffer.
    static bool renderImage(GifFileType* gifIn,
                            GifByteType* rasterBits,
                            int imageIndex,
                            int transparentColorIndex,
                            ColorARGB* renderBuffer,
                            ColorARGB bgColor,
                            GifImageDesc prevImageDimens,
                            int prevImageDisposalMode);

    // Fills a rectangle in the buffer with a solid color.
    static void fillRect(ColorARGB* renderBuffer,
                         int imageWidth,
                         int imageHeight,
                         int left,
                         int top,
                         int width,
                         int height,
                         ColorARGB color);

    // Computes the color for the pixel (x,y) in the current image in the output GIF.
    static GifByteType computeNewColorIndex(GifFileType* gifIn,
                                            int transparentColorIndex,
                                            ColorARGB* renderBuffer,
                                            int x,
                                            int y);

    // Computes the average color (by averaging the per-channel (ARGB) values).
    static ColorARGB computeAverage(ColorARGB c1, ColorARGB c2, ColorARGB c3, ColorARGB c4);

    // Searches a color map for the color closest (Euclidean distance) to the target color.
    static GifByteType findBestColor(ColorMapObject* colorMap, int transparentColorIndex,
                                     ColorARGB targetColor);

    // Computes distance (squared) between 2 colors, considering each channel a separate dimension.
    static int computeDistance(ColorARGB c1, ColorARGB c2);

    // Returns the local color map of the current image (if any), or else the global color map.
    static ColorMapObject* getColorMap(GifFileType* gifIn);

    // Returns an indexed color from the color map.
    static ColorARGB getColorARGB(ColorMapObject* colorMap, int transparentColorIndex,
                                  GifByteType colorIndex);

    // Converts a 24-bit GIF color (RGB) to a 32-bit ARGB color.
    static ColorARGB gifColorToColorARGB(const GifColorType& color);
};

// Wrapper class that automatically closes the GIF files when the wrapper goes out of scope.
class GifFilesCloser {
public:
    GifFilesCloser() {}
    ~GifFilesCloser();

    void setGifIn(GifFileType* gifIn);
    void releaseGifIn();

    void setGifOut(GifFileType* gifOut);
    void releaseGifOut();

private:
    GifFileType* mGifIn = NULL;
    GifFileType* mGifOut = NULL;
};

#endif // GIF_TRANSCODER_H
