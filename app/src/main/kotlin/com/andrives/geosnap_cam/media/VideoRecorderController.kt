package com.andrives.geosnap_cam.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRecorderController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "VideoRecorderCtrl"
    }

    private val muxer = GeoSnapVideoMuxer(context)

    fun startRecording(outputPath: String): Boolean {
        return try {
            muxer.startRecording(File(outputPath))
            true
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed: ${e.message}")
            false
        }
    }

    fun stopRecording(): String? {
        return muxer.stopRecording()
    }

    val isRecording: Boolean
        get() = muxer.isRecording

    fun processFrame(proxy: ImageProxy, overlayBitmap: Bitmap?, isFrontCamera: Boolean) {
        muxer.processFrame(proxy, overlayBitmap, isFrontCamera)
    }

    fun dispose() {
        if (isRecording) {
            stopRecording()
        }
    }
}
