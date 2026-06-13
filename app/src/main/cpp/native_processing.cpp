#include "native_processing.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>
#include <stdint.h>
#include <jni.h>
#include <android/bitmap.h>

#define LOG_TAG "NativeEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Helper to blend a single RGBA_8888 pixel onto an RGB background pixel
static inline void blendPixel(uint8_t& outR, uint8_t& outG, uint8_t& outB, uint32_t overlayPixel) {
    uint8_t a = (overlayPixel >> 24) & 0xFF;
    if (a == 0) return; // fully transparent
    
    // AndroidBitmap format RGBA_8888 on little endian is actually A-B-G-R in uint32_t
    uint8_t r = overlayPixel & 0xFF;
    uint8_t g = (overlayPixel >> 8) & 0xFF;
    uint8_t b = (overlayPixel >> 16) & 0xFF;

    if (a == 255) {
        outR = r; outG = g; outB = b;
        return;
    }

    uint32_t alpha = a;
    uint32_t invAlpha = 255 - alpha;
    outR = (r * alpha + outR * invAlpha) / 255;
    outG = (g * alpha + outG * invAlpha) / 255;
    outB = (b * alpha + outB * invAlpha) / 255;
}

// Convert YUV to RGB helper
static inline void yuvToRgb(uint8_t y, uint8_t u, uint8_t v, uint8_t& r, uint8_t& g, uint8_t& b) {
    int c = y - 16;
    int d = u - 128;
    int e = v - 128;
    
    int r_val = (298 * c + 409 * e + 128) >> 8;
    int g_val = (298 * c - 100 * d - 208 * e + 128) >> 8;
    int b_val = (298 * c + 516 * d + 128) >> 8;
    
    r = std::clamp(r_val, 0, 255);
    g = std::clamp(g_val, 0, 255);
    b = std::clamp(b_val, 0, 255);
}

