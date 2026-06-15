package com.andrives.geosnap_cam.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.andrives.geosnap_cam.ui.screen.settings.component.ColorPickerTile
import com.andrives.geosnap_cam.ui.screen.settings.component.ContentSection
import com.andrives.geosnap_cam.ui.screen.settings.component.GoogleAttributionColorSetting
import com.andrives.geosnap_cam.ui.screen.settings.component.SettingsDivider
import com.andrives.geosnap_cam.ui.screen.settings.component.SettingsPreviewCard
import com.andrives.geosnap_cam.ui.screen.settings.component.SettingsSectionCard
import com.andrives.geosnap_cam.ui.screen.settings.component.SliderSettingTile
import com.andrives.geosnap_cam.ui.screen.settings.component.TemplateSection

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
        LoadingSettings()
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black, Color(0xFF080C10), Color(0xFF06090B)),
                    ),
                )
                .padding(padding),
        ) {
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
                item {
                    TemplateSection(
                        selected = config.template,
                        onTemplateChanged = viewModel::setTemplate,
                    )
                }
                item {
                    ContentSection(
                        mapType = config.mapType,
                        showWeather = config.showWeather,
                        showDate = config.showDate,
                        showAddress = config.showAddress,
                        showCoordinates = config.showCityCoords,
                        onMapTypeChanged = viewModel::setMapType,
                        onShowWeatherChanged = viewModel::setShowWeather,
                        onShowDateChanged = viewModel::setShowDate,
                        onShowAddressChanged = viewModel::setShowAddress,
                        onShowCoordinatesChanged = viewModel::setShowCityCoords,
                    )
                }
                item {
                    SettingsSectionCard(title = "Apariencia") {
                        SliderSettingTile(
                            icon = Icons.Default.FormatSize,
                            title = "Tamaño de la fuente",
                            valueLabel = "${(config.textScale * 100).toInt()}%",
                            value = config.textScale.toFloat(),
                            min = 0.4f,
                            max = 1.6f,
                            steps = 23,
                            onChanged = { viewModel.setFontScale(it.toDouble()) },
                        )
                        SettingsDivider()
                        SliderSettingTile(
                            icon = Icons.Default.Opacity,
                            title = "Opacidad del cristal",
                            valueLabel = "${(config.glassOpacity * 100).toInt()}%",
                            value = config.glassOpacity.toFloat(),
                            min = 0f,
                            max = 1f,
                            steps = 19,
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
                        SettingsDivider()
                        GoogleAttributionColorSetting(
                            config = config,
                            onColorChanged = viewModel::setMapAttributionColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingSettings() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color(0xFF007AFF))
    }
}
