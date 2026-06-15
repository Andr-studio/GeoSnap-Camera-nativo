package com.andrives.geosnap_cam.ui.screen.camera.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val FocusYellow = Color(0xFFFFC928)

// Ajusta estas medidas para cambiar el tamano del control AE/AF.
private val FocusRingSize = 64.dp
private val FocusRingStroke = 1.6.dp
private val LockIconSize = 18.dp
private val ExposureWidth = 132.dp
private val ExposureTouchHeight = 42.dp
private val ExposureOffsetY = 40.dp
private val ExposureTravel = 88.dp
private val SunSize = 20.dp
private val SunLineGap = 13.dp

@Composable
fun FocusExposureOverlay(
    visible: Boolean,
    isLocked: Boolean,
    isActive: Boolean,
    x: Float,
    y: Float,
    exposureRatio: Float,
    onExposureChange: (Float) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(100)) + scaleIn(tween(180), initialScale = 1.22f),
        exit = fadeOut(tween(180)),
    ) {
        val targetAlpha = if (isActive || !isLocked) 1f else 0.38f
        val alpha by animateFloatAsState(targetAlpha, tween(220), label = "focusIdleAlpha")
        val color = if (isLocked) FocusYellow else Color.White

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val rawX = with(density) { x.toDp() }
            val rawY = with(density) { y.toDp() }
            val centerX = rawX.coerceIn(50.dp, maxWidth - 50.dp)
            val centerY = rawY.coerceIn(70.dp, maxHeight - 115.dp)

            FocusRing(
                color = color,
                isLocked = isLocked,
                alpha = alpha,
                centerX = centerX,
                centerY = centerY,
            )
            ExposureControl(
                color = color,
                alpha = alpha,
                centerX = centerX,
                centerY = centerY,
                exposureRatio = exposureRatio,
                onExposureChange = onExposureChange,
                onInteraction = onInteraction,
            )
        }
    }
}

@Composable
private fun FocusRing(
    color: Color,
    isLocked: Boolean,
    alpha: Float,
    centerX: Dp,
    centerY: Dp,
) {
    Canvas(
        Modifier
            .offset(
                centerX - FocusRingSize / 2,
                centerY - FocusRingSize / 2,
            )
            .size(FocusRingSize)
            .alpha(alpha),
    ) {
        drawCircle(color, style = Stroke(width = FocusRingStroke.toPx()))
    }

    if (isLocked) {
        Canvas(
            Modifier
                .offset(
                    centerX - LockIconSize / 2,
                    centerY - FocusRingSize / 2 - LockIconSize * 0.72f,
                )
                .size(LockIconSize)
                .alpha(alpha),
        ) {
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * 0.25f, size.height * 0.05f),
                size = Size(size.width * 0.5f, size.height * 0.6f),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.16f, size.height * 0.42f),
                size = Size(size.width * 0.68f, size.height * 0.5f),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ExposureControl(
    color: Color,
    alpha: Float,
    centerX: Dp,
    centerY: Dp,
    exposureRatio: Float,
    onExposureChange: (Float) -> Unit,
    onInteraction: () -> Unit,
) {
    val currentRatio by rememberUpdatedState(exposureRatio)
    val currentOnChange by rememberUpdatedState(onExposureChange)
    val currentOnInteraction by rememberUpdatedState(onInteraction)
    Box(
        modifier = Modifier
            .offset(centerX - ExposureWidth / 2, centerY + ExposureOffsetY)
            .size(ExposureWidth, ExposureTouchHeight)
            .alpha(alpha)
            .pointerInput(Unit) {
                var dragRatio = currentRatio
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragRatio = currentRatio
                        currentOnInteraction()
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        currentOnInteraction()
                        dragRatio = (dragRatio + amount / size.width).coerceIn(0f, 1f)
                        currentOnChange(dragRatio)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val margin = 7.dp.toPx()
            val sunCenterX = size.width / 2f +
                (exposureRatio - 0.5f) * ExposureTravel.toPx()
            val gap = SunLineGap.toPx()
            val leftEnd = sunCenterX - gap
            val rightStart = sunCenterX + gap

            if (leftEnd > margin) {
                drawLine(
                    color.copy(alpha = 0.9f),
                    Offset(margin, centerY),
                    Offset(leftEnd, centerY),
                    strokeWidth = 1.3.dp.toPx(),
                )
            }
            if (rightStart < size.width - margin) {
                drawLine(
                    color.copy(alpha = 0.9f),
                    Offset(rightStart, centerY),
                    Offset(size.width - margin, centerY),
                    strokeWidth = 1.3.dp.toPx(),
                )
            }
        }

        SunIcon(
            color = color,
            modifier = Modifier.offset(x = ExposureTravel * (exposureRatio - 0.5f)),
        )
    }
}

@Composable
private fun SunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(SunSize)) {
        val center = this.center
        val radius = 4.dp.toPx()
        drawCircle(color, radius, center)
        repeat(8) { index ->
            val angle = index * Math.PI / 4
            val inner = radius + 2.5.dp.toPx()
            val outer = radius + 5.dp.toPx()
            drawLine(
                color,
                Offset(
                    center.x + (Math.cos(angle) * inner).toFloat(),
                    center.y + (Math.sin(angle) * inner).toFloat(),
                ),
                Offset(
                    center.x + (Math.cos(angle) * outer).toFloat(),
                    center.y + (Math.sin(angle) * outer).toFloat(),
                ),
                strokeWidth = 1.3.dp.toPx(),
            )
        }
    }
}
