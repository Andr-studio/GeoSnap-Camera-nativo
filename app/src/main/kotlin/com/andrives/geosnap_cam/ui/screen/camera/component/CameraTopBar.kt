package com.andrives.geosnap_cam.ui.screen.camera.component

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrives.geosnap_cam.data.model.LocationData
import com.andrives.geosnap_cam.ui.screen.camera.CameraMode
import com.andrives.geosnap_cam.ui.screen.camera.FlashMode

/**
 * Top bar with GPS status indicator, flash toggle, and settings button.
 */
@Composable
fun CameraTopBar(
    location: LocationData?,
    gpsReady: Boolean,
    flashMode: FlashMode,
    mode: CameraMode,
    isFlashMenuOpen: Boolean,
    isRecording: Boolean,
    iconRotationDegrees: Float,
    onFlashTap: () -> Unit,
    onFlashSelect: (FlashMode) -> Unit,
    onSettingsTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(targetValue = iconRotationDegrees, label = "topBarRotation")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // GPS status pill — left
        GpsStatusPill(
            location = location,
            gpsReady = gpsReady,
            modifier = Modifier.align(Alignment.CenterStart)
        )

            // Right controls row
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Flash button + dropdown
                Box {
                    CameraIconButton(
                        painter = when (flashMode) {
                            FlashMode.ON -> painterResource(com.andrives.geosnap_cam.R.drawable.ic_flash_on_oneui)
                            FlashMode.AUTO -> painterResource(com.andrives.geosnap_cam.R.drawable.ic_flash_auto_oneui)
                            FlashMode.OFF -> painterResource(com.andrives.geosnap_cam.R.drawable.ic_flash_off_oneui)
                        },
                        contentDescription = "Flash",
                        onClick = onFlashTap,
                        modifier = Modifier.rotate(rotation)
                    )

                    DropdownMenu(
                        expanded = isFlashMenuOpen,
                        onDismissRequest = { onFlashSelect(flashMode) },
                        containerColor = Color(0xFF1C1C1E),
                    ) {
                        FlashMenuItem(FlashMode.OFF, "Apagado", painterResource(com.andrives.geosnap_cam.R.drawable.ic_flash_off_oneui), onFlashSelect)
                        FlashMenuItem(FlashMode.ON, "Encendido", painterResource(com.andrives.geosnap_cam.R.drawable.ic_flash_on_oneui), onFlashSelect)
                        if (mode == CameraMode.PHOTO) {
                            FlashMenuItem(FlashMode.AUTO, "Automático", painterResource(com.andrives.geosnap_cam.R.drawable.ic_flash_auto_oneui), onFlashSelect)
                        }
                    }
                }

                // Settings button
                AnimatedVisibility(
                    visible = !isRecording,
                    enter = fadeIn() + slideInHorizontally { it / 2 },
                    exit = fadeOut() + slideOutHorizontally { it / 2 },
                ) {
                    CameraIconButton(
                        icon = Icons.Default.Tune,
                        contentDescription = "Ajustes de marca de agua",
                        onClick = onSettingsTap,
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        }
}

@Composable
private fun FlashMenuItem(
    mode: FlashMode,
    label: String,
    painter: Painter,
    onSelect: (FlashMode) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = Color.White, fontSize = 14.sp) },
        leadingIcon = { Icon(painter = painter, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp)) },
        onClick = { onSelect(mode) },
        colors = MenuDefaults.itemColors(textColor = Color.White),
    )
}

@Composable
private fun GpsStatusPill(
    location: LocationData?,
    gpsReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val dotColor = if (gpsReady) Color(0xFF34C759) else Color(0xFFFF9500)
    val label = if (location != null) {
        location.city.take(14).ifEmpty { "GPS activo" }
    } else {
        "Buscando GPS…"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
