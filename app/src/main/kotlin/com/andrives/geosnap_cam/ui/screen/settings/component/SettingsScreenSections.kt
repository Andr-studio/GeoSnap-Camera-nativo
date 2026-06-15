package com.andrives.geosnap_cam.ui.screen.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrives.geosnap_cam.data.model.WatermarkMapType
import com.andrives.geosnap_cam.data.model.WatermarkTemplateType

@Composable
fun TemplateSection(
    selected: WatermarkTemplateType,
    onTemplateChanged: (WatermarkTemplateType) -> Unit,
) {
    SettingsSectionCard(title = "Plantilla de Diseno") {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            Color(0xFF007AFF).copy(alpha = 0.18f),
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(17.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Estilo visual",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Elige la plantilla de la marca de agua",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                }
            }
            SegmentedControl(
                options = listOf(
                    WatermarkTemplateType.CRYSTAL to "Cristal",
                    WatermarkTemplateType.PILL to "Pildora",
                    WatermarkTemplateType.CINEMA to "Cine",
                ),
                selected = selected,
                onSelect = onTemplateChanged,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ContentSection(
    mapType: WatermarkMapType,
    showWeather: Boolean,
    showDate: Boolean,
    showAddress: Boolean,
    showCoordinates: Boolean,
    onMapTypeChanged: (WatermarkMapType) -> Unit,
    onShowWeatherChanged: (Boolean) -> Unit,
    onShowDateChanged: (Boolean) -> Unit,
    onShowAddressChanged: (Boolean) -> Unit,
    onShowCoordinatesChanged: (Boolean) -> Unit,
) {
    SettingsSectionCard(title = "Contenido") {
        SettingTile(
            icon = Icons.Default.Map,
            title = "Vista del mapa",
            subtitle = "Elige el estilo del mapa dentro de la marca",
            stackTrailing = true,
            trailing = {
                SegmentedControl(
                    options = listOf(
                        WatermarkMapType.STANDARD to "Mapa",
                        WatermarkMapType.SATELLITE to "Sat",
                        WatermarkMapType.TERRAIN to "Rel",
                    ),
                    selected = mapType,
                    onSelect = onMapTypeChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
        SettingsDivider()
        ToggleSettingTile(
            icon = Icons.Default.Cloud,
            title = "Mostrar clima",
            checked = showWeather,
            onCheckedChange = onShowWeatherChanged,
        )
        SettingsDivider()
        ToggleSettingTile(
            icon = Icons.Default.CalendarToday,
            title = "Mostrar fecha y hora",
            checked = showDate,
            onCheckedChange = onShowDateChanged,
        )
        SettingsDivider()
        ToggleSettingTile(
            icon = Icons.Default.Place,
            title = "Mostrar direccion y codigo postal",
            checked = showAddress,
            onCheckedChange = onShowAddressChanged,
        )
        SettingsDivider()
        ToggleSettingTile(
            icon = Icons.Default.MyLocation,
            title = "Mostrar latitud y longitud",
            checked = showCoordinates,
            onCheckedChange = onShowCoordinatesChanged,
        )
    }
}
