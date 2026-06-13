package com.andrives.geosnap_cam.ui.screen.preview

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun MediaPreviewScreen(
    initialPath: String,
    initialIsVideo: Boolean,
    sessionPaths: List<String>,
    sessionIsVideo: List<Boolean>,
    onBack: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Initialize ViewModel on first composition
    LaunchedEffect(initialPath) {
        viewModel.initialize(initialPath, initialIsVideo, sessionPaths, sessionIsVideo)
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.activeIndex.coerceAtLeast(0),
        pageCount = { sessionPaths.size.coerceAtLeast(1) },
    )

    // Sync pager page with ViewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    // Background fades with drag
    val bgAlpha = (1f - (uiState.dragY / 500f)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha)),
    ) {
        // ── Media carousel ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = uiState.dragY
                    scaleX = uiState.dragScale
                    scaleY = uiState.dragScale
                }
                .pointerInput(Unit) {
                    detectTapGestures { viewModel.toggleChrome() }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (viewModel.shouldDismiss(0f)) onBack()
                            else viewModel.snapBack()
                        },
                        onVerticalDrag = { _, dragAmount ->
                            viewModel.onDragUpdate(dragAmount)
                        }
                    )
                },
        ) {
            if (sessionPaths.isEmpty()) {
                SingleMediaView(
                    path = initialPath,
                    isVideo = initialIsVideo,
                    onPlaybackChanged = viewModel::onPlaybackStateChanged,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val path = sessionPaths.getOrElse(page) { initialPath }
                    val isVideo = sessionIsVideo.getOrElse(page) { false }
                    SingleMediaView(
                        path = path,
                        isVideo = isVideo && page == pagerState.currentPage,
                        onPlaybackChanged = if (page == pagerState.currentPage)
                            viewModel::onPlaybackStateChanged else null,
                    )
                }
            }
        }

        // ── Top bar ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(icon = Icons.Default.ArrowBack, onClick = onBack)
                GlassIconButton(icon = Icons.Default.PhotoLibrary, onClick = viewModel::openSystemGallery)
            }
        }

        // ── Bottom chrome panel ───────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.chromeVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomChromePanel(
                isVideo = uiState.currentIsVideo,
                isPlaying = uiState.isPlaying,
                positionMs = uiState.videoPositionMs,
                durationMs = uiState.videoDurationMs,
                onShare = viewModel::shareCurrentFile,
                onDelete = viewModel::showDeleteDialog,
                onTogglePlay = {
                    // Toggle is handled by ExoPlayer inside SingleMediaView
                    // We just request the toggle via state
                    viewModel.onPlaybackStateChanged(
                        !uiState.isPlaying, uiState.videoPositionMs, uiState.videoDurationMs
                    )
                },
                onSeek = { ms -> viewModel.onSeekRequest(ms) },
            )
        }

        // ── Delete confirmation dialog ─────────────────────────────────────
        if (uiState.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDeleteDialog,
                title = { Text("Eliminar") },
                text = {
                    Text(
                        if (uiState.currentIsVideo)
                            "¿Eliminar este video? Esta acción no se puede deshacer."
                        else
                            "¿Eliminar esta foto? Esta acción no se puede deshacer."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val deleted = viewModel.deleteCurrentFile()
                            if (deleted) onBack()
                        },
                    ) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDeleteDialog) {
                        Text("Cancelar")
                    }
                },
                containerColor = Color(0xFF1C1C1E),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

// ── Single media view (photo or video) ───────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
private fun SingleMediaView(
    path: String,
    isVideo: Boolean,
    onPlaybackChanged: ((Boolean, Long, Long) -> Unit)? = null,
) {
    if (isVideo) {
        VideoPlayerView(path = path, onPlaybackChanged = onPlaybackChanged)
    } else {
        AsyncImage(
            model = File(path),
            contentDescription = "Captura",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerView(
    path: String,
    onPlaybackChanged: ((Boolean, Long, Long) -> Unit)?,
) {
    val context = LocalContext.current
    val exoPlayer = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    // Report playback state back to ViewModel
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackChanged?.invoke(
                    isPlaying,
                    exoPlayer.currentPosition,
                    exoPlayer.duration.coerceAtLeast(0L),
                )
            }
        })
    }

    DisposableEffect(path) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false  // We use our own controls
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ── Bottom chrome panel ───────────────────────────────────────────────────────

@Composable
private fun BottomChromePanel(
    isVideo: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Video controls
            if (isVideo) {
                VideoControlsRow(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = onTogglePlay,
                    onSeek = onSeek,
                )
            }

            // Action row: share | play/pause | delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    GlassIconButton(icon = Icons.Default.Share, onClick = onShare)
                }

                if (isVideo) {
                    GlassIconButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        onClick = onTogglePlay,
                    )
                } else {
                    Spacer(Modifier.size(56.dp))
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    GlassIconButton(
                        icon = Icons.Default.DeleteOutline,
                        onClick = onDelete,
                        tint = Color(0xFFFF3B30),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoControlsRow(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatMs(positionMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(formatMs(durationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }

        // Seek slider
        if (durationMs > 0L) {
            Slider(
                value = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f),
                onValueChange = { onSeek((it * durationMs).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
