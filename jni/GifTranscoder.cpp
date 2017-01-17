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

#include <jni.h>
#include <time.h>
#include <stdio.h>
#include <memory>
#include <vector>

#include <android/log.h>

#include "GifTranscoder.h"

#define SQUARE(a) ((a)*(a))

// GIF does not support partial transparency, so our alpha channels are always 0x0 or 0xff.
static const ColorARGB TRANSPARENT = 0x0;

#define ALPHA(color) (((color) >> 24) & 0xff)
#define RED(color)   (((color) >> 16) & 0xff)
#define GREEN(color) (((color) >>  8) & 0xff)
#define BLUE(color)  (((color) >>  0) & 0xff)

#define MAKE_COLOR_ARGB(a, r, g, b) \
    ((a) << 24 | (r) << 16 | (g) << 8 | (b))

#define MAX_COLOR_DISTANCE (255 * 255 * 255)

#define TAG "GifTranscoder.cpp"
#define LOGD_ENABLED 0
#if LOGD_ENABLED
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__))
#else
#define LOGD(...) ((void)0)
#endif
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__))

// This macro expects the assertion to pass, but logs a FATAL if not.
#define ASSERT(cond, ...) \
    ( (__builtin_expect((cond) == 0, 0)) \
    ? ((void)__android_log_assert(#cond, TAG, ## __VA_ARGS__)) \
    : (void) 0 )
#define ASSERT_ENABLED 1

namespace {

// Current time in milliseconds since Unix epoch.
double now(void) {
    struct timespec res;
    clock_gettime(CLOCK_REALTIME, &res);
    return 1000.0 * res.tv_sec + (double) res.tv_nsec / 1e6;
}

// Gets the pixel at position (x,y) from a buffer that uses row-major order to store an image with
// the specified width.
template <typename T>
T* getPixel(T* buffer, int width, int x, int y) {
    return buffer + (y * width + x);
}

} // namespace

int GifTranscoder::transcode(const char* pathIn, const char* pathOut) {
    int error;
    double t0;
    GifFileType* gifIn;
    GifFileType* gifOut;

    // Automatically closes the GIF files when this method returns
    GifFilesCloser closer;

    gifIn = DGifOpenFileName(pathIn, &error);
    if (gifIn) {
        closer.setGifIn(gifIn);
        LOGD("Opened input GIF: %s", pathIn);
    } else {
        LOGE("Could not open input GIF: %s, error = %d", pathIn, error);
        return GIF_ERROR;
    }

    gifOut = EGifOpenFileName(pathOut, false, &error);
    if (gifOut) {
        closer.setGifOut(gifOut);
        LOGD("Opened output GIF: %s", pathOut);
    } else {
        LOGE("Could not open output GIF: %s, error = %d", pathOut, error);
        return GIF_ERROR;
    }

    t0 = now();
    if (resizeBoxFilter(gifIn, gifOut)) {
        LOGD("Resized GIF in %.2f ms", now() - t0);
    } else {
        LOGE("Could not resize GIF");
        return GIF_ERROR;
    }

    return GIF_OK;
}

bool GifTranscoder::resizeBoxFilter(GifFileType* gifIn, GifFileType* gifOut) {
    ASSERT(gifIn != NULL, "gifIn cannot be NULL");
    ASSERT(gifOut != NULL, "gifOut cannot be NULL");

    if (gifIn->SWidth < 0 || gifIn->SHeight < 0) {
        LOGE("Input GIF has invalid size: %d x %d", gifIn->SWidth, gifIn->SHeight);
        return false;
    }

    // Output GIF will be 50% the size of the original.
    if (EGifPutScreenDesc(gifOut,
                          gifIn->SWidth / 2,
                          gifIn->SHeight / 2,
                          gifIn->SColorResolution,
                          gifIn->SBackGroundColor,
                          gifIn->SColorMap) == GIF_ERROR) {
        LOGE("Could not write screen descriptor");
        return false;
    }
    LOGD("Wrote screen descriptor");

    // Index of the current image.
    int imageIndex = 0;

    // Transparent color of the current image.
    int transparentColor = NO_TRANSPARENT_COLOR;

    // Buffer for reading raw images from the input GIF.
    std::vector<GifByteType> srcBuffer(gifIn->SWidth * gifIn->SHeight);

    // Buffer for rendering images from the input GIF.
    std::unique_ptr<ColorARGB[]> renderBuffer(new ColorARGB[gifIn->SWidth * gifIn->SHeight]);

    // Buffer for writing new images to output GIF (one row at a time).
    std::unique_ptr<GifByteType[]> dstRowBuffer(new GifByteType[gifOut->SWidth]);

    // Many GIFs use DISPOSE_DO_NOT to make images draw on top of previous images. They can also
    // use DISPOSE_BACKGROUND to clear the last image region before drawing the next one. We need
    // to keep track of the disposal mode as we go along to properly render the GIF.
    int disposalMode = DISPOSAL_UNSPECIFIED;
    int prevImageDisposalMode = DISPOSAL_UNSPECIFIED;
    GifImageDesc prevImageDimens;

    // Background color (applies to entire GIF).
    ColorARGB bgColor = TRANSPARENT;

    GifRecordType recordType;
    do {
        if (DGifGetRecordType(gifIn, &recordType) == GIF_ERROR) {
            LOGE("Could not get record type");
            return false;
        }
        LOGD("Read record type: %d", recordType);
        switch (recordType) {
            case IMAGE_DESC_RECORD_TYPE: {
                if (DGifGetImageDesc(gifIn) == GIF_ERROR) {
                    LOGE("Could not read image descriptor (%d)", imageIndex);
                    return false;
                }

                // Sanity-check the current image position.
                if (gifIn->Image.Left < 0 ||
                        gifIn->Image.Top < 0 ||
                        gifIn->Image.Left + gifIn->Image.Width > gifIn->SWidth ||
                        gifIn->Image.Top + gifIn->Image.Height > gifIn->SHeight) {
                    LOGE("GIF image extends beyond logical screen");
                    return false;
                }

                // Write the new image descriptor.
                if (EGifPutImageDesc(gifOut,
                                     0, // Left
                                     0, // Top
                                     gifOut->SWidth,
                                     gifOut->SHeight,
                                     false, // Interlace
                                     gifIn->Image.ColorMap) == GIF_ERROR) {
                    LOGE("Could not write image descriptor (%d)", imageIndex);
                    return false;
                }

                // Read the image from the input GIF. The buffer is already initialized to the
                // size of the GIF, which is usually equal to the size of all the images inside it.
                // If not, the call to resize below ensures that the buffer is the right size.
                srcBuffer.resize(gifIn->Image.Width * gifIn->Image.Height);
                if (readImage(gifIn, srcBuffer.data()) == false) {
                    LOGE("Could not read image data (%d)", imageIndex);
                    return false;
                }
                LOGD("Read image data (%d)", imageIndex);
                // Render the image from the input GIF.
                if (renderImage(gifIn,
                                srcBuffer.data(),
                                imageIndex,
                                transparentColor,
                                renderBuffer.get(),
                                bgColor,
                                prevImageDimens,
                                prevImageDisposalMode) == false) {
                    LOGE("Could not render %d", imageIndex);
                    return false;
                }
                LOGD("Rendered image (%d)", imageIndex);

                // Generate the image in the output GIF.
                for (int y = 0; y < gifOut->SHeight; y++) {
                    for (int x = 0; x < gifOut->SWidth; x++) {
                      const GifByteType dstColorIndex = computeNewColorIndex(
                          gifIn, transparentColor, renderBuffer.get(), x, y);
                      *(dstRowBuffer.get() + x) = dstColorIndex;
                    }
                    if (EGifPutLine(gifOut, dstRowBuffer.get(), gifOut->SWidth) == GIF_ERROR) {
                        LOGE("Could not write raster data (%d)", imageIndex);
                        return false;
                    }
                }
                LOGD("Wrote raster data (%d)", imageIndex);

                // Save the disposal mode for rendering the next image.
                // We only support DISPOSE_DO_NOT and DISPOSE_BACKGROUND.
                prevImageDisposalMode = disposalMode;
                if (prevImageDisposalMode == DISPOSAL_UNSPECIFIED) {
                    prevImageDisposalMode = DISPOSE_DO_NOT;
                } else if (prevImageDisposalMode == DISPOSE_PREVIOUS) {
                    prevImageDisposalMode = DISPOSE_BACKGROUND;
                }
                if (prevImageDisposalMode == DISPOSE_BACKGROUND) {
                    prevImageDimens.Left = gifIn->Image.Left;
                    prevImageDimens.Top = gifIn->Image.Top;
                    prevImageDimens.Width = gifIn->Image.Width;
                    prevImageDimens.Height = gifIn->Image.Height;
                }

                if (gifOut->Image.ColorMap) {
                    GifFreeMapObject(gifOut->Image.ColorMap);
                    gifOut->Image.ColorMap = NULL;
                }

                imageIndex++;
            } break;
            case EXTENSION_RECORD_TYPE: {
                int extCode;
                GifByteType* ext;
                if (DGifGetExtension(gifIn, &extCode, &ext) == GIF_ERROR) {
                    LOGE("Could not read extension block");
                    return false;
                }
                LOGD("Read extension block, code: %d", extCode);
                if (extCode == GRAPHICS_EXT_FUNC_CODE) {
                    GraphicsControlBlock gcb;
                    if (DGifExtensionToGCB(ext[0], ext + 1, &gcb) == GIF_ERROR) {
                        LOGE("Could not interpret GCB extension");
                        return false;
                    }
                    transparentColor = gcb.TransparentColor;

                    // This logic for setting the background color based on the first GCB
                    // doesn't quite match the GIF spec, but empirically it seems to work and it
                    // matches what libframesequence (Rastermill) does.
                    if (imageIndex == 0 && gifIn->SColorMap) {
                        if (gcb.TransparentColor == NO_TRANSPARENT_COLOR) {
                            if (gifIn->SBackGroundColor < 0 ||
                                gifIn->SBackGroundColor >= gifIn->SColorMap->ColorCount) {
                                LOGE("SBackGroundColor overflow");
                                return false;
                            }
                            GifColorType bgColorIndex =
                                    gifIn->SColorMap->Colors[gifIn->SBackGroundColor];
                            bgColor = gifColorToColorARGB(bgColorIndex);
                            LOGD("Set background color based on first GCB");
                        }
                    }

                    // Record the original disposal mode and then update it.
                    disposalMode = gcb.DisposalMode;
                    gcb.DisposalMode = DISPOSE_BACKGROUND;
                    EGifGCBToExtension(&gcb, ext + 1);
                }
                if (EGifPutExtensionLeader(gifOut, extCode) == GIF_ERROR) {
                    LOGE("Could not write extension leader");
                    return false;
                }
                if (EGifPutExtensionBlock(gifOut, ext[0], ext + 1) == GIF_ERROR) {
                    LOGE("Could not write extension block");
                    return false;
                }
                LOGD("Wrote extension block");
                while (ext != NULL) {
                    if (DGifGetExtensionNext(gifIn, &ext) == GIF_ERROR) {
                        LOGE("Could not read extension continuation");
                        return false;
                    }
                    if (ext != NULL) {
                        LOGD("Read extension continuation");
                        if (EGifPutExtensionBlock(gifOut, ext[0], ext + 1) == GIF_ERROR) {
                            LOGE("Could not write extension continuation");
                            return false;
                        }
                        LOGD("Wrote extension continuation");
                    }
                }
                if (EGifPutExtensionTrailer(gifOut) == GIF_ERROR) {
                    LOGE("Could not write extension trailer");
                    return false;
                }
            } break;
        }

    } while (recordType != TERMINATE_RECORD_TYPE);
    LOGD("No more records");

    return true;
}

bool GifTranscoder::readImage(GifFileType* gifIn, GifByteType* rasterBits) {
    if (gifIn->Image.Interlace) {
        int interlacedOffset[] = { 0, 4, 2, 1 };
        int interlacedJumps[] = { 8, 8, 4, 2 };

        // Need to perform 4 passes on the image
        for (int i = 0; i < 4; i++) {
            for (int j = interlacedOffset[i]; j < gifIn->Image.Height; j += interlacedJumps[i]) {
                if (DGifGetLine(gifIn,
                                rasterBits + j * gifIn->Image.Width,
                                gifIn->Image.Width) == GIF_ERROR) {
                    LOGE("Could not read interlaced raster data");
                    return false;
                }
            }
        }
    } else {
        if (DGifGetLine(gifIn, rasterBits, gifIn->Image.Width * gifIn->Image.Height) == GIF_ERROR) {
            LOGE("Could not read raster data");
            return false;
        }
    }
    return true;
}

bool GifTranscoder::renderImage(GifFileType* gifIn,
                                GifByteType* rasterBits,
                                int imageIndex,
                                int transparentColorIndex,
                                ColorARGB* renderBuffer,
                                ColorARGB bgColor,
                                GifImageDesc prevImageDimens,
                                int prevImageDisposalMode) {
    ASSERT(imageIndex < gifIn->ImageCount,
           "Image index %d is out of bounds (count=%d)", imageIndex, gifIn->ImageCount);

    ColorMapObject* colorMap = getColorMap(gifIn);
    if (colorMap == NULL) {
        LOGE("No GIF color map found");
        return false;
    }

    // Clear all or part of the background, before drawing the first image and maybe before drawing
    // subsequent images (depending on the DisposalMode).
    if (imageIndex == 0) {
        fillRect(renderBuffer, gifIn->SWidth, gifIn->SHeight,
                 0, 0, gifIn->SWidth, gifIn->SHeight, bgColor);
    } else if (prevImageDisposalMode == DISPOSE_BACKGROUND) {
        fillRect(renderBuffer, gifIn->SWidth, gifIn->SHeight,
                 prevImageDimens.Left, prevImageDimens.Top,
                 prevImageDimens.Width, prevImageDimens.Height, TRANSPARENT);
    }

    // Paint this image onto the canvas
    for (int y = 0; y < gifIn->Image.Height; y++) {
        for (int x = 0; x < gifIn->Image.Width; x++) {
            GifByteType colorIndex = *getPixel(rasterBits, gifIn->Image.Width, x, y);
            if (colorIndex >= colorMap->ColorCount) {
                LOGE("Color Index %d is out of bounds (count=%d)", colorIndex,
                    colorMap->ColorCount);
                return false;
            }

            // This image may be smaller than the GIF's "logical screen"
            int renderX = x + gifIn->Image.Left;
            int renderY = y + gifIn->Image.Top;

            // Skip drawing transparent pixels if this image renders on top of the last one
            if (imageIndex > 0 && prevImageDisposalMode == DISPOSE_DO_NOT &&
                colorIndex == transparentColorIndex) {
                continue;
            }

            ColorARGB* renderPixel = getPixel(renderBuffer, gifIn->SWidth, renderX, renderY);
            *renderPixel = getColorARGB(colorMap, transparentColorIndex, colorIndex);
        }
    }
    return true;
}

void GifTranscoder::fillRect(ColorARGB* renderBuffer,
                             int imageWidth,
                             int imageHeight,
                             int left,
                             int top,
                             int width,
                             int height,
                             ColorARGB color) {
    ASSERT(left + width <= imageWidth, "Rectangle is outside image bounds");
    ASSERT(top + height <= imageHeight, "Rectangle is outside image bounds");

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            ColorARGB* renderPixel = getPixel(renderBuffer, imageWidth, x + left, y + top);
            *renderPixel = color;
        }
    }
}

