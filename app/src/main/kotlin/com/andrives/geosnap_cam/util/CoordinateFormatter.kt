package com.andrives.geosnap_cam.util

import java.util.Locale

/**
 * GPS coordinate formatting utilities for the watermark.
 */
object CoordinateFormatter {

    /**
     * Format latitude/longitude to DMS (Degrees Minutes Seconds) format.
     * Example: "23°36'14.6\"S  70°22'38.5\"W"
     */
    fun formatDMS(latitude: Double, longitude: Double): String {
        val latDMS = decimalToDMS(latitude)
        val lonDMS = decimalToDMS(longitude)
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        return "${latDMS}${latDir}  ${lonDMS}${lonDir}"
    }

    /**
     * Format latitude/longitude to decimal format.
     * Example: "-23.604062, -70.377349"
     */
    fun formatDecimal(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    }

    private fun decimalToDMS(decimal: Double): String {
        val absDecimal = kotlin.math.abs(decimal)
        val degrees = absDecimal.toInt()
        val minutesDecimal = (absDecimal - degrees) * 60
        val minutes = minutesDecimal.toInt()
        val seconds = (minutesDecimal - minutes) * 60
        return String.format(Locale.US, "%d°%02d'%04.1f\"", degrees, minutes, seconds)
    }
}
