package com.andrives.geosnap_cam.ui.screen.settings.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrives.geosnap_cam.data.model.LocationData
import com.andrives.geosnap_cam.data.model.WatermarkConfig
import com.andrives.geosnap_cam.media.WatermarkRenderer
import java.util.Date

/**
 * Live preview card that renders the watermark using WatermarkRenderer.
 * Updates in real-time as the user changes settings.
 */
@Composable
fun SettingsPreviewCard(
    config: WatermarkConfig,
    location: LocationData,
    mapBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0D0D0D))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        WatermarkPreviewCanvas(
            config = config,
            location = location,
            mapBitmap = mapBitmap,
        )
    }
}

@Composable
private fun WatermarkPreviewCanvas(
    config: WatermarkConfig,
    location: LocationData,
    mapBitmap: Bitmap?,
) {
    // Render the watermark bitmap
    val previewBitmap = remember(config, location, mapBitmap) {
        try {
            val renderer = WatermarkRenderer(
                location = location,
                config = config,
                date = Date(),
                mapImage = mapBitmap,
                canvasWidth = WatermarkRenderer.DEFAULT_CANVAS_WIDTH,
            )
            renderer.renderToBitmap(scale = 1f)
        } catch (e: Exception) {
            null
        }
    }

    if (previewBitmap != null) {
        val imageBitmap = previewBitmap.asImageBitmap()
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 8.dp),
        ) {
            val scale = size.width / imageBitmap.width.toFloat()
            val scaledHeight = imageBitmap.height * scale

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(
                    previewBitmap,
                    android.graphics.Matrix().apply {
                        postScale(scale, scale)
                        postTranslate(0f, (size.height - scaledHeight) / 2f)
                    },
                    null,
                )
            }
        }
    } else {
        Text(
            text = "Vista previa",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 14.sp,
        )
    }
}
