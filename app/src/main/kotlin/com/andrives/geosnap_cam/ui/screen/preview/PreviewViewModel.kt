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
        val index = sessionPaths.indexOf(mediaPath).let {
            if (it >= 0) it else (sessionPaths.size - 1).coerceAtLeast(0)
        }
        _uiState.update {
            it.copy(
                currentPath = mediaPath,
                currentIsVideo = isVideo,
                activeIndex = index,
                sessionPaths = sessionPaths,
                sessionIsVideo = sessionIsVideo,
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

    fun onSeekRequest(positionMs: Long): Long = positionMs  // pass-through; ExoPlayer handles actual seek

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
            File(path).delete()
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
            val file = File(path)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
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
}
