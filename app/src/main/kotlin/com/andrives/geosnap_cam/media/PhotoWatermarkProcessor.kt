package com.andrives.geosnap_cam.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.andrives.geosnap_cam.data.model.LocationData
import com.andrives.geosnap_cam.data.model.WatermarkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PhotoWatermarkProcessor — Overlays watermark PNG on a photo using Android Bitmap/Canvas.
 *
 * Equivalent of Flutter's PhotoWatermarkProcessor (154 lines).
 * Uses Android BitmapFactory + Canvas instead of image_editor plugin.
 * Runs on Dispatchers.IO for background processing.
 */
object PhotoWatermarkProcessor {

    private const val TAG = "PhotoWmProcessor"

    suspend fun apply(
        inputPath: String,
        outputPath: String,
        watermarkBitmap: Bitmap,
        config: WatermarkConfig,
        location: LocationData,
    ): String? = withContext(Dispatchers.IO) {
        val stopwatch = System.currentTimeMillis()

        try {
            // Read original photo and determine logical dimensions
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(inputPath, opts)

            val exif = ExifInterface(inputPath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            // Determine logical width/height based on EXIF orientation
            val isRotated90or270 = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            val logicalWidth = if (isRotated90or270) opts.outHeight else opts.outWidth
            val logicalHeight = if (isRotated90or270) opts.outWidth else opts.outHeight

            // Decode full photo
            val originalBitmap = BitmapFactory.decodeFile(inputPath)
                ?: run {
                    Log.e(TAG, "Failed to decode input image: $inputPath")
                    return@withContext null
                }

            // Apply EXIF rotation to get correctly oriented bitmap
            val rotatedBitmap = applyExifRotation(originalBitmap, orientation)

            // Calculate watermark overlay size and position
            val widthFactor = config.effectiveGlassWidth.coerceIn(0.32, 0.96)
            val targetWidth = (rotatedBitmap.width * widthFactor).toInt()
            val targetHeight = (targetWidth.toDouble() * watermarkBitmap.height / watermarkBitmap.width).toInt()
            val safeWidth = targetWidth.coerceIn(1, rotatedBitmap.width)
            val safeHeight = targetHeight.coerceIn(1, rotatedBitmap.height)

            val x = (rotatedBitmap.width - safeWidth) / 2
            val y = (rotatedBitmap.height - safeHeight - (rotatedBitmap.height * 0.02).toInt())
                .coerceIn(0, rotatedBitmap.height - safeHeight)

            // Scale watermark to target size
            val scaledWatermark = Bitmap.createScaledBitmap(
                watermarkBitmap, safeWidth, safeHeight, true
            )

            // Composite watermark onto photo
            val resultBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(resultBitmap)
            canvas.drawBitmap(scaledWatermark, x.toFloat(), y.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

            // Save result as JPEG
            FileOutputStream(outputPath).use { fos ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }

            // Write EXIF data to output
            writePhotoExif(inputPath, outputPath, location)

            // Cleanup bitmaps
            if (rotatedBitmap !== originalBitmap) rotatedBitmap.recycle()
            originalBitmap.recycle()
            resultBitmap.recycle()
            scaledWatermark.recycle()

            val elapsed = System.currentTimeMillis() - stopwatch
            Log.d(TAG, "⏱️ Photo watermark applied in ${elapsed}ms")

            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "apply failed: ${e.message}", e)
            null
        }
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun writePhotoExif(sourcePath: String, outputPath: String, location: LocationData) {
        try {
            val sourceExif = ExifInterface(sourcePath)
            val outputExif = ExifInterface(outputPath)

            // Copy relevant EXIF attributes from source
            val tagsToCopy = arrayOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_APERTURE_VALUE,
                ExifInterface.TAG_ISO_SPEED,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
            )
            for (tag in tagsToCopy) {
                sourceExif.getAttribute(tag)?.let { outputExif.setAttribute(tag, it) }
            }

            // Reset orientation since we already rotated the image
            outputExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())

            // Write GPS coordinates
            outputExif.setLatLong(location.latitude, location.longitude)
            outputExif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS")

            outputExif.saveAttributes()
        } catch (e: Exception) {
            Log.e(TAG, "writePhotoExif failed: ${e.message}")
            // Don't fail the photo save flow for EXIF errors
        }
    }
}
