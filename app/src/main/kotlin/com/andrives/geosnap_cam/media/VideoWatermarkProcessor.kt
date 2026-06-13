package com.andrives.geosnap_cam.media

import android.util.Log
import com.andrives.geosnap_cam.data.model.WatermarkConfig
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * VideoWatermarkProcessor — Encodes watermark overlay onto video using FFmpegKit.
 *
 * Direct equivalent of Flutter's VideoWatermarkProcessor.
 * Tries h264_mediacodec (HW) first, falls back to libx264 (SW) if it fails.
 */
object VideoWatermarkProcessor {

    private const val TAG = "VideoWmProcessor"

    suspend fun encode(
        inputPath: String,
        watermarkPath: String,
        outputPath: String,
        config: WatermarkConfig,
        watermarkWidth: Float,
        watermarkHeight: Float,
        videoWidth: Int,
        videoHeight: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting video encoding...")
        Log.d(TAG, "  inputPath: $inputPath")
        Log.d(TAG, "  watermarkPath: $watermarkPath")
        Log.d(TAG, "  outputPath: $outputPath")
        Log.d(TAG, "  watermarkSize: ${watermarkWidth}x${watermarkHeight}")
        Log.d(TAG, "  videoSize: ${videoWidth}x${videoHeight}")

        val widthFactor = config.effectiveGlassWidth.coerceIn(0.42, 0.96)

        // Use min(w,h) to correctly size watermark for both portrait and landscape
        // videos, especially when rotation metadata might be wrong
        val safeBaseWidth = min(videoWidth, videoHeight)
        var targetWmWidth = (safeBaseWidth * widthFactor).toInt()
        var targetWmHeight = (targetWmWidth * (watermarkHeight / watermarkWidth)).toInt()

        // Force even numbers for h264_mediacodec
        if (targetWmWidth % 2 != 0) targetWmWidth -= 1
        if (targetWmHeight % 2 != 0) targetWmHeight -= 1

        Log.d(TAG, "  targetWmSize: ${targetWmWidth}x${targetWmHeight}")

        val filterComplex =
            "[1:v]scale=$targetWmWidth:$targetWmHeight[wm];" +
                "[0:v][wm]overlay=(main_w-overlay_w)/2:main_h-overlay_h-(main_h*0.02)," +
                "crop='trunc(iw/16)*16':'trunc(ih/16)*16'," +
                "format=yuv420p[out]"

        // ── Try hardware encoding first ──
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val hwCommand = "-y -i \"$inputPath\" -i \"$watermarkPath\" " +
                "-filter_complex \"$filterComplex\" " +
                "-map \"[out]\" -map 0:a? " +
                "-c:v h264_mediacodec -b:v 8M -c:a aac -b:a 128k \"$outputPath\""

            Log.d(TAG, "Running HW command...")
            val hwSession = FFmpegKit.execute(hwCommand)
            val hwCode = hwSession.returnCode

            if (ReturnCode.isSuccess(hwCode)) {
                Log.d(TAG, "HW encoding succeeded!")
                return@withContext true
            }

            // Log HW failure
            val hwLogs = hwSession.logs?.joinToString("\n") { it.message } ?: "No logs"
            Log.w(TAG, "HW encoding failed. Logs:\n$hwLogs")
        } else {
            Log.d(TAG, "Skipping HW encoding on API < 29 to prevent native crashes.")
        }

        // ── Fallback to software encoding ──
        val swCommand = "-y -i \"$inputPath\" -i \"$watermarkPath\" " +
            "-filter_complex \"$filterComplex\" " +
            "-map \"[out]\" -map 0:a? " +
            "-c:v mpeg4 -q:v 5 -c:a aac -b:a 128k \"$outputPath\""

        Log.d(TAG, "Running SW command...")
        val swSession = FFmpegKit.execute(swCommand)
        val swCode = swSession.returnCode

        if (ReturnCode.isSuccess(swCode)) {
            Log.d(TAG, "SW encoding succeeded!")
            return@withContext true
        }

        val swLogs = swSession.logs?.joinToString("\n") { it.message } ?: "No logs"
        Log.e(TAG, "SW encoding also failed. Logs:\n$swLogs")

        false
    }
}