GifByteType GifTranscoder::computeNewColorIndex(GifFileType* gifIn,
                                                int transparentColorIndex,
                                                ColorARGB* renderBuffer,
                                                int x,
                                                int y) {
    ColorMapObject* colorMap = getColorMap(gifIn);

    // Compute the average color of 4 adjacent pixels from the input image.
    ColorARGB c1 = *getPixel(renderBuffer, gifIn->SWidth, x * 2, y * 2);
    ColorARGB c2 = *getPixel(renderBuffer, gifIn->SWidth, x * 2 + 1, y * 2);
    ColorARGB c3 = *getPixel(renderBuffer, gifIn->SWidth, x * 2, y * 2 + 1);
    ColorARGB c4 = *getPixel(renderBuffer, gifIn->SWidth, x * 2 + 1, y * 2 + 1);
    ColorARGB avgColor = computeAverage(c1, c2, c3, c4);

    // Search the color map for the best match.
    return findBestColor(colorMap, transparentColorIndex, avgColor);
}

ColorARGB GifTranscoder::computeAverage(ColorARGB c1, ColorARGB c2, ColorARGB c3, ColorARGB c4) {
    char avgAlpha = (char)(((int) ALPHA(c1) + (int) ALPHA(c2) +
                            (int) ALPHA(c3) + (int) ALPHA(c4)) / 4);
    char avgRed =   (char)(((int) RED(c1) + (int) RED(c2) +
                            (int) RED(c3) + (int) RED(c4)) / 4);
    char avgGreen = (char)(((int) GREEN(c1) + (int) GREEN(c2) +
                            (int) GREEN(c3) + (int) GREEN(c4)) / 4);
    char avgBlue =  (char)(((int) BLUE(c1) + (int) BLUE(c2) +
                            (int) BLUE(c3) + (int) BLUE(c4)) / 4);
    return MAKE_COLOR_ARGB(avgAlpha, avgRed, avgGreen, avgBlue);
}

