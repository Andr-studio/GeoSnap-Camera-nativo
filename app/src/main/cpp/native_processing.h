#ifndef GEOSNAP_NATIVE_PROCESSING_H
#define GEOSNAP_NATIVE_PROCESSING_H

#include <android/bitmap.h>
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Will use dynamic registration (JNI_OnLoad) so these names don't matter as much,
// but we'll export them normally just in case.

JNIEXPORT void JNICALL
nativeProcessVideoFrame(
    JNIEnv *env, jobject thiz, jobject yBuffer, jobject uBuffer,
    jobject vBuffer, jint yRowStride, jint uRowStride, jint vRowStride,
    jint pixelStride, jobject overlayBitmap, jint width, jint height,
    jint rotation, jbyteArray targetNv12, jboolean isFrontCamera, jboolean isPlanar);

#ifdef __cplusplus
}
#endif

#endif // GEOSNAP_NATIVE_PROCESSING_H
