package com.andrives.geosnap_cam.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaSaver — Saves watermarked photos and videos to the device gallery.
 *
 * Uses MediaStore API for Android 10+ (scoped storage) with
 * fallback to direct file write for Android 9.
 */
@Singleton
class MediaSaver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "MediaSaver"
        private const val ALBUM_NAME = "GeoSnap Cam"
    }

    /**
     * Save a media file to the gallery. Returns the content URI on success.
     */
    suspend fun saveToGallery(
        filePath: String,
        isVideo: Boolean,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist: $filePath")
                return@withContext null
            }

            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val extension = if (isVideo) ".mp4" else ".jpg"
            val fileName = "GeoSnap_${System.currentTimeMillis()}$extension"
            val relativePath = if (isVideo) {
                "${Environment.DIRECTORY_MOVIES}/$ALBUM_NAME"
            } else {
                "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage (Android 10+)
                val collection = if (isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(collection, values)
                    ?: run {
                        Log.e(TAG, "Failed to create MediaStore entry")
                        return@withContext null
                    }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream, bufferSize = 8192)
                    }
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)

                Log.d(TAG, "Saved to gallery: $uri")
                uri
            } else {
                // Legacy storage (Android 9)
                val dir = if (isVideo) {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ALBUM_NAME)
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_NAME)
                }
                dir.mkdirs()

                val destFile = File(dir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)

                // Notify media scanner
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                }

                val collection = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val uri = context.contentResolver.insert(collection, values)
                Log.d(TAG, "Saved to gallery (legacy): $uri")
                uri
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed: ${e.message}", e)
            null
        }
    }
}
