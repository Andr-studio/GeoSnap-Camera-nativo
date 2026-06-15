package com.andrives.geosnap_cam.data.model

/**
 * Map tile source type for the watermark mini-map.
 */
enum class WatermarkMapType(val key: String) {
    STANDARD("standard"),
    SATELLITE("satellite"),
    TERRAIN("terrain");

    companion object {
        fun fromKey(key: String): WatermarkMapType =
            entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/**
 * Watermark visual template/style.
 */
enum class WatermarkTemplateType(val key: String) {
    CRYSTAL("crystal"),
    PILL("pill"),
    CINEMA("cinema");

    companion object {
        fun fromKey(key: String): WatermarkTemplateType =
            entries.firstOrNull { it.key == key } ?: CRYSTAL
    }
}

/**
 * Complete watermark configuration — persisted in SharedPreferences.
 * Direct equivalent of Flutter's WatermarkConfig class.
 *
 * Uses Kotlin data class for automatic copy(), equals(), hashCode().
 * Default values match the Flutter originals exactly.
 */
data class WatermarkConfig(
    val showDate: Boolean = true,
    val showAddress: Boolean = true,
    val showCityCoords: Boolean = true,
    val showWeather: Boolean = true,
    val template: WatermarkTemplateType = WatermarkTemplateType.CRYSTAL,
    val mapType: WatermarkMapType = WatermarkMapType.STANDARD,
    val titleScale: Double = 0.75,
    val textScale: Double = 0.65,
    val glassOpacity: Double = 0.55,
    val glassWidth: Double = 1.0,
    val titleColorValue: Int = 0xFFFFFFFF.toInt(),
    val textColorValue: Int = 0xFFFFFFFF.toInt(),
    val glassColorValue: Int = 0xFF070707.toInt(),
    val mapAttributionScale: Double = FIXED_MAP_ATTRIBUTION_SCALE,
    val mapAttributionOutlineWidth: Double = FIXED_MAP_ATTRIBUTION_OUTLINE_WIDTH,
    val mapAttributionColorValue: Int = 0xFFFFFFFF.toInt(),
) {
    companion object {
        const val WIDTH_SCALE_FACTOR = 1.0
        const val FIXED_GLASS_WIDTH = 1.0
        const val FIXED_MAP_ATTRIBUTION_SCALE = 2.2
        const val FIXED_MAP_ATTRIBUTION_OUTLINE_WIDTH = 1.3
        const val TITLE_TO_BODY_SCALE = 1.15
    }

    val effectiveGlassWidth: Double
        get() = glassWidth * WIDTH_SCALE_FACTOR
}
