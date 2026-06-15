package com.andrives.geosnap_cam.media

import android.graphics.Bitmap
import android.util.Log
import com.andrives.geosnap_cam.data.model.LocationData
import com.andrives.geosnap_cam.data.model.WatermarkConfig
import com.andrives.geosnap_cam.data.repository.WatermarkSettingsRepository
import com.andrives.geosnap_cam.service.MapTileService
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WatermarkService — Orchestrates watermark creation and application.
 *
 * Direct equivalent of Flutter's WatermarkService.
 * Handles:
 * - Creating watermark PNG from WatermarkRenderer
 * - Applying it to photos (via PhotoWatermarkProcessor)
 * - Applying it to videos (via VideoWatermarkProcessor + FFmpegKit)
 */
@Singleton
class WatermarkService @Inject constructor(
    private val settingsRepository: WatermarkSettingsRepository,
    private val mapTileService: MapTileService,
) {
    private var lastOverlayBitmap: Bitmap? = null
    private var lastOverlayTime: Long = 0L
    companion object {
        private const val TAG = "WatermarkService"
        const val CANVAS_WIDTH = 760f
    }

    sealed class WatermarkResult {
        data class Success(val outputPath: String) : WatermarkResult()
        data class Failure(val message: String) : WatermarkResult()
    }

    /**
     * Main entry point — applies watermark to a photo or video.
     */
    suspend fun applyWatermark(
        inputPath: String,
        isVideo: Boolean,
        location: LocationData,
        outputPath: String? = null,
    ): WatermarkResult = withContext(Dispatchers.IO) {
        try {
            // Parallelize video probe and config loading
            val configDeferred = async { settingsRepository.getConfig() }
            val probeDeferred = async {
                if (isVideo) probeVideoSize(inputPath)
                else null
            }

            val config = configDeferred.await()
            val videoInfo = probeDeferred.await()

            val videoW = videoInfo?.width ?: 1080
            val videoH = videoInfo?.height ?: 1920

            // Create watermark image
            val watermarkResult = createWatermarkImage(location, config, videoW, videoH)

            // Resolve output path
            val resolvedOutput = outputPath
                ?: (File(inputPath).parent?.let { "$it/" } ?: "") + "watermarked_${System.currentTimeMillis()}." + File(inputPath).extension

            try {
                if (isVideo) {
                    val success = VideoWatermarkProcessor.encode(
                        inputPath = inputPath,
                        watermarkPath = watermarkResult.file.absolutePath,
                        outputPath = resolvedOutput,
                        config = config,
                        watermarkWidth = watermarkResult.width,
                        watermarkHeight = watermarkResult.height,
                        videoWidth = videoW,
                        videoHeight = videoH,
                    )
                    if (success) WatermarkResult.Success(resolvedOutput)
                    else WatermarkResult.Failure("Video encoding failed")
                } else {
                    val result = PhotoWatermarkProcessor.apply(
                        inputPath = inputPath,
                        outputPath = resolvedOutput,
                        watermarkBitmap = watermarkResult.bitmap,
                        config = config,
                        location = location,
                    )
                    if (result != null) WatermarkResult.Success(result)
                    else WatermarkResult.Failure("Photo processing failed")
                }
            } finally {
                // Cleanup temp watermark file
                try {
                    watermarkResult.file.delete()
                    watermarkResult.bitmap.recycle()
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyWatermark failed: ${e.message}", e)
            WatermarkResult.Failure("Unexpected error: ${e.message}")
        }
    }

    private var lastValidMapImage: Bitmap? = null

    /**
     * Returns a cached watermark bitmap for real-time video encoding.
     * Updates at most once per second.
     */
    suspend fun getLiveWatermarkBitmap(
        location: LocationData?,
        config: WatermarkConfig,
        videoWidth: Int,
        videoHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (location == null) return@withContext null
        
        val nowMs = System.currentTimeMillis()
        if (lastOverlayBitmap == null || nowMs - lastOverlayTime > 1000) {
            lastOverlayBitmap?.recycle()
            
            var mapImage = mapTileService.getCachedMapImage(
                location.latitude, location.longitude, config.mapType
            )
            
            if (mapImage == null) {
                // Cache miss due to GPS drift. Trigger async fetch and reuse last valid map.
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    mapTileService.getOrFetchMapImage(location.latitude, location.longitude, config.mapType)
                        ?.let { lastValidMapImage = it }
                }
                mapImage = lastValidMapImage ?: mapTileService.getLastCachedMapImage(config.mapType)
            } else {
                lastValidMapImage = mapImage
            }
            
            val canvasW = CANVAS_WIDTH * config.effectiveGlassWidth.toFloat()
            val renderer = WatermarkRenderer(
                location = location,
                config = config,
                date = Date(),
                mapImage = mapImage,
                canvasWidth = canvasW,
            )
            val measuredSize = WatermarkRenderer.measureSize(location, config, Date(), canvasW)
            val widthFactor = config.effectiveGlassWidth.coerceIn(0.32, 0.96).toFloat()
            val safeBaseWidth = kotlin.math.min(videoWidth, videoHeight)
            val targetWidth = safeBaseWidth * widthFactor
            val scale = (targetWidth / measuredSize.width).coerceAtLeast(0.5f)
            
            lastOverlayBitmap = renderer.renderToBitmap(scale = scale)
            lastOverlayTime = nowMs
        }
        return@withContext lastOverlayBitmap
    }

    /**
     * Returns cached map bitmap for live preview overlay.
     */
    fun getCachedMapImage(location: LocationData?, config: WatermarkConfig): Bitmap? {
        if (location == null) return null
        return mapTileService.getCachedMapImage(
            location.latitude, location.longitude, config.mapType
        )
    }

    /**
     * Pre-downloads map tiles so they're ready when the user takes a photo/video.
     */
    suspend fun prewarmWatermarkAssets(location: LocationData?, config: WatermarkConfig) {
        if (location == null) return
        lastValidMapImage = mapTileService.getOrFetchMapImage(
            location.latitude, location.longitude, config.mapType
        )
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private data class WatermarkImageResult(
        val bitmap: Bitmap,
        val file: File,
        val width: Float,
        val height: Float,
    )

    private suspend fun createWatermarkImage(
        location: LocationData,
        config: WatermarkConfig,
        videoWidth: Int,
        videoHeight: Int,
    ): WatermarkImageResult = withContext(Dispatchers.IO) {
        val now = Date()

        // Fetch map tile (may use cache)
        val mapImage = mapTileService.getOrFetchMapImage(
            location.latitude, location.longitude, config.mapType
        )
        if (mapImage != null) {
            lastValidMapImage = mapImage
        }

        val canvasW = CANVAS_WIDTH * config.effectiveGlassWidth.toFloat()

        // Render watermark at 3x scale for quality
        val renderer = WatermarkRenderer(
            location = location,
            config = config,
            date = now,
            mapImage = mapImage,
            canvasWidth = canvasW,
        )
        val bitmap = renderer.renderToBitmap(scale = 3f)

        // Save to temp file for FFmpeg
        val tempFile = File.createTempFile("wm_", ".png")
        FileOutputStream(tempFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }

        WatermarkImageResult(
            bitmap = bitmap,
            file = tempFile,
            width = bitmap.width.toFloat(),
            height = bitmap.height.toFloat(),
        )
    }

    private data class VideoSize(val width: Int, val height: Int)

    private fun probeVideoSize(path: String): VideoSize {
        try {
            val session = FFprobeKit.getMediaInformation(path)
            val info = session.mediaInformation ?: return VideoSize(1080, 1920)

            for (stream in info.streams) {
                if (stream.type == "video") {
                    val w = stream.width?.toInt() ?: 1080
                    val h = stream.height?.toInt() ?: 1920
                    var finalW = w
                    var finalH = h
                    var rotate = 0

                    val properties = stream.allProperties
                    if (properties != null) {
                        val tags = properties["tags"]
                        if (tags is Map<*, *>) {
                            val rotateStr = tags["rotate"]?.toString()
                            if (rotateStr != null) {
                                rotate = rotateStr.toIntOrNull() ?: 0
                            }
                        }

                        if (rotate == 0) {
                            val sideDataList = properties["side_data_list"]
                            if (sideDataList is List<*>) {
                                for (sd in sideDataList) {
                                    if (sd is Map<*, *> && sd.containsKey("rotation")) {
                                        rotate = sd["rotation"].toString().toDoubleOrNull()
                                            ?.toInt()?.let { kotlin.math.abs(it) } ?: 0
                                    }
                                }
                            }
                        }
                    }

                    if (rotate == 90 || rotate == 270) {
                        finalW = h
                        finalH = w
                    }

                    return VideoSize(finalW, finalH)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "probeVideoSize failed: ${e.message}")
        }
        return VideoSize(1080, 1920)
    }
}
