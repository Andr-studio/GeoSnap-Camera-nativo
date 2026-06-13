package com.andrives.geosnap_cam

import android.graphics.Bitmap
import java.nio.ByteBuffer

object NativeEngine {
    init {
        System.loadLibrary("geosnap_native")
    }

    /**
     * Rotates YUV data, blends the overlay bitmap, and outputs NV12 into [targetNv12].
     */
    external fun processVideoFrame(
        y: ByteBuffer, u: ByteBuffer, v: ByteBuffer,
        yRowStride: Int, uRowStride: Int, vRowStride: Int,
        pixelStride: Int, overlayBitmap: Bitmap?,
        width: Int, height: Int, rotation: Int,
        targetNv12: ByteArray,
        isFrontCamera: Boolean,
        isPlanar: Boolean
    )
}