GifByteType GifTranscoder::findBestColor(ColorMapObject* colorMap, int transparentColorIndex,
                                         ColorARGB targetColor) {
    // Return the transparent color if the average alpha is zero.
    char alpha = ALPHA(targetColor);
    if (alpha == 0 && transparentColorIndex != NO_TRANSPARENT_COLOR) {
        return transparentColorIndex;
    }

    GifByteType closestColorIndex = 0;
    int closestColorDistance = MAX_COLOR_DISTANCE;
    for (int i = 0; i < colorMap->ColorCount; i++) {
        // Skip the transparent color (we've already eliminated that option).
        if (i == transparentColorIndex) {
            continue;
        }
        ColorARGB indexedColor = gifColorToColorARGB(colorMap->Colors[i]);
        int distance = computeDistance(targetColor, indexedColor);
        if (distance < closestColorDistance) {
            closestColorIndex = i;
            closestColorDistance = distance;
        }
    }
    return closestColorIndex;
}

int GifTranscoder::computeDistance(ColorARGB c1, ColorARGB c2) {
    return SQUARE(RED(c1) - RED(c2)) +
           SQUARE(GREEN(c1) - GREEN(c2)) +
           SQUARE(BLUE(c1) - BLUE(c2));
}

ColorMapObject* GifTranscoder::getColorMap(GifFileType* gifIn) {
    if (gifIn->Image.ColorMap) {
        return gifIn->Image.ColorMap;
    }
    return gifIn->SColorMap;
}

