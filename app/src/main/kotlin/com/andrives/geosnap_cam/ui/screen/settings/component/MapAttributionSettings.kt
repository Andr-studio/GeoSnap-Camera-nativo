package com.andrives.geosnap_cam.ui.screen.settings.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.runtime.Composable
import com.andrives.geosnap_cam.data.model.WatermarkConfig

@Composable
fun GoogleAttributionColorSetting(
    config: WatermarkConfig,
    onColorChanged: (Int) -> Unit,
) {
    ColorPickerTile(
        icon = Icons.Default.Brush,
        title = "Color del logo Google",
        selectedArgb = config.mapAttributionColorValue,
        onSelected = onColorChanged,
        colors = listOf(
            0xFFFFFFFF.toInt(),
            0,
        ),
        allowCustomColor = false,
    )
}
