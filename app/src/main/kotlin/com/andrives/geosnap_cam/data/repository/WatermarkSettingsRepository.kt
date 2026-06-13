package com.andrives.geosnap_cam.data.repository

import android.content.SharedPreferences
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
            template = WatermarkTemplateType.fromKey(
                prefs.getString("wm_template", null) ?: WatermarkTemplateType.CRYSTAL.key
            ),
            mapType = WatermarkMapType.fromKey(
                prefs.getString("wm_mapType", null) ?: WatermarkMapType.STANDARD.key
            ),
            titleScale = prefs.getFloat("wm_titleScale", 0.55f).toDouble()
                .coerceIn(0.4, 1.6),
            textScale = prefs.getFloat("wm_textScale", 0.65f).toDouble()
                .coerceIn(0.4, 1.6),
            glassOpacity = prefs.getFloat("wm_glassOpacity", 0.55f).toDouble()
                .coerceIn(0.0, 1.0),
            glassWidth = prefs.getFloat("wm_glassWidth", 1.0f).toDouble()
                .coerceIn(0.5, 1.0),
            titleColorValue = prefs.getInt("wm_titleColorValue", 0xFFFFFFFF.toInt()),
            textColorValue = prefs.getInt("wm_textColorValue", 0xFFFFFFFF.toInt()),
            glassColorValue = prefs.getInt("wm_glassColorValue", 0xFF070707.toInt()),
            mapAttributionScale = prefs.getFloat("wm_mapAttributionScale", 1.0f).toDouble()
                .coerceIn(0.7, 2.2),
            mapAttributionOutlineWidth = prefs.getFloat("wm_mapAttributionOutlineWidth", 1.2f).toDouble()
                .coerceIn(0.0, 4.0),
            mapAttributionColorValue = prefs.getInt("wm_mapAttributionColorValue", 0xFFFFFFFF.toInt()),
        )
        _configFlow.value = config
        return config
    }

    fun saveConfig(config: WatermarkConfig) {
        prefs.edit().apply {
            putBoolean("wm_showDate", config.showDate)
            putBoolean("wm_showAddress", config.showAddress)
            putBoolean("wm_showCityCoords", config.showCityCoords)
            putString("wm_template", config.template.key)
            putString("wm_mapType", config.mapType.key)
            putFloat("wm_titleScale", config.titleScale.toFloat())
            putFloat("wm_textScale", config.textScale.toFloat())
            putFloat("wm_glassOpacity", config.glassOpacity.toFloat())
            putFloat("wm_glassWidth", config.glassWidth.toFloat())
            putInt("wm_titleColorValue", config.titleColorValue)
            putInt("wm_textColorValue", config.textColorValue)
            putInt("wm_glassColorValue", config.glassColorValue)
            putFloat("wm_mapAttributionScale", config.mapAttributionScale.toFloat())
            putFloat("wm_mapAttributionOutlineWidth", config.mapAttributionOutlineWidth.toFloat())
            putInt("wm_mapAttributionColorValue", config.mapAttributionColorValue)
            apply()
        }
        _configFlow.value = config
    }
}