ColorARGB GifTranscoder::getColorARGB(ColorMapObject* colorMap, int transparentColorIndex,
                                      GifByteType colorIndex) {
    if (colorIndex == transparentColorIndex) {
        return TRANSPARENT;
    }
    return gifColorToColorARGB(colorMap->Colors[colorIndex]);
}

ColorARGB GifTranscoder::gifColorToColorARGB(const GifColorType& color) {
    return MAKE_COLOR_ARGB(0xff, color.Red, color.Green, color.Blue);
}

GifFilesCloser::~GifFilesCloser() {
    if (mGifIn) {
        DGifCloseFile(mGifIn, NULL);
        mGifIn = NULL;
    }
    if (mGifOut) {
        EGifCloseFile(mGifOut, NULL);
        mGifOut = NULL;
    }
}

void GifFilesCloser::setGifIn(GifFileType* gifIn) {
    ASSERT(mGifIn == NULL, "mGifIn is already set");
    mGifIn = gifIn;
}

void GifFilesCloser::releaseGifIn() {
    ASSERT(mGifIn != NULL, "mGifIn is already NULL");
    mGifIn = NULL;
}

void GifFilesCloser::setGifOut(GifFileType* gifOut) {
    ASSERT(mGifOut == NULL, "mGifOut is already set");
    mGifOut = gifOut;
}

