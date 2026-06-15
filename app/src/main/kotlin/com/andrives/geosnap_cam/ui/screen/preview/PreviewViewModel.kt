package com.andrives.geosnap_cam.ui.screen.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class PreviewUiState(
    val currentPath: String = "",
    val currentIsVideo: Boolean = false,
    val activeIndex: Int = 0,
    val sessionPaths: List<String> = emptyList(),
    val sessionIsVideo: List<Boolean> = emptyList(),
    val isPlaying: Boolean = false,
    val videoPositionMs: Long = 0L,
    val videoDurationMs: Long = 0L,
    val chromeVisible: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val dragY: Float = 0f,
    val dragScale: Float = 1f,
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "PreviewViewModel"
        private const val DRAG_DISMISS_THRESHOLD = 150f
        private const val DRAG_VELOCITY_THRESHOLD = 800f
        private const val CHROME_AUTOHIDE_MS = 4_000L
        private const val ALBUM_NAME = "GeoSnap Cam"
    }

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    private var autohideJob: kotlinx.coroutines.Job? = null

    fun initialize(
        mediaPath: String,
        isVideo: Boolean,
        sessionPaths: List<String>,
        sessionIsVideo: List<Boolean>,
    ) {
        val library = loadPrivateGeoSnapLibrary(mediaPath, isVideo, sessionPaths, sessionIsVideo)
        val paths = library.map { it.path }
        val videos = library.map { it.isVideo }
        val index = paths.indexOf(mediaPath).coerceAtLeast(0)
        val currentPath = paths.getOrElse(index) { mediaPath }
        val currentIsVideo = videos.getOrElse(index) { isVideo }

        _uiState.update {
            it.copy(
                currentPath = currentPath,
                currentIsVideo = currentIsVideo,
                activeIndex = 0,
                sessionPaths = paths,
                sessionIsVideo = videos,
            )
        }
        scheduleAutohide()
    }

    fun onPageChanged(index: Int) {
        val state = _uiState.value
        if (index >= state.sessionPaths.size) return
        val path = state.sessionPaths[index]
        val isVideo = state.sessionIsVideo.getOrElse(index) { false }
        _uiState.update {
            it.copy(
                activeIndex = index,
                currentPath = path,
                currentIsVideo = isVideo,
                isPlaying = false,
                videoPositionMs = 0L,
                videoDurationMs = 0L,
            )
        }
        scheduleAutohide()
    }

    // ── Video playback state (managed externally by ExoPlayer, reported back here) ──

    fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        _uiState.update {
            it.copy(
                isPlaying = isPlaying,
                videoPositionMs = positionMs,
                videoDurationMs = durationMs,
            )
        }
    }

    fun onSeekRequest(positionMs: Long) {
        _uiState.update { it.copy(videoPositionMs = positionMs) }
    }

    // ── Chrome visibility ─────────────────────────────────────────────────────

    fun toggleChrome() {
        val visible = !_uiState.value.chromeVisible
        _uiState.update { it.copy(chromeVisible = visible) }
        if (visible) scheduleAutohide()
    }

    private fun scheduleAutohide() {
        autohideJob?.cancel()
        autohideJob = viewModelScope.launch {
            delay(CHROME_AUTOHIDE_MS)
            if (isActive) _uiState.update { it.copy(chromeVisible = false) }
        }
    }

    // ── Swipe to dismiss ──────────────────────────────────────────────────────

    fun onDragUpdate(dy: Float) {
        val newDragY = _uiState.value.dragY + dy
        val progress = (newDragY.coerceAtLeast(0f) / 400f).coerceIn(0f, 1f)
        val scale = 1f - (progress * 0.3f)
        _uiState.update {
            it.copy(dragY = newDragY, dragScale = scale, chromeVisible = false)
        }
        autohideJob?.cancel()
    }

    fun shouldDismiss(velocityY: Float): Boolean {
        val dragY = _uiState.value.dragY
        return dragY > DRAG_DISMISS_THRESHOLD || velocityY > DRAG_VELOCITY_THRESHOLD
    }

    fun snapBack() {
        _uiState.update { it.copy(dragY = 0f, dragScale = 1f) }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteCurrentFile(): Boolean {
        return try {
            val path = _uiState.value.currentPath
            if (path.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(path), null, null) > 0
            } else {
                File(path).delete()
            }
            _uiState.update { it.copy(showDeleteDialog = false) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteCurrentFile failed: ${e.message}")
            false
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    fun shareCurrentFile() {
        val path = _uiState.value.currentPath
        val isVideo = _uiState.value.currentIsVideo
        try {
            val uri = path.toShareUri()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (isVideo) "video/mp4" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "shareCurrentFile failed: ${e.message}")
        }
    }

    // ── Open system gallery ───────────────────────────────────────────────────

    fun openSystemGallery() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openSystemGallery failed: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        autohideJob?.cancel()
    }

    private data class MediaEntry(
        val path: String,
        val isVideo: Boolean,
        val modifiedAt: Long,
    )

    private fun loadPrivateGeoSnapLibrary(
        mediaPath: String,
        isVideo: Boolean,
        sessionPaths: List<String>,
        sessionIsVideo: List<Boolean>,
    ): List<MediaEntry> {
        val entries = linkedMapOf<String, MediaEntry>()

        sessionPaths.forEachIndexed { index, path ->
            if (path.isBlank()) return@forEachIndexed
            val file = path.takeUnless { it.startsWith("content://") }?.let(::File)
            val modifiedAt = file?.takeIf { it.exists() }?.lastModified() ?: System.currentTimeMillis()
            entries[path] = MediaEntry(
                path = path,
                isVideo = sessionIsVideo.getOrElse(index) { path.isVideoPath() },
                modifiedAt = modifiedAt,
            )
        }

        scanAppMediaDir().forEach { entries[it.path] = it }

        if (!entries.containsKey(mediaPath)) {
            val file = mediaPath.takeUnless { it.startsWith("content://") }?.let(::File)
            entries[mediaPath] = MediaEntry(
                path = mediaPath,
                isVideo = isVideo,
                modifiedAt = file?.takeIf { it.exists() }?.lastModified() ?: System.currentTimeMillis(),
            )
        }

        val currentEntry = entries[mediaPath]
        val orderedEntries = entries.values
            .filter { it.path.startsWith("content://") || File(it.path).exists() }
            .sortedByDescending { it.modifiedAt }

        return if (currentEntry != null) {
            listOf(currentEntry) + orderedEntries.filterNot { it.path == mediaPath }
        } else {
            orderedEntries
        }
    }

    private fun scanAppMediaDir(): List<MediaEntry> {
        val dir = File(context.getExternalFilesDir(null), ALBUM_NAME)
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.lowercase(Locale.US) in setOf("jpg", "jpeg", "mp4") }
            ?.map {
                MediaEntry(
                    path = it.absolutePath,
                    isVideo = it.extension.equals("mp4", ignoreCase = true),
                    modifiedAt = it.lastModified(),
                )
            }
            ?.toList()
            .orEmpty()
    }

    private fun String.isVideoPath(): Boolean {
        return endsWith(".mp4", ignoreCase = true) || startsWith("content://media/external/video")
    }

    private fun String.toShareUri(): Uri {
        if (startsWith("content://")) return Uri.parse(this)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(this),
        )
    }
}
