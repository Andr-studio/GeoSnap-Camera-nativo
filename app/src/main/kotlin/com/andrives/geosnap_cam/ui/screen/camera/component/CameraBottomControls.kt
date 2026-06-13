package com.andrives.geosnap_cam.ui.screen.camera.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.andrives.geosnap_cam.ui.screen.camera.CameraMode
import com.andrives.geosnap_cam.ui.screen.camera.ProcessingState

/**
 * Bottom control bar with mode selector, shutter button, gallery thumbnail, and camera switch.
 */
@Composable
fun CameraBottomControls(
    mode: CameraMode,
    processingState: ProcessingState,
    isRecording: Boolean,
    recordingSeconds: Int,
    lastCapturedPath: String?,
    lastCapturedIsVideo: Boolean,
    showControls: Boolean,
    iconRotationDegrees: Float,
    onModeTap: (CameraMode) -> Unit,
    onShutterTap: () -> Unit,
    onSwitchCamera: () -> Unit,
    onGalleryTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Recording timer
        AnimatedVisibility(visible = isRecording) {
            RecordingTimer(seconds = recordingSeconds)
        }

        // Mode selector (Photo / Video)
        AnimatedVisibility(
            visible = showControls && !isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ModeSelectorRow(
                selectedMode = mode,
                onSelect = onModeTap,
            )
        }

        // Main row: gallery | shutter | switch camera
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Gallery thumbnail
            GalleryThumbnail(
                path = lastCapturedPath,
                isVideo = lastCapturedIsVideo,
                isProcessing = processingState == ProcessingState.PROCESSING,
                rotationDegrees = iconRotationDegrees,
                onClick = onGalleryTap,
            )

            // Shutter button
            ShutterButton(
                mode = mode,
                isRecording = isRecording,
                isProcessing = processingState == ProcessingState.PROCESSING,
                onClick = onShutterTap,
            )

            // Switch camera button
            AnimatedVisibility(
                visible = showControls && !isRecording,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                CameraIconButton(
                    icon = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Cambiar cámara",
                    onClick = onSwitchCamera,
                    modifier = Modifier.rotate(iconRotationDegrees),
                )
            }
            if (!showControls || isRecording) {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun ShutterButton(
    mode: CameraMode,
    isRecording: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isProcessing) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shutter_scale",
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape),
        )
        // Inner button
        AnimatedContent(
            targetState = when {
                isRecording -> "stop"
                mode == CameraMode.VIDEO -> "video"
                else -> "photo"
            },
            label = "shutter_inner",
        ) { state ->
            when (state) {
                "stop" -> Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFF3B30)),
                )
                "video" -> Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30)),
                )
                else -> Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun ModeSelectorRow(
    selectedMode: CameraMode,
    onSelect: (CameraMode) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeTab(label = "FOTO", selected = selectedMode == CameraMode.PHOTO) {
            onSelect(CameraMode.PHOTO)
        }
        ModeTab(label = "VIDEO", selected = selectedMode == CameraMode.VIDEO) {
            onSelect(CameraMode.VIDEO)
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
        label = "mode_tab_color",
    )
    Text(
        text = label,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun GalleryThumbnail(
    path: String?,
    isVideo: Boolean,
    isProcessing: Boolean,
    rotationDegrees: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .rotate(rotationDegrees)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else if (path != null) {
            AsyncImage(
                model = path,
                contentDescription = "Última captura",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isVideo) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Icon(Icons.Default.Photo, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun RecordingTimer(seconds: Int) {
    val minutes = seconds / 60
    val secs = seconds % 60
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFF3B30))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Text(
            text = "%02d:%02d".format(minutes, secs),
            color = Color.White,
            fontSize = 15.sp,
        )
    }
}
