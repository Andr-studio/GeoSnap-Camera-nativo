package com.andrives.geosnap_cam.ui.screen.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrives.geosnap_cam.data.model.LocationData
import com.andrives.geosnap_cam.data.model.WatermarkConfig
import com.andrives.geosnap_cam.data.model.WatermarkMapType
import com.andrives.geosnap_cam.data.model.WatermarkTemplateType
import com.andrives.geosnap_cam.data.repository.WatermarkSettingsRepository
import com.andrives.geosnap_cam.media.WatermarkService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val config: WatermarkConfig = WatermarkConfig(),
    val isLoading: Boolean = true,
    val previewMapBitmap: android.graphics.Bitmap? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: WatermarkSettingsRepository,
    private val watermarkService: WatermarkService,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"

        /** Demo location used for preview when GPS location is not available */
        val DEMO_LOCATION = LocationData(
            latitude = -23.604062,
            longitude = -70.377349,
            address = "Pasaje Ejemplo 1234",
            city = "Ejemplopolis",
            region = "Ejemploregion",
            country = "Ejemplopais",
            countryCode = "CL",
            postalCode = "1234567",
            timezone = "America/Santiago",
            temperatureC = 17.9,
            windKmh = 9.1,
            uvIndex = 0.0,
        )
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var currentLocation: LocationData? = null

    init {
        loadConfig()
    }

    fun setCurrentLocation(location: LocationData?) {
        currentLocation = location
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val config = settingsRepo.getConfig()
            _uiState.update { it.copy(config = config, isLoading = false) }
            prewarmAssets(config)
        }
    }

    fun updateConfig(newConfig: WatermarkConfig) {
        _uiState.update { it.copy(config = newConfig) }
        viewModelScope.launch {
            try {
                settingsRepo.saveConfig(newConfig)
                prewarmAssets(newConfig)
            } catch (e: Exception) {
                Log.e(TAG, "updateConfig failed: ${e.message}")
            }
        }
    }

    private suspend fun prewarmAssets(config: WatermarkConfig) {
        val location = currentLocation ?: DEMO_LOCATION
        watermarkService.prewarmWatermarkAssets(location, config)
        val bitmap = watermarkService.getCachedMapImage(location, config)
        _uiState.update { it.copy(previewMapBitmap = bitmap) }
    }

    // ── Config mutation helpers ────────────────────────────────────────────────

    fun setTemplate(template: WatermarkTemplateType) =
        updateConfig(_uiState.value.config.copy(template = template))

    fun setTitleScale(value: Double) =
        updateConfig(_uiState.value.config.copy(titleScale = value))

    fun setTextScale(value: Double) =
        updateConfig(_uiState.value.config.copy(textScale = value))

    fun setGlassWidth(value: Double) =
        updateConfig(_uiState.value.config.copy(glassWidth = value))

    fun setGlassOpacity(value: Double) =
        updateConfig(_uiState.value.config.copy(glassOpacity = value))

    fun setGlassColor(argb: Int) =
        updateConfig(_uiState.value.config.copy(glassColorValue = argb))

    fun setTitleColor(argb: Int) =
        updateConfig(_uiState.value.config.copy(titleColorValue = argb))

    fun setTextColor(argb: Int) =
        updateConfig(_uiState.value.config.copy(textColorValue = argb))

    fun setMapType(mapType: WatermarkMapType) =
        updateConfig(_uiState.value.config.copy(mapType = mapType))

    fun setShowDate(value: Boolean) =
        updateConfig(_uiState.value.config.copy(showDate = value))

    fun setShowAddress(value: Boolean) =
        updateConfig(_uiState.value.config.copy(showAddress = value))

    fun setShowCityCoords(value: Boolean) =
        updateConfig(_uiState.value.config.copy(showCityCoords = value))

    fun setMapAttributionScale(value: Double) =
        updateConfig(_uiState.value.config.copy(mapAttributionScale = value))

    fun setMapAttributionOutlineWidth(value: Double) =
        updateConfig(_uiState.value.config.copy(mapAttributionOutlineWidth = value))

    fun setMapAttributionColor(argb: Int) =
        updateConfig(_uiState.value.config.copy(mapAttributionColorValue = argb))
}
