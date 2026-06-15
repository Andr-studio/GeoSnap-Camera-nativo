package com.andrives.geosnap_cam.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrives.geosnap_cam.data.model.LocationData
import com.andrives.geosnap_cam.data.model.WatermarkMapType
import com.andrives.geosnap_cam.data.model.WatermarkTemplateType
import com.andrives.geosnap_cam.ui.screen.settings.component.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkSettingsScreen(
    currentLocation: LocationData? = null,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(currentLocation) {
        viewModel.setCurrentLocation(currentLocation)
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color(0xFF007AFF))
        }
        return
    }

    val config = uiState.config
    val previewLocation = currentLocation ?: SettingsViewModel.DEMO_LOCATION

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Marca de agua GPS",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    scrolledContainerColor = Color.Black,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black, Color(0xFF080C10), Color(0xFF06090B))
                    )
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // ── Live Preview ───────────────────────────────────────────
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingsPreviewCard(
                        config = config,
                        location = previewLocation,
                        mapBitmap = uiState.previewMapBitmap,
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {

                // ── Template / Style ──────────────────────────────────────
                item {
                    SettingsSectionCard(title = "Plantilla de Diseño") {
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
                                        .background(Color(0xFF007AFF).copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF007AFF), modifier = Modifier.size(17.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Estilo visual", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text("Elige la plantilla de la marca de agua", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                            }

                            SegmentedControl(
                                options = listOf(
                                    WatermarkTemplateType.CRYSTAL to "Cristal",
                                    WatermarkTemplateType.PILL to "Píldora",
                                    WatermarkTemplateType.CINEMA to "Cine",
                                ),
                                selected = config.template,
                                onSelect = viewModel::setTemplate,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // ── Appearance ────────────────────────────────────────────
                item {
                    SettingsSectionCard(title = "Apariencia") {
                        SliderSettingTile(
                            icon = Icons.Default.FormatSize,
                            title = "Tamaño del título",
                            valueLabel = "${(config.titleScale * 100).toInt()}%",
                            value = config.titleScale.toFloat(),
                            min = 0.4f, max = 1.6f, steps = 23,
                            onChanged = { viewModel.setTitleScale(it.toDouble()) },
                        )
                        SettingsDivider()
                        SliderSettingTile(
                            icon = Icons.Default.FormatSize,
                            title = "Tamaño del texto",
                            valueLabel = "${(config.textScale * 100).toInt()}%",
                            value = config.textScale.toFloat(),
                            min = 0.4f, max = 1.6f, steps = 23,
                            onChanged = { viewModel.setTextScale(it.toDouble()) },
                        )
                        SettingsDivider()
                        SliderSettingTile(
                            icon = Icons.Default.AspectRatio,
                            title = "Ancho máximo",
                            valueLabel = "${(config.glassWidth * 100).toInt()}%",
                            value = config.glassWidth.toFloat(),
                            min = 0.5f, max = 1.0f, steps = 9,
                            onChanged = { viewModel.setGlassWidth(it.toDouble()) },
                        )
                        SettingsDivider()
                        SliderSettingTile(
                            icon = Icons.Default.Opacity,
                            title = "Opacidad del cristal",
                            valueLabel = "${(config.glassOpacity * 100).toInt()}%",
                            value = config.glassOpacity.toFloat(),
                            min = 0f, max = 1f, steps = 19,
                            onChanged = { viewModel.setGlassOpacity(it.toDouble()) },
                        )
                        SettingsDivider()
                        ColorPickerTile(
                            icon = Icons.Default.WaterDrop,
                            title = "Color del cristal",
                            selectedArgb = config.glassColorValue,
                            onSelected = viewModel::setGlassColor,
                            colors = listOf(
                                0xFF123A55.toInt(),
                                0xFF2A6F97.toInt(),
                                0xFF070707.toInt(),
                                0xFF4C3F91.toInt(),
                                0xFF255D42.toInt(),
                                0xFF5C4635.toInt(),
                            ),
                        )
                        SettingsDivider()
                        ColorPickerTile(
                            icon = Icons.Default.Palette,
                            title = "Color del título",
                            selectedArgb = config.titleColorValue,
                            onSelected = viewModel::setTitleColor,
                            colors = listOf(
                                0xFFFFFFFF.toInt(),
                                0xFFFFCC00.toInt(),
                                0xFF007AFF.toInt(),
                                0xFF34C759.toInt(),
                                0xFFFF9500.toInt(),
                                0xFFFF3B30.toInt(),
                            ),
                        )
                        SettingsDivider()
                        ColorPickerTile(
                            icon = Icons.Default.TextFormat,
                            title = "Color del texto",
                            selectedArgb = config.textColorValue,
                            onSelected = viewModel::setTextColor,
                            colors = listOf(
                                0xFFFFFFFF.toInt(),
                                0xFFE0E0E0.toInt(),
                                0xFFBDBDBD.toInt(),
                                0xFFFFCC00.toInt(),
                                0xFF007AFF.toInt(),
                                0xFF34C759.toInt(),
                            ),
                        )
                    }
                }

                // ── Map attribution ───────────────────────────────────────
                item {
                    MapAttributionSettings(
                        config = config,
                        onScaleChanged = viewModel::setMapAttributionScale,
                        onOutlineChanged = viewModel::setMapAttributionOutlineWidth,
                        onColorChanged = viewModel::setMapAttributionColor,
                    )
                }

                // ── Content ───────────────────────────────────────────────
                item {
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
                                    selected = config.mapType,
                                    onSelect = viewModel::setMapType,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        )
                        SettingsDivider()
                        ToggleSettingTile(
                            icon = Icons.Default.Cloud,
                            title = "Mostrar clima",
                            checked = config.showWeather,
                            onCheckedChange = viewModel::setShowWeather,
                        )
                        SettingsDivider()
                        ToggleSettingTile(
                            icon = Icons.Default.CalendarToday,
                            title = "Mostrar fecha y hora",
                            checked = config.showDate,
                            onCheckedChange = viewModel::setShowDate,
                        )
                        SettingsDivider()
                        ToggleSettingTile(
                            icon = Icons.Default.Place,
                            title = "Mostrar dirección y código postal",
                            checked = config.showAddress,
                            onCheckedChange = viewModel::setShowAddress,
                        )
                        SettingsDivider()
                        ToggleSettingTile(
                            icon = Icons.Default.MyLocation,
                            title = "Mostrar latitud y longitud",
                            checked = config.showCityCoords,
                            onCheckedChange = viewModel::setShowCityCoords,
                        )
                    }
                }
                }
            }
        }
    }
}