// Convert RGB to YUV helper (for NV12)
static inline void rgbToYuv(uint8_t r, uint8_t g, uint8_t b, uint8_t& y, uint8_t& u, uint8_t& v) {
    y = std::clamp(((66 * r + 129 * g + 25 * b + 128) >> 8) + 16, 16, 235);
    u = std::clamp(((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128, 16, 240);
    v = std::clamp(((112 * r - 94 * g - 18 * b + 128) >> 8) + 128, 16, 240);
}

extern "C"
JNIEXPORT void JNICALL
nativeProcessVideoFrame(
    JNIEnv* env, jobject thiz,
    jobject yBuffer, jobject uBuffer, jobject vBuffer,
    jint yRowStride, jint uRowStride, jint vRowStride,
    jint pixelStride, jobject overlayBitmap,
    jint width, jint height, jint rotation,
    jbyteArray targetNv12,
    jboolean isFrontCamera,
    jboolean isPlanar) {

    uint8_t* yData = (uint8_t*)env->GetDirectBufferAddress(yBuffer);
    uint8_t* uData = (uint8_t*)env->GetDirectBufferAddress(uBuffer);
    uint8_t* vData = (uint8_t*)env->GetDirectBufferAddress(vBuffer);
    
    if (!yData || !uData || !vData) {
        LOGE("Failed to get direct buffer addresses for YUV");
        return;
    }

    uint32_t* overlayPixels = nullptr;
    AndroidBitmapInfo overlayInfo;
    if (overlayBitmap) {
        if (AndroidBitmap_getInfo(env, overlayBitmap, &overlayInfo) == 0 &&
            AndroidBitmap_lockPixels(env, overlayBitmap, (void**)&overlayPixels) == 0) {
            // Success
        } else {
            overlayPixels = nullptr;
        }
    }

    jbyte* outNv12 = env->GetByteArrayElements(targetNv12, nullptr);
    int outYIdx = 0;
    
    int outWidth = (rotation == 90 || rotation == 270) ? height : width;
    int outHeight = (rotation == 90 || rotation == 270) ? width : height;
    int outUvIdx = outWidth * outHeight;
    
    // Position the overlay at the bottom center
    int overlayX = 0;
    int overlayY = 0;
    if (overlayPixels && overlayInfo.width > 0 && overlayInfo.height > 0) {
        overlayX = (outWidth - overlayInfo.width) / 2;
        overlayY = outHeight - overlayInfo.height - (int)(outHeight * 0.02f);
        if (overlayX < 0) overlayX = 0;
        if (overlayY < 0) overlayY = 0;
    }

    for (int j = 0; j < outHeight; ++j) {
        for (int i = 0; i < outWidth; ++i) {
            int finalI = isFrontCamera ? (outWidth - 1 - i) : i;
            int srcX = finalI;
            int srcY = j;
            
            if (rotation == 90) {
                srcX = j;
                srcY = outWidth - 1 - finalI;
            } else if (rotation == 180) {
                srcX = outWidth - 1 - finalI;
                srcY = outHeight - 1 - j;
            } else if (rotation == 270) {
                srcX = outHeight - 1 - j;
                srcY = finalI;
            }
            
            int yIndex = srcY * yRowStride + srcX;
            int uvIndex = (srcY / 2) * uRowStride + (srcX / 2) * pixelStride;
            
            uint8_t yVal = yData[yIndex];
            uint8_t uVal = uData[uvIndex];
            uint8_t vVal = vData[uvIndex];
            
            // Blend overlay
            if (overlayPixels && i >= overlayX && i < overlayX + (int)overlayInfo.width && j >= overlayY && j < overlayY + (int)overlayInfo.height) {
                int overI = i - overlayX;
                int overJ = j - overlayY;
                uint32_t overPx = overlayPixels[overJ * overlayInfo.width + overI];
                if ((overPx >> 24) > 0) {
                    uint8_t a = (overPx >> 24) & 0xFF;
                    uint8_t b = (overPx >> 16) & 0xFF;
                    uint8_t g = (overPx >> 8) & 0xFF;
                    uint8_t r = overPx & 0xFF;
                    
                    uint8_t overY = (uint8_t)((0.299 * r) + (0.587 * g) + (0.114 * b));
                    uint8_t overU = (uint8_t)(128 - (0.168736 * r) - (0.331264 * g) + (0.5 * b));
                    uint8_t overV = (uint8_t)(128 + (0.5 * r) - (0.418688 * g) - (0.081312 * b));
                    
                    yVal = (uint8_t)((yVal * (255 - a) + overY * a) / 255);
                    uVal = (uint8_t)((uVal * (255 - a) + overU * a) / 255);
                    vVal = (uint8_t)((vVal * (255 - a) + overV * a) / 255);
                }
            }
            
            outNv12[j * outWidth + i] = yVal;
            
            if (j % 2 == 0 && i % 2 == 0) {
                if (isPlanar) {
                    int uvIndex = (j / 2) * (outWidth / 2) + (i / 2);
                    int uPlaneSize = (outWidth / 2) * (outHeight / 2);
                    outNv12[outUvIdx + uvIndex] = uVal;
                    outNv12[outUvIdx + uPlaneSize + uvIndex] = vVal;
                } else {
                    int uvOutIndex = outUvIdx + (j / 2) * outWidth + i;
                    outNv12[uvOutIndex] = uVal;
                    outNv12[uvOutIndex + 1] = vVal;
                }
            }
        }
    }
    
    if (overlayPixels) {
        AndroidBitmap_unlockPixels(env, overlayBitmap);
    }
    
    env->ReleaseByteArrayElements(targetNv12, outNv12, 0);
}

// Removed unused nativeBlendOverlayOnBitmap

static const JNINativeMethod kMethods[] = {
    {"processVideoFrame", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIIILandroid/graphics/Bitmap;III[BZZ)V", (void*)nativeProcessVideoFrame},
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    // Register for com.andrives.geosnap_cam.NativeEngine
    const jclass clazz = env->FindClass("com/andrives/geosnap_cam/NativeEngine");
    if (clazz) {
        env->RegisterNatives(clazz, kMethods, sizeof(kMethods) / sizeof(kMethods[0]));
        LOGD("JNI_OnLoad: NativeEngine methods registered OK");
    } else {
        LOGE("JNI_OnLoad: NativeEngine class not found");
        env->ExceptionClear();
    }
    return JNI_VERSION_1_6;
}
