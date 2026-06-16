package com.andrives.geosnap_cam.data.repository

import android.content.SharedPreferences
import com.andrives.geosnap_cam.data.model.VolumeButtonBehavior
import com.andrives.geosnap_cam.data.model.WatermarkConfig
import com.andrives.geosnap_cam.data.model.WatermarkMapType
import com.andrives.geosnap_cam.data.model.WatermarkTemplateType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads/writes WatermarkConfig to SharedPreferences.
 *
 * Uses the SAME keys as the Flutter version so that if the user has
 * existing settings from the Flutter app, they carry over seamlessly.
 */
@Singleton
class WatermarkSettingsRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {
    private val _configFlow = MutableStateFlow(WatermarkConfig())
    val configFlow: StateFlow<WatermarkConfig> = _configFlow.asStateFlow()

    fun getConfig(): WatermarkConfig {
        val config = WatermarkConfig(
            showDate = prefs.getBoolean("wm_showDate", true),
            showAddress = prefs.getBoolean("wm_showAddress", true),
            showCityCoords = prefs.getBoolean("wm_showCityCoords", true),
            showWeather = prefs.getBoolean("wm_showWeather", true),
            template = WatermarkTemplateType.fromKey(
                prefs.getString("wm_template", null) ?: WatermarkTemplateType.CRYSTAL.key
            ),
            mapType = WatermarkMapType.fromKey(
                prefs.getString("wm_mapType", null) ?: WatermarkMapType.STANDARD.key
            ),
            textScale = prefs.getFloat("wm_textScale", 0.65f).toDouble()
                .coerceIn(0.4, 1.6),
            titleScale = (prefs.getFloat("wm_textScale", 0.65f).toDouble() *
                WatermarkConfig.TITLE_TO_BODY_SCALE).coerceIn(0.4, 1.6),
            glassOpacity = prefs.getFloat("wm_glassOpacity", 0.55f).toDouble()
                .coerceIn(0.0, 1.0),
            glassWidth = WatermarkConfig.FIXED_GLASS_WIDTH,
            titleColorValue = prefs.getInt("wm_titleColorValue", 0xFFFFFFFF.toInt()),
            textColorValue = prefs.getInt("wm_textColorValue", 0xFFFFFFFF.toInt()),
            glassColorValue = prefs.getInt("wm_glassColorValue", 0xFF070707.toInt()),
            mapAttributionScale = WatermarkConfig.FIXED_MAP_ATTRIBUTION_SCALE,
            mapAttributionOutlineWidth = WatermarkConfig.FIXED_MAP_ATTRIBUTION_OUTLINE_WIDTH,
            mapAttributionColorValue = prefs.getInt("wm_mapAttributionColorValue", 0xFFFFFFFF.toInt()),
            volumeButtonBehavior = VolumeButtonBehavior.fromKey(
                prefs.getString("app_volumeButtonBehavior", null) ?: VolumeButtonBehavior.BOTH.key
            ),
        )
        _configFlow.value = config
        return config
    }

    fun saveConfig(config: WatermarkConfig) {
        prefs.edit().apply {
            putBoolean("wm_showDate", config.showDate)
            putBoolean("wm_showAddress", config.showAddress)
            putBoolean("wm_showCityCoords", config.showCityCoords)
            putBoolean("wm_showWeather", config.showWeather)
            putString("wm_template", config.template.key)
            putString("wm_mapType", config.mapType.key)
            putFloat("wm_titleScale", config.titleScale.toFloat())
            putFloat("wm_textScale", config.textScale.toFloat())
            putFloat("wm_glassOpacity", config.glassOpacity.toFloat())
            putFloat("wm_glassWidth", WatermarkConfig.FIXED_GLASS_WIDTH.toFloat())
            putInt("wm_titleColorValue", config.titleColorValue)
            putInt("wm_textColorValue", config.textColorValue)
            putInt("wm_glassColorValue", config.glassColorValue)
            putFloat("wm_mapAttributionScale", WatermarkConfig.FIXED_MAP_ATTRIBUTION_SCALE.toFloat())
            putFloat("wm_mapAttributionOutlineWidth", WatermarkConfig.FIXED_MAP_ATTRIBUTION_OUTLINE_WIDTH.toFloat())
            putInt("wm_mapAttributionColorValue", config.mapAttributionColorValue)
            putString("app_volumeButtonBehavior", config.volumeButtonBehavior.key)
            apply()
        }
        _configFlow.value = config
    }
}
