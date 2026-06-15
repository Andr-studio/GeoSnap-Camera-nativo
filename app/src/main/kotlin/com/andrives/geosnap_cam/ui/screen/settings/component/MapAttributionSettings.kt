package com.andrives.geosnap_cam.ui.screen.settings.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.runtime.Composable
import com.andrives.geosnap_cam.data.model.WatermarkConfig

@Composable
fun MapAttributionSettings(
    config: WatermarkConfig,
    onScaleChanged: (Double) -> Unit,
    onOutlineChanged: (Double) -> Unit,
    onColorChanged: (Int) -> Unit,
) {
    SettingsSectionCard(title = "Marca del Mapa") {
        SliderSettingTile(
            icon = Icons.Default.FormatSize,
            title = "Tamano de atribucion",
            valueLabel = "${(config.mapAttributionScale * 100).toInt()}%",
            value = config.mapAttributionScale.toFloat(),
            min = 0.7f,
            max = 2.2f,
            steps = 14,
            onChanged = { onScaleChanged(it.toDouble()) },
        )
        SettingsDivider()
        SliderSettingTile(
            icon = Icons.Default.Circle,
            title = "Contorno de atribucion",
            valueLabel = "${"%.1f".format(config.mapAttributionOutlineWidth)} px",
            value = config.mapAttributionOutlineWidth.toFloat(),
            min = 0f,
            max = 4f,
            steps = 15,
            onChanged = { onOutlineChanged(it.toDouble()) },
        )
        SettingsDivider()
        ColorPickerTile(
            icon = Icons.Default.Brush,
            title = "Color de atribucion",
            selectedArgb = config.mapAttributionColorValue,
            onSelected = onColorChanged,
            colors = listOf(
                0xFFFFFFFF.toInt(),
                0xFF000000.toInt(),
                0,
                0xFF4285F4.toInt(),
                0xFF34A853.toInt(),
                0xFFFBBC05.toInt(),
                0xFFEA4335.toInt(),
            ),
        )
    }
}
