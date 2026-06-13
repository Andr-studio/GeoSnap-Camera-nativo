package com.andrives.geosnap_cam.ui.screen.camera.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Animated focus ring that appears on tap-to-focus.
 */
@Composable
fun FocusRingOverlay(
    visible: Boolean,
    x: Float,
    y: Float,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(120),
        label = "focus_alpha",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = x.dp - 28.dp, y = y.dp - 28.dp)
                .size(56.dp)
                .alpha(alpha)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, Color(0xFFFFCC00), RoundedCornerShape(6.dp)),
        )
    }
}

/**
 * Reusable glass-effect icon button for camera controls.
 */
@Composable
fun CameraIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