void GifFilesCloser::releaseGifOut() {
    ASSERT(mGifOut != NULL, "mGifOut is already NULL");
    mGifOut = NULL;
}

// JNI stuff

jboolean transcode(JNIEnv* env, jobject clazz, jstring filePath, jstring outFilePath) {
    const char* pathIn = env->GetStringUTFChars(filePath, JNI_FALSE);
    const char* pathOut = env->GetStringUTFChars(outFilePath, JNI_FALSE);

    GifTranscoder transcoder;
    int gifCode = transcoder.transcode(pathIn, pathOut);

    env->ReleaseStringUTFChars(filePath, pathIn);
    env->ReleaseStringUTFChars(outFilePath, pathOut);

    return (gifCode == GIF_OK);
}

const char *kClassPathName = "com/android/messaging/util/GifTranscoder";

JNINativeMethod kMethods[] = {
        { "transcodeInternal", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*)transcode },
};

int registerNativeMethods(JNIEnv* env, const char* className,
                          JNINativeMethod* gMethods, int numMethods) {
    jclass clazz = env->FindClass(className);
    if (clazz == NULL) {
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    if (!registerNativeMethods(env, kClassPathName,
                               kMethods, sizeof(kMethods) / sizeof(kMethods[0]))) {
      return -1;
    }
    return JNI_VERSION_1_6;
}
